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

import java.io.File

/** Exports only synthetic fixtures; the independent decoder is supplied separately, offline. */
object QrReferenceDecodeProbe {
    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 1)
        val directory = File(args[0]).also { check(it.isDirectory) }
        var count = 0
        for (correction in QrCorrection.entries) {
            val maximum = listOf(2953, 2331, 1663, 1273)[correction.ordinal]
            for ((name, payload) in listOf(
                "ascii" to "RastrIO independent QR decode".encodeToByteArray(),
                "utf8" to "नमस्ते café e\u0301".encodeToByteArray(),
                "opaque" to ByteArray(256) { it.toByte() },
                "capacity" to ByteArray(maximum) { ((it * 149 + 37) % 256).toByte() },
            )) {
                val qr = QrMatrixEncoder.encode(payload, correction, utf8Eci = name == "utf8")
                val width = (qr.size + 8) * 3
                val file = File(directory, "${correction.name}-$name.pgm")
                file.outputStream().use { output ->
                    output.write("P5\n$width $width\n255\n".encodeToByteArray())
                    check(qr.rasterBands(3, 7) { band ->
                        val packed = band.copyBytes()
                        val row = ByteArray(width)
                        for (y in 0 until band.height) {
                            for (x in 0 until width) row[x] = if (packed[y * band.rowBytes + x / 8].toInt() and (128 ushr (x % 8)) != 0) 0 else (-1).toByte()
                            output.write(row)
                        }
                    } is RasterResult.Completed)
                }
                File(directory, "${correction.name}-$name.payload").writeBytes(payload)
                count++
            }
        }
        println("QR decoder fixtures: $count final raster PGM files")
    }
}
