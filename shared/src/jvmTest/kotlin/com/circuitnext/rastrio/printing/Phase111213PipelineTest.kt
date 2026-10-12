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
import com.circuitnext.rastrio.core.preview.*
import com.circuitnext.rastrio.core.printer.*
import com.circuitnext.rastrio.core.profile.*
import com.circuitnext.rastrio.core.templates.*
import com.circuitnext.rastrio.core.text.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class Phase111213PipelineTest {
    private class ObservedBackend(private val actual: RasterTextBackend) : RasterTextBackend by actual {
        var measurements = 0
        var rasters = 0
        override fun measure(request: TextMeasureRequest): TextMeasureResult {
            measurements++
            return actual.measure(request)
        }
        override fun measure(request: TextMeasureRequest, isCancelled: () -> Boolean): TextMeasureResult {
            measurements++
            return actual.measure(request, isCancelled)
        }
        override fun rasterize(request: RasterTextRequest): RasterTextResult {
            rasters++
            return actual.rasterize(request)
        }
    }

    private suspend fun encode(plan: PreparedPrint): ByteArray {
        val output = mutableListOf<Byte>()
        val result = EscPosEncoder(EncodingResourcePolicy(maxChunkBytes = 7)).encode(plan,
            EncodedByteSink { output.addAll(it.toList()); true })
        assertEquals(output.size.toLong(), assertIs<EncodingResult.Success>(result).bytesEmitted)
        return output.toByteArray()
    }

    private fun previewArtifacts(plan: PreparedPrint) {
        val preview = assertIs<PrintPreviewResult.Success>(plan.toPrintPreview(
            PhysicalPreviewResourcePolicy(preferredTileHeightDots = 7))).preview
        val seen = mutableSetOf<PreparedOperation>()
        repeat(preview.tileCount) { index ->
            val tile = assertIs<PrintPreviewTileResult.Success>(preview.tile(index)).tile
            for (primitive in tile.primitives.filterIsInstance<MonochromePrimitive>()) {
                assertTrue(plan.operations.any { it === primitive.operation })
                val original = when (val operation = primitive.operation) {
                    is PreparedRasterGraphic -> operation.graphic
                    is NativeQr -> operation.representativeGraphic
                    is com.circuitnext.rastrio.core.printer.Separator -> operation.graphic
                    is ManualCutGuide -> operation.graphic
                    else -> error("Unexpected monochrome operation")
                }
                assertSame(original, primitive.graphic)
                assertContentEquals(original.copyBytes(), primitive.graphic.copyBytes())
                assertEquals(if (primitive.operation is NativeQr)
                    PhysicalPreviewAccuracy.EXACT_GEOMETRY_REPRESENTATIVE_QR
                    else PhysicalPreviewAccuracy.EXACT_PREPARED_DOTS, primitive.accuracy)
                seen += primitive.operation
            }
        }
        for (operation in plan.operations) if (operation is PreparedRasterGraphic || operation is NativeQr)
            assertTrue(operation in seen, "Prepared graphic must reach physical preview")
    }

    @Test fun mixedRasterTextAndQrConsumeOneFinalizedPlanWithoutBackendCalls() = runTest {
        val backend = ObservedBackend(BundledRasterTextBackend.create())
        val document = ThermalDocument(blocks = listOf(
            Paragraph(Alignment.LEFT, listOf(Text("ASCII native"))),
            Paragraph(Alignment.CENTER, listOf(Text("Mixed हिन्दी e\u0301 ő"))),
            QrCode("https://example.invalid/हिन्दी", Alignment.RIGHT, QrErrorCorrection.HIGH,
                Length(25.0)),
        ))
        val plan = assertIs<PreparationResult.Success>(BasicPreparationEngine(rasterTextBackend = backend)
            .prepare(document, SyntheticReferenceProfiles.narrow, PrintOptions(qrStrategy = QrStrategy.RASTER))).print
        assertTrue(plan.operations.any { it is NativeText })
        assertTrue(plan.operations.any { it is RasterText })
        assertTrue(plan.operations.any { it is RasterQr })
        assertTrue(backend.measurements > 0)
        assertTrue(backend.rasters > 0)
        val counts = backend.measurements to backend.rasters
        val before = encode(plan)
        previewArtifacts(plan)
        assertContentEquals(before, encode(plan))
        assertEquals(counts, backend.measurements to backend.rasters)
        var offset = 0
        for (operation in plan.operations.filterIsInstance<PreparedRasterGraphic>()) {
            val graphic = operation.graphic
            val frame = byteArrayOf(0x1d, 0x76, 0x30, 0, (graphic.rowBytes and 255).toByte(),
                (graphic.rowBytes ushr 8).toByte(), (graphic.heightDots and 255).toByte(),
                (graphic.heightDots ushr 8).toByte()) + graphic.copyBytes()
            val position = (offset..before.size - frame.size).firstOrNull { start ->
                frame.indices.all { before[start + it] == frame[it] }
            }
            assertNotNull(position, "Encoder must serialize the exact finalized band in operation order")
            offset = position + frame.size
        }
    }

    @Test fun allFiveSemanticTemplatesReachExistingPreparationPreviewAndEncoder() = runTest {
        val backend = ObservedBackend(BundledRasterTextBackend.create())
        val states = listOf(
            TodoState("Tasks", listOf(TodoItem("हिन्दी", true), TodoItem("Second", false))),
            ShoppingListState("Shopping", listOf(ShoppingItem("दूध", "2 L"))),
            QuickNoteState("Note", "First line\nहिन्दी e\u0301"),
            MemoState("Memo", "Team", "हिन्दी memo", "Author"),
            QrMessageState("A", "हिन्दी caption", QrErrorCorrection.HIGH, Length(58.0 / 203 * 25.4)),
        )
        for (profile in listOf(SyntheticReferenceProfiles.narrow, SyntheticReferenceProfiles.wide80mm)) {
            for (state in states) {
                val built = TemplateDocumentBuilder.build(state)
                assertTrue(built.diagnostics.isEmpty())
                val document = assertNotNull(built.document)
                val prepared = BasicPreparationEngine(rasterTextBackend = backend).prepare(document, profile)
                val plan = assertIs<PreparationResult.Success>(prepared,
                    "${state::class.simpleName} / ${profile.profileId}: " +
                        ((prepared as? PreparationResult.Failure)?.diagnostics ?: "unexpected result")).print
                assertTrue(plan.operations.any { it is RasterText }, "Unicode template content needs real raster text")
                val counts = backend.measurements to backend.rasters
                val encoded = encode(plan)
                previewArtifacts(plan)
                assertContentEquals(encoded, encode(plan))
                assertEquals(counts, backend.measurements to backend.rasters)
                if (state is QrMessageState) assertTrue(plan.operations.any {
                    if (profile === SyntheticReferenceProfiles.wide80mm) it is NativeQr else it is RasterQr
                })
            }
        }
    }

    @Test fun qrTemplateHonorsForcedStrategiesWhileRetainingRealRasterCaption() = runTest {
        val backend = ObservedBackend(BundledRasterTextBackend.create())
        val document = assertNotNull(TemplateDocumentBuilder.build(QrMessageState("A", "हिन्दी",
            QrErrorCorrection.HIGH, Length(58.0 / 203 * 25.4))).document)
        for (strategy in listOf(QrStrategy.NATIVE, QrStrategy.RASTER)) {
            val plan = assertIs<PreparationResult.Success>(BasicPreparationEngine(rasterTextBackend = backend)
                .prepare(document, SyntheticReferenceProfiles.wide80mm, PrintOptions(qrStrategy = strategy))).print
            assertTrue(plan.operations.any { if (strategy == QrStrategy.NATIVE) it is NativeQr else it is RasterQr })
            assertTrue(plan.operations.any { it is RasterText })
            val counts = backend.measurements to backend.rasters
            val before = encode(plan)
            previewArtifacts(plan)
            assertContentEquals(before, encode(plan))
            assertEquals(counts, backend.measurements to backend.rasters)
        }
    }

    @Test fun consecutiveQrSymbolsRetainOrderSizesAndFinalArtifactsBetweenText() = runTest {
        val backend = ObservedBackend(BundledRasterTextBackend.create())
        val document = ThermalDocument(blocks = listOf(
            Paragraph(Alignment.LEFT, listOf(Text("Before"))),
            QrCode("first", Alignment.LEFT, QrErrorCorrection.LOW, Length(12.0)),
            QrCode("middle", Alignment.CENTER, QrErrorCorrection.MEDIUM, Length(20.0)),
            QrCode("last", Alignment.RIGHT, QrErrorCorrection.HIGH, Length(30.0)),
            Paragraph(Alignment.LEFT, listOf(Text("हिन्दी after"))),
        ))
        for (strategy in QrStrategy.entries) {
            val plan = assertIs<PreparationResult.Success>(BasicPreparationEngine(rasterTextBackend = backend)
                .prepare(document, SyntheticReferenceProfiles.wide80mm, PrintOptions(qrStrategy = strategy))).print
            val symbols = plan.operations.filter { it is NativeQr || it is RasterQr }
                .groupBy { it.sourceBlockIndex }
            assertEquals(listOf(1, 2, 3), symbols.keys.toList())
            assertEquals(listOf(87, 145, 232), symbols.values.map { it.first().bounds.width })
            assertEquals(listOf("L", "M", "H"), symbols.values.map {
                when (val operation = it.first()) {
                    is NativeQr -> operation.errorCorrection
                    is RasterQr -> operation.errorCorrection
                    else -> error("Expected finalized QR")
                }
            })
            assertTrue(symbols.values.zipWithNext().all { (a, b) -> a.last().bounds.bottom <= b.first().bounds.y })
            assertEquals(if (strategy == QrStrategy.RASTER) 0 else 3, plan.operations.count { it is NativeQr })
            val counts = backend.measurements to backend.rasters
            val encoded = encode(plan)
            previewArtifacts(plan)
            assertContentEquals(encoded, encode(plan))
            assertEquals(counts, backend.measurements to backend.rasters)
        }
    }
}
