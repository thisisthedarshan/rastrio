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

package com.circuitnext.rastrio.core.raster

/** Straight (unpremultiplied) ARGB8888, row-major; the source is snapshotted once. */
class DecodedImage(val width: Int, val height: Int, argb: IntArray) {
    init {
        require(width in 1..32768 && height in 1..32768)
        require(width.toLong() * height <= 16_000_000 && argb.size.toLong() == width.toLong() * height)
    }
    private val pixels = argb.copyOf()
    fun pixel(x: Int, y: Int): Int {
        require(x in 0 until width && y in 0 until height)
        return pixels[y * width + x]
    }
}

enum class DitherAlgorithm { THRESHOLD, BAYER, ATKINSON, FLOYD_STEINBERG }
/** EXIF orientation numbers; crop coordinates are in the oriented image. */
data class ImageCrop(val x: Int, val y: Int, val width: Int, val height: Int)
data class RasterOptions(
    val width: Int,
    val height: Int,
    val orientation: Int = 1,
    val crop: ImageCrop? = null,
    val brightness: Int = 0,
    val contrastPercent: Int = 100,
    val gammaHundredths: Int = 100,
    val algorithm: DitherAlgorithm = DitherAlgorithm.THRESHOLD,
    val threshold: Int = 128,
    val bandHeight: Int = 256,
    val minBandHeight: Int = 1,
)
data class RasterResourcePolicy(
    val maxOutputPixels: Long = 100_000_000,
    val maxPayloadBytes: Long = 128L * 1024 * 1024,
    val maxBandBytes: Int = 2 * 1024 * 1024,
    val maxScratchBytes: Int = 16 * 1024 * 1024,
) {
    init {
        require(maxOutputPixels in 1..100_000_000 && maxPayloadBytes in 1..128L * 1024 * 1024)
        require(maxBandBytes in 1..2 * 1024 * 1024 && maxScratchBytes in 1..16 * 1024 * 1024)
    }
}
/** Black = 1, MSB first, white/unused padding = 0. Bands never overlap. */
class RasterBand internal constructor(
    val width: Int, val height: Int, val startRow: Int, private val bytes: ByteArray,
) {
    val rowBytes: Int get() = (width + 7) / 8
    fun copyBytes(): ByteArray = bytes.copyOf()
}
sealed interface RasterResult {
    data class Completed(val bandCount: Int, val payloadBytes: Long) : RasterResult
    data class Invalid(val code: String, val message: String) : RasterResult
    data object Cancelled : RasterResult
}

object RasterEngine {
    /**
     * Nearest-neighbor pixel centers; white alpha compositing rounded half-up;
     * BT.601 integer luma (77R+150G+29B+128)/256. Adjustments clamp before gamma.
     * Diffusion uses signed Q8 error, truncating division toward zero, left-to-right.
     * Consumers must bound retained bands. Callback exceptions propagate to their owner.
     */
    fun process(
        image: DecodedImage,
        options: RasterOptions,
        policy: RasterResourcePolicy = RasterResourcePolicy(),
        isCancelled: () -> Boolean = { false },
        onBand: (RasterBand) -> Unit,
    ): RasterResult {
        val o = options
        fun invalid(message: String) = RasterResult.Invalid("RAS001", message)
        if (o.width !in 1..32768 || o.height !in 1..1_000_000 || o.orientation !in 1..8 ||
            o.brightness !in -255..255 || o.contrastPercent !in 0..400 ||
            o.gammaHundredths !in 10..400 || o.threshold !in 0..255 || o.bandHeight <= 0 || o.minBandHeight !in 1..o.bandHeight
        ) return invalid("Invalid raster dimensions or adjustment parameters")
        val rotated = o.orientation >= 5
        val orientedWidth = if (rotated) image.height else image.width
        val orientedHeight = if (rotated) image.width else image.height
        val crop = o.crop ?: ImageCrop(0, 0, orientedWidth, orientedHeight)
        if (crop.x < 0 || crop.y < 0 || crop.width <= 0 || crop.height <= 0 ||
            crop.x.toLong() + crop.width > orientedWidth || crop.y.toLong() + crop.height > orientedHeight
        ) return invalid("Crop is outside the oriented image")
        val rowBytes = (o.width + 7) / 8
        val payload = rowBytes.toLong() * o.height
        if (o.width.toLong() * o.height > policy.maxOutputPixels || payload > policy.maxPayloadBytes)
            return RasterResult.Invalid("RAS120", "Raster output exceeds resource limits")
        val diffusion = o.algorithm == DitherAlgorithm.ATKINSON || o.algorithm == DitherAlgorithm.FLOYD_STEINBERG
        val errorBytes = if (diffusion) o.width.toLong() * 3 * 4 else 0L
        // Include the gamma table/buffer bookkeeping and a consumer snapshot of one band.
        val available = minOf(policy.maxBandBytes.toLong(), (policy.maxScratchBytes - errorBytes - 4096) / 2)
        val rows = minOf(o.bandHeight.toLong(), o.height.toLong(), available / rowBytes).toInt()
        if (rows < o.minBandHeight || o.height.toLong() < ((o.height.toLong() + rows - 1) / rows) * o.minBandHeight)
            return RasterResult.Invalid("RAS120", "Raster scratch or band geometry cannot fit supported rows")
        if (isCancelled()) return RasterResult.Cancelled
        val gamma = IntArray(256) { gamma(it, o.gammaHundredths) }
        val errors = if (diffusion) Array(3) { IntArray(o.width) } else emptyArray()
        var bandCount = 0
        var start = 0
        while (start < o.height) {
            val remaining = o.height - start
            val bandsLeft = (remaining.toLong() + rows - 1) / rows
            val height = minOf(rows.toLong(), remaining - (bandsLeft - 1) * o.minBandHeight).toInt()
            val packed = ByteArray(rowBytes * height)
            for (localY in 0 until height) {
                if (isCancelled()) return RasterResult.Cancelled
                val y = start + localY
                val sy = crop.y + ((2L * y + 1) * crop.height / (2L * o.height)).toInt()
                for (x in 0 until o.width) {
                    if (x % 1024 == 0 && isCancelled()) return RasterResult.Cancelled
                    val sx = crop.x + ((2L * x + 1) * crop.width / (2L * o.width)).toInt()
                    val pixel = orientedPixel(image, sx, sy, o.orientation)
                    val alpha = pixel ushr 24
                    fun channel(shift: Int) = (((pixel ushr shift) and 255) * alpha + 255 * (255 - alpha) + 127) / 255
                    val gray = (77 * channel(16) + 150 * channel(8) + 29 * channel(0) + 128) / 256
                    val adjusted = gamma[((gray - 128) * o.contrastPercent / 100 + 128 + o.brightness).coerceIn(0, 255)]
                    val value = adjusted * 256 + if (diffusion) errors[0][x] else 0
                    val cutoff = if (o.algorithm == DitherAlgorithm.BAYER) (bayer[(y % 4) * 4 + x % 4] * 16 + 8) * 256 else o.threshold * 256
                    val black = value < cutoff
                    if (black) {
                        val index = localY * rowBytes + x / 8
                        packed[index] = (packed[index].toInt() or (128 ushr (x % 8))).toByte()
                    }
                    if (diffusion) {
                        val error = value - if (black) 0 else 255 * 256
                        fun add(row: Int, column: Int, amount: Int) {
                            if (column in 0 until o.width) errors[row][column] += amount
                        }
                        if (o.algorithm == DitherAlgorithm.ATKINSON) {
                            val eighth = error / 8
                            add(0, x + 1, eighth); add(0, x + 2, eighth)
                            add(1, x - 1, eighth); add(1, x, eighth); add(1, x + 1, eighth)
                            add(2, x, eighth)
                        } else {
                            add(0, x + 1, error * 7 / 16)
                            add(1, x - 1, error * 3 / 16); add(1, x, error * 5 / 16); add(1, x + 1, error / 16)
                        }
                    }
                }
                if (diffusion) {
                    val used = errors[0]
                    errors[0] = errors[1]; errors[1] = errors[2]; errors[2] = used
                    used.fill(0)
                }
            }
            if (isCancelled()) return RasterResult.Cancelled
            onBand(RasterBand(o.width, height, start, packed))
            bandCount++
            start += height
        }
        return RasterResult.Completed(bandCount, payload)
    }

    private fun orientedPixel(image: DecodedImage, x: Int, y: Int, orientation: Int): Int {
        val w = image.width; val h = image.height
        return when (orientation) {
            1 -> image.pixel(x, y)
            2 -> image.pixel(w - 1 - x, y)
            3 -> image.pixel(w - 1 - x, h - 1 - y)
            4 -> image.pixel(x, h - 1 - y)
            5 -> image.pixel(y, x)
            6 -> image.pixel(y, h - 1 - x)
            7 -> image.pixel(w - 1 - y, h - 1 - x)
            else -> image.pixel(w - 1 - y, x)
        }
    }

    // Q24 power with a Q16 exponent and integer square roots avoids host pow/rounding differences.
    private fun gamma(gray: Int, gammaHundredths: Int): Int {
        if (gammaHundredths == 100 || gray == 0 || gray == 255) return gray
        val one = 1L shl 24
        var base = gray * one / 255
        val exponent = 100 * 65536 / gammaHundredths
        var value = one
        repeat(exponent ushr 16) { value = value * base / one }
        for (bit in 15 downTo 0) {
            base = integerSqrt(base * one)
            if ((exponent and (1 shl bit)) != 0) value = value * base / one
        }
        return ((value * 255 + one / 2) / one).toInt()
    }

    private fun integerSqrt(value: Long): Long {
        var low = 0L
        var high = (1L shl 24) + 1
        while (low + 1 < high) {
            val middle = (low + high) / 2
            if (middle * middle <= value) low = middle else high = middle
        }
        return low
    }
    private val bayer = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)
}
