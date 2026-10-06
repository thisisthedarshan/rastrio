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

class StructuredResourceSafetyTest {
    private val body = ResolvedTypography("structured-budget", TextStyle(), TypographyMetrics(3.0, 1.0, 1.0, 5.0, 3.0), FixedCellGeometry(1.0, 1.0, 4.0))
    private val input = LayoutConstraints(Length(1000.0), Orientation.PORTRAIT, TypographyContext(body, body, body))
    private fun p(text: String = "A") = Paragraph(Alignment.LEFT, listOf(Text(text)))
    private fun result(block: DocumentBlock, constraints: LayoutConstraints = input) =
        FoundationLayoutEngine(AsciiFixedCellMeasurer()).layout(ThermalDocument(blocks = listOf(block)), constraints)
    private fun failure(result: LayoutResult, code: String = "LAY120") =
        assertEquals(code, assertIs<LayoutResult.Failure>(result).diagnostics.single().code)

    @Test fun blockDepthExactAndOneOverAreIndependentOfInlineDepth() {
        for (list in listOf(false, true)) {
            var block: DocumentBlock = p()
            repeat(63) { block = if (list) UnorderedList(listOf(ListItem(listOf(block)))) else Quote(listOf(block)) }
            val output = assertIs<LayoutResult.Success>(result(block)).document
            assertEquals(7.5, output.heightMm)
            assertEquals(output, assertIs<LayoutResult.Success>(result(block)).document)
            failure(result(if (list) UnorderedList(listOf(ListItem(listOf(block)))) else Quote(listOf(block))))
            failure(result(block, input.copy(resources = input.resources.copy(maxBlockDepth = 63))))
        }
    }

    @Test fun nestedBlockAndTextCountsArePreflightedBeforeMeasurement() {
        var calls = 0
        val measurer = TextMeasurer { request -> calls++; AsciiFixedCellMeasurer().measure(request) }
        val document = ThermalDocument(blocks = listOf(Quote(listOf(p("ABC"), p()))))
        for (policy in listOf(input.resources.copy(maxBlocks = 2), input.resources.copy(maxTextCodeUnits = 3),
            input.resources.copy(maxInlineNodes = 1))) {
            failure(FoundationLayoutEngine(measurer).layout(document, input.copy(resources = policy)))
            assertEquals(0, calls)
        }
    }

    @Test fun checklistEmptyItemsHaveExactReservedBoundary() {
        val unchecked = Checklist(listOf(ChecklistItem(false, emptyList())))
        val exact = input.copy(resources = input.resources.copy(maxItems = 7))
        val output = assertIs<LayoutResult.Success>(result(unchecked, exact)).document
        assertEquals(5.0, output.heightMm)
        failure(result(unchecked, exact.copy(resources = exact.resources.copy(maxItems = 6))))
        val checked = Checklist(listOf(ChecklistItem(true, emptyList())))
        assertIs<LayoutResult.Success>(result(checked, exact.copy(resources = exact.resources.copy(maxItems = 10))))
        failure(result(checked, exact.copy(resources = exact.resources.copy(maxItems = 9))))
        failure(result(Checklist(List(100_001) { ChecklistItem(false, emptyList()) })))
        failure(result(Checklist(List(40_000) { ChecklistItem(false, emptyList()) })))
    }

    @Test fun emptyOrdinaryItemsAndQuotesFailWithoutCreatingGeometry() {
        failure(result(UnorderedList(listOf(ListItem(emptyList())))), "LAY105")
        failure(result(OrderedList(1, listOf(ListItem(emptyList())))), "LAY105")
        failure(result(Quote(emptyList())), "LAY105")
        assertIs<LayoutResult.Success>(result(UnorderedList(emptyList())))
        assertIs<LayoutResult.Success>(result(OrderedList(0, emptyList())))
        assertIs<LayoutResult.Success>(result(Checklist(emptyList())))
    }

    @Test fun exhaustedContentWidthIsDifferentFromClusterOverflow() {
        val block = UnorderedList(listOf(ListItem(listOf(p()))))
        failure(result(block, input.copy(canvasWidth = Length(6.0))), "LAY107")
        val positive = input.copy(canvasWidth = Length(6.000001))
        val output = assertIs<LayoutResult.Success>(result(block, positive)).document
        assertEquals("LAY101", output.diagnostics.single().code)
        val item = assertIs<LogicalListBlock>(output.blocks.single()).items.single()
        assertEquals(0.000001, assertIs<LogicalTextBlock>(item.blocks.single()).lines.single().availableWidthMm)
        assertEquals(1.0, assertIs<LogicalTextBlock>(item.blocks.single()).lines.single().advanceMm)
    }

    @Test fun diagnosticsAreBoundedAtExactStructuredCostLimit() {
        val block = UnorderedList(List(3) { ListItem(listOf(p())) })
        val exact = input.copy(canvasWidth = Length(6.000001), resources = input.resources.copy(maxItems = 31))
        val output = assertIs<LayoutResult.Success>(result(block, exact)).document
        assertEquals(3, output.diagnostics.size)
        assertTrue(output.diagnostics.all { it.code == "LAY101" && it.sourceBlockIndex == 0 })
        assertEquals(output, assertIs<LayoutResult.Success>(result(block, exact)).document)
        failure(result(block, exact.copy(resources = exact.resources.copy(maxItems = 30))))
    }

    @Test fun codeAndTabAmplificationStopsAtTheSharedCostBudget() {
        val newline = CodeBlock("\n")
        val exact = input.copy(resources = input.resources.copy(maxItems = 6))
        assertEquals(2, assertIs<LogicalTextBlock>(assertIs<LayoutResult.Success>(result(newline, exact)).document.blocks.single()).lines.size)
        failure(result(newline, exact.copy(resources = exact.resources.copy(maxItems = 5))))
        failure(result(CodeBlock("\n".repeat(65_536))))
        failure(result(CodeBlock("\t".repeat(65_536))))
        failure(result(CodeBlock("A".repeat(65_536)), input.copy(canvasWidth = Length(0.000001))))
        failure(result(OrderedList(Long.MAX_VALUE, List(2) { ListItem(listOf(p())) })), "LAY106")
        failure(result(OrderedList(-1, listOf(ListItem(listOf(p()))))), "LAY106")
    }

    @Test fun markerMeasurementIsBudgetedBeforeTheNextMarker() {
        var calls = 0
        val service = TextMeasurer { request -> calls++; AsciiFixedCellMeasurer().measure(request) }
        val document = ThermalDocument(blocks = listOf(OrderedList(1, List(7) { ListItem(listOf(p())) })))
        failure(FoundationLayoutEngine(service).layout(document, input.copy(resources = input.resources.copy(maxItems = 7))))
        assertEquals(1, calls)
    }

    @Test fun orderedMarkerRejectsZeroCanonicalLineHeight() {
        val tiny = body.copy(metrics = TypographyMetrics(0.0, 0.0, 0.0, 0.0000004, 0.0))
        val constrained = input.copy(typography = TypographyContext(body, body, tiny))
        failure(result(OrderedList(1, listOf(ListItem(listOf(p())))), constrained), "LAY122")
    }

    @Test fun textServiceFailurePreservesBoundedDiagnostics() {
        val service = TextMeasurer { TextMeasureResult.Failure(SnapshotList(listOf(
            TextDiagnostic("TXT100", "Unsupported coverage"), TextDiagnostic("TXT101", "Missing metric")))) }
        val engine = FoundationLayoutEngine(service)
        for ((block, exact) in listOf(p() to 4, OrderedList(1, listOf(ListItem(listOf(p())))) to 6)) {
            val document = ThermalDocument(blocks = listOf(block))
            val constrained = input.copy(resources = input.resources.copy(maxItems = exact))
            val failed = assertIs<LayoutResult.Failure>(engine.layout(document, constrained))
            assertEquals(listOf("TXT100", "TXT101"), failed.diagnostics.map { it.code })
            assertTrue(failed.diagnostics.all { it.sourceBlockIndex == 0 })
            failure(engine.layout(document, constrained.copy(resources = constrained.resources.copy(maxItems = exact - 1))))
        }
    }
}
