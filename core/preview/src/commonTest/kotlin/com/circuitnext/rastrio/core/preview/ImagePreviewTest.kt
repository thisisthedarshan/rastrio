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
import com.circuitnext.rastrio.core.raster.*
import kotlin.test.*

class ImagePreviewTest {
    private fun print(): PreparedPrint {
        val reference=EmbeddedAssetReference("selected")
        val source=DecodedImage(9,6,IntArray(54){if(it%2==0)0xff000000.toInt() else -1})
        return assertIs<PreparationResult.Success>(BasicPreparationEngine().prepare(
            ThermalDocument(blocks=listOf(Image(reference,Alignment.LEFT,RequestedWidthSizing(Length(9.0/203*25.4))))),
            SyntheticReferenceProfiles.rasterOnly,resolvedImages=mapOf(reference to ResolvedRasterImage(source)))).print
    }
    @Test fun tilesReadOriginalFinalizedImageObjectAndExactBytes() {
        val plan=print()
        val original=plan.operations.filterIsInstance<RasterOperation>().single()
        val preview=assertIs<PrintPreviewResult.Success>(plan.toPrintPreview(PhysicalPreviewResourcePolicy(preferredTileHeightDots=3))).preview
        val encountered=mutableListOf<MonochromePrimitive>()
        repeat(preview.tileCount){index->
            val tile=assertIs<PrintPreviewTileResult.Success>(preview.tile(index)).tile
            encountered.addAll(tile.primitives.filterIsInstance<MonochromePrimitive>())
        }
        assertTrue(encountered.size>=2)
        for(primitive in encountered) {
            assertSame(original,primitive.operation)
            assertSame(original.graphic,primitive.graphic)
            assertEquals(PreparedGraphicKind.IMAGE,primitive.kind)
            assertEquals(PhysicalPreviewAccuracy.EXACT_PREPARED_DOTS,primitive.accuracy)
            assertContentEquals(original.graphic.copyBytes(),primitive.graphic.copyBytes())
        }
    }
    @Test fun previewReadCopiesCannotChangePreparedOrFutureTiles() {
        val plan=print()
        val original=plan.operations.filterIsInstance<RasterOperation>().single()
        val bytes=original.graphic.copyBytes()
        val baseline=bytes.copyOf(); bytes.fill(0)
        val preview=assertIs<PrintPreviewResult.Success>(plan.toPrintPreview()).preview
        repeat(2){
            val primitive=assertIs<PrintPreviewTileResult.Success>(preview.tile(0)).tile.primitives.filterIsInstance<MonochromePrimitive>().single()
            assertContentEquals(baseline,primitive.graphic.copyBytes())
        }
    }
    @Test fun qrTilesRetainOriginalArtifactsAndDeclareNativeMatrixAccuracy() {
        for (strategy in listOf(QrStrategy.NATIVE, QrStrategy.RASTER)) {
            val plan = assertIs<PreparationResult.Success>(BasicPreparationEngine().prepare(
                ThermalDocument(blocks = listOf(QrCode("A", Alignment.CENTER, QrErrorCorrection.HIGH,
                    Length(58.0 / 203 * 25.4)))), SyntheticReferenceProfiles.wide80mm,
                PrintOptions(qrStrategy = strategy))).print
            val preview = assertIs<PrintPreviewResult.Success>(plan.toPrintPreview(
                PhysicalPreviewResourcePolicy(preferredTileHeightDots = 7))).preview
            val primitives = (0 until preview.tileCount).flatMap {
                assertIs<PrintPreviewTileResult.Success>(preview.tile(it)).tile.primitives.filterIsInstance<MonochromePrimitive>()
            }
            assertTrue(primitives.size > 1)
            for (primitive in primitives) {
                assertTrue(plan.operations.any { it === primitive.operation })
                when (val original = primitive.operation) {
                    is NativeQr -> {
                        assertSame(original.representativeGraphic, primitive.graphic)
                        assertEquals(PreparedGraphicKind.NATIVE_QR, primitive.kind)
                        assertEquals(PhysicalPreviewAccuracy.EXACT_GEOMETRY_REPRESENTATIVE_QR, primitive.accuracy)
                    }
                    is RasterQr -> {
                        assertSame(original.graphic, primitive.graphic)
                        assertEquals(PreparedGraphicKind.RASTER_QR, primitive.kind)
                        assertEquals(PhysicalPreviewAccuracy.EXACT_PREPARED_DOTS, primitive.accuracy)
                    }
                    else -> error("Unexpected QR primitive")
                }
                val before = primitive.graphic.copyBytes()
                primitive.graphic.copyBytes().fill(0)
                assertContentEquals(before, primitive.graphic.copyBytes())
                assertTrue(primitive.clippedBounds.y >= primitive.bounds.y)
                assertTrue(primitive.clippedBounds.bottom <= primitive.bounds.bottom)
            }
        }
    }

}
