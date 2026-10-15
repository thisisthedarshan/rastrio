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
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals
import kotlin.test.assertIs

class BundledRasterTextBackendTest {
    private val backend = BundledRasterTextBackend.create()
    @Test fun arrowsUseGenuineFallbackFacesWithoutChangingBodyTextOrSplittingClusters() {
        for (style in listOf(TextStyle(), TextStyle(weight = TextWeight.BOLD),
            TextStyle(emphasis = true), TextStyle(weight = TextWeight.BOLD, emphasis = true))) {
            val body = backend.typography(style)
            val code = backend.typography(style.copy(role = TextRole.CODE))
            assertTrue(body.metrics.ascentMm >= code.metrics.ascentMm)
            assertTrue(body.metrics.descentMm >= code.metrics.descentMm)
            assertTrue(body.metrics.lineHeightMm >= code.metrics.lineHeightMm)
            val variant = when {
                style.weight == TextWeight.BOLD && style.emphasis -> "BoldItalic"
                style.weight == TextWeight.BOLD -> "Bold"
                style.emphasis -> "Italic"
                else -> "Regular"
            }
            for (arrow in listOf("↑", "→", "↓", "↗", "↘")) {
                fun measured(text: String, typography: ResolvedTypography) = assertIs<TextMeasureResult.Success>(
                    backend.measure(TextMeasureRequest(text, typography))).measurement
                val mixed = measured("AV${arrow}To", body)
                assertEquals(LogicalGeometry.normalize(measured("AV", body).advanceMm +
                    measured(arrow, code).advanceMm + measured("To", body).advanceMm), mixed.advanceMm)
                val request = RasterTextRequest("AV${arrow}To", body, 577, 64, 40, 203, 203, originDots = 16)
                val raster = assertIs<RasterTextResult.Success>(backend.rasterize(request))
                assertTrue("NotoSans-$variant@" in raster.fontIdentity)
                assertTrue("JetBrainsMono-$variant@" in raster.fontIdentity)
                assertContentEquals(raster.bitmap.copyBytes(), assertIs<RasterTextResult.Success>(backend.rasterize(request)).bitmap.copyBytes())
                val standalone = assertIs<RasterTextResult.Success>(backend.rasterize(request.copy(text = arrow)))
                val expected = assertIs<RasterTextResult.Success>(backend.rasterize(request.copy(text = arrow, typography = code)))
                assertContentEquals(expected.bitmap.copyBytes(), standalone.bitmap.copyBytes())
                assertTrue(standalone.bitmap.copyBytes().any { it.toInt() != 0 })
                val combining = measured(arrow + "\u0301", body)
                assertEquals(1, combining.clusters.size)
                assertEquals(2, combining.clusters.single().endOffset)
            }
        }
    }
    @Test fun unsupportedSymbolsIdentifyCodePointWithoutDisclosingSurroundingText() {
        val typography = backend.typography()
        for ((symbol, point) in listOf("🎵" to "U+1F3B5", "🎼" to "U+1F3BC", "↗\uFE0F" to "U+FE0F")) {
            val text = "private text $symbol private suffix"
            val diagnostic = assertIs<TextMeasureResult.Failure>(backend.measure(TextMeasureRequest(text, typography))).diagnostics.single()
            assertEquals("TXT130", diagnostic.code)
            assertTrue(point in diagnostic.message, diagnostic.message)
            assertTrue("private" !in diagnostic.message)
            val raster = assertIs<RasterTextResult.Failure>(backend.rasterize(RasterTextRequest(text, typography, 577, 64, 40, 203, 203)))
            assertTrue(point in raster.diagnostics.single().message)
        }
    }
    @Test fun arrowFallbackKeepsRunBoundsAndCancellation() {
        val typography = backend.typography()
        val exact = "A↑".repeat(128)
        assertIs<TextMeasureResult.Success>(backend.measure(TextMeasureRequest(exact, typography)))
        assertEquals("TXT120", assertIs<TextMeasureResult.Failure>(backend.measure(TextMeasureRequest(exact + "A", typography))).diagnostics.single().code)
        assertEquals("TXT120", assertIs<RasterTextResult.Failure>(backend.rasterize(RasterTextRequest(exact + "A", typography, 8192, 64, 40, 203, 203))).diagnostics.single().code)
        var polls = 0
        assertIs<TextMeasureResult.Failure>(backend.measure(TextMeasureRequest(exact, typography)) { ++polls >= 8 })
        assertEquals(8, polls)
    }
    @Test fun genuineShapingAndDeterministicRaster() {
        val typography = backend.typography(sizeMm = 3.0)
        for (text in listOf("Hello", "a\u0301", "क्षि", "नमस्ते", "Latin हिन्दी", "Ελληνικά Привет", "iİıI")) {
            val measured = assertIs<TextMeasureResult.Success>(backend.measure(TextMeasureRequest(text, typography))).measurement
            assertTrue(measured.advanceMm > 0)
            if (text == "क्षि") assertEquals(1, measured.clusters.size)
            val request = RasterTextRequest(text, typography, 576, 64, 40, 203, 203)
            val raster = assertIs<RasterTextResult.Success>(backend.rasterize(request)).bitmap
            assertTrue(raster.copyBytes().any { it.toInt() != 0 })
            assertContentEquals(raster.copyBytes(), assertIs<RasterTextResult.Success>(backend.rasterize(request)).bitmap.copyBytes())
        }
    }
    @Test fun genuineBundledStyleFacesHavePinnedIdentitiesAndDistinctRasterOutput() {
        val styles = listOf(
            TextStyle(weight = TextWeight.BOLD) to "NotoSans-Bold@c976e4b1b99edc88775377fcc21692ca4bfa46b6d6ca6522bfda505b28ff9d6a",
            TextStyle(emphasis = true) to "NotoSans-Italic@36cff144df01309dab648bea71baff9bb074026914afe63aeacc8bc90b67a28b",
            TextStyle(weight = TextWeight.BOLD, emphasis = true) to "NotoSans-BoldItalic@6edf4227ef0fa846aca70e86a307804ca4401741830f5b3af0f2554abe2b8466",
            TextStyle(role = TextRole.CODE) to "JetBrainsMono-Regular@a0bf60ef0f83c5ed4d7a75d45838548b1f6873372dfac88f71804491898d138f",
            TextStyle(role = TextRole.CODE, weight = TextWeight.BOLD) to "JetBrainsMono-Bold@5590990c82e097397517f275f430af4546e1c45cff408bde4255dad142479dcb",
            TextStyle(role = TextRole.CODE, emphasis = true) to "JetBrainsMono-Italic@9d0a1f7a708e6af183f1193b7e81d40da294f5c67682c085d8401c60aac8ded4",
            TextStyle(role = TextRole.CODE, weight = TextWeight.BOLD, emphasis = true) to "JetBrainsMono-BoldItalic@4039d5ce0ed225bf9c8b2c8c6436290ae2f356b7e90d70fa666227238324aa3b",
        )
        val bitmaps = HashSet<List<Byte>>()
        for ((style, identity) in styles) {
            val typography = backend.typography(style)
            val result = assertIs<RasterTextResult.Success>(backend.rasterize(
                RasterTextRequest("Hello", typography, 576, 64, 40, 203, 203, originDots = 16)))
            assertEquals(identity, result.fontIdentity)
            assertTrue(result.bitmap.copyBytes().any { it.toInt() != 0 })
            bitmaps.add(result.bitmap.copyBytes().toList())
        }
        assertEquals(styles.size, bitmaps.size)
    }
    @Test fun rasterMeasurementRunEndsDoNotForceStyleSpanBreaks() {
        for ((text, style) in listOf("ab" to TextStyle(), "cd" to TextStyle(weight = TextWeight.BOLD))) {
            val measured = assertIs<TextMeasureResult.Success>(backend.measure(TextMeasureRequest(text, backend.typography(style)))).measurement
            assertEquals(emptyList(), measured.breakOpportunities)
        }
        val hyphenated = assertIs<TextMeasureResult.Success>(backend.measure(TextMeasureRequest("prefix-suffix", backend.typography()))).measurement
        assertEquals(listOf(BreakOpportunity(7, BreakKind.ALLOWED)), hyphenated.breakOpportunities)
    }
    @Test fun controlledLinuxRasterGoldens() {
        if (!System.getProperty("os.name").contains("Linux") || System.getProperty("os.arch") !in listOf("amd64", "x86_64")) return
        val typography = backend.typography(sizeMm = 3.0)
        val expected = mapOf(
            "Hello" to "0cf429c0a541c9e67c698ccbc203246b4d9b4b0750fa20961b9ffae282a60db5",
            "a\u0301" to "2cb0a39671803befba4cf1c84ca21cda4cc93430db84d67e8b03403cd99779dc",
            "क्षि" to "e699280399b6335f623180174c94e42a363849992cb28cdde2a0eb3a4244e040",
            "नमस्ते" to "576fbcdf8a0549903a47160f3b611d35226df5aaa9d4784d28d75920e0f4d97f",
            "Latin हिन्दी" to "8c96d53a55fa997708d69bacd022e6a1369418068e750dac1b0dd6024e608493",
        )
        for ((text, hash) in expected) {
            val result = assertIs<RasterTextResult.Success>(backend.rasterize(RasterTextRequest(text, typography, 576, 64, 40, 203, 203)))
            val actual = java.security.MessageDigest.getInstance("SHA-256").digest(result.bitmap.copyBytes()).joinToString("") { "%02x".format(it.toInt() and 255) }
            assertEquals(hash, actual)
            assertTrue(result.fontIdentity.contains("@"))
        }
    }
    @Test fun nativeCancellationAndOddWidthAreBounded() {
        val typography = backend.typography()
        var polls = 0
        assertIs<RasterTextResult.Failure>(backend.rasterize(RasterTextRequest("Long run", typography, 577, 64, 40, 203, 203, { ++polls >= 4 })))
        val odd = assertIs<RasterTextResult.Success>(backend.rasterize(RasterTextRequest("Hello", typography, 577, 64, 40, 203, 203))).bitmap
        for (y in 0 until odd.heightDots) assertEquals(0, odd.copyBytes()[(y + 1) * odd.rowBytes - 1].toInt() and 0x7f)
    }
    @Test fun measurementCancellationReachesBetweenNativeFallbackRuns() {
        val typography = backend.typography()
        var polls = 0
        val result = assertIs<TextMeasureResult.Failure>(backend.measure(
            TextMeasureRequest("Жक".repeat(16), typography), { ++polls >= 10 }))
        assertEquals("TXT130", result.diagnostics.single().code)
        assertEquals(10, polls)
        assertIs<TextMeasureResult.Success>(backend.measure(TextMeasureRequest("Жक", typography)))
    }
    @Test fun fontRunWorkLimitAcceptsExactBoundaryAndRejectsOneOverBeforeShaping() {
        val typography = backend.typography()
        val exact = "Жक".repeat(128)
        assertIs<TextMeasureResult.Success>(backend.measure(TextMeasureRequest(exact, typography)))
        assertEquals("TXT120", assertIs<TextMeasureResult.Failure>(backend.measure(
            TextMeasureRequest(exact + "Ж", typography))).diagnostics.single().code)
        assertIs<RasterTextResult.Success>(backend.rasterize(RasterTextRequest(exact, typography, 8192, 64, 40, 203, 203)))
        assertEquals("TXT120", assertIs<RasterTextResult.Failure>(backend.rasterize(
            RasterTextRequest(exact + "Ж", typography, 8192, 64, 40, 203, 203))).diagnostics.single().code)
    }
    @Test fun maximumUniformTextRemainsRepresentableWithinOneFontRun() {
        val typography = backend.typography()
        val result = assertIs<TextMeasureResult.Success>(backend.measure(TextMeasureRequest("Ж".repeat(65536), typography)))
        assertEquals(65536, result.measurement.textLength)
        assertEquals(65536, result.measurement.clusters.size)
    }
    @Test fun missingGlyphBidiStyleAndResourceFailuresAreStructured() {
        val typography = backend.typography()
        for (text in listOf("😀", "العربية", "\ud800", "x".repeat(65537)))
            assertIs<TextMeasureResult.Failure>(backend.measure(TextMeasureRequest(text, typography)))
        assertIs<TextMeasureResult.Failure>(backend.measure(TextMeasureRequest("नमस्ते", backend.typography(TextStyle(emphasis = true)))))
        assertIs<RasterTextResult.Failure>(backend.rasterize(RasterTextRequest("text", typography, 32768, 32768, 40, 203, 203)))
        assertIs<RasterTextResult.Failure>(backend.rasterize(RasterTextRequest("text", typography, 576, 64, 40, 203, 203, { true })))
    }
    @Test fun directionMarksAndFormattingCannotSilentlyDisappear() {
        val typography = backend.typography()
        for (code in listOf(0x200e, 0x200f) + (0x202a..0x202e) + (0x2066..0x206f)) {
            val text = "A${code.toChar()}B"
            assertEquals("TXT130", assertIs<TextMeasureResult.Failure>(backend.measure(
                TextMeasureRequest(text, typography))).diagnostics.single().code)
            assertIs<RasterTextResult.Failure>(backend.rasterize(
                RasterTextRequest(text, typography, 576, 64, 40, 203, 203)))
        }
    }
    @Test fun bitmapDefensivelyOwnsBytesAndPadding() {
        val bytes = byteArrayOf(0x80.toByte())
        val bitmap = TextBitmap(1, 1, bytes)
        bytes[0] = 0
        assertTrue(bitmap.isBlack(0, 0))
        bitmap.copyBytes()[0] = 0
        assertTrue(bitmap.isBlack(0, 0))
    }
}
