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

class TablePlaceholderResourceTest {
    private val body = ResolvedTypography("table-budget", TextStyle(), TypographyMetrics(3.0, 1.0, 1.0, 5.0, 3.0), FixedCellGeometry(1.0, 1.0, 4.0))
    private val input = LayoutConstraints(Length(1000.0), Orientation.PORTRAIT, TypographyContext(body, body, body))
    private fun cell(text: String = "") = TableCell(listOf(Text(text)))
    private fun table(columns: Int = 1, bodyRows: Int = 0, content: TableCell = cell()) =
        Table(List(columns) { TableColumn(Alignment.LEFT) }, List(columns) { content }, List(bodyRows) { List(columns) { content } })
    private fun result(vararg blocks: DocumentBlock, constraints: LayoutConstraints = input, measurer: TextMeasurer = AsciiFixedCellMeasurer()) =
        FoundationLayoutEngine(measurer).layout(ThermalDocument(blocks = blocks.toList()), constraints)
    private fun failure(result: LayoutResult, code: String = "LAY120"): LayoutDiagnostic =
        assertIs<LayoutResult.Failure>(result).diagnostics.single().also { assertEquals(code, it.code) }
    private fun noMeasurement(block: DocumentBlock, constraints: LayoutConstraints = input, code: String = "LAY120") {
        var calls = 0
        val measured = TextMeasurer { request -> calls++; AsciiFixedCellMeasurer().measure(request) }
        val diagnostic = failure(result(Paragraph(Alignment.LEFT, listOf(Text("A"))), block,
            constraints = constraints, measurer = measured), code)
        assertEquals(0, calls)
        assertEquals(1, diagnostic.sourceBlockIndex)
        assertFalse(diagnostic.message.contains("private"))
    }

    @Test fun malformedTablesFailBeforeAnyDocumentMeasurement() {
        noMeasurement(Table(emptyList(), emptyList(), emptyList()), code = "LAY105")
        noMeasurement(Table(listOf(TableColumn(Alignment.LEFT)), emptyList(), emptyList()), code = "LAY105")
        noMeasurement(Table(listOf(TableColumn(Alignment.LEFT)), listOf(cell()), listOf(emptyList())), code = "LAY105")
        noMeasurement(Table(listOf(TableColumn(Alignment.LEFT)), listOf(cell()), listOf(listOf(cell(), cell()))), code = "LAY105")
    }

    @Test fun malformedLaterRowsFailBeforeAnyCellContentTraversal() {
        val columns = listOf(TableColumn(Alignment.LEFT))
        val constraints = input.copy(resources = input.resources.copy(maxTextCodeUnits = 1))
        failure(result(Table(columns, listOf(cell("AB")), listOf(emptyList())), constraints = constraints), "LAY105")
        failure(result(Table(columns, listOf(cell()), listOf(listOf(cell("AB")), emptyList())),
            constraints = constraints), "LAY105")
        val inaccessibleContent = object : AbstractList<InlineContent>() {
            override val size: Int get() = 1
            override fun get(index: Int): InlineContent = error("Malformed shape must precede content traversal")
        }
        failure(result(Table(columns, listOf(TableCell(inaccessibleContent)), listOf(emptyList()))), "LAY105")
    }

    @Test fun exactEmptyTableAndTextCostsShareTheCumulativeItemBudget() {
        for ((semantic, exact) in listOf(table() to 10, table(content = cell("A")) to 12, table(bodyRows = 1) to 17, table(columns = 2) to 17)) {
            val constraints = input.copy(resources = input.resources.copy(maxItems = exact))
            assertIs<LayoutResult.Success>(result(semantic, constraints = constraints))
            failure(result(semantic, constraints = constraints.copy(resources = constraints.resources.copy(maxItems = exact - 1))))
        }
        val twoTables = input.copy(resources = input.resources.copy(maxItems = 20))
        assertIs<LayoutResult.Success>(result(table(), table(), constraints = twoTables))
        failure(result(table(), table(), constraints = twoTables.copy(resources = twoTables.resources.copy(maxItems = 19))))
        val mixed = input.copy(resources = input.resources.copy(maxItems = 12))
        assertIs<LayoutResult.Success>(result(table(), QrCode("", Alignment.LEFT, QrErrorCorrection.AUTO),
            Image(EmbeddedAssetReference("a"), Alignment.LEFT, AutoSizing), constraints = mixed))
        failure(result(table(), QrCode("", Alignment.LEFT, QrErrorCorrection.AUTO),
            Image(EmbeddedAssetReference("a"), Alignment.LEFT, AutoSizing), constraints = mixed.copy(resources = mixed.resources.copy(maxItems = 11))))
    }

    @Test fun tableDimensionsAndCellsHaveIndependentExactAndOneOverLimits() {
        val rows = input.copy(resources = input.resources.copy(maxTableRows = 1))
        assertIs<LayoutResult.Success>(result(table(bodyRows = 1), constraints = rows))
        noMeasurement(table(bodyRows = 2), rows)
        val columns = input.copy(resources = input.resources.copy(maxTableColumns = 2))
        assertIs<LayoutResult.Success>(result(table(columns = 2), constraints = columns))
        noMeasurement(table(columns = 3), columns)
        val cells = input.copy(resources = input.resources.copy(maxTableCells = 3))
        assertIs<LayoutResult.Success>(result(table(bodyRows = 2), constraints = cells))
        noMeasurement(table(bodyRows = 3), cells)
        noMeasurement(table(columns = 2, bodyRows = 1), cells)
        assertIs<LayoutResult.Success>(result(table(columns = 256)))
        noMeasurement(table(columns = 257))
        assertIs<LayoutResult.Success>(result(table(bodyRows = 20_000), constraints = input.copy(resources = input.resources.copy(maxItems = 150_000))))
        noMeasurement(table(bodyRows = 20_001))
    }

    @Test fun largeDeclaredDimensionsAreRejectedWithoutIndexingOrMultiplicationOverflow() {
        val rows = object : AbstractList<List<TableCell>>() {
            override val size: Int get() = Int.MAX_VALUE
            override fun get(index: Int): List<TableCell> = error("Declared rows must not be accessed")
        }
        noMeasurement(Table(listOf(TableColumn(Alignment.LEFT)), listOf(cell()), rows))
        val columns = object : AbstractList<TableColumn>() {
            override val size: Int get() = Int.MAX_VALUE
            override fun get(index: Int): TableColumn = error("Declared columns must not be accessed")
        }
        val header = object : AbstractList<TableCell>() {
            override val size: Int get() = Int.MAX_VALUE
            override fun get(index: Int): TableCell = error("Declared header must not be accessed")
        }
        noMeasurement(Table(columns, header, rows))
    }

    @Test fun manyEmptyCellsAndWideGeometryCannotBypassSmallerRuntimeBudget() {
        noMeasurement(table(columns = 10, bodyRows = 10_000))
        noMeasurement(table(columns = 256, bodyRows = 500))
        noMeasurement(table(columns = 250, bodyRows = 1000))
        val exact = input.copy(resources = input.resources.copy(maxTableCells = 250_000, maxItems = 2_000_000))
        // The one-over cell check runs before touching these repeated, compact input rows.
        noMeasurement(table(columns = 250, bodyRows = 1000), exact)
    }

    @Test fun inlineTextAndDepthBudgetsCoverTableCellsBeforeMeasurement() {
        val constraints = input.copy(resources = input.resources.copy(maxInlineNodes = 2, maxTextCodeUnits = 2))
        assertIs<LayoutResult.Success>(result(table(content = TableCell(listOf(Text("A"), Text("B")))), constraints = constraints))
        noMeasurement(table(content = TableCell(listOf(Text("A"), Text("B"), Text("C")))), constraints)
        noMeasurement(table(content = cell("ABC")), constraints)
        var node: InlineContent = Text("A")
        repeat(63) { node = Strong(listOf(node)) }
        assertIs<LayoutResult.Success>(result(table(content = TableCell(listOf(node)))))
        noMeasurement(table(content = TableCell(listOf(Strong(listOf(node))))))
        failure(result(table(content = cell("é"))), "TXT100")
        failure(result(table(content = cell("A".repeat(65_537)))), "TXT120")
    }

    @Test fun wrappingTabsBreaksAndStylesConsumeCellWorkBudget() {
        failure(result(table(content = cell("A".repeat(65_536))), constraints = input.copy(canvasWidth = Length(2.000001))))
        failure(result(table(content = cell("\t".repeat(65_536)))))
        failure(result(table(content = TableCell(List(65_536) { LineBreak }))))
        failure(result(table(content = TableCell(List(65_536) { i -> if (i % 2 == 0) Text("A") else Strong(listOf(Text("B"))) }))))
        val narrow = input.copy(canvasWidth = Length(2.000001), resources = input.resources.copy(maxItems = 16))
        val document = assertIs<LayoutResult.Success>(result(table(content = cell("AB")), constraints = narrow)).document
        assertEquals("LAY101", document.diagnostics.single().code)
        failure(result(table(content = cell("AB")), constraints = narrow.copy(resources = narrow.resources.copy(maxItems = 15))))
    }

    @Test fun placeholderCountsAndRetainedOpaqueStringsAreBounded() {
        val image = Image(ExternalAssetReference("a"), Alignment.LEFT, AutoSizing, "b")
        val qr = QrCode("ab", Alignment.LEFT, QrErrorCorrection.AUTO)
        val exact = input.copy(resources = input.resources.copy(maxItems = 2, maxTextCodeUnits = 4))
        assertIs<LayoutResult.Success>(result(image, qr, constraints = exact))
        failure(result(image, qr, constraints = exact.copy(resources = exact.resources.copy(maxItems = 1))))
        failure(result(image, qr, constraints = exact.copy(resources = exact.resources.copy(maxTextCodeUnits = 3))))
        val many = input.copy(resources = input.resources.copy(maxItems = 100))
        val images = Array<DocumentBlock>(100) { image }
        assertIs<LayoutResult.Success>(result(*images, constraints = many))
        failure(result(*images, image, constraints = many))
        val codes = Array<DocumentBlock>(100) { qr }
        assertIs<LayoutResult.Success>(result(*codes, constraints = many))
        failure(result(*codes, qr, constraints = many))
    }

    @Test fun paddingExhaustionIsDistinctFromCoordinateAndResourceFailures() {
        failure(result(table(), constraints = input.copy(canvasWidth = Length(2.0))), "LAY107")
        failure(result(table(columns = 2), constraints = input.copy(canvasWidth = Length(4.0))), "LAY107")
        val tiny = body.copy(metrics = TypographyMetrics(0.000001, 0.0, 0.0, 0.000001, 0.000001))
        failure(result(table(), constraints = input.copy(typography = TypographyContext(tiny, tiny, tiny))), "LAY122")
        val huge = body.copy(metrics = TypographyMetrics(3.0, 1.0, 1.0, 1_000_000_000.0, 3.0))
        val constrained = input.copy(typography = TypographyContext(huge, huge, huge))
        for (block in listOf(table(), QrCode("private", Alignment.LEFT, QrErrorCorrection.AUTO),
            Image(ExternalAssetReference("private"), Alignment.LEFT, AutoSizing))) {
            assertEquals(0, failure(result(block, constraints = constrained), "LAY122").sourceBlockIndex)
        }
    }
}
