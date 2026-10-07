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

class Phase3ExitTest {
    private val body = ResolvedTypography("phase3-exit", TextStyle(),
        TypographyMetrics(3.0, 1.0, 1.0, 5.0, 3.0), FixedCellGeometry(1.0, 1.0, 4.0))
    private val input = LayoutConstraints(Length(48.0), Orientation.PORTRAIT, TypographyContext(body, body, body))
    private val engine = FoundationLayoutEngine(AsciiFixedCellMeasurer())
    private fun p(text: String) = Paragraph(Alignment.LEFT, listOf(Text(text)))
    private fun cell(text: String, alignment: Alignment? = null) = TableCell(listOf(Text(text)), alignment)
    private fun output(document: ThermalDocument, constraints: LayoutConstraints = input) =
        assertIs<LayoutResult.Success>(engine.layout(document, constraints)).document

    private fun mixed() = ThermalDocument(blocks = listOf(
        Heading(2, Alignment.CENTER, listOf(Text("HEADER"))),
        Paragraph(Alignment.RIGHT, listOf(Text("A\tB "), Strong(listOf(Text("C"))), LineBreak, InlineCode("D E"))),
        OrderedList(9, listOf(ListItem(listOf(p("A".repeat(35)),
            UnorderedList(listOf(ListItem(listOf(p("nested"))))))), ListItem(listOf(p("second"))))),
        Checklist(listOf(ChecklistItem(false, listOf(p("unchecked"))), ChecklistItem(true, listOf(p("checked"))))),
        Quote(listOf(CodeBlock("A\tB\r\n\nC\n"), Table(
            listOf(TableColumn(Alignment.LEFT), TableColumn(Alignment.CENTER), TableColumn(Alignment.RIGHT)),
            listOf(cell("H"), cell(""), cell("Q")),
            listOf(listOf(cell("A".repeat(17)), cell("B", Alignment.RIGHT), cell("C")))))),
        Separator,
        Image(EmbeddedAssetReference("image"), Alignment.CENTER, RequestedWidthSizing(Length(8.0)), "alt"),
        Image(ExternalAssetReference("file:///inert"), Alignment.LEFT, AutoSizing),
        Image(ExternalAssetReference("https://invalid.example/inert"), Alignment.RIGHT, FitWidthSizing),
        QrCode("opaque", Alignment.RIGHT, QrErrorCorrection.HIGH, Length(7.0)),
        QrCode("opaque-default", Alignment.CENTER, QrErrorCorrection.AUTO),
    ))

    @Test fun repeatedMixedOutputAndDiagnosticsAreIdenticalUnderEveryConstraint() {
        val semantic = mixed()
        for (width in listOf(24.0, 48.000001, 180.0)) {
            val portrait = input.copy(canvasWidth = Length(width))
            val expected = output(semantic, portrait)
            repeat(5) { assertEquals(expected, output(semantic, portrait)) }
            val landscape = output(semantic, portrait.copy(orientation = Orientation.LANDSCAPE))
            assertEquals(expected.blocks, landscape.blocks)
            assertEquals(expected.diagnostics, landscape.diagnostics)
            assertEquals(expected.heightMm, landscape.heightMm)
            invariants(expected)
            invariants(landscape)
        }
        // Repeated successful overflow preserves diagnostic ordering and source identity too.
        val overflow = ThermalDocument(blocks = listOf(p("AB"), CodeBlock("CD")))
        val narrow = input.copy(canvasWidth = Length(0.000001))
        val expected = output(overflow, narrow)
        assertEquals(listOf("LAY101", "LAY101"), expected.diagnostics.map { it.code })
        assertEquals(listOf(0, 1), expected.diagnostics.map { it.sourceBlockIndex })
        repeat(5) { assertEquals(expected, output(overflow, narrow)) }
    }

    @Test fun widthChangesOnlyExplicitWrappingColumnsAndPlaceholderGeometry() {
        val semantic = ThermalDocument(layout = DocumentLayout(Orientation.LANDSCAPE, Length(999.0)), blocks = listOf(
            p("A".repeat(60)), Table(List(2) { TableColumn(Alignment.LEFT) },
                listOf(cell("B".repeat(50)), cell("C")), emptyList()),
            Image(EmbeddedAssetReference("opaque"), Alignment.LEFT, AutoSizing),
            QrCode("opaque", Alignment.CENTER, QrErrorCorrection.AUTO)))
        for ((width, paragraphLines, tableLines, height) in listOf(
            listOf(24.0, 3.0, 5.0, 107.5), listOf(48.0, 2.0, 3.0, 122.5), listOf(180.0, 1.0, 1.0, 239.5))) {
            for (orientation in Orientation.entries) {
                val result = output(semantic, input.copy(canvasWidth = Length(width), orientation = orientation))
                assertEquals(width, result.widthMm)
                assertEquals(paragraphLines.toInt(), assertIs<LogicalTextBlock>(result.blocks[0]).lines.size)
                val table = assertIs<LogicalTableBlock>(result.blocks[1])
                assertEquals(listOf(width / 2, width / 2), table.columns.map { it.bounds.widthMm })
                assertEquals(tableLines.toInt(), table.rows.single().cells.first().text.lines.size)
                assertEquals(width, assertIs<LogicalImagePlaceholder>(result.blocks[2]).bounds.heightMm)
                val qr = assertIs<LogicalQrPlaceholder>(result.blocks[3])
                assertEquals(minOf(30.0, width), qr.bounds.widthMm)
                assertEquals((width - qr.bounds.widthMm) / 2, qr.bounds.xMm)
                assertEquals(height, result.heightMm)
                invariants(result)
            }
        }
    }

    @Test fun cumulativeMixedBudgetHasAnExactAtomicBoundaryAndRecoversAfterFailure() {
        val semantic = mixed()
        val expected = output(semantic)
        // Find the first accepted budget, without copying the production accounting algorithm.
        var low = 1
        var high = 100_000
        while (low < high) {
            val middle = low + (high - low) / 2
            val result = engine.layout(semantic, input.copy(resources = input.resources.copy(maxItems = middle)))
            if (result is LayoutResult.Success) high = middle else {
                assertEquals("LAY120", assertIs<LayoutResult.Failure>(result).diagnostics.single().code)
                low = middle + 1
            }
        }
        assertEquals(expected.blocks, output(semantic, input.copy(resources = input.resources.copy(maxItems = low))).blocks)
        val tooSmall = input.copy(resources = input.resources.copy(maxItems = low - 1))
        val failure = assertIs<LayoutResult.Failure>(engine.layout(semantic, tooSmall))
        assertEquals("LAY120", failure.diagnostics.single().code)
        assertFalse(failure.diagnostics.single().message.contains("opaque"))
        assertEquals(failure, engine.layout(semantic, tooSmall))
        assertEquals(expected, output(semantic))
    }

    private fun edge(origin: Double, size: Double) = LogicalGeometry.add(origin, size)
    private fun contains(parent: LogicalBounds, child: LogicalBounds) {
        assertTrue(LogicalGeometry.fits(parent.xMm, child.xMm))
        assertTrue(LogicalGeometry.fits(parent.yMm, child.yMm))
        assertTrue(LogicalGeometry.fits(edge(child.xMm, child.widthMm), edge(parent.xMm, parent.widthMm)))
        assertTrue(LogicalGeometry.fits(edge(child.yMm, child.heightMm), edge(parent.yMm, parent.heightMm)))
    }
    private fun invariants(document: LogicalDocument) {
        val canvas = LogicalBounds(0.0, 0.0, document.widthMm, document.heightMm)
        var bottom = 0.0
        for ((index, block) in document.blocks.withIndex()) {
            assertEquals(index, block.sourceBlockIndex)
            assertTrue(LogicalGeometry.fits(bottom, block.bounds.yMm))
            bottom = edge(block.bounds.yMm, block.bounds.heightMm)
            inspect(canvas, block)
        }
    }
    private fun inspect(parent: LogicalBounds, block: LogicalBlock) {
        contains(parent, block.bounds)
        when (block) {
            is LogicalTextBlock -> for (line in block.lines) {
                contains(block.bounds, line.bounds)
                val spare = LogicalGeometry.subtract(block.bounds.widthMm, line.advanceMm)
                val offset = when (line.alignment) {
                    Alignment.LEFT -> 0.0
                    Alignment.CENTER -> LogicalGeometry.scale(spare, 0.5)
                    Alignment.RIGHT -> spare
                }
                assertEquals(LogicalGeometry.add(block.bounds.xMm, offset), line.bounds.xMm)
                for (run in line.runs) {
                    contains(line.bounds, run.bounds)
                    assertEquals(line.baselineMm, LogicalGeometry.add(run.bounds.yMm, run.typography.metrics.baselineOffsetMm))
                    assertEquals(run.bounds.widthMm, LogicalGeometry.normalize(run.measurement.advanceMm))
                }
            }
            is LogicalListBlock -> for (item in block.items) {
                contains(block.bounds, item.bounds)
                contains(item.bounds, item.marker.bounds)
                item.blocks.forEach { inspect(item.bounds, it) }
            }
            is LogicalQuoteBlock -> block.blocks.forEach { inspect(block.bounds, it) }
            is LogicalTableBlock -> {
                assertEquals(LogicalGeometry.ticks(block.bounds.widthMm), block.columns.sumOf { LogicalGeometry.ticks(it.bounds.widthMm) })
                var x = block.bounds.xMm
                for (column in block.columns) {
                    contains(block.bounds, column.bounds)
                    assertEquals(x, column.bounds.xMm)
                    x = edge(x, column.bounds.widthMm)
                }
                var y = block.bounds.yMm
                for (row in block.rows) {
                    contains(block.bounds, row.bounds)
                    assertEquals(y, row.bounds.yMm)
                    for ((index, cell) in row.cells.withIndex()) {
                        assertEquals(index, cell.columnIndex)
                        assertEquals(block.columns[index].bounds.xMm, cell.bounds.xMm)
                        assertEquals(row.bounds.yMm, cell.bounds.yMm)
                        assertEquals(row.bounds.heightMm, cell.bounds.heightMm)
                        contains(row.bounds, cell.bounds)
                        contains(cell.bounds, cell.contentBounds)
                        inspect(cell.contentBounds, cell.text)
                    }
                    val tallest = row.cells.maxOf { it.text.bounds.heightMm }
                    assertEquals(LogicalGeometry.add(tallest, LogicalGeometry.add(block.cellPaddingMm, block.cellPaddingMm)), row.bounds.heightMm)
                    y = edge(y, row.bounds.heightMm)
                }
                assertEquals(edge(block.bounds.yMm, block.bounds.heightMm), y)
            }
            is LogicalImagePlaceholder -> assertEquals(block.bounds.widthMm, block.bounds.heightMm)
            is LogicalQrPlaceholder -> assertEquals(block.bounds.widthMm, block.bounds.heightMm)
            is LogicalSeparator, is LogicalPlaceholder -> Unit
        }
    }
}
