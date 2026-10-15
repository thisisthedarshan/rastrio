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

package com.circuitnext.rastrio.presentation

import com.circuitnext.rastrio.core.document.*
import com.circuitnext.rastrio.core.escpos.*
import com.circuitnext.rastrio.core.printer.*
import com.circuitnext.rastrio.core.preview.*
import com.circuitnext.rastrio.core.profile.SyntheticReferenceProfiles
import com.circuitnext.rastrio.core.raster.*
import com.circuitnext.rastrio.printing.*
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class ImagePrintSessionTest {
    private val source = DecodedImage(3, 2, intArrayOf(-1, 0xff000000.toInt(), -1, 0xff404040.toInt(), -1, 0xff808080.toInt()))
    private fun ready(session: ImagePrintSession) = assertIs<PhysicalPreviewResult.Ready>(session.state.result)

    @Test fun preparationIsExplicitAndUnchangedInputsKeepTheSamePlan() {
        val session = ImagePrintSession()
        session.select(ImageInput(source))
        session.edit(session.state.edits.copy(widthDots = "17"))
        assertSame(PhysicalPreviewResult.NotPrepared, session.state.result)
        session.prepare()
        val first = ready(session)
        assertTrue(first.print.operations.any { it is RasterOperation })
        session.prepare()
        assertSame(first, session.state.result)
        val tile = assertIs<PrintPreviewTileResult.Success>(first.preview.tile(0)).tile
        for (primitive in tile.primitives) assertTrue(first.print.operations.any { it === primitive.operation })
    }

    private fun rasterBytes(session: ImagePrintSession): ByteArray = ready(session).print.operations
        .filterIsInstance<RasterOperation>().flatMap { it.graphic.copyBytes().toList() }.toByteArray()

    @Test fun adjustmentsChangeFinalPixelsAndResetAlwaysUsesTheOriginalSource() {
        val pixels = IntArray(54) {
            val value = (it % 9 * 29 + it / 9 * 17) % 256
            (0xff000000L or value.toLong() * 0x010101).toInt()
        }
        val source = DecodedImage(9, 6, pixels)
        val session = ImagePrintSession().also { it.select(ImageInput(source)) }
        val defaults = ImagePrintEdits(widthDots = "9", threshold = 90)
        session.edit(defaults)
        session.prepare()
        val initial = rasterBytes(session)
        for (edit in listOf(defaults.copy(brightness = 70), defaults.copy(contrastPercent = 200),
            defaults.copy(gammaHundredths = 180), defaults.copy(threshold = 190))) {
            session.edit(edit)
            session.prepare()
            assertFalse(initial.contentEquals(rasterBytes(session)), "$edit must change finalized pixels")
            session.edit(defaults)
            session.prepare()
            assertContentEquals(initial, rasterBytes(session))
        }
        val outputs = DitherAlgorithm.entries.map { algorithm ->
            session.edit(defaults.copy(algorithm = algorithm))
            session.prepare()
            rasterBytes(session).toList()
        }
        assertEquals(DitherAlgorithm.entries.size, outputs.toSet().size)
        session.edit(defaults.copy(algorithm = DitherAlgorithm.FLOYD_STEINBERG))
        session.prepare()
        val first = rasterBytes(session)
        session.edit(defaults.copy(algorithm = DitherAlgorithm.BAYER))
        session.prepare()
        session.edit(defaults.copy(algorithm = DitherAlgorithm.FLOYD_STEINBERG))
        session.prepare()
        assertContentEquals(first, rasterBytes(session))
        assertSame(source, session.state.input!!.image)
        pixels.fill(-1)
        session.invalidate()
        session.prepare()
        assertContentEquals(first, rasterBytes(session))
    }

    @Test fun orientationCropScalingAndAlignmentChangeThePhysicalPlanWithoutResettingAdjustments() {
        val session = ImagePrintSession().also { it.select(ImageInput(source)) }
        val edits = ImagePrintEdits(widthDots = "9", brightness = 25, threshold = 170)
        session.edit(edits)
        session.prepare()
        val initial = rasterBytes(session)
        session.edit(edits.copy(orientation = 6, cropWidth = "1", cropHeight = "3"))
        session.prepare()
        assertFalse(initial.contentEquals(rasterBytes(session)))
        assertEquals(25, session.state.edits.brightness)
        assertEquals(170, session.state.edits.threshold)
        session.edit(edits.copy(alignment = Alignment.LEFT))
        session.prepare()
        assertContentEquals(initial, rasterBytes(session))
        assertEquals(Alignment.LEFT, session.state.edits.alignment)
        val nine = ready(session).print.operations.filterIsInstance<RasterOperation>().first()
        session.edit(edits.copy(widthDots = "17"))
        session.prepare()
        assertNotEquals(nine.graphic.widthDots, ready(session).print.operations.filterIsInstance<RasterOperation>().first().graphic.widthDots)
    }

    @Test fun adjustedImagePreviewAndExplicitFakePrintConsumeIdenticalFinalArtifacts() = runTest {
        val session = ImagePrintSession().also { it.select(ImageInput(source)) }
        session.edit(ImagePrintEdits(widthDots = "17", brightness = 20, algorithm = DitherAlgorithm.FLOYD_STEINBERG))
        session.prepareAsync()
        val result = ready(session)
        repeat(result.preview.tileCount) { index ->
            val tile = assertIs<PrintPreviewTileResult.Success>(result.preview.tile(index)).tile
            for (primitive in tile.primitives.filterIsInstance<MonochromePrimitive>()) {
                val operation = result.print.operations.filterIsInstance<RasterOperation>().single { it === primitive.operation }
                assertSame(operation.graphic, primitive.graphic)
            }
        }
        val encoded = InMemoryByteSink()
        assertIs<EncodingResult.Success>(EscPosEncoder().encode(result.print, encoded))
        val transport = FakePrinterTransport()
        assertEquals(0, transport.connections)
        val printer = PrinterInstance("image", "Image fixture", PrinterTransportKind.DEVELOPMENT, "fixture",
            result.print.target.profileId, result.print.target.profileRevision)
        val printSession = PrintSession(transport)
        printSession.print(printer, result.print)
        assertContentEquals(encoded.bytes(), transport.capturedBytes())
        printSession.print(printer, result.print)
        assertEquals(1, transport.connections)
    }

    @Test fun everyAdjustmentAlgorithmSourceProfileAndOptionsChangeInvalidatesOutput() {
        val session = ImagePrintSession()
        session.select(ImageInput(source))
        // Keep browser work small while checking every stale-plan transition.
        session.edit(ImagePrintEdits(widthDots = "9"))
        session.prepare()
        val initial = ready(session).print
        for (algorithm in DitherAlgorithm.entries) {
            session.edit(session.state.edits.copy(algorithm = algorithm, widthDots = (17 + algorithm.ordinal).toString()))
            assertSame(PhysicalPreviewResult.NotPrepared, session.state.result)
            session.prepare()
            assertNotSame(initial, ready(session).print)
        }
        for (edit in listOf(session.state.edits.copy(brightness = 10), session.state.edits.copy(contrastPercent = 150),
            session.state.edits.copy(gammaHundredths = 150), session.state.edits.copy(cropWidth = "2"))) {
            session.edit(edit)
            assertSame(PhysicalPreviewResult.NotPrepared, session.state.result)
            session.prepare()
            ready(session)
        }
        session.configure(SyntheticReferenceProfiles.wide80mm)
        assertSame(PhysicalPreviewResult.NotPrepared, session.state.result)
        session.prepare()
        ready(session)
        session.configure(options = PrintOptions(finalFeedDots = 10))
        assertSame(PhysicalPreviewResult.NotPrepared, session.state.result)
        session.prepare()
        ready(session)
        session.select(ImageInput(DecodedImage(1, 1, intArrayOf(-1))))
        assertSame(PhysicalPreviewResult.NotPrepared, session.state.result)
    }

    @Test fun orientedCropUsesOrientedCoordinatesAndBadEditsNeverReuseTheOldPlan() {
        val session = ImagePrintSession()
        session.select(ImageInput(source, orientation = 6))
        session.edit(ImagePrintEdits(cropX = "1", cropY = "0", cropWidth = "1", cropHeight = "3", widthDots = "9"))
        session.prepare()
        ready(session)
        for (bad in listOf("bad", "-1", "0", "2147483648", "385")) {
            session.edit(session.state.edits.copy(widthDots = bad))
            session.prepare()
            assertSame(PhysicalPreviewResult.NotPrepared, session.state.result)
            assertNotNull(session.state.editDiagnostic)
        }
        session.edit(ImagePrintEdits(cropX = "2147483647", cropWidth = "2147483647"))
        session.prepare()
        assertSame(PhysicalPreviewResult.NotPrepared, session.state.result)
    }

    @Test fun invalidAdjustmentAndUnavailableRasterAreExplicitFailures() {
        val session = ImagePrintSession()
        session.select(ImageInput(source))
        session.edit(ImagePrintEdits(gammaHundredths = 0))
        session.prepare()
        assertNotNull(session.state.editDiagnostic)
        session.edit(ImagePrintEdits())
        session.configure(session.state.profile.copy(raster = com.circuitnext.rastrio.core.profile.RasterCapability(false)))
        session.prepare()
        assertIs<PhysicalPreviewResult.PreparationFailed>(session.state.result)
    }

    @Test fun markdownImageReferenceRequiresExplicitPickedPixelsAndSourceEditsReleaseBindings() {
        val authoring = createAuthoringSession()
        authoring.edit("![fixture](https://invalid.example/image.png)")
        authoring.compile()
        val physical = PhysicalPreviewSession()
        physical.prepare(authoring.state.result)
        assertIs<PhysicalPreviewResult.PreparationFailed>(physical.state.result)
        val reference = markdownImageReferences(authoring.state.result.semanticDocument()!!).single()
        assertEquals(ExternalAssetReference("https://invalid.example/image.png"), reference)
        assertTrue(physical.resolveImage(reference, ResolvedRasterImage(source)))
        assertSame(PhysicalPreviewResult.NotPrepared, physical.state.result)
        physical.prepare(authoring.state.result)
        val ready = assertIs<PhysicalPreviewResult.Ready>(physical.state.result)
        assertTrue(ready.print.operations.any { it is RasterOperation })
        physical.prepare(authoring.state.result)
        assertSame(ready, physical.state.result)
        physical.invalidate()
        physical.prepare(authoring.state.result)
        assertIs<PhysicalPreviewResult.PreparationFailed>(physical.state.result)
    }

    @Test fun referenceInventoryTraversesNestedBlocksAndDeduplicatesWithoutFetching() {
        val image = Image(ExternalAssetReference("file:///untrusted.png"), Alignment.LEFT, FitWidthSizing)
        val document = ThermalDocument(blocks = listOf(Quote(listOf(UnorderedList(listOf(ListItem(listOf(image)))))), image))
        assertEquals(listOf(image.asset), markdownImageReferences(document))
    }

    @Test fun backgroundPreparationCannotPublishAfterAnEditOrCancellation() = runTest {
        val session = ImagePrintSession()
        session.select(ImageInput(source))
        session.edit(ImagePrintEdits(widthDots = "17"))
        val first = launch(start = CoroutineStart.UNDISPATCHED) { session.prepareAsync() }
        session.edit(ImagePrintEdits(widthDots = "19"))
        first.join()
        assertSame(PhysicalPreviewResult.NotPrepared, session.state.result)
        // Cancel a queued job; UNDISPATCHED can finish this tiny fixture before cancel().
        val cancelled = launch { session.prepareAsync() }
        cancelled.cancel()
        cancelled.join()
        assertSame(PhysicalPreviewResult.NotPrepared, session.state.result)
        session.prepareAsync()
        ready(session)
    }

    @Test fun targetOptionsSourceAndNavigationChangesDiscardPendingPreparation() = runTest {
        val session = ImagePrintSession()
        session.select(ImageInput(source))
        session.edit(ImagePrintEdits(widthDots = "17"))
        val mutations: List<() -> Unit> = listOf(
            { session.configure(SyntheticReferenceProfiles.wide80mm) },
            { session.configure(options = PrintOptions(finalFeedDots = 12)) },
            { session.select(ImageInput(DecodedImage(2, 1, intArrayOf(-1, 0xff000000.toInt()))))
                session.edit(ImagePrintEdits(widthDots = "17")) },
            { session.edit(session.state.edits.copy(cropWidth = "1")) },
            { session.invalidate() },
        )
        for (mutate in mutations) {
            session.invalidate()
            val pending = launch(start = CoroutineStart.UNDISPATCHED) { session.prepareAsync() }
            mutate()
            pending.join()
            assertSame(PhysicalPreviewResult.NotPrepared, session.state.result)
            session.prepareAsync()
            ready(session)
        }
        val expectedFeed = session.state.options.finalFeedDots
        assertEquals(12, expectedFeed)
        assertEquals(SyntheticReferenceProfiles.wide80mm.profileId, ready(session).print.target.profileId)
    }

    @Test fun clearingExplicitMarkdownBindingsDuringBackgroundPreparationDiscardsTheResult() = runTest {
        val authoring = createAuthoringSession().also { it.edit("![image](selected.png)"); it.compile() }
        val physical = PhysicalPreviewSession()
        val reference = markdownImageReferences(authoring.state.result.semanticDocument()!!).single()
        physical.resolveImage(reference, ResolvedRasterImage(source))
        val job = launch(start = CoroutineStart.UNDISPATCHED) { physical.prepareAsync(authoring.state.result) }
        physical.invalidate()
        job.join()
        assertSame(PhysicalPreviewResult.NotPrepared, physical.state.result)
        physical.prepareAsync(authoring.state.result)
        assertIs<PhysicalPreviewResult.PreparationFailed>(physical.state.result)
    }
}
