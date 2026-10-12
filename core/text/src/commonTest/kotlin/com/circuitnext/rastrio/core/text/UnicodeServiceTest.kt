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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UnicodeServiceTest {
    @Test fun unicode18IndicRuleAndSupplementaryGraphemes() {
        assertEquals(listOf(0, 2), UnicodeSegmentation.graphemeBoundaries("\u094d\u0915"))
        assertEquals(listOf(0, 3), UnicodeSegmentation.graphemeBoundaries("a\u094d\u0924"))
        assertEquals(listOf(0, 5), UnicodeSegmentation.graphemeBoundaries("👩‍💻"))
        assertEquals(listOf(0, 4, 8), UnicodeSegmentation.graphemeBoundaries("🇮🇳🇬🇧"))
        assertEquals(listOf(0, 2), UnicodeSegmentation.graphemeBoundaries("\r\n"))
        for (units in listOf(listOf(0xd800), listOf(0xdc00), listOf(0xd800, 0x78))) {
            // Runtime code units survive JS source encoding of lone-surrogate literals.
            val invalid = units.map { it.toChar() }.toCharArray().concatToString()
            assertEquals(units, invalid.map { it.code })
            assertFailsWith<IllegalArgumentException> { UnicodeSegmentation.graphemeBoundaries(invalid) }
        }
    }
    @Test fun nonbreakingAndNumericPunctuation() {
        assertEquals(listOf(3), UnicodeLineBreaking.opportunities("a\u00a0b").map { it.offset })
        assertEquals(listOf(1, 3), UnicodeLineBreaking.opportunities(" .5").map { it.offset })
        assertEquals(listOf(7), UnicodeLineBreaking.opportunities("$12.50%").map { it.offset })
    }
    @Test fun unsupportedAdapterHasStructuredFailureWithoutFabricatedMetrics() {
        val backend = UnsupportedRasterTextBackend()
        val exception = assertFailsWith<TextBackendUnavailableException> { backend.typography() }
        assertEquals("TXT132", exception.diagnostics.single().code)
        val typography = ResolvedTypography("test", TextStyle(), TypographyMetrics(1.0, 0.0, 0.0, 1.0, 1.0))
        val measurement = backend.measure(TextMeasureRequest("text", typography))
        assertTrue(measurement is TextMeasureResult.Failure)
        val raster = backend.rasterize(RasterTextRequest("text", typography, 8, 8, 4, 203, 203))
        assertTrue(raster is RasterTextResult.Failure)
    }
    @Test fun portableBitmapChecksPaddingAndTakesSnapshot() {
        val bytes = byteArrayOf(0x80.toByte(), 0x80.toByte())
        val bitmap = TextBitmap(9, 1, bytes)
        bytes.fill(0)
        assertTrue(bitmap.isBlack(0, 0))
        assertTrue(bitmap.isBlack(8, 0))
        assertFailsWith<IllegalArgumentException> { TextBitmap(9, 1, byteArrayOf(0, 1)) }
        assertFailsWith<IllegalArgumentException> { TextBitmap(Int.MAX_VALUE, 1, byteArrayOf()) }
    }
}
