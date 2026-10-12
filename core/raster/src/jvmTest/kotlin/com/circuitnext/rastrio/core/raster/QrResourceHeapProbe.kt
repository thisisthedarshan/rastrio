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

/** Run separately in a 16 MiB heap; matrix scratch and streamed output never form a receipt bitmap. */
object QrResourceHeapProbe {
    @JvmStatic fun main(args: Array<String>) {
        var symbols = 0
        var deliveredBytes = 0L
        repeat(8) {
            for ((correction, capacity) in QrCorrection.entries.zip(listOf(2953, 2331, 1663, 1273))) {
                val matrix = QrMatrixEncoder.encode(ByteArray(capacity) { ((it * 149 + 37) % 256).toByte() }, correction)
                check(matrix.version == 40)
                var nextRow = 0
                val result = matrix.rasterBands(8, 16, RasterResourcePolicy(maxBandBytes = 2960, maxScratchBytes = 10016)) { band ->
                    check(band.startRow == nextRow && band.height <= 16)
                    nextRow += band.height
                    check(band.copyBytes().size <= 2960)
                    deliveredBytes += band.copyBytes().size
                }
                check(result is RasterResult.Completed && nextRow == 1480)
                symbols++
            }
        }
        check(deliveredBytes == 8_761_600L)
        println("QR heap probe: symbols=$symbols deliveredBytes=$deliveredBytes maxBandBytes=2960")
    }
}
