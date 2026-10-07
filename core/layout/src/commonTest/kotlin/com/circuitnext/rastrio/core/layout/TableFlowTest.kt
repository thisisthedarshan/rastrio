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

class TableFlowTest {
    private val body = ResolvedTypography("table", TextStyle(), TypographyMetrics(3.0, 1.0, 1.0, 5.0, 3.0), FixedCellGeometry(1.0, 1.0, 4.0))
    private val input = LayoutConstraints(Length(48.0), Orientation.PORTRAIT, TypographyContext(body, body, body))
    private fun cell(text: String = "", alignment: Alignment? = null) = TableCell(listOf(Text(text)), alignment)
    private fun table(columns: Int, header: List<TableCell>, vararg rows: List<TableCell>) =
        Table(List(columns) { TableColumn(Alignment.LEFT) }, header, rows.toList())
    private fun layout(block: DocumentBlock, constraints: LayoutConstraints = input, semantic: DocumentLayout = DocumentLayout()): LogicalDocument =
        assertIs<LayoutResult.Success>(FoundationLayoutEngine(AsciiFixedCellMeasurer()).layout(
            ThermalDocument(layout = semantic, blocks = listOf(block)), constraints)).document
    private fun resolved(table: Table, constraints: LayoutConstraints = input) = assertIs<LogicalTableBlock>(layout(table, constraints).blocks.single())

    @Test fun approvedTwoColumnGoldenContainsAllCellAndTextGeometry() {
        val semantic = table(2, listOf(cell("H"), cell("Q")), listOf(cell("A".repeat(23)), cell("B")))
        val document = layout(semantic)
        val table = assertIs<LogicalTableBlock>(document.blocks.single())
        assertEquals(LogicalBounds(0.0, 2.5, 48.0, 19.0), table.bounds)
        assertEquals(listOf(LogicalBounds(0.0, 2.5, 24.0, 19.0), LogicalBounds(24.0, 2.5, 24.0, 19.0)), table.columns.map { it.bounds })
        assertEquals(listOf(LogicalBounds(0.0, 2.5, 48.0, 7.0), LogicalBounds(0.0, 9.5, 48.0, 12.0)), table.rows.map { it.bounds })
        assertEquals(listOf(true, false), table.rows.map { it.isHeader })
        assertEquals(1.0, table.cellPaddingMm)
        assertEquals(2.5, table.beforeSpacingMm)
        assertEquals(2.5, table.afterSpacingMm)
        val first = table.rows[1].cells[0]
        val second = table.rows[1].cells[1]
        assertEquals(LogicalBounds(0.0, 9.5, 24.0, 12.0), first.bounds)
        assertEquals(LogicalBounds(24.0, 9.5, 24.0, 12.0), second.bounds)
        assertEquals(LogicalBounds(1.0, 10.5, 22.0, 10.0), first.contentBounds)
        assertEquals(LogicalBounds(25.0, 10.5, 22.0, 10.0), second.contentBounds)
        assertEquals(LogicalBounds(25.0, 10.5, 22.0, 5.0), second.text.bounds)
        assertEquals(listOf(LogicalBounds(1.0, 10.5, 22.0, 5.0), LogicalBounds(1.0, 15.5, 1.0, 5.0)), first.text.lines.map { it.bounds })
        assertEquals(listOf(13.5, 18.5), first.text.lines.map { it.baselineMm })
        assertEquals(listOf("A".repeat(22), "A"), first.text.lines.map { line -> line.runs.joinToString("") { it.text } })
        assertEquals(first.text.lines.map { it.bounds }, first.text.lines.map { it.runs.single().bounds })
        assertEquals(24.0, document.heightMm)
    }

    @Test fun singleColumnHeaderOnlyAndEmptyCellsKeepOneLine() {
        val table = resolved(table(1, listOf(TableCell(emptyList()))))
        assertEquals(1, table.rows.size)
        assertTrue(table.rows.single().isHeader)
        val cell = table.rows.single().cells.single()
        assertEquals(LogicalBounds(1.0, 3.5, 46.0, 5.0), cell.contentBounds)
        assertEquals("", cell.text.lines.single().runs.single().text)
        assertEquals(6.5, cell.text.lines.single().baselineMm)
    }

    @Test fun emptyCellsReuseCanonicalBodyLineWithoutTableOnlyRuns() {
        val table = resolved(table(2, listOf(TableCell(emptyList()), cell()), listOf(cell(), TableCell(emptyList()))))
        val canonical = assertIs<LogicalTextBlock>(layout(Paragraph(Alignment.LEFT, emptyList())).blocks.single()).lines.single()
        assertEquals(listOf(7.0, 7.0), table.rows.map { it.bounds.heightMm })
        for (row in table.rows) for (cell in row.cells) {
            val line = cell.text.lines.single()
            assertEquals(5.0, line.bounds.heightMm)
            assertEquals(canonical.runs.size, line.runs.size)
            assertEquals(canonical.runs.map { it.text }, line.runs.map { it.text })
            assertEquals(canonical.runs.map { it.measurement }, line.runs.map { it.measurement })
            assertEquals(7.0, cell.bounds.heightMm)
        }
    }

    @Test fun rowHeightsDifferAndAreDrivenByTallestCellInEitherColumn() {
        val table = resolved(table(2, listOf(cell(), cell()), listOf(cell(), cell("B".repeat(45))), listOf(cell("A"), cell())))
        assertEquals(listOf(7.0, 17.0, 7.0), table.rows.map { it.bounds.heightMm })
        assertEquals(listOf(2.5, 9.5, 26.5), table.rows.map { it.bounds.yMm })
        assertEquals(listOf(22.0, 22.0, 1.0), table.rows[1].cells[1].text.lines.map { it.advanceMm })
        assertTrue(table.rows.all { row -> row.cells.all { it.bounds.yMm == row.bounds.yMm && it.bounds.heightMm == row.bounds.heightMm } })
    }

    @Test fun columnAlignmentAndCellOverrideResolveAbsoluteRunOrigins() {
        val semantic = Table(listOf(TableColumn(Alignment.LEFT), TableColumn(Alignment.CENTER), TableColumn(Alignment.RIGHT)),
            listOf(cell("A"), cell("B"), cell("C")), listOf(listOf(cell("D", Alignment.RIGHT), cell("E", Alignment.LEFT), cell("F", Alignment.CENTER))))
        val table = resolved(semantic)
        assertEquals(listOf(1.0, 23.5, 46.0), table.rows[0].cells.map { it.text.lines.single().bounds.xMm })
        assertEquals(listOf(14.0, 17.0, 39.5), table.rows[1].cells.map { it.text.lines.single().bounds.xMm })
        assertEquals(listOf(Alignment.RIGHT, Alignment.LEFT, Alignment.CENTER), table.rows[1].cells.map { it.alignment })
        assertEquals(listOf(Alignment.LEFT, Alignment.CENTER, Alignment.RIGHT), table.columns.map { it.alignment })
        assertTrue(table.rows[0].cells.all { it.text.lines.single().runs.single().typography.style.weight == TextWeight.NORMAL })
    }

    @Test fun styledBreakAndTabContentUsesSharedTextFlow() {
        val content = listOf(Text("A"), Strong(listOf(Emphasis(listOf(Strike(listOf(Text("B"))))))), LineBreak, InlineCode("C\tD"))
        val table = resolved(table(1, listOf(TableCell(content))))
        val text = table.rows.single().cells.single().text
        assertEquals(listOf("AB", "C\tD"), text.lines.map { line -> line.runs.joinToString("") { it.text } })
        assertEquals(listOf(2.0, 5.0), text.lines.map { it.advanceMm })
        assertTrue(text.lines.first().endsWithExplicitBreak)
        val styled = text.lines.first().runs.last().typography.style
        assertEquals(TextWeight.BOLD, styled.weight)
        assertTrue(styled.emphasis && styled.strike)
        assertEquals(listOf(1.0, 2.0, 5.0), text.lines.last().runs.map { it.bounds.xMm })
        assertEquals(listOf(1.0, 3.0, 1.0), text.lines.last().runs.map { it.measurement.advanceMm })
        assertEquals(12.0, table.bounds.heightMm)
    }

    @Test fun tallInlineMetricsDriveRowWithoutAddingParagraphSpacing() {
        val tall = body.copy(identity = "tall", metrics = TypographyMetrics(6.0, 2.0, 2.0, 10.0, 6.0))
        val constraints = input.copy(typography = TypographyContext(body, tall, body))
        val table = resolved(table(2, listOf(TableCell(listOf(Text("A"), InlineCode("B"))), cell("C"))), constraints)
        assertEquals(12.0, table.rows.single().bounds.heightMm)
        assertEquals(9.5, table.rows.single().cells.first().text.lines.single().baselineMm)
        assertEquals(listOf(6.5, 3.5), table.rows.single().cells.first().text.lines.single().runs.map { it.bounds.yMm })
    }

    @Test fun leftoverTicksAreDistributedLeftToRightWithExactCanvasCoverage() {
        val table = resolved(table(3, List(3) { cell() }), input.copy(canvasWidth = Length(10.000001)))
        assertEquals(listOf(3.333334, 3.333334, 3.333333), table.columns.map { it.bounds.widthMm })
        assertEquals(listOf(0.0, 3.333334, 6.666668), table.columns.map { it.bounds.xMm })
        assertEquals(10.000001, table.bounds.widthMm)
    }

    @Test fun explicitWidthChangesWrappingAndLandscapeDoesNotSupplyWidth() {
        val semantic = table(2, listOf(cell("A".repeat(50)), cell("B")))
        for ((width, lineCount) in listOf(48.0 to 3, 100.0 to 2, 180.0 to 1)) {
            val portrait = layout(semantic, input.copy(canvasWidth = Length(width)))
            val landscape = layout(semantic, input.copy(canvasWidth = Length(width), orientation = Orientation.LANDSCAPE),
                DocumentLayout(Orientation.LANDSCAPE, Length(180.0)))
            assertEquals(width, portrait.widthMm)
            assertEquals(width, landscape.widthMm)
            assertEquals(portrait.blocks, landscape.blocks)
            assertEquals(portrait.heightMm, landscape.heightMm)
            val table = assertIs<LogicalTableBlock>(landscape.blocks.single())
            assertEquals(lineCount, table.rows.single().cells.first().text.lines.size)
            assertEquals(width / 2, table.columns.first().bounds.widthMm)
        }
        assertEquals(layout(semantic).blocks, layout(semantic, semantic = DocumentLayout(Orientation.LANDSCAPE, Length(180.0))).blocks)
    }

    @Test fun nestedTableUsesContentOriginAndWidthAndFinalSnapshots() {
        val content = mutableListOf<InlineContent>(Text("A"))
        val header = mutableListOf(TableCell(content))
        val rows = mutableListOf<List<TableCell>>(mutableListOf(cell("B")))
        val columns = mutableListOf(TableColumn(Alignment.LEFT))
        val semantic = Quote(listOf(Table(columns, header, rows)))
        val document = layout(semantic)
        columns.clear(); header.clear(); rows.clear(); content.clear()
        val table = assertIs<LogicalTableBlock>(assertIs<LogicalQuoteBlock>(document.blocks.single()).blocks.single())
        assertEquals(LogicalBounds(4.0, 2.5, 44.0, 14.0), table.bounds)
        assertEquals(LogicalBounds(5.0, 3.5, 42.0, 5.0), table.rows.first().cells.single().contentBounds)
        assertEquals("A", table.rows.first().cells.single().text.lines.single().runs.single().text)
        assertFalse((table.rows as List<*>) is MutableList<*>)
        assertFalse((table.columns as List<*>) is MutableList<*>)
        assertFalse((table.rows.first().cells as List<*>) is MutableList<*>)
        assertEquals(19.0, document.heightMm)
    }

    @Test fun narrowCellsRetainOversizedClustersAndContentPrivateDiagnostics() {
        val table = table(1, listOf(cell("AB", Alignment.RIGHT)))
        val constraints = input.copy(canvasWidth = Length(2.000001))
        val document = layout(table, constraints)
        val cell = assertIs<LogicalTableBlock>(document.blocks.single()).rows.single().cells.single()
        assertEquals(0.000001, cell.contentBounds.widthMm)
        assertEquals(listOf(1.0, 1.0), cell.text.lines.map { it.bounds.xMm })
        assertEquals(listOf(1.0, 1.0), cell.text.lines.map { it.advanceMm })
        assertEquals("LAY101", document.diagnostics.single().code)
        assertFalse(document.diagnostics.single().message.contains("AB"))
        assertEquals(0, document.diagnostics.single().sourceBlockIndex)
        assertEquals(document, layout(table, constraints))
    }

    @Test fun tablesAmongTextHaveExplicitSpacingAndDocumentHeight() {
        val semantic = ThermalDocument(blocks = listOf(Paragraph(Alignment.LEFT, listOf(Text("P"))),
            table(1, listOf(cell("H"))), Paragraph(Alignment.LEFT, listOf(Text("Q")))))
        val document = assertIs<LayoutResult.Success>(FoundationLayoutEngine(AsciiFixedCellMeasurer()).layout(semantic, input)).document
        assertEquals(listOf(0.0, 10.0, 19.5), document.blocks.map { it.bounds.yMm })
        assertEquals(27.0, document.heightMm)
    }
}
