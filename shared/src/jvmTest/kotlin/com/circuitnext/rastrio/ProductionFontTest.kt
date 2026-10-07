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

import com.circuitnext.rastrio.presentation.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.*

class ProductionFontTest {
    @Test fun productionMeasurementsMatchPinnedFontTables() {
        val stream = javaClass.classLoader.getResourceAsStream(
            "composeResources/rastrio.shared.generated.resources/font/jetbrains_mono_regular.ttf")
        val bytes = assertNotNull(stream).use { it.readBytes() }
        val font = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        fun u16(offset: Int) = font.getShort(offset).toInt() and 65535
        val tables = (0 until u16(4)).associate { i ->
            val offset = 12 + i * 16
            String(bytes, offset, 4, Charsets.US_ASCII) to font.getInt(offset + 8)
        }
        val units = u16(tables.getValue("head") + 18)
        val hhea = tables.getValue("hhea")
        val session = createAuthoringSession()
        session.edit("A~")
        session.compile()
        val ready = assertIs<MarkdownAuthoringResult.Ready>(session.state.result)
        val typography = ready.logicalDocument.constraints.typography.body
        val scale = 3.0 / units
        assertEquals(font.getShort(hhea + 4) * scale, typography.metrics.ascentMm, 0.000001)
        assertEquals(-font.getShort(hhea + 6) * scale, typography.metrics.descentMm, 0.000001)
        assertEquals(font.getShort(hhea + 8) * scale, typography.metrics.lineGapMm, 0.000001)
        // Resolve each printable ASCII glyph using the font's format-4 cmap, not assumed test metrics.
        val cmap = tables.getValue("cmap")
        val subtable = (0 until u16(cmap + 2)).map { cmap + font.getInt(cmap + 4 + it * 8 + 4) }
            .first { u16(it) == 4 }
        val segments = u16(subtable + 6) / 2
        val ends = subtable + 14
        val starts = ends + segments * 2 + 2
        val deltas = starts + segments * 2
        val offsets = deltas + segments * 2
        val horizontalMetrics = u16(hhea + 34)
        for (code in 32..126) {
            val segment = (0 until segments).first { code in u16(starts + it * 2)..u16(ends + it * 2) }
            val delta = font.getShort(deltas + segment * 2).toInt()
            val rangeOffset = u16(offsets + segment * 2)
            val glyph = if (rangeOffset == 0) (code + delta) and 65535 else {
                val raw = u16(offsets + segment * 2 + rangeOffset + (code - u16(starts + segment * 2)) * 2)
                if (raw == 0) 0 else (raw + delta) and 65535
            }
            assertTrue(glyph != 0, "ASCII coverage missing: $code")
            val advance = u16(tables.getValue("hmtx") + minOf(glyph, horizontalMetrics - 1) * 4)
            assertEquals(advance * scale, assertNotNull(typography.fixedCell).advanceMm, 0.000001)
        }
    }
}
