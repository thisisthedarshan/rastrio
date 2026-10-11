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

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import com.circuitnext.rastrio.core.profile.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal fun readProfileBytes(input: InputStream): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = input.read(buffer, 0, minOf(buffer.size, 1024 * 1024 + 1 - output.size()))
        if (count < 0) break
        require(count > 0) { "Profile stream made no progress" }
        output.write(buffer, 0, count)
        require(output.size() <= 1024 * 1024) { "Profile input limit exceeded" }
    }
    val bytes = output.toByteArray()
    require(bytes.size <= 1024 * 1024) { "Profile input limit exceeded" }
    return bytes
}

internal suspend fun readProfileStream(open: () -> InputStream?): ByteArray = suspendCancellableCoroutine { continuation ->
    val active = AtomicReference<InputStream?>()
    continuation.invokeOnCancellation { try { active.get()?.close() } catch (_: java.io.IOException) { } }
    try {
        val input = open() ?: throw IllegalArgumentException("Unreadable profile")
        active.set(input)
        if (!continuation.isActive) { input.close(); return@suspendCancellableCoroutine }
        val bytes = input.use(::readProfileBytes)
        continuation.resume(bytes)
    } catch (failure: Exception) {
        if (continuation.isActive) continuation.resumeWithException(failure)
    } finally { active.set(null) }
}

/** Private fixed-path storage; acquisition and strict decoding precede publication. */
class AndroidPrintingPlatform(
    private val context: Context,
    private val scope: CoroutineScope,
    private var requestPermission: () -> Unit,
    private var chooseProfile: () -> Unit,
) : PrintingPlatform, ImagePlatform {
    private val instances = PrinterInstanceStore(context)
    private val savedProfile = AtomicFile(File(context.filesDir, "selected-printer.tcfg"))
    private val codec = TcfgCodec(BaselineTrustedProfileRegistry,
        PrinterProfileValidationLimits(600,1024,256,8,1024,131072,16,65536,1000,16))
    private val mutablePrinters = MutableStateFlow<List<PrinterInstance>>(emptyList())
    private val mutableProfile = MutableStateFlow<PrinterProfile?>(null)
    private var profileGeneration = 0L
    private var profileJob: Job? = null
    private val profileMutex = Mutex()
    private val mutableStatus = MutableStateFlow("Import a validated target profile before live printing")
    override val printers: StateFlow<List<PrinterInstance>> = mutablePrinters
    override val profile: StateFlow<PrinterProfile?> = mutableProfile
    override val status: StateFlow<String> = mutableStatus
    private val mutableForeground = MutableStateFlow(false)
    override val foreground: StateFlow<Boolean> = mutableForeground
    private val connection = ForegroundPrintConnection { mutableForeground.value }
    private var chooseImage: () -> Unit = {}
    private val decoder = AndroidImageDecoder(context.contentResolver)
    private var imageJob: Job? = null
    private val mutableImage = MutableStateFlow<ImageInput?>(null)
    private val mutableImageStatus = MutableStateFlow("Choose a JPEG or PNG image")
    override val image: StateFlow<ImageInput?> = mutableImage
    override val imageStatus: StateFlow<String> = mutableImageStatus
    override fun importImage() {
        imageJob?.cancel()
        mutableImage.value = null
        mutableImageStatus.value = "Choose a JPEG or PNG image"
        chooseImage()
    }
    fun bindImageAction(action: () -> Unit) { chooseImage = action }
    fun imageResult(uri: Uri?) {
        if (uri == null) { mutableImageStatus.value = "Image selection cancelled"; return }
        imageJob?.cancel()
        mutableImage.value = null
        imageJob = scope.launch {
            mutableImageStatus.value = "Importing image"
            try {
            when (val result = withTimeout(30_000) { decoder.decode(uri) }) {
                is AndroidImageDecodeResult.Success -> {
                    mutableImage.value = result.input
                    mutableImageStatus.value = if (result.sampled) "Image imported at a bounded working resolution" else "Image imported"
                }
                is AndroidImageDecodeResult.Invalid -> mutableImageStatus.value = "${result.code}: ${result.message}"
            }
            } catch (_: TimeoutCancellationException) {
                mutableImageStatus.value = "Image import timed out"
            } finally {
                if (!isActive && mutableImage.value == null) mutableImageStatus.value = "Image import cancelled"
            }
        }
    }
    fun setForeground(value: Boolean) {
        mutableForeground.value = value
        if (!value) { connection.close(); imageJob?.cancel() }
    }

    init {
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                try { savedProfile.openRead().use { decode(readProfileBytes(it)) } }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { null }
            }
            if (profileGeneration != 0L) return@launch
            mutableProfile.value = loaded
            if (loaded != null) mutableStatus.value = "Saved profile loaded; refresh paired printers"
        }
    }

    fun bindActions(permission: () -> Unit, profile: () -> Unit) {
        requestPermission = permission; chooseProfile = profile
    }
    override fun refreshPairedPrinters() = requestPermission()
    override fun importProfile() = chooseProfile()
    fun permissionResult(granted: Boolean) {
        if (!granted) {
            mutablePrinters.value = emptyList()
            mutableStatus.value = "Bluetooth permission denied"
            return
        }
        scope.launch {
            try {
                val paired = withContext(Dispatchers.IO) { pairedPrinters(context) }
                val selectedProfile = mutableProfile.value
                if (selectedProfile == null) {
                    mutablePrinters.value = emptyList()
                    mutableStatus.value = "Import a validated target profile first"
                    return@launch
                }
                val previous = withContext(Dispatchers.IO) { instances.load() }
                if (selectedProfile != mutableProfile.value) return@launch
                mutablePrinters.value = paired.map { device ->
                    PrinterInstance(previous?.takeIf { it.transportLocator == device.locator }?.id ?: UUID.randomUUID().toString(),
                        device.displayName, PrinterTransportKind.BLUETOOTH_CLASSIC, device.locator,
                        selectedProfile.profileId, selectedProfile.profileRevision)
                }
                mutableStatus.value = if (paired.isEmpty()) "No paired Classic devices; pair in Android settings"
                    else "Choose a paired device and confirm its profile association"
            } catch (_: SecurityException) {
                mutablePrinters.value = emptyList(); mutableStatus.value = "Bluetooth permission revoked"
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                mutablePrinters.value = emptyList(); mutableStatus.value = "Bluetooth is disabled or unavailable"
            }
        }
    }

    fun profileResult(uri: Uri?) {
        if (uri == null) return
        val generation = ++profileGeneration
        profileJob?.cancel()
        profileJob = scope.launch {
            profileMutex.withLock {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    withTimeout(30_000) { readProfileStream { context.contentResolver.openInputStream(uri) } }
                }
                val validated = withContext(Dispatchers.Default) { decode(bytes) }
                    ?: throw IllegalArgumentException("Invalid profile")
                if (generation != profileGeneration) return@launch
                withContext(Dispatchers.IO) {
                    val output = savedProfile.startWrite()
                    try { output.write(bytes); savedProfile.finishWrite(output) }
                    catch (failure: Exception) { savedProfile.failWrite(output); throw failure }
                }
                if (generation != profileGeneration) return@launch
                mutableProfile.value = validated
                mutablePrinters.value = emptyList()
                mutableStatus.value = "Validated profile imported; refresh paired printers"
            } catch (_: TimeoutCancellationException) {
                mutableStatus.value = "Profile import timed out"
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                mutableStatus.value = "Profile import failed strict validation or resource checks"
            }
            }
        }
    }

    private fun decode(bytes: ByteArray): PrinterProfile? = when (val result = codec.decode(bytes)) {
        is ProfileValidationResult.Success -> result.value.profile.takeUnless {
            it.profileId.startsWith("synthetic") || it.profileId.startsWith("rastrio.synthetic") ||
                it.profileId.startsWith("org.rastrio.test.")
        }
        is ProfileValidationResult.Failure -> null
    }

    override fun selectPrinter(instance: PrinterInstance) {
        if (instance !in mutablePrinters.value) return
        scope.launch {
            val saved = withContext(Dispatchers.IO) { instances.save(instance) }
            mutableStatus.value = if (saved) "Printer association saved privately" else "Printer association could not be saved"
        }
    }

    override fun createTransport(): PrinterTransport =
        connection.wrap(BluetoothPrinterTransport(context, TransportConstraints(1024)))
}

/** Cached sessions reactivate their own socket; foreground loss closes the current connection. */
internal class ForegroundPrintConnection(private val foreground: () -> Boolean) {
    private var active: PrinterTransport? = null

    fun wrap(transport: PrinterTransport): PrinterTransport = object : PrinterTransport {
            override val constraints = transport.constraints
            override suspend fun connect(instance: PrinterInstance) {
                check(foreground()) { "Printing requires a foreground application" }
                if (active !== this) active?.close()
                active = this
                transport.connect(instance)
            }
            override suspend fun write(bytes: ByteArray, offset: Int, length: Int): Int {
                check(foreground()) { "Printing requires a foreground application" }
                return transport.write(bytes, offset, length)
            }
            override fun close() = transport.close()
        }

    fun close() { active?.close() }
}
