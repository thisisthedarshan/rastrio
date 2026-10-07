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

package com.circuitnext.rastrio.core.preview

import com.circuitnext.rastrio.core.document.*
import com.circuitnext.rastrio.core.layout.*
import com.circuitnext.rastrio.core.text.*
import kotlin.test.*

class LogicalPreviewTest {
    private val body = ResolvedTypography("preview-test", TextStyle(),
        TypographyMetrics(3.0, 1.0, 1.0, 5.0, 3.0), FixedCellGeometry(1.0, 1.0, 4.0))
    private val heading = body.copy(identity = "heading", style = TextStyle(weight = TextWeight.BOLD, role = TextRole.HEADING))
    private val code = body.copy(identity = "code", style = TextStyle(role = TextRole.CODE))
    private val constraints = LayoutConstraints(Length(48.0), Orientation.PORTRAIT,
        TypographyContext(body, code, body, SnapshotList(List(6) { heading })))
    private fun p(text: String, alignment: Alignment = Alignment.LEFT) = Paragraph(alignment, listOf(Text(text)))
    private fun cell(text: String, alignment: Alignment? = null) = TableCell(listOf(Text(text)), alignment)
    private fun layout(blocks: List<DocumentBlock>, input: LayoutConstraints = constraints,
        measurer: TextMeasurer = AsciiFixedCellMeasurer()) = assertIs<LayoutResult.Success>(
        FoundationLayoutEngine(measurer).layout(ThermalDocument(blocks = blocks), input)).document

    private fun preview(document: LogicalDocument): LogicalPreview {
        val original = document.copy()
        val result = document.toLogicalPreview()
        assertEquals(document.widthMm, result.widthMm)
        assertEquals(document.heightMm, result.heightMm)
        assertEquals(document.constraints.orientation, result.orientation)
        // Sharing finalized immutable snapshots preserves all nested geometry without duplication.
        assertSame(document.blocks, result.blocks)
        assertSame(document.diagnostics, result.diagnostics)
        assertEquals(original, document)
        assertEquals(result, document.toLogicalPreview())
        return result
    }

    @Test fun emptyDocumentPreservesCanvasInBothOrientations() {
        for (orientation in Orientation.entries) {
            val result = preview(layout(emptyList(), constraints.copy(orientation = orientation)))
            assertEquals(48.0, result.widthMm)
            assertEquals(0.0, result.heightMm)
            assertTrue(result.blocks.isEmpty())
            assertTrue(result.diagnostics.isEmpty())
        }
    }

    @Test fun paragraphAndHeadingKeepExactBoundsBaselinesAndTypography() {
        val result = preview(layout(listOf(p("AB"), Heading(2, Alignment.CENTER, listOf(Text("TITLE"))))))
        assertEquals(listOf(0, 1), result.blocks.map { it.sourceBlockIndex })
        assertEquals(LogicalBounds(0.0, 0.0, 48.0, 5.0), result.blocks[0].bounds)
        val paragraph = assertIs<LogicalTextBlock>(result.blocks[0]).lines.single()
        assertEquals(LogicalBounds(0.0, 0.0, 2.0, 5.0), paragraph.bounds)
        assertEquals(3.0, paragraph.baselineMm)
        assertEquals(body, paragraph.runs.single().typography)
        val title = assertIs<LogicalTextBlock>(result.blocks[1]).lines.single()
        assertEquals(LogicalBounds(21.5, 7.5, 5.0, 5.0), title.bounds)
        assertEquals(10.5, title.baselineMm)
        assertEquals(heading, title.runs.single().typography)
        assertEquals(15.0, result.heightMm)
    }

    @Test fun wrappingExplicitBreaksAndPerLineAlignmentArePreserved() {
        for ((alignment, xs) in listOf(Alignment.LEFT to listOf(0.0, 0.0, 0.0, 0.0),
            Alignment.CENTER to listOf(0.0, 1.5, 2.5, 2.0), Alignment.RIGHT to listOf(0.0, 3.0, 5.0, 4.0))) {
            val paragraph = Paragraph(alignment, listOf(Text("ABCDEFG"), LineBreak, LineBreak, Text("Z")))
            val result = preview(layout(listOf(paragraph), constraints.copy(canvasWidth = Length(5.0))))
            val lines = assertIs<LogicalTextBlock>(result.blocks.single()).lines
            assertEquals(listOf("ABCDE", "FG", "", "Z"), lines.map { it.runs.joinToString("") { run -> run.text } })
            assertEquals(xs, lines.map { it.bounds.xMm })
            assertEquals(listOf(0.0, 5.0, 10.0, 15.0), lines.map { it.bounds.yMm })
            assertEquals(listOf(3.0, 8.0, 13.0, 18.0), lines.map { it.baselineMm })
            assertEquals(listOf(false, true, true, false), lines.map { it.endsWithExplicitBreak })
        }
    }

    @Test fun nestedListsKeepResolvedMarkerColumnsAndIndentation() {
        val source = listOf(OrderedList(9, listOf(ListItem(listOf(p("first"),
            UnorderedList(listOf(ListItem(listOf(p("nested"))))))), ListItem(listOf(p("second"))))))
        val document = layout(source)
        val result = preview(document)
        val list = assertIs<LogicalListBlock>(result.blocks.single())
        assertEquals(LogicalListKind.ORDERED, list.kind)
        assertEquals(3.0, list.markerColumnWidthMm)
        assertEquals(8.0, list.contentOriginMm)
        assertEquals(listOf(9L, 10L), list.items.map { assertIs<LogicalOrderedMarker>(it.marker).number })
        assertEquals(listOf(5.0, 4.0), list.items.map { it.marker.bounds.xMm })
        val nested = assertIs<LogicalListBlock>(list.items.first().blocks[1])
        assertEquals(LogicalListKind.UNORDERED, nested.kind)
        assertEquals(8.0, nested.bounds.xMm)
        assertEquals(18.0, nested.contentOriginMm)
        assertEquals(LogicalBounds(16.0, 9.5, 1.0, 1.0), nested.items.single().marker.bounds)
        assertEquals(18.0, nested.items.single().blocks.single().bounds.xMm)
    }

    @Test fun checklistKeepsExplicitStrokesAndCheckPoints() {
        val result = preview(layout(listOf(Checklist(listOf(ChecklistItem(false, listOf(p("open"))),
            ChecklistItem(true, listOf(p("done"))))))))
        val items = assertIs<LogicalListBlock>(result.blocks.single()).items
        val open = assertIs<LogicalChecklistMarker>(items[0].marker)
        val done = assertIs<LogicalChecklistMarker>(items[1].marker)
        assertFalse(open.checked)
        assertTrue(open.checkPoints.isEmpty())
        assertTrue(done.checked)
        assertEquals(0.32, done.strokeWidthMm)
        assertEquals(LogicalBounds(4.16, 8.16, 3.68, 3.68), done.outlineCenterlineBounds)
        assertEquals(listOf(LogicalPoint(4.8, 10.0), LogicalPoint(5.6, 10.8), LogicalPoint(7.2, 9.0)), done.checkPoints)
    }

    @Test fun quoteCodeTabsAndSeparatorKeepResolvedGeometry() {
        val result = preview(layout(listOf(Quote(listOf(CodeBlock("A\tB\r\n\nC\n"))), Separator)))
        val quote = assertIs<LogicalQuoteBlock>(result.blocks[0])
        assertEquals(4.0, quote.contentOriginMm)
        val block = assertIs<LogicalTextBlock>(quote.blocks.single())
        assertEquals(LogicalTextKind.CODE, block.kind)
        assertEquals(listOf("A\tB", "", "C", ""), block.lines.map { it.runs.joinToString("") { run -> run.text } })
        val line = block.lines.first()
        assertEquals(listOf("A", "\t", "B"), line.runs.map { it.text })
        assertTrue(line.runs.all { it.typography == code })
        assertEquals(5.0, line.advanceMm)
        assertEquals(listOf(1.0, 3.0, 1.0), line.runs.map { it.measurement.advanceMm })
        assertEquals(listOf(4.0, 5.0, 8.0), line.runs.map { it.bounds.xMm })
        val rule = assertIs<LogicalSeparator>(result.blocks[1])
        assertEquals(LogicalBounds(0.0, 27.5, 48.0, 0.2), rule.bounds)
        assertEquals(2.5, rule.beforeSpacingMm)
        assertEquals(2.5, rule.afterSpacingMm)
        assertEquals(30.2, result.heightMm)
    }

    @Test fun normalAndWideTablesRemainOneAssembledCanvas() {
        val table = Table(listOf(TableColumn(Alignment.LEFT), TableColumn(Alignment.RIGHT)),
            listOf(cell("HEADER"), cell("SECOND")), listOf(listOf(cell("A".repeat(50)), cell("B", Alignment.CENTER))))
        for ((width, orientation) in listOf(48.0 to Orientation.PORTRAIT, 180.0 to Orientation.LANDSCAPE)) {
            val result = preview(layout(listOf(table), constraints.copy(canvasWidth = Length(width), orientation = orientation)))
            val block = assertIs<LogicalTableBlock>(result.blocks.single())
            assertEquals(width, result.widthMm)
            assertEquals(width, block.bounds.widthMm)
            assertEquals(listOf(width / 2, width / 2), block.columns.map { it.bounds.widthMm })
            assertEquals(listOf(0.0, width / 2), block.columns.map { it.bounds.xMm })
            assertEquals(listOf(true, false), block.rows.map { it.isHeader })
            assertEquals(Alignment.CENTER, block.rows[1].cells[1].alignment)
            val lines = block.rows[1].cells[0].text.lines
            assertEquals(if (width == 48.0) listOf(22, 22, 6) else listOf(50), lines.map { it.runs.sumOf { run -> run.text.length } })
            assertEquals(if (width == 48.0) 17.0 else 7.0, block.rows[1].bounds.heightMm)
            assertEquals(1.0, block.cellPaddingMm)
        }
    }

    @Test fun imageAndQrPlaceholdersRetainOpaqueIntentWithoutResolution() {
        val uri = "file:///preview-must-not-open"
        val payload = "https://preview-must-not-fetch.invalid/opaque"
        val result = preview(layout(listOf(Image(ExternalAssetReference(uri), Alignment.CENTER,
            RequestedWidthSizing(Length(8.0)), "alt"), QrCode(payload, Alignment.RIGHT, QrErrorCorrection.HIGH, Length(7.0)))))
        val image = assertIs<LogicalImagePlaceholder>(result.blocks[0])
        assertEquals(LogicalBounds(20.0, 2.5, 8.0, 8.0), image.bounds)
        assertFalse(image.intrinsicSizeResolved)
        assertEquals(ExternalAssetReference(uri), image.asset)
        assertEquals(RequestedWidthSizing(Length(8.0)), image.sizing)
        assertEquals("alt", image.altText)
        val qr = assertIs<LogicalQrPlaceholder>(result.blocks[1])
        assertEquals(LogicalBounds(41.0, 15.5, 7.0, 7.0), qr.bounds)
        assertEquals(payload, qr.payload)
        assertEquals(QrErrorCorrection.HIGH, qr.errorCorrection)
        assertEquals(Length(7.0), qr.requestedSize)
        assertEquals(25.0, result.heightMm)
    }

    @Test fun conversionCannotRemeasureOrRewrapAnOversizedIndivisibleCluster() {
        var calls = 0
        var conversionStarted = false
        val measurer = TextMeasurer { request ->
            check(!conversionStarted) { "Preview must not measure text" }
            calls++
            TextMeasureResult.Success(TextMeasurement(request.text.length, 12.0, request.typography.metrics,
                SnapshotList(listOf(MeasuredCluster(0, request.text.length, 12.0))), SnapshotList(emptyList())))
        }
        val document = layout(listOf(p("AB", Alignment.RIGHT)), constraints.copy(canvasWidth = Length(5.0)), measurer)
        val measuredCalls = calls
        conversionStarted = true
        val result = preview(document)
        assertEquals(measuredCalls, calls)
        val line = assertIs<LogicalTextBlock>(result.blocks.single()).lines.single()
        assertEquals("AB", line.runs.single().text)
        assertEquals(12.0, line.advanceMm)
        assertEquals(5.0, line.availableWidthMm)
        assertEquals(0.0, line.bounds.xMm)
        assertEquals(listOf(MeasuredCluster(0, 2, 12.0)), line.runs.single().measurement.clusters)
        assertEquals(listOf("LAY101"), result.diagnostics.map { it.code })
        assertEquals(0, result.diagnostics.single().sourceBlockIndex)
    }

    @Test fun equivalentInputsProduceEquivalentPreviewsWithoutReordering() {
        val blocks = listOf(p("start"), Separator, Image(EmbeddedAssetReference("asset"), Alignment.LEFT, AutoSizing),
            QrCode("opaque", Alignment.CENTER, QrErrorCorrection.AUTO), p("end"))
        val first = preview(layout(blocks))
        val second = preview(layout(blocks.toList()))
        assertEquals(first, second)
        assertEquals(listOf(0, 1, 2, 3, 4), first.blocks.map { it.sourceBlockIndex })
        assertEquals(listOf("start", "end"), first.blocks.filterIsInstance<LogicalTextBlock>().map {
            it.lines.single().runs.single().text })
    }

    @Test fun mutableSourceCollectionsCannotAlterPreviewSnapshots() {
        val content = mutableListOf<InlineContent>(Text("AB"))
        val blocks = mutableListOf<DocumentBlock>(Paragraph(Alignment.LEFT, content))
        val document = layout(blocks)
        val result = preview(document)
        content.clear()
        blocks.clear()
        assertEquals("AB", assertIs<LogicalTextBlock>(result.blocks.single()).lines.single().runs.single().text)
        assertFalse((result.blocks as List<*>) is MutableList<*>)
        assertFalse((result.diagnostics as List<*>) is MutableList<*>)
        assertEquals(result, document.toLogicalPreview())
    }

    @Test fun longDocumentConversionRetainsIndexedSnapshotsWithoutAllocatingASceneOrBitmap() {
        val document = layout(List(2000) { p("ABCD") })
        val result = preview(document)
        assertEquals(2000, result.blocks.size)
        assertEquals(15000.0, result.heightMm)
        assertSame(document.blocks.last(), result.blocks[1999])
    }

    @Test fun geometryOnlyPlaceholderFromTheFoundationRemainsRepresentable() {
        val block = LogicalPlaceholder(0, LogicalBounds(3.0, 4.0, 8.0, 9.0), PlaceholderKind.IMAGE, Alignment.CENTER)
        val document = LogicalDocument(constraints, 20.0, SnapshotList(listOf(block)))
        assertSame(block, preview(document).blocks.single())
    }
}
