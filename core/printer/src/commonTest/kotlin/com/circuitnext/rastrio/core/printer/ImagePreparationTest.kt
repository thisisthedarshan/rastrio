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
import com.circuitnext.rastrio.core.layout.LogicalImagePlaceholder
import com.circuitnext.rastrio.core.profile.*
import com.circuitnext.rastrio.core.raster.*
import kotlin.test.*

class ImagePreparationTest {
    private val reference = EmbeddedAssetReference("image")
    private val source = DecodedImage(9, 6, IntArray(54) { val v=(it%9*29+it/9*17)%256; (0xff000000L or v.toLong()*0x010101).toInt() })
    private val profile = SyntheticReferenceProfiles.rasterOnly.copy(raster=SyntheticReferenceProfiles.rasterOnly.raster.copy(bandHeightDots=RasterBandHeight(1,2,3)))
    private fun document(width: Int = 9, reference: AssetReference = this.reference, alignment: Alignment = Alignment.LEFT) =
        ThermalDocument(blocks=listOf(Image(reference,alignment,RequestedWidthSizing(Length(width.toDouble()/203*25.4)))))
    private fun prepare(document: ThermalDocument = document(), source: DecodedImage = this.source,
        options: RasterOptions = RasterOptions(source.width,source.height), profile: PrinterProfile = this.profile,
        resources: PreparationResourcePolicy = PreparationResourcePolicy()) = BasicPreparationEngine(resources).prepare(
        document,profile,resolvedImages=mapOf(reference to ResolvedRasterImage(source,options)))
    private fun print(result: PreparationResult) = assertIs<PreparationResult.Success>(result).print
    private fun code(result: PreparationResult) = assertIs<PreparationResult.Failure>(result).diagnostics.first().code

    @Test fun rasterOnlyProfileNeedsNoInventedNativeFontAndKeepsSquareLogicalFlow() {
        val plan=print(prepare())
        assertFalse(plan.operations.any{it is NativeText})
        val logical=assertIs<LogicalImagePlaceholder>(plan.logicalDocument.blocks.single())
        assertEquals(logical.bounds.widthMm,logical.bounds.heightMm)
        assertEquals(listOf(DotBounds(0,1,9,2),DotBounds(0,3,9,2),DotBounds(0,5,9,2)),plan.operations.filterIsInstance<RasterOperation>().map{it.bounds})
        assertEquals(listOf(0,1,2),plan.operations.filterIsInstance<RasterOperation>().map{it.bandOrdinal})
        assertEquals(11,plan.heightDots)
        assertEquals(listOf(1,3,1),plan.operations.filterIsInstance<Feed>().map{it.advanceDots})
    }
    @Test fun allAlgorithmFinalizedBytesAreRepeatableAndBandsRespectProfile() {
        for(algorithm in DitherAlgorithm.entries) {
            val first=print(prepare(options=RasterOptions(9,6,algorithm=algorithm))).operations.filterIsInstance<RasterOperation>()
            val second=print(prepare(options=RasterOptions(9,6,algorithm=algorithm))).operations.filterIsInstance<RasterOperation>()
            assertEquals(first.map{it.bounds},second.map{it.bounds})
            for((a,b) in first.zip(second)) {
                assertEquals("escpos.raster.gs-v-0",a.strategy)
                assertContentEquals(a.graphic.copyBytes(),b.graphic.copyBytes())
                assertTrue(a.graphic.heightDots in 1..3)
                assertEquals(0,a.graphic.copyBytes().last().toInt() and 127)
            }
        }
    }
    @Test fun explicitResolutionIsRequiredForEmbeddedAndRemoteImages() {
        for(ref in listOf(reference,ExternalAssetReference("https://invalid.example/private.png"),ExternalAssetReference("file:///private.png"))) {
            assertEquals("PRN104",code(BasicPreparationEngine().prepare(document(reference=ref),profile)))
        }
        val ref=ExternalAssetReference("https://invalid.example/image.png")
        assertIs<PreparationResult.Success>(BasicPreparationEngine().prepare(document(reference=ref),profile,
            resolvedImages=mapOf(ref to ResolvedRasterImage(source))))
    }
    @Test fun cropAndExifDetermineAspectInsideFinalizedOuterBox() {
        val cropped=print(prepare(options=RasterOptions(9,6,crop=ImageCrop(0,0,3,6))))
        assertEquals(4,cropped.operations.filterIsInstance<RasterOperation>().first().bounds.width)
        assertEquals(9,cropped.operations.filterIsInstance<RasterOperation>().sumOf{it.bounds.height})
        val rotated=print(prepare(options=RasterOptions(9,6,orientation=6)))
        assertEquals(6,rotated.operations.filterIsInstance<RasterOperation>().first().bounds.width)
        assertEquals(9,rotated.operations.filterIsInstance<RasterOperation>().sumOf{it.bounds.height})
        assertEquals(11,rotated.heightDots)
    }
    @Test fun innerImageAlignmentIsResolvedBeforeConsumers() {
        for((alignment,x) in listOf(Alignment.LEFT to 0,Alignment.CENTER to 190,Alignment.RIGHT to 380)) {
            val plan=print(prepare(document=document(alignment=alignment),options=RasterOptions(9,6,crop=ImageCrop(0,0,3,6))))
            assertEquals(x,plan.operations.filterIsInstance<RasterOperation>().first().bounds.x)
        }
    }
    @Test fun nativeTextAndExplicitImagesShareOnePhysicalPlan() {
        val input=ThermalDocument(blocks=listOf(Paragraph(Alignment.LEFT,listOf(Text("A"))),document().blocks.single(),Paragraph(Alignment.LEFT,listOf(Text("B")))))
        val plan=print(prepare(document=input,profile=SyntheticReferenceProfiles.narrow))
        assertEquals(listOf("A","B"),plan.operations.filterIsInstance<NativeText>().map{it.text})
        assertTrue(plan.operations.any{it is RasterOperation})
        assertTrue(plan.operations.zipWithNext().all{(a,b)->a.bounds.y<=b.bounds.y})
    }
    @Test fun rasterUnavailableAndNativeTextOnRasterOnlyFailExplicitly() {
        assertEquals("PRN104",code(prepare(profile=SyntheticReferenceProfiles.narrow.copy(raster=RasterCapability(false)))))
        assertEquals("PRN101",code(prepare(document=ThermalDocument(blocks=listOf(Paragraph(Alignment.LEFT,listOf(Text("A"))))))))
        assertEquals("PRN101",code(BasicPreparationEngine().prepare(document(),profile,PrintOptions(nativeFontId="font-a"),resolvedImages=mapOf(reference to ResolvedRasterImage(source)))))
    }
    @Test fun invalidCropAdjustmentAndOverflowsFailWithoutAPartialPlan() {
        for(options in listOf(RasterOptions(9,6,orientation=9),RasterOptions(9,6,crop=ImageCrop(Int.MAX_VALUE,0,Int.MAX_VALUE,1)),RasterOptions(9,6,gammaHundredths=0))) {
            assertEquals("PRN100",code(prepare(options=options)))
        }
        assertEquals("PRN120",code(prepare(resources=PreparationResourcePolicy(maxHeightDots=10))))
    }
    @Test fun residentPayloadBandAndOperationBudgetsArePreserved() {
        assertIs<PreparationResult.Success>(prepare(resources=PreparationResourcePolicy(maxResidentGraphicBytes=12,maxPreparedGraphicBytes=12,maxGraphicBandBytes=2)))
        assertEquals("PRN120",code(prepare(resources=PreparationResourcePolicy(maxResidentGraphicBytes=11))))
        assertEquals("PRN120",code(prepare(resources=PreparationResourcePolicy(maxPreparedGraphicBytes=11))))
        assertEquals("PRN120",code(prepare(resources=PreparationResourcePolicy(maxGraphicBandBytes=1))))
        assertEquals("PRN120",code(prepare(resources=PreparationResourcePolicy(maxOperations=3))))
        val repeated=ThermalDocument(blocks=listOf(document().blocks.single(),document().blocks.single()))
        assertEquals("PRN120",code(prepare(document=repeated,resources=PreparationResourcePolicy(maxResidentGraphicBytes=20))))
    }
    @Test fun cancellationBeforeAndDuringPreparationDiscardsPartialBands() {
        assertEquals("PRN130",code(BasicPreparationEngine().prepare(document(),profile,isCancelled={true})))
        var polls=0
        val result=BasicPreparationEngine().prepare(document(384),profile,
            resolvedImages=mapOf(reference to ResolvedRasterImage(source)),isCancelled={++polls>20})
        assertEquals("PRN130",code(result))
    }
    @Test fun invalidFinalizedContentCannotEnterAPreparedArtifact() {
        assertFailsWith<IllegalArgumentException>{PreparedMonochrome(9,1,byteArrayOf(0))}
        assertFailsWith<IllegalArgumentException>{PreparedMonochrome(9,1,byteArrayOf(0,1))}
        assertFailsWith<IllegalArgumentException>{RasterOperation(DotBounds(0,0,8,1),PreparedMonochrome(9,1,byteArrayOf(0,0)),"escpos.raster.gs-v-0",0)}
        assertFailsWith<IllegalArgumentException>{RasterOperation(DotBounds(0,0,9,1),PreparedMonochrome(9,1,byteArrayOf(0,0)),"escpos.raster.gs-v-0",-1)}
    }
    @Test fun duplicateOrMissingBandIdentityCannotEnterAPreparedPlan() {
        val plan=print(prepare())
        val operations=plan.operations.map{operation->
            if(operation is RasterOperation) RasterOperation(operation.bounds,operation.graphic,operation.strategy,0,operation.sourceBlockIndex)
            else operation
        }
        assertFailsWith<IllegalArgumentException>{PreparedPrint(plan.logicalDocument,plan.target,
            com.circuitnext.rastrio.core.text.SnapshotList(operations),plan.diagnostics,plan.heightDots)}
    }
    @Test fun verticalDpiResolvesPhysicalAspectWithoutChangingLogicalSquare() {
        val nonSquare=profile.copy(geometry=profile.geometry.copy(verticalDpi=127))
        val plan=print(prepare(profile=nonSquare))
        val logical=assertIs<LogicalImagePlaceholder>(plan.logicalDocument.blocks.single())
        assertEquals(logical.bounds.widthMm,logical.bounds.heightMm)
        assertEquals(4,plan.operations.filterIsInstance<RasterOperation>().sumOf{it.bounds.height})
    }
}
