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

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.*

class QrMatrixTest {
    private fun payload(length: Int) = ByteArray(length) { ((it * 149 + 37) % 256).toByte() }
    private fun fingerprint(qr: QrMatrix): String {
        var hash = 0x811c9dc5.toInt()
        for (y in 0 until qr.size) for (x in 0 until qr.size)
            hash = (hash xor if (qr.isDark(x, y)) 1 else 0) * 16777619
        return hash.toUInt().toString(16).padStart(8, '0')
    }

    // Fixed Nayuki 1.8.0 vectors, byte mode, boostecl=False; see reference-tools/README.md.
    @Test fun everyVersionAndCorrectionMatchesIndependentReference() {
        for ((correctionIndex, line) in referenceVectors.withIndex()) {
            val correction = QrCorrection.entries[correctionIndex]
            val vectors = line.split(' ')
            assertEquals(40, vectors.size)
            for ((index, vector) in vectors.withIndex()) {
                val fields = vector.split(':')
                val qr = QrMatrixEncoder.encode(payload(fields[0].toInt()), correction)
                assertEquals(index + 1, qr.version, "$correction version")
                assertEquals(correction, qr.errorCorrection)
                assertEquals(fields[1].toInt(), qr.mask, "$correction/${qr.version} mask")
                assertEquals(fields[2], fingerprint(qr), "$correction/${qr.version} modules")
            }
        }
    }

    @Test fun fullSmallMatrixMatchesReferenceAndIsRepeatable() {
        val qr = QrMatrixEncoder.encode("Hello, world!".encodeToByteArray(), QrCorrection.MEDIUM)
        val actual = (0 until qr.size).joinToString("\n") { y ->
            (0 until qr.size).joinToString("") { x -> if (qr.isDark(x, y)) "1" else "0" }
        }
        assertEquals(smallMatrix, actual)
        assertEquals(fingerprint(qr), fingerprint(QrMatrixEncoder.encode("Hello, world!".encodeToByteArray(), QrCorrection.MEDIUM)))
    }

    @Test fun utf8EciIsExplicitAndMatchesReference() {
        val payload = "नमस्ते café e\u0301".encodeToByteArray()
        val vectors = listOf("2:7:4cebf883", "3:4:ff3cd692", "3:1:bfb4d617", "4:6:03a94110")
        for ((correction, vector) in QrCorrection.entries.zip(vectors)) {
            val fields = vector.split(':')
            val qr = QrMatrixEncoder.encode(payload, correction, utf8Eci = true)
            assertEquals(fields[0].toInt(), qr.version)
            assertEquals(fields[1].toInt(), qr.mask)
            assertEquals(fields[2], fingerprint(qr))
            assertNotEquals(fingerprint(qr), fingerprint(QrMatrixEncoder.encode(payload, correction)))
        }
    }

    @Test fun byteCapacityAndEciOverheadAreExact() {
        for ((correction, maximum) in QrCorrection.entries.zip(listOf(2953, 2331, 1663, 1273))) {
            assertEquals(40, QrMatrixEncoder.encode(payload(maximum), correction).version)
            assertFailsWith<IllegalArgumentException> { QrMatrixEncoder.encode(payload(maximum + 1), correction) }
            assertEquals(40, QrMatrixEncoder.encode(payload(maximum - 1), correction, utf8Eci = true).version)
            assertFailsWith<IllegalArgumentException> { QrMatrixEncoder.encode(payload(maximum), correction, utf8Eci = true) }
        }
        assertEquals(1, QrMatrixEncoder.encode(payload(17), QrCorrection.LOW).version)
        assertEquals(2, QrMatrixEncoder.encode(payload(18), QrCorrection.LOW).version)
        assertEquals(1, QrMatrixEncoder.encode(payload(16), QrCorrection.LOW, utf8Eci = true).version)
        assertEquals(2, QrMatrixEncoder.encode(payload(17), QrCorrection.LOW, utf8Eci = true).version)
        assertFailsWith<IllegalArgumentException> { QrMatrixEncoder.encode(byteArrayOf(), QrCorrection.LOW) }
        assertFailsWith<IllegalArgumentException> { QrMatrixEncoder.encode(ByteArray(1_000_000), QrCorrection.LOW) }
    }

    @Test fun payloadAndInternalModulesCannotMutatePublishedMatrix() {
        val input = payload(100)
        val qr = QrMatrixEncoder.encode(input, QrCorrection.HIGH)
        val before = fingerprint(qr)
        input.fill(0)
        assertEquals(before, fingerprint(qr))
        val modules = BooleanArray(21 * 21) { true }
        val copied = QrMatrix(1, 0, QrCorrection.LOW, modules)
        modules.fill(false)
        assertTrue(copied.isDark(0, 0))
        assertFailsWith<IllegalArgumentException> { copied.isDark(-1, 0) }
        assertFailsWith<IllegalArgumentException> { copied.isDark(0, 21) }
        assertFailsWith<IllegalArgumentException> { QrMatrix(41, 0, QrCorrection.LOW, modules) }
        assertFailsWith<IllegalArgumentException> { QrMatrix(1, 8, QrCorrection.LOW, modules) }
        assertFailsWith<IllegalArgumentException> { QrMatrix(1, 0, QrCorrection.LOW, BooleanArray(1)) }
    }

    @Test fun scaledPixelsQuietZoneAndPaddingSurviveEveryBandBoundary() {
        val qr = QrMatrixEncoder.encode(payload(18), QrCorrection.LOW)
        for (scale in listOf(1, 2, 3, 7, 8)) for (bandHeight in listOf(1, 7, 256)) {
            var nextRow = 0
            val width = (qr.size + 8) * scale
            val result = qr.rasterBands(scale, bandHeight) { band ->
                assertEquals(width, band.width)
                assertEquals(nextRow, band.startRow)
                assertTrue(band.height in 1..bandHeight)
                val bytes = band.copyBytes()
                for (y in 0 until band.height) {
                    for (x in 0 until width) {
                        val moduleX = x / scale - 4
                        val moduleY = (band.startRow + y) / scale - 4
                        val expected = moduleX in 0 until qr.size && moduleY in 0 until qr.size && qr.isDark(moduleX, moduleY)
                        assertEquals(expected, bytes[y * band.rowBytes + x / 8].toInt() and (128 ushr (x % 8)) != 0)
                    }
                    if (width % 8 != 0) assertEquals(0, bytes[(y + 1) * band.rowBytes - 1].toInt() and ((1 shl (8 - width % 8)) - 1))
                }
                nextRow += band.height
                bytes.fill(-1)
                assertFalse(band.copyBytes().all { it == (-1).toByte() })
            }
            assertIs<RasterResult.Completed>(result)
            assertEquals(width, nextRow)
            assertEquals(((width + 7) / 8).toLong() * width, result.payloadBytes)
        }
    }

    @Test fun resourceLimitsFailBeforeDeliveryAndReduceBandsSafely() {
        val qr = QrMatrixEncoder.encode(payload(1), QrCorrection.LOW)
        fun invalid(scale: Int, height: Int = 256, policy: RasterResourcePolicy = RasterResourcePolicy()) {
            assertIs<RasterResult.Invalid>(qr.rasterBands(scale, height, policy) { error("must not emit") })
        }
        invalid(0); invalid(-1); invalid(Int.MAX_VALUE); invalid(1, 0)
        invalid(1, policy = RasterResourcePolicy(maxOutputPixels = 840))
        invalid(1, policy = RasterResourcePolicy(maxPayloadBytes = 115))
        invalid(1, policy = RasterResourcePolicy(maxBandBytes = 3))
        invalid(1, policy = RasterResourcePolicy(maxScratchBytes = 4103))
        var rows = 0
        val result = qr.rasterBands(1, Int.MAX_VALUE, RasterResourcePolicy(maxOutputPixels = 841, maxPayloadBytes = 116, maxBandBytes = 12, maxScratchBytes = 4120)) {
            assertTrue(it.height <= 3)
            assertTrue(it.copyBytes().size <= 12)
            rows += it.height
        }
        assertEquals(29, rows)
        assertEquals(RasterResult.Completed(10, 116), result)
    }

    @Test fun minimumBandHeightIsBalancedAndPreflighted() {
        val qr = QrMatrixEncoder.encode(payload(1), QrCorrection.LOW)
        for ((maximum, minimum, expected) in listOf(
            Triple(4, 3, listOf(4, 4, 4, 4, 4, 3, 3, 3)),
            Triple(10, 9, listOf(10, 10, 9)),
            Triple(29, 29, listOf(29)),
        )) {
            val heights = mutableListOf<Int>()
            var nextRow = 0
            val result = qr.rasterBands(1, maximum, minBandHeight = minimum) {
                assertEquals(nextRow, it.startRow)
                assertTrue(it.height in minimum..maximum)
                nextRow += it.height
                heights += it.height
            }
            assertEquals(expected, heights)
            assertEquals(29, nextRow)
            assertEquals(RasterResult.Completed(expected.size, 116), result)
        }
        for ((maximum, minimum) in listOf(4 to 4, 4 to 5, 4 to 0, 256 to 30)) {
            assertIs<RasterResult.Invalid>(qr.rasterBands(1, maximum, minBandHeight = minimum) { error("must not emit") })
        }
        assertIs<RasterResult.Invalid>(qr.rasterBands(1, 4, RasterResourcePolicy(maxBandBytes = 11),
            minBandHeight = 3) { error("scratch-reduced band cannot fit minimum") })
        assertEquals(RasterResult.Cancelled, qr.rasterBands(1, 4, isCancelled = { true },
            minBandHeight = 3) { error("must not emit") })
    }

    @Test fun cancellationStopsGenerationAndBandDelivery() {
        assertFailsWith<CancellationException> { QrMatrixEncoder.encode(payload(1), QrCorrection.LOW, { true }) }
        var checks = 0
        assertFailsWith<CancellationException> {
            QrMatrixEncoder.encode(payload(2953), QrCorrection.LOW, { ++checks == 10 })
        }
        assertEquals(10, checks)
        val qr = QrMatrixEncoder.encode(payload(1), QrCorrection.LOW)
        assertEquals(RasterResult.Cancelled, qr.rasterBands(1, isCancelled = { true }) { error("must not emit") })
        var count = 0
        assertEquals(RasterResult.Cancelled, qr.rasterBands(3, 1, isCancelled = { count == 3 }) { count++ })
        assertEquals(3, count)
        var rowChecks = 0
        assertEquals(RasterResult.Cancelled, qr.rasterBands(1, isCancelled = { ++rowChecks == 3 }) { error("must not emit partial band") })
    }

    private val referenceVectors = arrayOf(
        "1:0:70f71b3b 18:4:f67169b5 33:6:f232cdb9 54:4:d4eb6231 79:7:0480f54b 107:4:986ecce4 135:7:25d7665f 155:3:d58ac8df 193:2:e4b863d1 231:4:4cf203bd 272:1:f809d3b4 322:4:351f4d71 368:0:c0882b09 426:2:706fba73 459:2:782f184b 521:5:fc577a6d 587:2:25752bc5 645:3:90c68b58 719:4:8110d275 793:3:898ab99f 859:4:bf37d4fd 930:3:f8927bab 1004:3:296938d1 1092:2:d85defe7 1172:2:d87f770f 1274:0:143f6119 1368:3:5a52513f 1466:7:8ab6eb40 1529:2:ea6ba905 1629:6:1fa11d8a 1733:2:1f86281a 1841:3:f8e5c84a 1953:4:473cd210 2069:6:9001c7bd 2189:4:399cd2fd 2304:3:fd6c30e0 2432:4:df944331 2564:0:8f33acfb 2700:2:333268e9 2810:2:fbdd1e09",
        "1:1:54e9829d 15:4:4c860f03 27:2:37d2e092 43:2:65be6a5a 63:6:db1a4915 85:2:4e04ce7e 107:6:96415869 123:0:73ebeb49 153:2:969e15ab 181:5:98d85ba3 214:4:d5b91e0d 252:2:49c3a75d 288:2:39d4b15f 332:6:04afe3d7 363:4:87a2262c 413:2:f9a694de 451:2:36f76b5d 505:3:5fb71938 561:4:1a42ce87 625:6:6cf815ef 667:2:e26b41e7 712:2:538d7887 780:4:acf06b4f 858:3:e1fc1beb 912:2:f2a26b1d 998:3:c877dd15 1060:5:3fd7a29a 1126:7:31bfc416 1191:2:543dabf3 1265:4:7104f962 1371:2:fde870cc 1453:2:dd18f31e 1539:2:ac2fa4b6 1629:2:4d8b55fb 1723:2:1efc5735 1810:7:3f30775a 1912:4:e060a5c9 1990:0:e01c3c8f 2100:4:b76ece7f 2214:7:061ba4bd",
        "1:0:c79be96b 12:0:d81b9a77 21:1:7ebfff07 33:4:dc042f63 47:3:b0763e1d 61:4:73ab0498 75:2:ed691da1 87:4:13ec610f 109:4:44fa3d65 131:6:243e3777 152:2:ab1c4b59 178:0:875a8a63 204:0:4bfd835b 242:2:e38ac4e5 259:3:8fd856ec 293:4:68acd596 323:3:5c2f9a31 365:4:7842bf1c 395:4:c7928e11 443:6:0bdf55e3 483:3:d3575f51 510:2:f89c2737 566:4:b01a5df7 612:3:8b37f06b 662:5:d52eec6f 716:1:b444a1c2 752:2:ad032cc5 806:3:34711270 869:3:ebc71603 909:0:1be9f479 983:7:eb2640b4 1031:3:050d0194 1113:3:f4604579 1169:0:6ac88a8d 1229:2:cd74537b 1284:1:156fe938 1352:1:05841192 1424:1:f5556060 1500:0:5a27c215 1580:0:2aed3e43",
        "1:0:62945f3b 8:5:fb600aae 15:3:a996e9e9 25:6:5520e244 35:7:2c9215bb 45:2:aff544d2 59:2:aff51233 65:0:7b4bde0f 85:3:d20eea54 99:2:fb686a77 120:2:18f8961d 138:2:9d5dfec7 156:1:7082ded8 178:2:2813b207 195:3:4fd9a826 221:7:c57c0ab2 251:6:04452935 281:2:49d891c7 311:3:4f269139 339:6:9050679d 383:7:11f04043 404:5:2c5e8abb 440:7:e5a6596d 462:2:6c13c14f 512:0:22f961af 536:5:12b99014 594:2:8ece3a07 626:4:863a631e 659:3:e0e8a9bb 699:3:5fb2716c 743:5:d0555305 791:0:6bc25e85 843:3:244f6f6f 899:2:93ad4063 959:3:5ad956a7 984:0:830ad383 1052:1:e283ad9e 1094:5:dcb575ba 1140:6:92ca4e1a 1220:6:6054ef27",
    )
    private val smallMatrix = """
        111111100000101111111
        100000100101001000001
        101110101011101011101
        101110101000001011101
        101110101100101011101
        100000101111001000001
        111111101010101111111
        000000001010000000000
        101111100111001111100
        000110011000110011101
        000100101110111001110
        011001010011110101100
        110111101000101100001
        000000001000011111000
        111111100110111100110
        100000101010110101110
        101110101101111010011
        101110101010000111000
        101110101111101100100
        100000100010110011100
        111111101101001010010
    """.trimIndent()
}
