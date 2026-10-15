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

package com.circuitnext.rastrio.printing

import com.circuitnext.rastrio.core.document.*
import com.circuitnext.rastrio.core.escpos.*
import com.circuitnext.rastrio.core.layout.*
import com.circuitnext.rastrio.core.preview.*
import com.circuitnext.rastrio.core.printer.*
import com.circuitnext.rastrio.core.profile.*
import com.circuitnext.rastrio.core.text.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class ArrowFallbackPipelineTest {
    @Test fun mixedArrowsInHeadingsParagraphsAndWrappedTableReuseFinalPixels() = runTest {
        val actual = BundledRasterTextBackend.create()
        var finalized = false
        val backend = object : RasterTextBackend by actual {
            override fun measure(request: TextMeasureRequest): TextMeasureResult {
                check(!finalized) { "Downstream measurement after finalization" }
                return actual.measure(request)
            }
            override fun measure(request: TextMeasureRequest, isCancelled: () -> Boolean): TextMeasureResult {
                check(!finalized) { "Downstream measurement after finalization" }
                return actual.measure(request, isCancelled)
            }
            override fun rasterize(request: RasterTextRequest): RasterTextResult {
                check(!finalized) { "Downstream rasterization after finalization" }
                return actual.rasterize(request)
            }
        }
        val text = "AV↑To → value ↓ result ↗ next ↘ end "
        val document = ThermalDocument(blocks = listOf(
            Heading(2, Alignment.CENTER, listOf(Text(text))),
            Paragraph(Alignment.RIGHT, listOf(Text(text), Strong(listOf(Text(text))))),
            Table(listOf(TableColumn(Alignment.LEFT), TableColumn(Alignment.RIGHT)),
                listOf(TableCell(listOf(Strong(listOf(Text("Value ↑"))))), TableCell(listOf(Text("Result ↓")))),
                listOf(listOf(TableCell(listOf(Text(text.repeat(3)))),
                    TableCell(listOf(Strong(listOf(Text(text.repeat(3))))))))),
        ))
        val prepared = BasicPreparationEngine(rasterTextBackend = backend)
            .prepare(document, SyntheticReferenceProfiles.narrow)
        val plan = assertIs<PreparationResult.Success>(prepared,
            (prepared as? PreparationResult.Failure)?.diagnostics.toString()).print
        finalized = true
        val table = assertIs<LogicalTableBlock>(plan.logicalDocument.blocks[2])
        for (cell in table.rows.last().cells) {
            assertTrue(cell.text.lines.size > 1)
            val retained = cell.text.lines.flatMap { it.runs }.joinToString("") { it.text }
            for (arrow in "↑→↓↗↘") assertEquals(3, retained.count { it == arrow })
            for (line in cell.text.lines) {
                assertEquals(cell.alignment, line.alignment)
                assertTrue(line.bounds.xMm >= cell.contentBounds.xMm - 0.000001)
                assertTrue(line.bounds.xMm + line.advanceMm <=
                    cell.contentBounds.xMm + cell.contentBounds.widthMm + 0.000001)
                for (run in line.runs) {
                    assertTrue(line.baselineMm - run.typography.metrics.ascentMm >= line.bounds.yMm - 0.000001)
                    assertTrue(line.baselineMm + run.typography.metrics.descentMm <=
                        line.bounds.yMm + line.bounds.heightMm + 0.000001)
                }
            }
        }
        val rasters = plan.operations.filterIsInstance<RasterText>()
        assertEquals(setOf(0, 1, 2), rasters.map { it.sourceBlockIndex }.toSet())
        assertTrue(rasters.count { it.sourceBlockIndex == 2 } > 3, "Narrow table must wrap")
        assertTrue(rasters.any { "NotoSans-Regular@" in it.fontIdentity && "JetBrainsMono-Regular@" in it.fontIdentity })
        assertTrue(rasters.any { "NotoSans-Bold@" in it.fontIdentity && "JetBrainsMono-Bold@" in it.fontIdentity })
        assertTrue(rasters.all { it.backendIdentity == actual.identity && it.bounds.right <= plan.target.printableWidthDots })
        val preview = assertIs<PrintPreviewResult.Success>(plan.toPrintPreview(
            PhysicalPreviewResourcePolicy(preferredTileHeightDots = 7))).preview
        val seen = mutableSetOf<RasterText>()
        repeat(preview.tileCount) { index ->
            val tile = assertIs<PrintPreviewTileResult.Success>(preview.tile(index)).tile
            for (primitive in tile.primitives.filterIsInstance<MonochromePrimitive>()) {
                val raster = assertIs<RasterText>(primitive.operation)
                assertSame(rasters.single { it === raster }.graphic, primitive.graphic)
                assertEquals(PhysicalPreviewAccuracy.EXACT_PREPARED_DOTS, primitive.accuracy)
                seen += raster
            }
        }
        assertEquals(rasters.toSet(), seen)
        val output = mutableListOf<Byte>()
        assertIs<EncodingResult.Success>(EscPosEncoder(EncodingResourcePolicy(maxChunkBytes = 7))
            .encode(plan, EncodedByteSink { output.addAll(it.toList()); true }))
        var offset = 0
        for (raster in rasters) {
            val graphic = raster.graphic
            val frame = byteArrayOf(0x1d, 0x76, 0x30, 0, (graphic.rowBytes and 255).toByte(),
                (graphic.rowBytes ushr 8).toByte(), (graphic.heightDots and 255).toByte(),
                (graphic.heightDots ushr 8).toByte()) + graphic.copyBytes()
            val position = (offset..output.size - frame.size).firstOrNull { start ->
                frame.indices.all { output[start + it] == frame[it] }
            }
            assertNotNull(position, "Encoder must use the exact preview artifact in order")
            offset = position + frame.size
        }
    }

    @Test fun unsupportedMusicFailsAtomicallyWithCodePointAndBlockLocation() {
        for ((symbol, code) in listOf("🎵" to "U+1F3B5", "🎼" to "U+1F3BC")) {
            val document = ThermalDocument(blocks = listOf(
                Paragraph(Alignment.LEFT, listOf(Text("Supported ↑"))),
                Paragraph(Alignment.LEFT, listOf(Text("private $symbol suffix"))),
            ))
            val failure = assertIs<PreparationResult.Failure>(BasicPreparationEngine(
                rasterTextBackend = BundledRasterTextBackend.create()).prepare(document, SyntheticReferenceProfiles.narrow))
            val diagnostic = failure.diagnostics.single { code in it.message }
            assertEquals(1, diagnostic.sourceBlockIndex)
            assertFalse("private" in diagnostic.message || "suffix" in diagnostic.message)
        }
    }
}
