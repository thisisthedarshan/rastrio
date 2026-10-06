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

package com.circuitnext.rastrio.core.layout

import com.circuitnext.rastrio.core.document.*
import com.circuitnext.rastrio.core.text.*
import kotlin.test.*

class LayoutResourceSafetyTest {
    private val body = ResolvedTypography("resource-fixture", TextStyle(), TypographyMetrics(3.0, 1.0, 1.0, 5.0, 3.0), FixedCellGeometry(1.0, 1.0, 4.0))
    private val input = LayoutConstraints(Length(0.000001), Orientation.PORTRAIT, TypographyContext(body, body, body))
    private val engine = FoundationLayoutEngine(AsciiFixedCellMeasurer())
    private fun document(text: String) = ThermalDocument(blocks = listOf(Paragraph(Alignment.LEFT, listOf(Text(text)))))
    private fun failure(result: LayoutResult) = assertIs<LayoutResult.Failure>(result).also { assertEquals("LAY120", it.diagnostics.single().code) }
    private fun lines(result: LayoutResult) = assertIs<LogicalTextBlock>(assertIs<LayoutResult.Success>(result).document.blocks.single()).lines

    @Test fun maximumParagraphAmplificationStopsBeforeSecondMeasurement() {
        val text = "A".repeat(65536)
        val document = ThermalDocument(blocks = List(2) { Paragraph(Alignment.LEFT, listOf(Text(text))) })
        var calls = 0
        val service = TextMeasurer { request ->
            calls++
            check(calls == 1) { "Pathological layout reached another measurement" }
            AsciiFixedCellMeasurer().measure(request)
        }
        val engine = FoundationLayoutEngine(service)
        val result = failure(engine.layout(document, input))
        assertEquals(0, result.diagnostics.single().sourceBlockIndex)
        assertEquals(1, calls)
        calls = 0
        assertEquals(result, engine.layout(document, input))
        assertEquals(1, calls)
    }

    @Test fun overflowDiagnosticsAreCoalescedPerBlockAndChargedAtTheBoundary() {
        val exact = input.copy(resources = input.resources.copy(maxItems = 12))
        val output = assertIs<LayoutResult.Success>(engine.layout(document("ABC"), exact)).document
        assertEquals(3, assertIs<LogicalTextBlock>(output.blocks.single()).lines.size)
        assertEquals(1, output.diagnostics.size)
        assertEquals("LAY101", output.diagnostics.single().code)
        assertEquals(0, output.diagnostics.single().sourceBlockIndex)
        assertEquals(output, assertIs<LayoutResult.Success>(engine.layout(document("ABC"), exact)).document)
        failure(engine.layout(document("ABC"), exact.copy(resources = exact.resources.copy(maxItems = 11))))
        failure(engine.layout(document("ABCD"), exact))
        val multi = ThermalDocument(blocks = List(3) { Paragraph(Alignment.LEFT, listOf(Text("ABC"))) })
        val diagnostics = assertIs<LayoutResult.Success>(engine.layout(multi, input)).document.diagnostics
        assertEquals(listOf(0, 1, 2), diagnostics.map { it.sourceBlockIndex })
    }

    @Test fun normalMaximumAsciiParagraphStillFitsTheDefaultPolicy() {
        val text = "A".repeat(65536)
        val wide = input.copy(canvasWidth = Length(1000.0))
        val result = engine.layout(document(text), wide)
        val output = assertIs<LayoutResult.Success>(result).document
        val lines = lines(result)
        assertEquals(66, lines.size)
        assertEquals(536.0, lines.last().advanceMm)
        assertEquals(332.5, output.heightMm)
        assertEquals(text, lines.joinToString("") { it.runs.single().text })
        assertTrue(output.diagnostics.isEmpty())
        assertEquals(output, assertIs<LayoutResult.Success>(engine.layout(document(text), wide)).document)
    }

    @Test fun explicitBreakMarkersAreReservedBeforeGeometry() {
        val document = ThermalDocument(blocks = listOf(Paragraph(Alignment.LEFT, List(2) { LineBreak })))
        val exact = input.copy(resources = input.resources.copy(maxItems = 9))
        assertEquals(3, lines(engine.layout(document, exact)).size)
        failure(engine.layout(document, exact.copy(resources = exact.resources.copy(maxItems = 8))))
        val many = ThermalDocument(blocks = listOf(Paragraph(Alignment.LEFT, List(10000) { LineBreak })))
        failure(engine.layout(many, input.copy(resources = input.resources.copy(maxItems = 3))))
    }

    @Test fun zeroAdvanceStyledSpansAreReservedBeforeMoreMeasurement() {
        val zero = body.copy(fixedCell = FixedCellGeometry(0.0000004, 1.0, 4.0))
        val zeroInput = input.copy(typography = TypographyContext(zero, zero, zero), resources = input.resources.copy(maxItems = 15))
        val document = ThermalDocument(blocks = listOf(Paragraph(Alignment.LEFT, List(8) { i ->
            if (i % 2 == 0) Text("A") else Strong(listOf(Text("A")))
        })))
        var calls = 0
        val service = TextMeasurer { request -> calls++; AsciiFixedCellMeasurer().measure(request) }
        failure(FoundationLayoutEngine(service).layout(document, zeroInput))
        assertEquals(7, calls)
        val exact = zeroInput.copy(resources = zeroInput.resources.copy(maxItems = 26))
        val line = lines(engine.layout(document, exact)).single()
        assertEquals(8, line.runs.size)
        assertEquals(0.0, line.advanceMm)
        failure(engine.layout(document, exact.copy(resources = exact.resources.copy(maxItems = 25))))
    }

    @Test fun stdlibBreakLookupExcludesSliceStartAndIncludesSliceEnd() {
        val widths = listOf(1.0, 2.0, 3.0, 4.0, 7.0)
        for (width in widths) {
            val output = lines(engine.layout(document("A B C D"), input.copy(canvasWidth = Length(width))))
            assertEquals("A B C D", output.joinToString("") { it.runs.single().text })
            for (line in output) {
                val measurement = line.runs.single().measurement
                val expected = line.runs.single().text.indices.filter { line.runs.single().text[it] == ' ' }
                    .map { BreakOpportunity(it + 1, BreakKind.ALLOWED) }
                assertEquals(expected, measurement.breakOpportunities)
            }
        }
        assertTrue(lines(engine.layout(document("ABCDEF"), input.copy(canvasWidth = Length(2.0))))
            .all { it.runs.single().measurement.breakOpportunities.isEmpty() })
    }
}
