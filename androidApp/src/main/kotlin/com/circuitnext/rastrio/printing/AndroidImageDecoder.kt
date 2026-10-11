/**
 * Copyright 2026 Darshan <darshan@alchiemy.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/

package com.circuitnext.rastrio.printing

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import com.circuitnext.rastrio.core.raster.DecodedImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface AndroidImageDecodeResult {
    data class Success(val input: ImageInput, val sampled: Boolean) : AndroidImageDecodeResult
    data class Invalid(val code: String, val message: String) : AndroidImageDecodeResult
}

/** Explicit user-selected content only; serialize decode allocations and never retain platform pixels. */
class AndroidImageDecoder(private val resolver: ContentResolver) {
    private val decodeMutex = Mutex()
    suspend fun decode(uri: Uri): AndroidImageDecodeResult = decodeMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                if (uri.scheme != ContentResolver.SCHEME_CONTENT) imageInvalid("IMG100", "Select a local image through the image picker")
                val task = currentCoroutineContext()
                val encoded = readImageStream { resolver.openInputStream(uri) }
                task.ensureActive()
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(encoded, 0, encoded.size, bounds)
                if (bounds.outMimeType != "image/jpeg" && bounds.outMimeType != "image/png")
                    imageInvalid("IMG101", "Only static JPEG and PNG images are supported")
                val sample = imageDecodeSample(bounds.outWidth, bounds.outHeight, encoded.size)
                val pngExif = bounds.outMimeType == "image/png" && inspectPngImage(encoded) { task.ensureActive() }
                val orientation = if (bounds.outMimeType == "image/jpeg" || pngExif) {
                    if (pngExif && (Build.VERSION.SDK_INT < 30 || !ExifInterface.isSupportedMimeType("image/png")))
                        imageInvalid("IMG102", "PNG orientation metadata is unsupported on this Android version")
                    ByteArrayInputStream(encoded).use { stream ->
                        imageOrientation(ExifInterface(stream).getAttribute(ExifInterface.TAG_ORIENTATION))
                    }
                } else 1
                task.ensureActive()
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inMutable = true
                    inScaled = false
                    inPremultiplied = false
                    if (Build.VERSION.SDK_INT >= 26) inPreferredColorSpace =
                        android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB)
                }
                // API24+ native decoding cannot be interrupted; allocation is preflighted and cancellation checked on return.
                val bitmap = BitmapFactory.decodeByteArray(encoded, 0, encoded.size, options)
                    ?: imageInvalid("IMG101", "Image pixels could not be decoded")
                try {
                    task.ensureActive()
                    val pixels = bitmap.width.toLong() * bitmap.height
                    if (bitmap.config != Bitmap.Config.ARGB_8888 || pixels > 16_000_000 ||
                        bitmap.width > (bounds.outWidth.toLong() + sample - 1) / sample ||
                        bitmap.height > (bounds.outHeight.toLong() + sample - 1) / sample ||
                        bitmap.allocationByteCount.toLong() + pixels * 8 + IMAGE_INPUT_LIMIT.toLong() * 2 + IMAGE_DECODE_SCRATCH > IMAGE_DECODE_BUDGET)
                        imageInvalid("IMG120", "Decoded image exceeds working memory policy")
                    val argb = IntArray(pixels.toInt())
                    for (row in 0 until bitmap.height) {
                        task.ensureActive()
                        bitmap.getPixels(argb, row * bitmap.width, bitmap.width, 0, row, bitmap.width, 1)
                    }
                    task.ensureActive()
                    AndroidImageDecodeResult.Success(ImageInput(DecodedImage(bitmap.width, bitmap.height, argb), orientation), sample > 1)
                } finally { bitmap.recycle() }
            } catch (failure: ImageDecodeFailure) {
                AndroidImageDecodeResult.Invalid(failure.code, failure.message ?: "Image import failed")
            } catch (_: SecurityException) {
                AndroidImageDecodeResult.Invalid("IMG100", "Permission to the selected image is unavailable")
            } catch (_: IOException) {
                AndroidImageDecodeResult.Invalid("IMG101", "Selected image or its metadata could not be read")
            } catch (_: IllegalArgumentException) {
                AndroidImageDecodeResult.Invalid("IMG101", "Selected image is invalid")
            }
        }
    }
}

internal suspend fun readImageStream(open: () -> InputStream?): ByteArray = suspendCancellableCoroutine { continuation ->
    val active = AtomicReference<InputStream?>()
    continuation.invokeOnCancellation { try { active.get()?.close() } catch (_: IOException) { } }
    try {
        val input = open() ?: imageInvalid("IMG100", "Selected image is unavailable")
        active.set(input)
        if (!continuation.isActive) { input.close(); return@suspendCancellableCoroutine }
        val bytes = input.use { readImageBytes(it) {
            if (!continuation.isActive) throw kotlinx.coroutines.CancellationException("Image import cancelled")
        } }
        continuation.resume(bytes)
    } catch (failure: Exception) {
        if (continuation.isActive) continuation.resumeWithException(failure)
    } finally { active.set(null) }
}

internal const val IMAGE_INPUT_LIMIT = 32 * 1024 * 1024
internal const val IMAGE_DECODE_BUDGET = 128L * 1024 * 1024
// Reserve codec scratch plus a previous Compose pixel snapshot awaiting disposal.
internal const val IMAGE_DECODE_SCRATCH = 32L * 1024 * 1024

internal fun readImageBytes(input: InputStream, checkCancelled: () -> Unit = {}): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    while (true) {
        checkCancelled()
        val count = input.read(buffer)
        if (count == -1) break
        if (count <= 0) imageInvalid("IMG101", "Image stream made no progress")
        if (count > IMAGE_INPUT_LIMIT - output.size()) imageInvalid("IMG120", "Compressed image exceeds input policy")
        output.write(buffer, 0, count)
    }
    if (output.size() == 0) imageInvalid("IMG101", "Selected image is empty")
    return output.toByteArray()
}

/** Three simultaneous pixel stores plus encoded acquisition/snapshot and codec scratch. */
internal fun imageDecodeSample(width: Int, height: Int, encodedBytes: Int): Int {
    if (width !in 1..32768 || height !in 1..32768 || width.toLong() * height > 100_000_000 ||
        encodedBytes !in 1..IMAGE_INPUT_LIMIT) imageInvalid("IMG120", "Image dimensions or input exceed policy")
    var sample = 1
    while (true) {
        val w = (width.toLong() + sample - 1) / sample
        val h = (height.toLong() + sample - 1) / sample
        if (w * h <= 16_000_000 && w * h * 12 + IMAGE_INPUT_LIMIT.toLong() * 2 + IMAGE_DECODE_SCRATCH <= IMAGE_DECODE_BUDGET)
            return sample
        sample *= 2
    }
}
internal fun imageOrientation(value: String?): Int = if (value == null) 1 else
    value.toIntOrNull()?.takeIf { it in 1..8 } ?: imageInvalid("IMG102", "Invalid image orientation metadata")

/** Inspect bounded PNG chunk framing to reject animation and detect orientation metadata. */
internal fun inspectPngImage(bytes: ByteArray, checkCancelled: () -> Unit = {}): Boolean {
    if (bytes.size < 8 || !bytes.copyOfRange(0, 8).contentEquals(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)))
        imageInvalid("IMG101", "Invalid PNG signature")
    var position = 8
    var exif = false
    while (position < bytes.size) {
        checkCancelled()
        if (bytes.size - position < 12) imageInvalid("IMG101", "Truncated PNG chunk")
        var length = 0L
        for (offset in 0..3) length = (length shl 8) or (bytes[position + offset].toLong() and 255)
        if (length > bytes.size.toLong() - position - 12) imageInvalid("IMG101", "Truncated PNG chunk")
        val tag = String(bytes, position + 4, 4, Charsets.US_ASCII)
        if (tag == "acTL") imageInvalid("IMG101", "Animated images are unsupported")
        if (tag == "eXIf") exif = true
        position += length.toInt() + 12
        if (tag == "IEND") return exif
    }
    imageInvalid("IMG101", "PNG has no end chunk")
}
private class ImageDecodeFailure(val code: String, message: String) : RuntimeException(message)
private fun imageInvalid(code: String, message: String): Nothing = throw ImageDecodeFailure(code, message)
