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

package com.circuitnext.rastrio

import com.circuitnext.rastrio.core.text.NativeCoverageResult
import com.circuitnext.rastrio.core.text.NativeTextRepertoires
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.test.*

class NotoFontTest {
    private fun resource(path: String): ByteArray = assertNotNull(javaClass.classLoader.getResourceAsStream(
        "composeResources/rastrio.shared.generated.resources/$path")).use { it.readBytes() }

    @Test fun bundledFacesMatchPinnedHashesAndRendererMetrics() {
        val readme = resource("files/fonts/NOTO_README.md").toString(Charsets.UTF_8)
        assertTrue("ffebf8c1ee449e544955a7e813c54f9b73848eac" in readme)
        for ((face, expected) in mapOf(
            "regular" to "b85c38ecea8a7cfb39c24e395a4007474fa5a4fc864f6ee33309eb4948d232d5",
            "bold" to "c976e4b1b99edc88775377fcc21692ca4bfa46b6d6ca6522bfda505b28ff9d6a",
            "mono_regular" to "d9e2b23d19f8230be7146f409a52b1d23117e635e28f2e2892cf91b7382f325b",
            "mono_bold" to "beb857bd799a114de7d5d90cc75cc69f4b6ee8d401f4ab20697e6f9719a66dfc")) {
            val bytes = resource("font/noto_sans_$face.ttf")
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            assertEquals(expected, digest, face)
            assertTrue(expected in readme)
            val font = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            fun u16(offset: Int) = font.getShort(offset).toInt() and 65535
            val tables = (0 until u16(4)).associate { index ->
                val offset = 12 + index * 16
                String(bytes, offset, 4, Charsets.US_ASCII) to font.getInt(offset + 8)
            }
            assertEquals(1000, u16(tables.getValue("head") + 18))
            val hhea = tables.getValue("hhea")
            assertEquals(1069, font.getShort(hhea + 4).toInt())
            assertEquals(-293, font.getShort(hhea + 6).toInt())
            assertEquals(0, font.getShort(hhea + 8).toInt())
        }
    }

    @Test fun everySafeCp437ScalarHasAnExplicitBundledGlyphAtBothWeights() {
        val high = "ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜ¢£¥₧ƒáíóúñÑªº¿⌐¬½¼¡«»" +
            "░▒▓│┤╡╢╖╕╣║╗╝╜╛┐└┴┬├─┼╞╟╚╔╩╦╠═╬╧╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀" +
            "αßΓπΣσµτΦΘΩδ∞φε∩≡±≥≤⌠⌡÷≈°∙·√ⁿ²■\u00a0"
        val safe = (32..126).map(Int::toChar).joinToString("") + high
        assertEquals(223, safe.length)
        for (weight in listOf("regular", "bold")) {
            val base = resource("font/noto_sans_$weight.ttf")
            val supplement = resource("font/noto_sans_mono_$weight.ttf")
            for (character in safe) {
                assertIs<NativeCoverageResult.Representable>(NativeTextRepertoires.assess(character.toString(), "cp437"))
                assertEquals(!hasGlyph(base, character), physicalPreviewUsesMono(character), "Explicit selection for U+${character.code.toString(16)}")
                assertTrue(hasGlyph(if (physicalPreviewUsesMono(character)) supplement else base, character), "Missing $weight U+${character.code.toString(16)}")
            }
        }
        for (control in listOf('\u0000', '\u001b', '\u001d', '\u007f', '\u0085')) {
            assertIs<NativeCoverageResult.Unsupported>(NativeTextRepertoires.assess(control.toString(), "cp437"))
            assertFalse(physicalPreviewUsesMono(control))
        }
    }

    private fun hasGlyph(bytes: ByteArray, character: Char): Boolean {
        val font = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        fun u16(offset: Int) = font.getShort(offset).toInt() and 65535
        val cmap = (0 until u16(4)).map { 12 + it * 16 }.first {
            String(bytes, it, 4, Charsets.US_ASCII) == "cmap"
        }.let { font.getInt(it + 8) }
        val table = (0 until u16(cmap + 2)).map { cmap + font.getInt(cmap + 8 + it * 8) }.first { u16(it) == 4 }
        val count = u16(table + 6) / 2
        val ends = table + 14
        val starts = ends + count * 2 + 2
        val deltas = starts + count * 2
        val offsets = deltas + count * 2
        val index = (0 until count).firstOrNull { character.code in u16(starts + it * 2)..u16(ends + it * 2) } ?: return false
        val delta = font.getShort(deltas + index * 2).toInt()
        val range = u16(offsets + index * 2)
        val glyph = if (range == 0) (character.code + delta) and 65535 else {
            val raw = u16(offsets + index * 2 + range + (character.code - u16(starts + index * 2)) * 2)
            if (raw == 0) 0 else (raw + delta) and 65535
        }
        return glyph != 0
    }

    @Test fun bundledLicensePreservesUpstreamOflAndAttribution() {
        val license = resource("files/fonts/NOTO_OFL.txt").toString(Charsets.UTF_8)
        assertTrue(license.startsWith("Copyright 2018 The Noto Project Authors"))
        assertTrue("SIL OPEN FONT LICENSE Version 1.1" in license)
        assertTrue("PERMISSION & CONDITIONS" in license)
        assertTrue("TERMINATION" in license)
        assertTrue("DISCLAIMER" in license)
        val readme = resource("files/fonts/NOTO_README.md").toString(Charsets.UTF_8)
        assertTrue("Copyright 2015-2021 Google LLC. All Rights Reserved." in readme)
        assertTrue("version 2.008" in readme)
        assertTrue("version 2.007" in readme)
    }
}
