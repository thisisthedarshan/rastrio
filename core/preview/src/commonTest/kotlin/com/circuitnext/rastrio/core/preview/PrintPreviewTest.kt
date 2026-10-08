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
import com.circuitnext.rastrio.core.printer.*
import com.circuitnext.rastrio.core.profile.SyntheticReferenceProfiles
import com.circuitnext.rastrio.core.profile.PrinterProfile
import kotlin.test.*

class PrintPreviewTest {
    private fun paragraph(text: String, alignment: Alignment = Alignment.LEFT) = Paragraph(alignment, listOf(Text(text)))
    private fun prepare(
        blocks: List<DocumentBlock>,
        profile: PrinterProfile = SyntheticReferenceProfiles.narrow,
        options: PrintOptions = PrintOptions(),
    ): PreparedPrint = assertIs<PreparationResult.Success>(BasicPreparationEngine().prepare(
        ThermalDocument(blocks = blocks), profile, options)).print
    private fun preview(print: PreparedPrint, resources: PhysicalPreviewResourcePolicy = PhysicalPreviewResourcePolicy()) =
        assertIs<PrintPreviewResult.Success>(print.toPrintPreview(resources)).preview
    private fun tile(preview: PrintPreview, index: Int = 0) = assertIs<PrintPreviewTileResult.Success>(preview.tile(index)).tile

    @Test fun targetWrappingStylesAndNativeCellsAreTheActualPreparedValues() {
        for (profile in listOf(SyntheticReferenceProfiles.narrow, SyntheticReferenceProfiles.wide80mm)) {
            val print = prepare(listOf(paragraph("A".repeat(90), Alignment.CENTER),
                paragraph("é│α", Alignment.RIGHT)), profile, PrintOptions(widthScale = 2, heightScale = 2))
            val originalOperations = print.operations.toList()
            val preview = preview(print)
            assertSame(print.target, preview.target)
            assertEquals(print.target.printableWidthDots, preview.widthDots)
            assertEquals(print.heightDots, preview.heightDots)
            val natives = tile(preview).primitives.filterIsInstance<NativeTextPrimitive>()
            val prepared = print.operations.filterIsInstance<NativeText>()
            assertEquals(prepared.size, natives.size)
            prepared.zip(natives).forEach { (operation, primitive) ->
                assertSame(operation, primitive.operation)
                assertEquals(operation.bounds, primitive.bounds)
                assertEquals(operation.bounds, primitive.clippedBounds)
                assertEquals(PhysicalPreviewAccuracy.EXACT_GEOMETRY_REPRESENTATIVE_GLYPHS, primitive.accuracy)
                assertSame(operation.characterBytes, primitive.operation.characterBytes)
                assertSame(operation.font, primitive.operation.font)
                assertSame(operation.codePage, primitive.operation.codePage)
                assertEquals(2, primitive.operation.style.widthScale)
                assertEquals(2, primitive.operation.style.heightScale)
            }
            assertEquals(prepared.map { it.text }, natives.map { it.operation.text })
            assertEquals(tile(preview), tile(preview(print)))
            assertEquals(originalOperations, print.operations)
        }
    }

    @Test fun separatorAndManualGuideReadTheSameFinalizedBits() {
        val print = prepare(listOf(paragraph("start"), com.circuitnext.rastrio.core.document.Separator),
            options = PrintOptions(cutIntent = CutIntent.MANUAL_GUIDE, finalFeedDots = 7))
        val preview = preview(print, PhysicalPreviewResourcePolicy(preferredTileHeightDots = 1))
        val graphics = print.operations.filter { it is com.circuitnext.rastrio.core.printer.Separator || it is ManualCutGuide }
        assertEquals(2, graphics.size)
        for (operation in graphics) {
            val graphic = when (operation) {
                is com.circuitnext.rastrio.core.printer.Separator -> operation.graphic
                is ManualCutGuide -> operation.graphic
                else -> error("fixture")
            }
            val first = operation.bounds.y / preview.tileHeightDots
            val last = (operation.bounds.bottom - 1) / preview.tileHeightDots
            var representedRows = 0
            for (index in first..last) {
                val primitive = tile(preview, index).primitives.filterIsInstance<MonochromePrimitive>()
                    .single { it.operation === operation }
                assertSame(operation, primitive.operation)
                assertSame(graphic, primitive.graphic)
                assertEquals(operation.bounds, primitive.bounds)
                assertEquals(if (operation is ManualCutGuide) PreparedGraphicKind.MANUAL_CUT_GUIDE else PreparedGraphicKind.SEPARATOR, primitive.kind)
                assertEquals(PhysicalPreviewAccuracy.EXACT_PREPARED_DOTS, primitive.accuracy)
                for (y in primitive.clippedBounds.y until primitive.clippedBounds.bottom) {
                    for (x in 0 until graphic.widthDots) {
                        assertEquals(graphic.isBlack(x, y - operation.bounds.y), primitive.graphic.isBlack(x, y - primitive.bounds.y))
                    }
                    representedRows++
                }
            }
            assertEquals(graphic.heightDots, representedRows)
            val before = graphic.byteAt(0)
            graphic.copyBytes().fill(0)
            assertEquals(before, graphic.byteAt(0))
        }
        assertEquals(print.heightDots, preview.heightDots)
        assertTrue((0 until preview.tileCount).all { tile(preview, it).annotations.isEmpty() })
    }

    @Test fun automaticCutsAreZeroHeightAnnotationsWithFullOrPartialSemantics() {
        for (mode in CutMode.entries) {
            val print = prepare(listOf(paragraph("cut")), SyntheticReferenceProfiles.wide80mm,
                PrintOptions(cutIntent = CutIntent.AUTOMATIC, cutMode = mode, finalFeedDots = 1024))
            val preview = preview(print)
            val cut = print.operations.filterIsInstance<AutomaticCut>().single()
            assertEquals(print.heightDots, preview.heightDots)
            assertEquals(0, cut.bounds.height)
            val tiles = (0 until preview.tileCount).map { tile(preview, it) }
            val annotation = tiles.flatMap { it.annotations }.single()
            assertSame(cut, annotation.operation)
            assertEquals(cut.bounds.y, annotation.yDots)
            assertEquals(mode, annotation.mode)
            assertTrue(tiles.flatMap { it.primitives }.none { it.operation === cut })
            assertEquals(tiles.last().bounds.bottom, annotation.yDots)
        }
    }

    @Test fun longFeedIsLazyAndAdjacentTilesReconstructItsPreparedDistance() {
        val print = prepare(listOf(paragraph("A")), options = PrintOptions(finalFeedDots = 1_000_000))
        val preview = preview(print)
        assertTrue(preview.tileCount > 900)
        assertEquals(1024, preview.tileHeightDots)
        val feed = print.operations.filterIsInstance<Feed>().last()
        val last = tile(preview, preview.tileCount - 1)
        assertEquals(print.heightDots, last.bounds.bottom)
        assertTrue(last.bounds.height <= 1024)
        assertTrue(last.bounds.width.toLong() * last.bounds.height <= 2_000_000)
        assertSame(feed, last.primitives.filterIsInstance<FeedPrimitive>().single().operation)
        assertTrue(last.primitives.size <= 1)
        val crossing = feed.bounds.y / preview.tileHeightDots
        val firstPiece = tile(preview, crossing).primitives.filterIsInstance<FeedPrimitive>().single { it.operation === feed }
        val nextPiece = tile(preview, crossing + 1).primitives.filterIsInstance<FeedPrimitive>().single { it.operation === feed }
        assertEquals(firstPiece.clippedBounds.bottom, nextPiece.clippedBounds.y)
        assertEquals(feed.bounds, firstPiece.bounds)
        assertEquals(PhysicalPreviewAccuracy.EXACT_PAPER_ADVANCE, firstPiece.accuracy)
        assertFalse((last.primitives as List<*>) is MutableList<*>)
        assertEquals(last, tile(preview(print), preview.tileCount - 1))
    }

    @Test fun nativeTextCrossingTilesKeepsCompleteTextAndExactOriginalCellBounds() {
        val print = prepare(listOf(paragraph("ABC")))
        val operation = print.operations.filterIsInstance<NativeText>().single()
        val preview = preview(print, PhysicalPreviewResourcePolicy(preferredTileHeightDots = 7))
        val parts = (0 until preview.tileCount).flatMap { tile(preview, it).primitives }.filterIsInstance<NativeTextPrimitive>()
        assertTrue(parts.size > 1)
        assertEquals(operation.bounds.height, parts.sumOf { it.clippedBounds.height })
        parts.forEach { assertSame(operation, it.operation); assertEquals("ABC", it.operation.text); assertEquals(operation.bounds, it.bounds) }
        parts.zipWithNext().forEach { (first, second) -> assertEquals(first.clippedBounds.bottom, second.clippedBounds.y) }
    }

    @Test fun resourceLimitsAndInvalidRequestsFailStructurally() {
        val print = prepare(listOf(paragraph("A"), paragraph("B")))
        val preview = preview(print)
        for (index in listOf(-1, preview.tileCount, Int.MAX_VALUE)) {
            assertEquals("PRV101", assertIs<PrintPreviewTileResult.Failure>(preview.tile(index)).diagnostics.single().code)
        }
        assertEquals("PRV120", assertIs<PrintPreviewTileResult.Failure>(preview(print,
            PhysicalPreviewResourcePolicy(maxPrimitivesPerTile = 1)).tile(0)).diagnostics.single().code)
        assertEquals("PRV120", assertIs<PrintPreviewResult.Failure>(print.toPrintPreview(
            PhysicalPreviewResourcePolicy(maxTileAreaDots = print.target.printableWidthDots - 1))).diagnostics.single().code)
        val tiny = preview(print, PhysicalPreviewResourcePolicy(maxTileAreaDots = print.target.printableWidthDots))
        assertEquals(1, tiny.tileHeightDots)
        assertEquals(print.target.printableWidthDots, tile(tiny).bounds.width)
        for (height in listOf(0, 1025)) assertFailsWith<IllegalArgumentException> { PhysicalPreviewResourcePolicy(preferredTileHeightDots = height) }
        assertFailsWith<IllegalArgumentException> { PhysicalPreviewResourcePolicy(maxTileAreaDots = 2_000_001) }
        assertFailsWith<IllegalArgumentException> { PhysicalPreviewResourcePolicy(maxPrimitivesPerTile = 0) }
    }

    @Test fun viewportTilingKeepsPreparedContentAndTightensExistingPolicy() {
        val print = prepare(listOf(paragraph("A".repeat(40))))
        val original = preview(print, PhysicalPreviewResourcePolicy(maxPrimitivesPerTile = 1))
        val resized = assertIs<PrintPreviewResult.Success>(original.withTileAreaLimit(384)).preview
        assertSame(print.target, resized.target)
        assertEquals(print.heightDots, resized.heightDots)
        assertEquals(1, resized.tileHeightDots)
        assertSame(print.operations.filterIsInstance<NativeText>().first(),
            tile(resized).primitives.filterIsInstance<NativeTextPrimitive>().single().operation)
        val unchanged = assertIs<PrintPreviewResult.Success>(original.withTileAreaLimit(2_000_000)).preview
        assertIs<PrintPreviewTileResult.Failure>(unchanged.tile(0))
    }

    @Test fun upstreamDiagnosticsKeepTheirSubsystemIdentityAndSources() {
        val print = prepare(listOf(paragraph("fallback")), options = PrintOptions(cutIntent = CutIntent.AUTOMATIC_OR_MANUAL))
        assertTrue(print.diagnostics.isNotEmpty())
        val preview = preview(print)
        assertEquals(print.diagnostics.map { Triple(it.code, it.message, it.sourceBlockIndex) },
            preview.diagnostics.map { Triple(it.code, it.message, it.sourceBlockIndex) })
        assertTrue(preview.diagnostics.none { it.code.startsWith("PRV") })
        assertFalse((preview.diagnostics as List<*>) is MutableList<*>)
    }

    @Test fun emptyJobHasNoInventedMarksFeedOrPhysicalHeight() {
        val print = prepare(emptyList())
        val preview = preview(print)
        assertEquals(0, preview.heightDots)
        assertEquals(1, preview.tileCount)
        assertEquals(DotBounds(0, 0, print.target.printableWidthDots, 0), tile(preview).bounds)
        assertTrue(tile(preview).primitives.isEmpty())
        assertTrue(tile(preview).annotations.isEmpty())
    }
}
