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

class TabsAndCodeFlowTest {
    private val body = ResolvedTypography("tab-body", TextStyle(), TypographyMetrics(3.0, 1.0, 1.0, 5.0, 3.0), FixedCellGeometry(1.0, 1.0, 4.0))
    private val code = body.copy(identity = "tab-code", style = TextStyle(role = TextRole.CODE), fixedCell = FixedCellGeometry(2.0, 2.0, 4.0))
    private val input = LayoutConstraints(Length(40.0), Orientation.PORTRAIT, TypographyContext(body, code, body))
    private fun layout(block: DocumentBlock, width: Double = 40.0, typography: TypographyContext = input.typography,
        measurer: TextMeasurer = AsciiFixedCellMeasurer()): LogicalDocument =
        assertIs<LayoutResult.Success>(FoundationLayoutEngine(measurer).layout(ThermalDocument(blocks = listOf(block)),
            input.copy(canvasWidth = Length(width), typography = typography))).document
    private fun paragraph(text: String) = Paragraph(Alignment.LEFT, listOf(Text(text)))
    private fun lines(output: LogicalDocument) = assertIs<LogicalTextBlock>(output.blocks.single()).lines
    private fun texts(output: LogicalDocument) = lines(output).map { line -> line.runs.joinToString("") { it.text } }

    @Test fun tabStopsUseTheCurrentContentAdvance() {
        for ((position, end) in listOf(0 to 4.0, 1 to 4.0, 3 to 4.0, 4 to 8.0, 7 to 8.0)) {
            val output = layout(paragraph("A".repeat(position) + "\t"))
            val line = lines(output).single()
            assertEquals(end, line.advanceMm)
            assertEquals(end - position, line.runs.last().measurement.clusters.single().advanceMm)
            assertEquals("A".repeat(position) + "\t", texts(output).single())
            assertEquals(output, layout(paragraph("A".repeat(position) + "\t")))
        }
        assertEquals(8.0, lines(layout(paragraph("\t\t"))).single().advanceMm)
    }

    @Test fun wrappedTabResolvesAtTheNewLineOrigin() {
        val output = layout(paragraph("AAAA\tB"), width = 5.0)
        assertEquals(listOf("AAAA", "\tB"), texts(output))
        assertEquals(listOf(4.0, 5.0), lines(output).map { it.advanceMm })
        assertEquals(listOf(3.0, 8.0), lines(output).map { it.baselineMm })
        assertTrue(output.diagnostics.isEmpty())
        val oversize = layout(paragraph("\t"), width = 3.0)
        assertEquals(4.0, lines(oversize).single().advanceMm)
        assertEquals("LAY101", oversize.diagnostics.single().code)
    }

    @Test fun styledTabsDoNotResetTheLineOrigin() {
        val output = layout(Paragraph(Alignment.LEFT, listOf(Text("A"), Strong(listOf(Text("\tB"))))))
        assertEquals(5.0, lines(output).single().advanceMm)
        assertEquals(TextWeight.BOLD, lines(output).single().runs.last().typography.style.weight)
        assertEquals("A\tB", texts(output).single())
    }

    @Test fun proportionalTabsUseMeasuredSpaceRatherThanCharacterWidth() {
        val proportional = body.copy(fixedCell = null)
        for ((characterWidth, tabWidth, lineWidth) in listOf(Triple(1.5, 0.5, 3.5), Triple(1.75, 0.25, 3.75))) {
            val measurer = TextMeasurer { request ->
                val widths = request.text.map { if (it == ' ') 0.5 else characterWidth }
                TextMeasureResult.Success(TextMeasurement(request.text.length, widths.sum(), request.typography.metrics,
                    SnapshotList(widths.mapIndexed { i, width -> MeasuredCluster(i, i + 1, width) }), SnapshotList(emptyList())))
            }
            val output = layout(paragraph("A\tB"), typography = TypographyContext(proportional, code, proportional), measurer = measurer)
            assertEquals(lineWidth, lines(output).single().advanceMm)
            assertEquals(tabWidth, lines(output).single().runs[1].measurement.advanceMm)
        }
    }

    @Test fun codePreservesEmptyAndTrailingExplicitLines() {
        for ((text, expected) in listOf("" to listOf(""), "a\n" to listOf("a", ""), "\n" to listOf("", ""),
            "\n\n" to listOf("", "", ""), "a\nb" to listOf("a", "b"), "a\rb" to listOf("a", "b"), "a\r\nb" to listOf("a", "b"), "a\r" to listOf("a", ""), "a\r\n" to listOf("a", ""))) {
            val output = layout(CodeBlock(text))
            assertEquals(expected, texts(output))
            assertEquals(expected.indices.map { 2.5 + it * 5.0 }, lines(output).map { it.bounds.yMm })
            assertEquals(expected.size * 5.0 + 5.0, output.heightMm)
            assertEquals(expected.indices.map { it < expected.lastIndex }, lines(output).map { it.endsWithExplicitBreak })
            assertTrue(lines(output).all { it.runs.all { run -> run.typography == code } })
        }
    }

    @Test fun codeWhitespaceTabsAndEmergencyWrappingUseSharedFlow() {
        val output = layout(CodeBlock("  A  \tB "), width = 40.0)
        assertEquals(listOf("  A  \tB "), texts(output))
        assertEquals(20.0, lines(output).single().advanceMm)
        val wrapped = layout(CodeBlock("ABCDEFG"), width = 5.0)
        assertEquals(listOf("AB", "CD", "EF", "G"), texts(wrapped))
        assertEquals(listOf(4.0, 4.0, 4.0, 2.0), lines(wrapped).map { it.advanceMm })
        assertEquals(25.0, wrapped.heightMm)
        for ((position, end) in listOf(0 to 8.0, 1 to 8.0, 3 to 8.0, 4 to 16.0, 7 to 16.0))
            assertEquals(end, lines(layout(CodeBlock("A".repeat(position) + "\t"))).single().advanceMm)
    }

    @Test fun styledInlineCodeKeepsCodeCellTabMetric() {
        val service = TextMeasurer { request ->
            val widths = request.text.map { if (it == ' ') 0.5 else request.typography.fixedCell!!.advanceMm }
            TextMeasureResult.Success(TextMeasurement(request.text.length, widths.sum(), request.typography.metrics,
                SnapshotList(widths.mapIndexed { i, width -> MeasuredCluster(i, i + 1, width) }), SnapshotList(emptyList())))
        }
        val output = layout(Paragraph(Alignment.LEFT, listOf(Text("A"), Strong(listOf(InlineCode("\tB"))))), measurer = service)
        assertEquals(10.0, lines(output).single().advanceMm)
        assertEquals(7.0, lines(output).single().runs[1].measurement.advanceMm)
    }

    @Test fun fractionalTabsUseTheSharedPrecisionGrid() {
        val fractional = body.copy(fixedCell = FixedCellGeometry(0.1, 0.1, 4.0))
        val context = TypographyContext(fractional, fractional, fractional)
        for ((position, end) in listOf(0 to 0.4, 1 to 0.4, 3 to 0.4, 4 to 0.8, 7 to 0.8))
            assertEquals(end, lines(layout(paragraph("A".repeat(position) + "\t"), typography = context)).single().advanceMm)
        assertEquals(16.0, lines(layout(CodeBlock("\t\t"))).single().advanceMm)
    }

    @Test fun codeEmergencyWrapPreservesInjectedMultiCodeUnitClusters() {
        val service = TextMeasurer { request -> TextMeasureResult.Success(TextMeasurement(4, 6.0, request.typography.metrics,
            SnapshotList(listOf(MeasuredCluster(0, 2, 3.0), MeasuredCluster(2, 4, 3.0))), SnapshotList(emptyList()))) }
        assertEquals(listOf("AB", "CD"), texts(layout(CodeBlock("ABCD"), width = 3.0, measurer = service)))
    }
}
