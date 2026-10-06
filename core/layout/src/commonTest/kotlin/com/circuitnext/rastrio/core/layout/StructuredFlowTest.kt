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

class StructuredFlowTest {
    private val body = ResolvedTypography("structured", TextStyle(), TypographyMetrics(3.0, 1.0, 1.0, 5.0, 3.0), FixedCellGeometry(1.0, 1.0, 4.0))
    private val input = LayoutConstraints(Length(40.0), Orientation.PORTRAIT, TypographyContext(body, body, body))
    private fun p(text: String) = Paragraph(Alignment.LEFT, listOf(Text(text)))
    private fun layout(vararg blocks: DocumentBlock): LogicalDocument = assertIs<LayoutResult.Success>(
        FoundationLayoutEngine(AsciiFixedCellMeasurer()).layout(ThermalDocument(blocks = blocks.toList()), input)).document

    @Test fun unorderedItemsAndNestedBlocksFlow() {
        val output = layout(UnorderedList(listOf(ListItem(listOf(p("A"))), ListItem(listOf(p("B"))))))
        assertEquals(15.0, output.heightMm)
        assertEquals(LogicalBounds(0.0, 0.0, 40.0, 15.0), output.blocks.single().bounds)
        val nested = layout(UnorderedList(listOf(ListItem(listOf(p("A"), UnorderedList(listOf(ListItem(listOf(p("B"))))))))))
        assertEquals(15.0, nested.heightMm)
    }

    @Test fun orderedMarkersCrossADigitBoundaryAndSupportLongValues() {
        val output = layout(OrderedList(9, listOf(ListItem(listOf(p("A"))), ListItem(listOf(p("B"))))))
        assertEquals(15.0, output.heightMm)
        assertEquals(7.5, layout(OrderedList(Long.MAX_VALUE, listOf(ListItem(listOf(p("A")))))).heightMm)
        val engine = FoundationLayoutEngine(AsciiFixedCellMeasurer())
        assertIs<LayoutResult.Failure>(engine.layout(ThermalDocument(blocks = listOf(
            OrderedList(Long.MAX_VALUE, List(2) { ListItem(listOf(p("A"))) }))), input))
    }

    @Test fun checklistItemsWithEmptyParagraphsRetainNormalGeometry() {
        val paragraph = Paragraph(Alignment.LEFT, emptyList())
        val block = Checklist(listOf(ChecklistItem(false, listOf(paragraph)), ChecklistItem(true, listOf(paragraph))))
        val output = layout(block)
        val list = assertIs<LogicalListBlock>(output.blocks.single())
        assertEquals(LogicalBounds(0.0, 0.0, 40.0, 15.0), list.bounds)
        assertEquals(4.0, list.markerColumnWidthMm)
        assertEquals(9.0, list.contentOriginMm)
        assertEquals(listOf(LogicalBounds(4.0, 0.0, 36.0, 7.5), LogicalBounds(4.0, 7.5, 36.0, 7.5)),
            list.items.map { it.bounds })
        val markers = list.items.map { assertIs<LogicalChecklistMarker>(it.marker) }
        assertEquals(listOf(false, true), markers.map { it.checked })
        assertEquals(listOf(LogicalBounds(4.0, 0.5, 4.0, 4.0), LogicalBounds(4.0, 8.0, 4.0, 4.0)), markers.map { it.bounds })
        assertEquals(listOf(0.32, 0.32), markers.map { it.strokeWidthMm })
        assertEquals(listOf(LogicalBounds(4.16, 0.66, 3.68, 3.68), LogicalBounds(4.16, 8.16, 3.68, 3.68)),
            markers.map { it.outlineCenterlineBounds })
        assertTrue(markers.first().checkPoints.isEmpty())
        assertEquals(listOf(LogicalPoint(4.8, 10.0), LogicalPoint(5.6, 10.8), LogicalPoint(7.2, 9.0)), markers.last().checkPoints)
        val lines = list.items.map { assertIs<LogicalTextBlock>(it.blocks.single()).lines.single() }
        assertEquals(listOf(LogicalBounds(9.0, 0.0, 0.0, 5.0), LogicalBounds(9.0, 7.5, 0.0, 5.0)), lines.map { it.bounds })
        assertEquals(listOf(31.0, 31.0), lines.map { it.availableWidthMm })
        assertEquals(listOf(3.0, 10.5), lines.map { it.baselineMm })
        assertTrue(lines.all { it.runs.single().text.isEmpty() })
        assertEquals(15.0, output.heightMm)
        assertTrue(output.diagnostics.isEmpty())
        assertEquals(output, layout(block))
    }

    @Test fun quotesPreserveNestedFlowAndCodeSpacing() {
        val output = layout(Quote(listOf(p("A"), Quote(listOf(CodeBlock("B"))))))
        assertEquals(17.5, output.heightMm)
        assertEquals(LogicalBounds(0.0, 0.0, 40.0, 17.5), output.blocks.single().bounds)
    }

    @Test fun separatorGeometryAndSpacingAreExplicit() {
        val output = layout(Separator)
        assertEquals(LogicalBounds(0.0, 2.5, 40.0, 0.2), output.blocks.single().bounds)
        assertEquals(5.2, output.heightMm)
        assertEquals(20.2, layout(p("A"), Separator, p("B")).heightMm)
        assertEquals(10.4, layout(Separator, Separator).heightMm)
    }

    @Test fun unorderedMarkerAndHangingIndentAreAuthoritative() {
        val output = layout(UnorderedList(listOf(ListItem(listOf(p("A".repeat(35)), p("B"))))))
        val list = assertIs<LogicalListBlock>(output.blocks.single())
        assertEquals(LogicalListKind.UNORDERED, list.kind)
        assertEquals(1.0, list.markerColumnWidthMm)
        assertEquals(6.0, list.contentOriginMm)
        val item = list.items.single()
        assertEquals(LogicalBounds(4.0, 2.0, 1.0, 1.0), assertIs<LogicalUnorderedMarker>(item.marker).bounds)
        val first = assertIs<LogicalTextBlock>(item.blocks.first())
        assertEquals(listOf(34.0, 1.0), first.lines.map { it.advanceMm })
        assertEquals(listOf(6.0, 6.0), first.lines.map { it.bounds.xMm })
        assertEquals(listOf(3.0, 8.0), first.lines.map { it.baselineMm })
        assertEquals(34.0, first.lines.first().availableWidthMm)
        assertEquals(LogicalBounds(6.0, 12.5, 34.0, 5.0), item.blocks.last().bounds)
        assertEquals(20.0, output.heightMm)
    }

    @Test fun orderedContainerUsesCommonMaximumWidthColumn() {
        val output = layout(OrderedList(9, listOf(ListItem(listOf(p("A".repeat(33)), p("C"))), ListItem(listOf(p("B"))))))
        val list = assertIs<LogicalListBlock>(output.blocks.single())
        assertEquals(3.0, list.markerColumnWidthMm)
        assertEquals(8.0, list.contentOriginMm)
        assertEquals(listOf(8.0, 8.0), list.items.map { it.contentOriginMm })
        val markers = list.items.map { assertIs<LogicalOrderedMarker>(it.marker) }
        assertEquals(listOf(9L, 10L), markers.map { it.number })
        assertEquals(listOf("9.", "10."), markers.map { it.run.text })
        assertEquals(listOf(5.0, 4.0), markers.map { it.bounds.xMm })
        assertEquals(listOf(3.0, 23.0), markers.map { it.baselineMm })
        val first = assertIs<LogicalTextBlock>(list.items.first().blocks.first())
        assertEquals(listOf(32.0, 1.0), first.lines.map { it.advanceMm })
        assertEquals(listOf(8.0, 8.0), first.lines.map { it.bounds.xMm })
        assertEquals(8.0, list.items.first().blocks.last().bounds.xMm)
        assertEquals(27.5, output.heightMm)
        for (start in listOf(0L, 1L, 42L, Long.MAX_VALUE)) {
            val one = assertIs<LogicalListBlock>(layout(OrderedList(start, listOf(ListItem(listOf(p("A")))))).blocks.single())
            assertEquals("$start.", assertIs<LogicalOrderedMarker>(one.items.single().marker).run.text)
        }
    }

    @Test fun checklistGraphicsContainExplicitCheckStrokesWithoutGlyphs() {
        val output = layout(Checklist(listOf(ChecklistItem(false, listOf(p("A".repeat(32)))), ChecklistItem(true, listOf(p("B"))))))
        val list = assertIs<LogicalListBlock>(output.blocks.single())
        assertEquals(9.0, list.contentOriginMm)
        val unchecked = assertIs<LogicalChecklistMarker>(list.items.first().marker)
        assertEquals(LogicalBounds(4.0, 0.5, 4.0, 4.0), unchecked.bounds)
        assertEquals(0.32, unchecked.strokeWidthMm)
        assertEquals(LogicalBounds(4.16, 0.66, 3.68, 3.68), unchecked.outlineCenterlineBounds)
        assertFalse(unchecked.checked)
        assertTrue(unchecked.checkPoints.isEmpty())
        val checked = assertIs<LogicalChecklistMarker>(list.items.last().marker)
        assertTrue(checked.checked)
        assertEquals(listOf(LogicalPoint(4.8, 15.0), LogicalPoint(5.6, 15.8), LogicalPoint(7.2, 14.0)), checked.checkPoints)
        val lines = assertIs<LogicalTextBlock>(list.items.first().blocks.single()).lines
        assertEquals(listOf(9.0, 9.0), lines.map { it.bounds.xMm })
        assertEquals(listOf(31.0, 1.0), lines.map { it.advanceMm })
        assertEquals(20.0, output.heightMm)
    }

    @Test fun nestedListKindsAndQuotesKeepFinalizedOrigins() {
        val output = layout(UnorderedList(listOf(ListItem(listOf(
            p("A"), OrderedList(1, listOf(ListItem(listOf(Checklist(listOf(ChecklistItem(true, listOf(p("B"))))))))))))))
        val outer = assertIs<LogicalListBlock>(output.blocks.single())
        val ordered = assertIs<LogicalListBlock>(outer.items.single().blocks.last())
        assertEquals(14.0, ordered.items.single().marker.bounds.xMm)
        assertEquals(17.0, ordered.contentOriginMm)
        val checklist = assertIs<LogicalListBlock>(ordered.items.single().blocks.single())
        assertEquals(25.0, checklist.items.single().marker.bounds.xMm)
        assertEquals(30.0, checklist.contentOriginMm)
        val quote = assertIs<LogicalQuoteBlock>(layout(Quote(listOf(Quote(listOf(p("A"))), CodeBlock("B"),
            UnorderedList(listOf(ListItem(listOf(p("C")))))))).blocks.single())
        assertEquals(4.0, quote.contentOriginMm)
        val nested = assertIs<LogicalQuoteBlock>(quote.blocks.first())
        assertEquals(8.0, nested.contentOriginMm)
        assertEquals(LogicalBounds(8.0, 0.0, 32.0, 5.0), nested.blocks.single().bounds)
        val code = assertIs<LogicalTextBlock>(quote.blocks[1])
        assertEquals(LogicalTextKind.CODE, code.kind)
        assertEquals(LogicalBounds(4.0, 10.0, 36.0, 5.0), code.bounds)
        assertEquals(10.0, assertIs<LogicalListBlock>(quote.blocks.last()).contentOriginMm)
    }

    @Test fun quoteListCodeChainPreservesAbsoluteGeometryAndSpacing() {
        val output = layout(p("P"), Quote(listOf(UnorderedList(listOf(
            ListItem(listOf(CodeBlock("A\tB\nC"))))))))
        val quote = assertIs<LogicalQuoteBlock>(output.blocks.last())
        assertEquals(4.0, quote.contentOriginMm)
        assertEquals(LogicalBounds(0.0, 7.5, 40.0, 15.0), quote.bounds)
        val list = assertIs<LogicalListBlock>(quote.blocks.single())
        assertEquals(LogicalListKind.UNORDERED, list.kind)
        assertEquals(1.0, list.markerColumnWidthMm)
        assertEquals(10.0, list.contentOriginMm)
        assertEquals(LogicalBounds(4.0, 7.5, 36.0, 15.0), list.bounds)
        val item = list.items.single()
        assertEquals(LogicalBounds(8.0, 9.5, 1.0, 1.0), assertIs<LogicalUnorderedMarker>(item.marker).bounds)
        assertEquals(10.0, item.contentOriginMm)
        assertEquals(LogicalBounds(8.0, 7.5, 32.0, 15.0), item.bounds)
        val code = assertIs<LogicalTextBlock>(item.blocks.single())
        assertEquals(LogicalTextKind.CODE, code.kind)
        assertEquals(LogicalBounds(10.0, 10.0, 30.0, 10.0), code.bounds)
        assertEquals(listOf(30.0, 30.0), code.lines.map { it.availableWidthMm })
        assertEquals(listOf(LogicalBounds(10.0, 10.0, 5.0, 5.0), LogicalBounds(10.0, 15.0, 1.0, 5.0)),
            code.lines.map { it.bounds })
        assertEquals(listOf(13.0, 18.0), code.lines.map { it.baselineMm })
        assertEquals(listOf("A\tB", "C"), code.lines.map { line -> line.runs.joinToString("") { it.text } })
        assertEquals(listOf(LogicalBounds(10.0, 10.0, 1.0, 5.0), LogicalBounds(11.0, 10.0, 3.0, 5.0),
            LogicalBounds(14.0, 10.0, 1.0, 5.0)), code.lines.first().runs.map { it.bounds })
        assertEquals(2.5, code.bounds.yMm - item.bounds.yMm)
        assertEquals(2.5, item.bounds.yMm + item.bounds.heightMm - (code.bounds.yMm + code.bounds.heightMm))
        assertEquals(22.5, output.heightMm)
        assertTrue(output.diagnostics.isEmpty())
    }

    @Test fun tallerOrderedMarkersDetermineItemAndFollowingItemExtents() {
        val markerTypography = body.copy(identity = "tall-marker", style = TextStyle(role = TextRole.LIST_MARKER),
            metrics = TypographyMetrics(7.0, 2.0, 1.0, 10.0, 7.0))
        val constraints = input.copy(typography = TypographyContext(body, body, markerTypography))
        val output = assertIs<LayoutResult.Success>(FoundationLayoutEngine(AsciiFixedCellMeasurer()).layout(
            ThermalDocument(blocks = listOf(OrderedList(9, listOf(ListItem(listOf(p("A"))), ListItem(listOf(p("B"))))))),
            constraints)).document
        val list = assertIs<LogicalListBlock>(output.blocks.single())
        assertEquals(3.0, list.markerColumnWidthMm)
        assertEquals(8.0, list.contentOriginMm)
        assertEquals(listOf(8.0, 8.0), list.items.map { it.contentOriginMm })
        val markers = list.items.map { assertIs<LogicalOrderedMarker>(it.marker) }
        assertEquals(listOf("9.", "10."), markers.map { it.run.text })
        assertEquals(listOf(LogicalBounds(5.0, 0.0, 2.0, 10.0), LogicalBounds(4.0, 10.0, 3.0, 10.0)),
            markers.map { it.bounds })
        assertEquals(listOf(7.0, 17.0), markers.map { it.baselineMm })
        assertTrue(markers.all { it.run.typography == markerTypography })
        val content = list.items.map { assertIs<LogicalTextBlock>(it.blocks.single()) }
        assertEquals(listOf(LogicalBounds(8.0, 0.0, 32.0, 5.0), LogicalBounds(8.0, 10.0, 32.0, 5.0)),
            content.map { it.bounds })
        assertEquals(listOf(LogicalBounds(8.0, 0.0, 1.0, 5.0), LogicalBounds(8.0, 10.0, 1.0, 5.0)),
            content.map { it.lines.single().bounds })
        assertEquals(listOf(3.0, 13.0), content.map { it.lines.single().baselineMm })
        assertEquals(listOf(LogicalBounds(4.0, 0.0, 36.0, 10.0), LogicalBounds(4.0, 10.0, 36.0, 10.0)),
            list.items.map { it.bounds })
        assertEquals(LogicalBounds(0.0, 0.0, 40.0, 20.0), list.bounds)
        assertEquals(20.0, output.heightMm)
        assertTrue(output.diagnostics.isEmpty())
    }

    @Test fun separatorInsideContainersUsesContentWidth() {
        val output = layout(Quote(listOf(Separator)))
        val quote = assertIs<LogicalQuoteBlock>(output.blocks.single())
        val separator = assertIs<LogicalSeparator>(quote.blocks.single())
        assertEquals(LogicalBounds(4.0, 2.5, 36.0, 0.2), separator.bounds)
        assertEquals(2.5, separator.beforeSpacingMm)
        assertEquals(2.5, separator.afterSpacingMm)
    }

    @Test fun listAndQuoteSnapshotsSurviveSemanticMutation() {
        val children = mutableListOf<DocumentBlock>(p("A"))
        val items = mutableListOf(ListItem(children))
        val output = layout(Quote(listOf(UnorderedList(items))))
        children.clear()
        items.clear()
        val list = assertIs<LogicalListBlock>(assertIs<LogicalQuoteBlock>(output.blocks.single()).blocks.single())
        assertEquals("A", assertIs<LogicalTextBlock>(list.items.single().blocks.single()).lines.single().runs.single().text)
        assertFalse((list.items as List<*>) is MutableList<*>)
    }
}
