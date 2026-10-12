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
import com.circuitnext.rastrio.core.printer.*
import com.circuitnext.rastrio.core.preview.*
import com.circuitnext.rastrio.core.profile.SyntheticReferenceProfiles
import com.circuitnext.rastrio.core.raster.*
import com.circuitnext.rastrio.printing.ImageInput
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

    @Test fun everyAdjustmentAlgorithmSourceProfileAndOptionsChangeInvalidatesOutput() {
        val session = ImagePrintSession()
        session.select(ImageInput(source))
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
