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
import com.circuitnext.rastrio.core.printer.*
import com.circuitnext.rastrio.core.preview.*
import com.circuitnext.rastrio.core.profile.*
import com.circuitnext.rastrio.core.raster.*
import com.circuitnext.rastrio.presentation.createAuthoringSession
import com.circuitnext.rastrio.presentation.semanticDocument
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class RasterPipelineTest {
    private val reference=EmbeddedAssetReference("fixture-image")
    private val profile=SyntheticReferenceProfiles.rasterOnly.copy(raster=SyntheticReferenceProfiles.rasterOnly.raster.copy(bandHeightDots=RasterBandHeight(1,2,3)))
    private val pixels=IntArray(54){val v=(it%9*29+it/9*17)%256;(0xff000000L or v.toLong()*0x010101).toInt()}
    private fun plan(algorithm: DitherAlgorithm = DitherAlgorithm.THRESHOLD): PreparedPrint = assertIs<PreparationResult.Success>(
        BasicPreparationEngine().prepare(ThermalDocument(blocks=listOf(Image(reference,Alignment.LEFT,
            RequestedWidthSizing(Length(9.0/203*25.4))))),profile,
            resolvedImages=mapOf(reference to ResolvedRasterImage(DecodedImage(9,6,pixels),RasterOptions(9,6,algorithm=algorithm))))).print
    private fun hex(bytes: ByteArray)=bytes.joinToString(""){(it.toInt() and 255).toString(16).padStart(2,'0')}
    private suspend fun encode(print: PreparedPrint, chunk: Int = 65536): ByteArray {
        val sink=InMemoryByteSink()
        assertIs<EncodingResult.Success>(EscPosEncoder(EncodingResourcePolicy(maxChunkBytes=chunk)).encode(print,sink))
        return sink.bytes()
    }
    private fun instance(print: PreparedPrint)=PrinterInstance("image","Image fixture",PrinterTransportKind.DEVELOPMENT,"fixture",
        print.target.profileId,print.target.profileRevision)

    @Test fun allDitheringGoldensAreTheExactRasterPayloadAndProtocolFrames() = runTest {
        val goldens=mapOf(
            DitherAlgorithm.THRESHOLD to "f800f000f080e080e180c380",
            DitherAlgorithm.BAYER to "d000ea00d480e8805100a380",
            DitherAlgorithm.ATKINSON to "f000ec00e0809080d180c380",
            DitherAlgorithm.FLOYD_STEINBERG to "f400d200e880a480a980a380",
        )
        for((algorithm,payload) in goldens) {
            val print=plan(algorithm)
            val expected="1b401d50cbcb1d4c00001d5780011b61001b4a01"+
                payload.chunked(8).joinToString(""){"1d4c00001d76300002000200${it}1d4c0000"}+"1b4a031b4a01"
            assertEquals(payload,print.operations.filterIsInstance<RasterOperation>().joinToString(""){hex(it.graphic.copyBytes())})
            for(chunk in listOf(1,3,17,65536)) assertEquals(expected,hex(encode(print,chunk)),"$algorithm/$chunk")
        }
    }
    @Test fun previewAndEncoderConsumeTheSameImmutablePreparedBands() = runTest {
        val print=plan(DitherAlgorithm.ATKINSON)
        val expected=encode(print)
        val preview=assertIs<PrintPreviewResult.Success>(print.toPrintPreview(PhysicalPreviewResourcePolicy(preferredTileHeightDots=3))).preview
        val originals=print.operations.filterIsInstance<RasterOperation>()
        repeat(preview.tileCount){index->
            val tile=assertIs<PrintPreviewTileResult.Success>(preview.tile(index)).tile
            for(primitive in tile.primitives.filterIsInstance<MonochromePrimitive>()) {
                val original=originals.single{it === primitive.operation}
                assertSame(original.graphic,primitive.graphic)
                val disposable=primitive.graphic.copyBytes();disposable.fill(0)
                assertTrue(primitive.graphic.copyBytes().any{it.toInt()!=0})
            }
        }
        assertContentEquals(expected,encode(print))
    }
    @Test fun fakeImagePrintingUsesTheSpecificPreviewedPlanWithSmallTransportWrites() = runTest {
        val print=plan(DitherAlgorithm.FLOYD_STEINBERG)
        val preview=assertIs<PrintPreviewResult.Success>(print.toPrintPreview()).preview
        val expected=encode(print)
        val fake=FakePrinterTransport(TransportConstraints(maxWriteBytes=3))
        val session=PrintSession(fake)
        session.print(instance(print),print)
        assertEquals(PrintSessionPhase.TRANSMITTED_UNCONFIRMED,session.state.value.phase)
        assertContentEquals(expected,fake.capturedBytes())
        assertEquals(3,fake.largestWrite)
        val tile=assertIs<PrintPreviewTileResult.Success>(preview.tile(0)).tile
        assertTrue(tile.primitives.all{primitive->print.operations.any{it===primitive.operation}})
        assertTrue(session.state.value.outputMayExist)
        assertFalse(fake.connected)
    }
    @Test fun explicitMarkdownImageResolutionFeedsTheSamePreviewAndFakePrintPath() = runTest {
        val authoring=createAuthoringSession()
        authoring.edit("![fixture](https://invalid.example/image.png)")
        authoring.compile()
        val document=assertNotNull(authoring.state.result.semanticDocument())
        val image=document.blocks.filterIsInstance<Image>().single()
        assertIs<PreparationResult.Failure>(BasicPreparationEngine().prepare(document,profile))
        val print=assertIs<PreparationResult.Success>(BasicPreparationEngine().prepare(document,profile,
            resolvedImages=mapOf(image.asset to ResolvedRasterImage(DecodedImage(9,6,pixels))))).print
        val preview=assertIs<PrintPreviewResult.Success>(print.toPrintPreview()).preview
        val expected=encode(print)
        val fake=FakePrinterTransport()
        PrintSession(fake).print(instance(print),print)
        assertContentEquals(expected,fake.capturedBytes())
        assertTrue(assertIs<PrintPreviewTileResult.Success>(preview.tile(0)).tile.primitives.any{it is MonochromePrimitive&&it.kind==PreparedGraphicKind.IMAGE})
    }
    @Test fun sourceAndResolutionMapCanDisappearAfterPreparation() = runTest {
        val values=pixels.copyOf()
        val source=DecodedImage(9,6,values)
        val resolution=mutableMapOf<AssetReference,ResolvedRasterImage>(reference to ResolvedRasterImage(source))
        val print=assertIs<PreparationResult.Success>(BasicPreparationEngine().prepare(
            ThermalDocument(blocks=listOf(Image(reference,Alignment.LEFT,RequestedWidthSizing(Length(9.0/203*25.4))))),profile,
            resolvedImages=resolution)).print
        val expected=encode(print)
        values.fill(-1);resolution.clear()
        val preview=assertIs<PrintPreviewResult.Success>(print.toPrintPreview()).preview
        assertIs<PrintPreviewTileResult.Success>(preview.tile(0))
        assertContentEquals(expected,encode(print))
    }
    @Test fun failedImageDeliveryNeverAutomaticallyReplaysTheFinalizedPlan() = runTest {
        val print=plan()
        val fake=FakePrinterTransport(failAfterBytes=32)
        val session=PrintSession(fake)
        session.print(instance(print),print)
        assertEquals(PrintSessionPhase.FAILED_PARTIAL_OR_AMBIGUOUS,session.state.value.phase)
        assertTrue(session.state.value.outputMayExist)
        val transferred=fake.byteCount
        session.print(instance(print),print)
        assertEquals(1,fake.connections)
        assertEquals(transferred,fake.byteCount)
    }
    @Test fun commandLikeRasterBytesRemainBoundedBinaryPayload() = runTest {
        val values=intArrayOf(0x1b,0x1d,0x40)
        val source=DecodedImage(24,1,IntArray(24){if(values[it/8] and (128 ushr(it%8))!=0)0xff000000.toInt() else -1})
        val print=assertIs<PreparationResult.Success>(BasicPreparationEngine().prepare(
            ThermalDocument(blocks=listOf(Image(reference,Alignment.LEFT,RequestedWidthSizing(Length(24.0/203*25.4))))),
            profile,resolvedImages=mapOf(reference to ResolvedRasterImage(source)))).print
        assertTrue(hex(encode(print,1)).contains("1d763000030001001b1d40"))
        assertContentEquals(byteArrayOf(0x1b,0x1d,0x40),print.operations.filterIsInstance<RasterOperation>().single().graphic.copyBytes())
    }
}
