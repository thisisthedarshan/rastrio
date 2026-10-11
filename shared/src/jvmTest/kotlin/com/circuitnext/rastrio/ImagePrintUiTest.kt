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

package com.circuitnext.rastrio

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.semantics.SemanticsProperties
import com.circuitnext.rastrio.core.profile.PrinterProfile
import com.circuitnext.rastrio.core.profile.SyntheticReferenceProfiles
import com.circuitnext.rastrio.core.raster.DecodedImage
import com.circuitnext.rastrio.printing.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class ImagePrintUiTest {
    private class FixturePlatform : PrintingPlatform, ImagePlatform {
        override val image = MutableStateFlow<ImageInput?>(null)
        override val imageStatus = MutableStateFlow("Choose an image")
        override val profile = MutableStateFlow<PrinterProfile?>(SyntheticReferenceProfiles.narrow.copy(profileId = "org.rastrio.fixture.image-ui"))
        val printer = PrinterInstance("image-fixture", "Image fixture", PrinterTransportKind.DEVELOPMENT,
            "image-locator", profile.value!!.profileId, profile.value!!.profileRevision)
        override val printers = MutableStateFlow(listOf(printer))
        override val status = MutableStateFlow("Hardware unverified development fixture")
        override val foreground = MutableStateFlow(true)
        val transport = FakePrinterTransport()
        override fun refreshPairedPrinters() = Unit
        override fun importProfile() = Unit
        override fun selectPrinter(instance: PrinterInstance) = Unit
        override fun createTransport(): PrinterTransport = transport
        override fun importImage() {
            image.value = ImageInput(DecodedImage(4, 2, intArrayOf(-1, 0xff000000.toInt(), -1, -1,
                0xff404040.toInt(), -1, 0xff000000.toInt(), -1)))
            imageStatus.value = "Image selected explicitly"
        }
    }

    @Test fun pickerControlsRequireExplicitPreparationAndEditsInvalidatePreview() = runComposeUiTest {
        val platform = FixturePlatform()
        setContent { RastrIOApp(printingPlatform = platform) }
        onNodeWithText("Open Image Print").performClick()
        onNodeWithText("Prepare image").performScrollTo().assertIsNotEnabled()
        onNodeWithText("Choose image").performScrollTo().performClick()
        onNodeWithText("Oriented image: 4 × 2 pixels").assertExists()
        onNodeWithText("Print width (dots)").performScrollTo().performTextInput("17")
        onNodeWithText("Prepare image").performScrollTo().performClick()
        waitUntil { onAllNodesWithText("Image prepared:", substring = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Open image physical preview").performScrollTo().assertIsEnabled().assertIsDisplayed().performClick()
        onNodeWithTag("physical-canvas").assertIsDisplayed()
        onNodeWithText("Back to image settings").performClick()
        onNodeWithText("Bayer").performScrollTo().performClick()
        onNodeWithText("Open image physical preview").assertIsNotEnabled()
        onNodeWithText("Prepare image").performClick()
        waitUntil { onAllNodesWithText("Image prepared:", substring = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Open image physical preview").assertIsEnabled()
        assertEquals(0, platform.transport.connections)
    }

    @Test fun selectedInstancePrintsTheImagePreviewPlanAndSyntheticSimulationCannotSend() = runComposeUiTest {
        val platform = FixturePlatform()
        setContent { RastrIOApp(printingPlatform = platform) }
        onNodeWithText("Open Image Print").performClick()
        onNodeWithText("Choose image").performClick()
        onNodeWithText("Print width (dots)").performScrollTo().performTextInput("17")
        onNodeWithTag("image-print-list").performScrollToNode(hasText("Use Image fixture"))
        onNodeWithText("Use Image fixture").performClick()
        onNodeWithText("Print prepared document").performScrollTo().assertIsNotEnabled()
        onNodeWithText("Prepare image").performScrollTo().performClick()
        waitUntil { onAllNodesWithText("Image prepared:", substring = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Open image physical preview").performScrollTo().assertIsDisplayed().performClick()
        onNodeWithText("Print prepared document").performScrollTo().assertIsEnabled().performClick()
        waitUntil { platform.transport.byteCount > 0 && !platform.transport.connected }
        onNodeWithText("Local transmission completed · physical printing is not confirmed").performScrollTo().assertExists()
        assertTrue(platform.transport.capturedBytes().toList().windowed(4).any {
            it.map(Byte::toInt) == listOf(0x1d, 0x76, 0x30, 0)
        })
        onNodeWithText("Back to image settings").performClick()
        onNodeWithText("Simulate 384 dots").performScrollTo().performClick()
        onNodeWithText("Prepare image").performScrollTo().performClick()
        waitUntil { onAllNodesWithText("Image prepared:", substring = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("image-print-list").performScrollToNode(hasText("Print prepared document"))
        onNodeWithText("Print prepared document").performScrollTo().assertIsNotEnabled()
        assertEquals(1, platform.transport.connections)
    }

    @Test fun malformedResizeEditKeepsPrintingDisabled() = runComposeUiTest {
        val platform = FixturePlatform()
        setContent { RastrIOApp(printingPlatform = platform) }
        onNodeWithText("Open Image Print").performClick()
        onNodeWithText("Choose image").performClick()
        onNodeWithTag("image-print-list").performScrollToNode(hasText("Use Image fixture"))
        onNodeWithText("Use Image fixture").performClick()
        onNodeWithText("Prepare image").performScrollTo().performClick()
        waitUntil { onAllNodesWithText("Image prepared:", substring = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Print width (dots)").performScrollTo().performTextInput("invalid")
        onNodeWithText("Prepare image").performScrollTo().performClick()
        waitUntil { onAllNodesWithText("Invalid crop, print width, or image adjustment").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Invalid crop, print width, or image adjustment").assertExists()
        onNodeWithText("Open image physical preview").assertIsNotEnabled()
        onNodeWithText("Print prepared document").performScrollTo().assertIsNotEnabled()
        assertEquals(0, platform.transport.connections)
    }

    @Test fun adjustmentSlidersExposeLabelsAndCurrentValues() = runComposeUiTest {
        val platform = FixturePlatform()
        setContent { RastrIOApp(printingPlatform = platform) }
        onNodeWithText("Open Image Print").performClick()
        onNodeWithTag("image-print-list").performScrollToNode(hasContentDescription("Brightness"))
        onNodeWithContentDescription("Brightness").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "0"))
        onNodeWithContentDescription("Contrast").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "100%"))
        onNodeWithContentDescription("Gamma").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "1.0"))
    }
}
