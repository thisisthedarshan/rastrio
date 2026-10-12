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


package com.circuitnext.rastrio.core.printer

import com.circuitnext.rastrio.core.document.*
import com.circuitnext.rastrio.core.layout.*
import com.circuitnext.rastrio.core.profile.SyntheticReferenceProfiles
import kotlin.math.round
import kotlin.test.*

class StructuredPreparationTest {
    private fun paragraph(text: String) = Paragraph(Alignment.LEFT, listOf(Text(text)))
    private fun prepare(vararg blocks: DocumentBlock, engine: BasicPreparationEngine = BasicPreparationEngine()) =
        assertIs<PreparationResult.Success>(engine.prepare(ThermalDocument(blocks = blocks.toList()),
            SyntheticReferenceProfiles.narrow)).print
    private fun text(print: PreparedPrint) = print.operations.filterIsInstance<NativeText>()

    @Test fun nestedQuotesUseResolvedIndentationWithoutDecorativeRules() {
        val plan = prepare(Quote(listOf(paragraph("A"), Quote(listOf(paragraph("B"))))), paragraph("C"))
        assertEquals(listOf("A", "B", "C"), text(plan).map { it.text })
        assertEquals(listOf(DotBounds(24, 0, 12, 24), DotBounds(48, 36, 12, 24),
            DotBounds(0, 72, 12, 24)), text(plan).map { it.bounds })
        assertEquals(listOf(0, 0, 1), text(plan).map { it.sourceBlockIndex })
        assertTrue(plan.operations.none { it is Separator })
        assertEquals(108, plan.heightDots)
    }

    @Test fun orderedMarkersAcrossDigitWidthsShareTheResolvedContentOriginAndAdvanceOnce() {
        val plan = prepare(OrderedList(9, listOf(ListItem(listOf(paragraph("A"))),
            ListItem(listOf(paragraph("B"))))), paragraph("C"))
        val runs = text(plan)
        assertEquals(listOf("9.", "A", "10.", "B", "C"), runs.map { it.text })
        assertEquals(listOf(DotBounds(36, 0, 24, 24), DotBounds(72, 0, 12, 24),
            DotBounds(24, 36, 36, 24), DotBounds(72, 36, 12, 24), DotBounds(0, 72, 12, 24)), runs.map { it.bounds })
        assertEquals(listOf(0, 24, 0, 24, 24), runs.map { it.advanceDots })
        assertEquals(listOf(PreparedAlignment.RIGHT, PreparedAlignment.LEFT, PreparedAlignment.RIGHT,
            PreparedAlignment.LEFT, PreparedAlignment.LEFT), runs.map { it.alignment })
        assertEquals(listOf(0, 0, 0, 0, 1), runs.map { it.sourceBlockIndex })
        assertEquals(108, plan.heightDots)
    }

    @Test fun orderedMarkerBeforeCodeUsesPartialAdvanceWithoutMovingTheNextLine() {
        val plan = prepare(OrderedList(1, listOf(ListItem(listOf(CodeBlock("A"))))))
        val runs = text(plan)
        assertEquals(listOf("1.", "A"), runs.map { it.text })
        assertEquals(listOf(0, 12), runs.map { it.bounds.y })
        assertEquals(listOf(12, 24), runs.map { it.advanceDots })
        assertEquals(48, plan.heightDots)
        assertEquals(DotBounds(0, 36, 0, 12), plan.operations.filterIsInstance<Feed>().single().bounds)
    }

    @Test fun borderlessTableInterleavesCellsByRowAndPreservesPaddingWrappingAndAlignment() {
        val table = Table(listOf(TableColumn(Alignment.LEFT), TableColumn(Alignment.RIGHT)),
            listOf(TableCell(listOf(Text("A"))), TableCell(listOf(Text("B")))),
            listOf(listOf(TableCell(listOf(Text("ABCDEFGHIJKLMNOP"))),
                TableCell(listOf(Text("C")), Alignment.CENTER))))
        val plan = prepare(table)
        val runs = text(plan)
        assertEquals(listOf("A", "B", "ABCDEFGHIJKLMNO", "C", "P"), runs.map { it.text })
        assertEquals(listOf(DotBounds(6, 18, 12, 24), DotBounds(366, 18, 12, 24),
            DotBounds(6, 54, 180, 24), DotBounds(282, 54, 12, 24), DotBounds(6, 78, 12, 24)), runs.map { it.bounds })
        assertEquals(listOf(0, 24, 0, 24, 24), runs.map { it.advanceDots })
        assertEquals(listOf(PreparedAlignment.LEFT, PreparedAlignment.RIGHT, PreparedAlignment.LEFT,
            PreparedAlignment.CENTER, PreparedAlignment.LEFT), runs.map { it.alignment })
        assertTrue(runs.all { it.sourceBlockIndex == 0 })
        assertEquals(listOf(DotBounds(0, 0, 0, 18), DotBounds(0, 42, 0, 12), DotBounds(0, 102, 0, 18)),
            plan.operations.filterIsInstance<Feed>().map { it.bounds })
        assertTrue(plan.operations.none { it is Separator })
        assertEquals(120, plan.heightDots)
    }

    @Test fun tableStyledRunsAndEmptyCellsRetainSourceAndOnlyFinalRunAdvances() {
        val table = Table(listOf(TableColumn(Alignment.LEFT), TableColumn(Alignment.LEFT)),
            listOf(TableCell(listOf(Text("A"), Strong(listOf(Text("B"))))), TableCell(emptyList())), emptyList())
        val plan = prepare(paragraph("Before"), Quote(listOf(table)), paragraph("After"))
        val runs = text(plan).filter { it.sourceBlockIndex == 1 }
        assertEquals(listOf("A", "B", ""), runs.map { it.text })
        assertEquals(listOf(false, true, false), runs.map { it.style.bold })
        assertEquals(listOf(0, 0, 24), runs.map { it.advanceDots })
        val logicalTable = (plan.logicalDocument.blocks[1] as LogicalQuoteBlock).blocks.single() as LogicalTableBlock
        val logicalRuns = logicalTable.rows.single().cells.flatMap { it.text.lines.flatMap { line -> line.runs } }
        assertEquals(logicalRuns.map { round(it.bounds.xMm / 25.4 * 203).toInt() }, runs.map { it.bounds.x })
        assertEquals(logicalRuns.map { round(it.bounds.yMm / 25.4 * 203).toInt() }, runs.map { it.bounds.y })
        assertEquals(round(plan.logicalDocument.heightMm / 25.4 * 203).toInt(), plan.heightDots)
    }

    @Test fun separatorsInsideQuotesKeepTheirOwnSourceAndIndentedWidth() {
        val plan = prepare(Quote(listOf(paragraph("A"), com.circuitnext.rastrio.core.document.Separator, paragraph("B"))))
        val separator = plan.operations.filterIsInstance<Separator>().single()
        assertEquals(24, separator.bounds.x)
        assertEquals(360, separator.bounds.width)
        assertEquals(0, separator.sourceBlockIndex)
        assertEquals(24, text(plan).last().bounds.x)
        assertTrue(plan.operations.zipWithNext().all { (left, right) -> left.bounds.y <= right.bounds.y })
    }

    @Test fun structuredPreflightBoundsDepthItemsCellsAndNestedText() {
        val table = Table(listOf(TableColumn(Alignment.LEFT), TableColumn(Alignment.LEFT)),
            listOf(TableCell(emptyList()), TableCell(emptyList())), emptyList())
        val cases = listOf(
            Quote(listOf(Quote(listOf(paragraph("A"))))) to BasicPreparationEngine(layoutResources = LayoutResourcePolicy(maxBlockDepth = 2)),
            OrderedList(0, List(2) { ListItem(listOf(paragraph("A"))) }) to BasicPreparationEngine(layoutResources = LayoutResourcePolicy(maxItems = 1)),
            table to BasicPreparationEngine(layoutResources = LayoutResourcePolicy(maxTableCells = 1)),
            Quote(listOf(paragraph("AB"))) to BasicPreparationEngine(resources = PreparationResourcePolicy(maxTextCodeUnits = 1)),
        )
        for ((block, engine) in cases) {
            val failure = assertIs<PreparationResult.Failure>(engine.prepare(ThermalDocument(blocks = listOf(block)),
                SyntheticReferenceProfiles.narrow))
            assertEquals("PRN120", failure.diagnostics.single().code)
            assertEquals(0, failure.diagnostics.single().sourceBlockIndex)
        }
    }

    @Test fun unsupportedGraphicMarkersAndNestedGlyphsRemainExplicitFailures() {
        for (block in listOf(UnorderedList(listOf(ListItem(listOf(paragraph("A"))))),
            Checklist(listOf(ChecklistItem(true, listOf(paragraph("A"))))))) {
            val failure = assertIs<PreparationResult.Failure>(BasicPreparationEngine().prepare(
                ThermalDocument(blocks = listOf(paragraph("Before"), Quote(listOf(block)))), SyntheticReferenceProfiles.narrow))
            assertEquals("TXT132", failure.diagnostics.single().code)
            assertEquals(1, failure.diagnostics.single().sourceBlockIndex)
        }
        val failure = assertIs<PreparationResult.Failure>(BasicPreparationEngine().prepare(
            ThermalDocument(blocks = listOf(Quote(listOf(paragraph("世界"))))), SyntheticReferenceProfiles.narrow))
        assertTrue(failure.diagnostics.isNotEmpty())
        assertTrue(failure.diagnostics.all { it.sourceBlockIndex == 0 })
    }
}
