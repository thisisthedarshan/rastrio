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

/** Run by resourceHeapProbe in a dedicated 32 MiB heap; no output is retained. */
object RasterResourceHeapProbe {
    @JvmStatic fun main(args: Array<String>) {
        val source = DecodedImage(1, 1, intArrayOf(0xff808080.toInt()))
        for (height in listOf(10_000, 100_000)) {
            var row = 0
            var maximumBandBytes = 0
            val result = RasterEngine.process(source, RasterOptions(576, height,
                algorithm = DitherAlgorithm.FLOYD_STEINBERG, bandHeight = 64)) { band ->
                check(band.startRow == row)
                row += band.height
                maximumBandBytes = maxOf(maximumBandBytes, band.copyBytes().size)
            }
            check(result is RasterResult.Completed && result.payloadBytes == 72L * height)
            check(row == height && maximumBandBytes <= 72 * 64)
            println("Raster heap probe: rows=$row payload=${72L * height} maxBand=$maximumBandBytes")
        }
    }
}
