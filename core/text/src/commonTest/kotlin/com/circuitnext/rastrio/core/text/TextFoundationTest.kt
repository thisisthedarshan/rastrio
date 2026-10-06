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

class TextFoundationTest {
    private val metrics = TypographyMetrics(3.0, 1.0, 1.0, 5.0, 3.0)
    private val body = ResolvedTypography("fixture-body-v1", TextStyle(), metrics, FixedCellGeometry(2.0, 2.0, 4.0))
    private val service = AsciiFixedCellMeasurer()

    @Test fun exactAdvancesAndSegmentation() {
        for ((text, advance) in listOf("" to 0.0, "A" to 2.0, "iii" to 6.0, "W W" to 6.0)) {
            val measured = assertIs<TextMeasureResult.Success>(service.measure(TextMeasureRequest(text, body))).measurement
            assertEquals(advance, measured.advanceMm)
            assertEquals(metrics, measured.metrics)
            assertEquals(text.indices.map { MeasuredCluster(it, it + 1, 2.0) }, measured.clusters)
        }
        val measured = assertIs<TextMeasureResult.Success>(service.measure(TextMeasureRequest("A B", body))).measurement
        assertEquals(listOf(BreakOpportunity(2, BreakKind.ALLOWED)), measured.breakOpportunities)
        assertEquals(service.measure(TextMeasureRequest("A B", body)), service.measure(TextMeasureRequest("A B", body)))
        assertEquals(9.0, assertIs<TextMeasureResult.Success>(service.measure(TextMeasureRequest("ABC",
            body.copy(fixedCell = FixedCellGeometry(3.0, 3.0, 4.0))))).measurement.advanceMm)
    }

    @Test fun unsupportedTextDoesNotReceiveInventedGeometry() {
        for (text in listOf("é", "e\u0301", "देव", "😀", "\uD800", "a\tb", "a\nb", "\u001b")) {
            val failure = assertIs<TextMeasureResult.Failure>(service.measure(TextMeasureRequest(text, body)))
            assertEquals("TXT100", failure.diagnostics.single().code)
            assertFalse(failure.diagnostics.single().message.contains(text))
        }
        assertIs<TextMeasureResult.Failure>(service.measure(TextMeasureRequest("abc", body.copy(fixedCell = null))))
    }

    @Test fun resourceAndArithmeticBoundaries() {
        val limited = AsciiFixedCellMeasurer(TextResourcePolicy(maxCodeUnits = 3))
        assertIs<TextMeasureResult.Success>(limited.measure(TextMeasureRequest("abc", body)))
        assertEquals("TXT120", assertIs<TextMeasureResult.Failure>(limited.measure(TextMeasureRequest("abcd", body))).diagnostics.single().code)
        val huge = body.copy(fixedCell = FixedCellGeometry(Double.MAX_VALUE, 2.0, 4.0))
        assertEquals("TXT120", assertIs<TextMeasureResult.Failure>(service.measure(TextMeasureRequest("abc", huge))).diagnostics.single().code)
    }

    @Test fun invalidMetricsAreRejected() {
        for (bad in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { metrics.copy(ascentMm = bad) }
            assertFailsWith<IllegalArgumentException> { FixedCellGeometry(bad, 2.0, 4.0) }
        }
        assertFailsWith<IllegalArgumentException> { metrics.copy(lineHeightMm = 4.0) }
        assertFailsWith<IllegalArgumentException> { metrics.copy(baselineOffsetMm = 2.0) }
        assertFailsWith<IllegalArgumentException> { metrics.copy(baselineOffsetMm = 5.0) }
        assertFailsWith<IllegalArgumentException> { FixedCellGeometry(0.0, 2.0, 4.0) }
    }

    @Test fun contextSelectionAndSnapshots() {
        val heading = body.copy(identity = "heading-v1")
        val entries = MutableList(6) { heading }
        val context = TypographyContext(body, body.copy(identity = "code-v1"), body.copy(identity = "marker-v1"), SnapshotList(entries))
        entries.clear()
        assertEquals(heading, context.heading(1))
        assertEquals(heading, context.heading(6))
        assertEquals("code-v1", context.code.identity)
        assertEquals("marker-v1", context.listMarker.identity)
        assertEquals(context, context.copy())
        assertFailsWith<IllegalArgumentException> { context.heading(0) }
        assertFailsWith<IllegalArgumentException> { context.copy(headings = SnapshotList(emptyList())) }
        assertFalse((context.headings as List<*>) is MutableList<*>)
    }
    @Test fun measurementRejectsInconsistentAndUnsafeBoundaries() {
        assertFailsWith<IllegalArgumentException> {
            TextMeasurement(2, 4.0, metrics, SnapshotList(listOf(MeasuredCluster(0, 1, 2.0))), SnapshotList(emptyList()))
        }
        assertFailsWith<IllegalArgumentException> {
            TextMeasurement(2, 4.0, metrics, SnapshotList(listOf(MeasuredCluster(0, 2, 4.0))), SnapshotList(listOf(BreakOpportunity(1, BreakKind.ALLOWED))))
        }
        assertFailsWith<IllegalArgumentException> {
            TextMeasurement(1, 99.0, metrics, SnapshotList(listOf(MeasuredCluster(0, 1, 2.0))), SnapshotList(emptyList()))
        }
    }

    @Test fun fractionalCellsHaveCanonicalTotals() {
        val fractional = body.copy(fixedCell = FixedCellGeometry(0.1, 0.1, 4.0))
        val request = TextMeasureRequest("ABC", fractional)
        val result = assertIs<TextMeasureResult.Success>(service.measure(request))
        assertEquals(0.3, result.measurement.advanceMm)
        assertEquals(result, service.measure(request))
        // Total equality is defined on the logical grid, not by binary left-to-right addition.
        assertEquals(0.3, TextMeasurement(3, 0.3, metrics,
            SnapshotList((0..2).map { MeasuredCluster(it, it + 1, 0.1) }), SnapshotList(emptyList())).advanceMm)
    }

    @Test fun logicalTickConversionsPreserveTiesAndCheckedRanges() {
        assertEquals(0L, LogicalGeometry.ticks(0.0000005))
        assertEquals(2L, LogicalGeometry.ticks(0.0000015))
        assertEquals(2L, LogicalGeometry.ticks(0.0000025))
        assertEquals(300000L, LogicalGeometry.ticks(0.3))
        assertEquals(0.3, LogicalGeometry.millimetres(300000L))
        assertEquals(0.0, LogicalGeometry.millimetres(0L))
        assertEquals(1000000000000000L, LogicalGeometry.ticks(1000000000.0))
        assertEquals(1000000000.0, LogicalGeometry.millimetres(1000000000000000L))
        for (value in listOf(-0.0000001, Double.NaN, Double.POSITIVE_INFINITY, 1000000000.000001))
            assertFailsWith<IllegalArgumentException> { LogicalGeometry.ticks(value) }
        for (value in listOf(-1L, 1000000000000001L, Long.MAX_VALUE))
            assertFailsWith<IllegalArgumentException> { LogicalGeometry.millimetres(value) }
    }

    @Test fun logicalPrecisionHasExplicitTiesRangeAndOverflowBoundaries() {
        assertEquals(0.0, LogicalGeometry.normalize(0.0000005))
        assertEquals(0.000002, LogicalGeometry.normalize(0.0000015))
        assertEquals(0.000002, LogicalGeometry.normalize(0.0000025))
        assertTrue(LogicalGeometry.fits(0.3000004, 0.3))
        assertFalse(LogicalGeometry.fits(0.300001, 0.3))
        assertEquals(0.3, LogicalGeometry.add(LogicalGeometry.add(0.1, 0.1), 0.1))
        assertEquals(0.1, LogicalGeometry.subtract(0.3, 0.2))
        assertEquals(0.15, LogicalGeometry.scale(0.3, 0.5))
        for (value in listOf(0.3, 0.000002, 999999999.999999, 1000000000.0)) {
            val canonical = LogicalGeometry.normalize(value)
            assertEquals(canonical, LogicalGeometry.normalize(canonical))
        }
        assertFailsWith<IllegalArgumentException> { LogicalGeometry.add(1000000000.0, 0.000001) }
        for (value in listOf(-0.0000001, Double.NaN, Double.POSITIVE_INFINITY, 1000000000.000001)) {
            assertFailsWith<IllegalArgumentException> { LogicalGeometry.normalize(value) }
        }
    }

}
