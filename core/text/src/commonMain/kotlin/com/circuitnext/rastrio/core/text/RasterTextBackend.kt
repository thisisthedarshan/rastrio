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

package com.circuitnext.rastrio.core.text

/** Final physical box supplied by preparation; output never performs wrapping. */
data class RasterTextRequest(
    val text: String,
    val typography: ResolvedTypography,
    val widthDots: Int,
    val heightDots: Int,
    val baselineDots: Int,
    val dpiX: Int,
    val dpiY: Int,
    val isCancelled: () -> Boolean = { false },
    val originDots: Int = 0,
)

/** MSB-first row bytes, black=1. Padding bits are zero. */
class TextBitmap(val widthDots: Int, val heightDots: Int, bytes: ByteArray) {
    val rowBytes: Int = ((widthDots.toLong() + 7) / 8).toInt()
    private val snapshot: ByteArray
    init {
        require(widthDots in 1..32768 && heightDots in 1..32768)
        require(rowBytes.toLong() * heightDots == bytes.size.toLong())
        require(bytes.size <= 8 * 1024 * 1024)
        snapshot = bytes.copyOf()
        val padding = (8 - widthDots % 8) % 8
        if (padding > 0) for (y in 0 until heightDots) require(snapshot[(y + 1) * rowBytes - 1].toInt() and ((1 shl padding) - 1) == 0)
    }
    fun copyBytes(): ByteArray = snapshot.copyOf()
    fun isBlack(x: Int, y: Int): Boolean {
        require(x in 0 until widthDots && y in 0 until heightDots)
        return snapshot[y * rowBytes + x / 8].toInt() and (0x80 ushr (x % 8)) != 0
    }
}
sealed interface RasterTextResult {
    data class Success(val bitmap: TextBitmap, val backendIdentity: String, val fontIdentity: String) : RasterTextResult
    data class Failure(val diagnostics: SnapshotList<TextDiagnostic>) : RasterTextResult {
        init { require(diagnostics.isNotEmpty()) }
    }
}
interface RasterTextBackend : TextMeasurer {
    val identity: String
    fun typography(style: TextStyle = TextStyle(), sizeMm: Double = 3.0): ResolvedTypography
    /** Minimum cooperative contract; controlled backends also check during bounded native work. */
    fun measure(request: TextMeasureRequest, isCancelled: () -> Boolean): TextMeasureResult {
        fun cancelled() = TextMeasureResult.Failure(SnapshotList(listOf(TextDiagnostic("TXT130", "Text measurement cancelled"))))
        if (isCancelled()) return cancelled()
        val result = measure(request)
        return if (isCancelled()) cancelled() else result
    }
    fun rasterize(request: RasterTextRequest): RasterTextResult
}
