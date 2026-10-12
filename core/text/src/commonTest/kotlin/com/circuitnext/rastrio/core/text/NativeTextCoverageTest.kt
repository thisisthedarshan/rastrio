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

import kotlin.test.*

class NativeTextCoverageTest {
    private val metrics = TypographyMetrics(3.0, 1.0, 1.0, 5.0, 3.0)
    private val typography = ResolvedTypography("cp437-fixture-v1", TextStyle(), metrics, FixedCellGeometry(0.1, 0.1, 4.0))

    @Test fun exactCharacterBytesArePortableAndImmutable() {
        val samples = listOf(
            " A~" to listOf(0x20, 0x41, 0x7e),
            "ÇüéÄÅæÖÜ£¥ñ¿½«»" to listOf(0x80, 0x81, 0x82, 0x8e, 0x8f, 0x91, 0x99, 0x9a, 0x9c, 0x9d, 0xa4, 0xa8, 0xab, 0xae, 0xaf),
            "░│─┼╔═┌█▀" to listOf(0xb0, 0xb3, 0xc4, 0xc5, 0xc9, 0xcd, 0xda, 0xdb, 0xdf),
            "αßπµΩ∞±≥÷°√²■\u00a0" to listOf(0xe0, 0xe1, 0xe3, 0xe6, 0xea, 0xec, 0xf1, 0xf2, 0xf6, 0xf8, 0xfb, 0xfd, 0xfe, 0xff),
        )
        for ((text, bytes) in samples) {
            val result = assertIs<NativeCoverageResult.Representable>(NativeTextRepertoires.assess(text, "cp437"))
            assertEquals(bytes, result.characterBytes)
            assertEquals(result, NativeTextRepertoires.assess(text, "cp437"))
            assertFalse((result.characterBytes as List<*>) is MutableList<*>)
        }
        val bytes = mutableListOf(0x41)
        val snapshot = NativeCoverageResult.Representable(SnapshotList(bytes))
        bytes.clear()
        assertEquals(listOf(0x41), snapshot.characterBytes)
        assertEquals(emptyList(), assertIs<NativeCoverageResult.Representable>(NativeTextRepertoires.assess("", "cp437")).characterBytes)
    }

    @Test fun controlsAndLowBytePictographsNeverBecomePrinterCommands() {
        for (character in (0..0x1f).map(Int::toChar) + listOf('\u007f', '\u0080', '☺', '☻', '♥', '⌂')) {
            assertIs<NativeCoverageResult.Unsupported>(NativeTextRepertoires.assess("A${character}B", "cp437"))
        }
        for (byte in listOf(0, 9, 10, 13, 27, 31, 127, 256, -1)) {
            assertFailsWith<IllegalArgumentException> { NativeCoverageResult.Representable(SnapshotList(listOf(byte))) }
        }
    }

    @Test fun unsupportedSequencesFailWithoutReplacementOrLeakingContent() {
        val invalidHigh = CharArray(1) { 0xD800.toChar() }.concatToString()
        val invalidLow = CharArray(1) { 0xDC00.toChar() }.concatToString()
        assertEquals(0xD800, invalidHigh.single().code)
        assertEquals(0xDC00, invalidLow.single().code)
        for (text in listOf("Ā", "€", "e\u0301", "देव", "العربية", "😀", invalidHigh, invalidLow, "a\u200db", "a\u202eb", "a\uFE0Fb")) {
            val coverage = assertIs<NativeCoverageResult.Unsupported>(NativeTextRepertoires.assess(text, "cp437"))
            assertEquals("TXT100", coverage.diagnostics.single().code)
            assertFalse(coverage.diagnostics.single().message.contains(text))
            assertIs<TextMeasureResult.Failure>(RegisteredFixedCellMeasurer().measure(TextMeasureRequest(text, typography)))
        }
        assertIs<NativeCoverageResult.Unknown>(NativeTextRepertoires.assess("ASCII", "CP437"))
        assertIs<NativeCoverageResult.Unknown>(NativeTextRepertoires.assess("", "cp850"))
        assertEquals("TXT102", assertIs<TextMeasureResult.Failure>(RegisteredFixedCellMeasurer("cp850")
            .measure(TextMeasureRequest("ASCII", typography))).diagnostics.single().code)
    }

    @Test fun fixedCellsUseSuppliedMetricsAndPreserveNonBreakingSpace() {
        val measurer = RegisteredFixedCellMeasurer()
        val request = TextMeasureRequest("é \u00a0│", typography)
        val measurement = assertIs<TextMeasureResult.Success>(measurer.measure(request)).measurement
        assertEquals(0.4, measurement.advanceMm)
        assertEquals(metrics, measurement.metrics)
        assertEquals((0..3).map { MeasuredCluster(it, it + 1, 0.1) }, measurement.clusters)
        assertEquals(listOf(BreakOpportunity(2, BreakKind.ALLOWED)), measurement.breakOpportunities)
        assertEquals(measurer.measure(request), measurer.measure(request))
        val wider = typography.copy(fixedCell = FixedCellGeometry(2.5, 2.0, 4.0))
        assertEquals(10.0, assertIs<TextMeasureResult.Success>(measurer.measure(request.copy(typography = wider))).measurement.advanceMm)
        val asciiRequest = request.copy(text = "A B~")
        val ascii = assertIs<TextMeasureResult.Success>(AsciiFixedCellMeasurer().measure(asciiRequest)).measurement
        val native = assertIs<TextMeasureResult.Success>(measurer.measure(asciiRequest)).measurement
        assertEquals(ascii.clusters, native.clusters)
        assertEquals(ascii.advanceMm, native.advanceMm)
        assertEquals(ascii.metrics, native.metrics)
        assertIs<TextMeasureResult.Failure>(AsciiFixedCellMeasurer().measure(request))
    }

    @Test fun nativeBreaksUseUnicode18WithoutChangingCoverage() {
        val measurer = RegisteredFixedCellMeasurer()
        for ((text, expected) in listOf(
            "a-b" to listOf(BreakOpportunity(2, BreakKind.ALLOWED)),
            "-ab" to emptyList(),
            "a\u00a0b" to emptyList(),
        )) {
            val measured = assertIs<TextMeasureResult.Success>(measurer.measure(TextMeasureRequest(text, typography))).measurement
            assertEquals(expected, measured.breakOpportunities)
            assertEquals(text.indices.map { MeasuredCluster(it, it + 1, 0.1) }, measured.clusters)
            assertEquals(LogicalGeometry.millimetres(100_000L * text.length), measured.advanceMm)
        }
        val unsupported = assertIs<TextMeasureResult.Failure>(measurer.measure(TextMeasureRequest("漢字", typography)))
        assertEquals("TXT100", unsupported.diagnostics.single().code)
    }

    @Test fun nativeMeasurementRunEndsDoNotForceStyleSpanBreaks() {
        val measurer = RegisteredFixedCellMeasurer()
        for ((text, style) in listOf("ab" to TextStyle(), "cd" to TextStyle(weight = TextWeight.BOLD))) {
            val measured = assertIs<TextMeasureResult.Success>(measurer.measure(TextMeasureRequest(text, typography.copy(style = style)))).measurement
            assertEquals(emptyList(), measured.breakOpportunities)
        }
        val hyphenated = assertIs<TextMeasureResult.Success>(measurer.measure(TextMeasureRequest("prefix-suffix", typography))).measurement
        assertEquals(listOf(BreakOpportunity(7, BreakKind.ALLOWED)), hyphenated.breakOpportunities)
    }

    @Test fun resourcesAndCoordinateOverflowFailBeforeFinalGeometry() {
        val resources = TextResourcePolicy(3)
        assertIs<NativeCoverageResult.Representable>(NativeTextRepertoires.assess("é│α", "cp437", resources))
        assertEquals("TXT120", assertIs<NativeCoverageResult.Unsupported>(NativeTextRepertoires.assess("é│αx", "cp437", resources)).diagnostics.single().code)
        val limited = RegisteredFixedCellMeasurer(resources = resources)
        assertIs<TextMeasureResult.Success>(limited.measure(TextMeasureRequest("é│α", typography)))
        assertEquals("TXT120", assertIs<TextMeasureResult.Failure>(limited.measure(TextMeasureRequest("é│αx", typography))).diagnostics.single().code)
        for (advance in listOf(Double.MAX_VALUE, 1_000_000_000.0)) {
            val huge = typography.copy(fixedCell = FixedCellGeometry(advance, 2.0, 4.0))
            assertEquals("TXT120", assertIs<TextMeasureResult.Failure>(RegisteredFixedCellMeasurer()
                .measure(TextMeasureRequest("éé", huge))).diagnostics.single().code)
        }
        assertEquals("TXT101", assertIs<TextMeasureResult.Failure>(RegisteredFixedCellMeasurer()
            .measure(TextMeasureRequest("é", typography.copy(fixedCell = null)))).diagnostics.single().code)
    }
}
