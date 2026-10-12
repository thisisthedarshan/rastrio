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

enum class QrCorrection { LOW, MEDIUM, QUARTILE, HIGH }

/** Immutable Model 2 modules; the required four-module quiet zone is added only by rasterBands. */
class QrMatrix internal constructor(
    val version: Int,
    val mask: Int,
    val errorCorrection: QrCorrection,
    modules: BooleanArray,
) {
    init {
        require(version in 1..40 && mask in 0..7)
        require(modules.size == (version * 4 + 17) * (version * 4 + 17))
    }
    val size: Int = version * 4 + 17
    private val modules = modules.copyOf()
    fun isDark(x: Int, y: Int): Boolean {
        require(x in 0 until size && y in 0 until size)
        return modules[y * size + x]
    }

    /** Integer scaling, black=1, MSB first, white padding; no smoothing or dithering. */
    fun rasterBands(
        moduleSizeDots: Int,
        bandHeight: Int = 256,
        policy: RasterResourcePolicy = RasterResourcePolicy(),
        isCancelled: () -> Boolean = { false },
        minBandHeight: Int = 1,
        onBand: (RasterBand) -> Unit,
    ): RasterResult {
        if (moduleSizeDots <= 0 || bandHeight <= 0 || minBandHeight !in 1..bandHeight)
            return RasterResult.Invalid("RAS001", "Invalid QR raster dimensions")
        val widthLong = (size + 8).toLong() * moduleSizeDots
        if (widthLong > 32768 || widthLong * widthLong > policy.maxOutputPixels)
            return RasterResult.Invalid("RAS120", "QR raster exceeds pixel limits")
        val width = widthLong.toInt()
        val rowBytes = (width + 7) / 8
        val payload = rowBytes.toLong() * width
        if (payload > policy.maxPayloadBytes) return RasterResult.Invalid("RAS120", "QR raster exceeds payload limit")
        val available = minOf(policy.maxBandBytes.toLong(), (policy.maxScratchBytes.toLong() - 4096) / 2)
        val rows = minOf(bandHeight.toLong(), widthLong, available / rowBytes).toInt()
        if (rows < minBandHeight || widthLong < ((widthLong + rows - 1) / rows) * minBandHeight)
            return RasterResult.Invalid("RAS120", "QR raster cannot fit supported band rows")
        var count = 0
        var start = 0
        while (start < width) {
            if (isCancelled()) return RasterResult.Cancelled
            val remaining = width - start
            val bandsLeft = (remaining.toLong() + rows - 1) / rows
            val height = minOf(rows.toLong(), remaining - (bandsLeft - 1) * minBandHeight).toInt()
            val bytes = ByteArray(rowBytes * height)
            for (localY in 0 until height) {
                if (isCancelled()) return RasterResult.Cancelled
                val y = (start + localY) / moduleSizeDots - 4
                if (y !in 0 until size) continue
                for (x in 0 until width) {
                    val moduleX = x / moduleSizeDots - 4
                    if (moduleX in 0 until size && modules[y * size + moduleX]) {
                        val index = localY * rowBytes + x / 8
                        bytes[index] = (bytes[index].toInt() or (128 ushr (x % 8))).toByte()
                    }
                }
            }
            if (isCancelled()) return RasterResult.Cancelled
            onBand(RasterBand(width, height, start, bytes))
            start += height; count++
        }
        return RasterResult.Completed(count, payload)
    }
}
