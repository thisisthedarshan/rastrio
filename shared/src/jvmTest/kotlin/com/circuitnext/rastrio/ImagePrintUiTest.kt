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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.material3.MaterialTheme
import com.circuitnext.rastrio.presentation.ImagePrintState
import com.circuitnext.rastrio.presentation.ImagePrintEdits
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
        override val selectedPrinter = MutableStateFlow<PrinterInstance?>(printer)
        override val printers = MutableStateFlow(listOf(printer))
        override val status = MutableStateFlow("Hardware unverified development fixture")
        override val foreground = MutableStateFlow(true)
        val transport = FakePrinterTransport()
        override fun refreshPairedPrinters() = Unit
        override fun importProfile() = Unit
        override fun selectPrinter(instance: PrinterInstance) { selectedPrinter.value = instance }
        override fun createTransport(): PrinterTransport = transport
        override fun importImage() {
            image.value = ImageInput(DecodedImage(4, 2, intArrayOf(-1, 0xff000000.toInt(), -1, -1,
                0xff404040.toInt(), -1, 0xff000000.toInt(), -1)))
            imageStatus.value = "Image selected explicitly"
        }
    }

    @Test fun selectedImageUpdatesAutomaticallyWithoutManualPreparation() = runComposeUiTest {
        val platform = FixturePlatform()
        setContent { RastrIOApp(printingPlatform = platform) }
        onNodeWithText("Open Image Print").performClick()
        onNodeWithText("Choose image").performClick()
        onNodeWithText("Prepare image").assertDoesNotExist()
        waitUntil { onAllNodesWithTag("physical-canvas").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(0, platform.transport.connections)
    }

    @Test fun automaticAdjustmentsRemainSelectedInPrintConfirmationAndTransmitOnce() = runComposeUiTest {
        val platform = FixturePlatform()
        setContent { RastrIOApp(printingPlatform = platform) }
        onNodeWithText("Open Image Print").performClick()
        onNodeWithText("Choose image").performClick()
        onNodeWithText("Print width (dots)").performScrollTo().performTextInput("17")
        onNodeWithText("Bayer").performScrollTo().performClick()
        onNodeWithText("Dithering: Bayer").assertExists()
        waitUntil { onAllNodesWithText("Image prepared:", substring = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Continue to Print").performClick()
        waitUntil { onAllNodesWithTag("physical-canvas").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("confirm-print").assertIsEnabled()
        assertEquals(0, platform.transport.connections)
        onNodeWithText("More print options").performClick()
        onNodeWithText("Dithering: Bayer").performScrollTo().assertExists()
        onNodeWithText("Atkinson").performScrollTo().performClick()
        waitUntil { onAllNodesWithTag("physical-canvas").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Dithering: Atkinson").assertExists()
        assertEquals(0, platform.transport.connections)
        onNodeWithTag("confirm-print").performClick()
        waitUntil { platform.transport.byteCount > 0 && !platform.transport.connected }
        onNodeWithTag("confirm-print").assertIsNotEnabled()
        assertEquals(1, platform.transport.connections)
        assertTrue(platform.transport.capturedBytes().toList().windowed(4).any {
            it.map(Byte::toInt) == listOf(0x1d, 0x76, 0x30, 0)
        })
    }

    @Test fun malformedResizeEditAutomaticallyRejectsOldPreviewAndCanRecover() = runComposeUiTest {
        val platform = FixturePlatform()
        setContent { RastrIOApp(printingPlatform = platform) }
        onNodeWithText("Open Image Print").performClick()
        onNodeWithText("Choose image").performClick()
        waitUntil { onAllNodesWithTag("physical-canvas").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Print width (dots)").performScrollTo().performTextInput("invalid")
        onNodeWithTag("image-print-list").performScrollToIndex(0)
        waitUntil { onAllNodesWithText("Invalid crop, print width, or image adjustment").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("physical-canvas").assertDoesNotExist()
        onNodeWithText("Reset image adjustments").performScrollTo().performClick()
        onNodeWithTag("image-print-list").performScrollToIndex(0)
        waitUntil { onAllNodesWithText("Image prepared:", substring = true).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(0, platform.transport.connections)
    }

    @Test fun physicalImagePreviewRemainsVisibleWhileAdjustmentControlsScroll() = runComposeUiTest {
        val platform = FixturePlatform()
        setContent { RastrIOApp(printingPlatform = platform) }
        onNodeWithText("Open Image Print").performClick()
        onNodeWithText("Choose image").performClick()
        waitUntil { onAllNodesWithTag("physical-canvas").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithContentDescription("Brightness").performScrollTo().assertIsDisplayed()
        onNodeWithTag("physical-canvas").assertIsDisplayed()
        onNodeWithText("Crop width").performScrollTo().assertIsDisplayed()
        onNodeWithTag("physical-canvas").assertIsDisplayed()
        assertEquals(0, platform.transport.connections)
    }

    @Test fun compactHeightKeepsRasterAndAdjustmentsUsableTogether() = runComposeUiTest {
        val session = com.circuitnext.rastrio.presentation.ImagePrintSession()
        session.select(ImageInput(DecodedImage(4, 2, IntArray(8) { 0xff404040.toInt() })))
        session.prepare()
        var compact by mutableStateOf(false)
        setContent {
            MaterialTheme {
                androidx.compose.foundation.layout.Box(
                    androidx.compose.ui.Modifier.requiredSize(if (compact) 480.dp else 900.dp, if (compact) 320.dp else 365.dp)) {
                    ImagePrintScreen(session.state, null, "Fixture", false, {}, {}, {})
                }
            }
        }
        val raster = onNodeWithTag("physical-canvas").getUnclippedBoundsInRoot()
        assertTrue(raster.bottom - raster.top >= 60.dp, "Compact raster viewport must remain useful: $raster")
        onNodeWithContentDescription("Gamma").performScrollTo().assertIsDisplayed()
        onNodeWithTag("physical-canvas").assertIsDisplayed()
        onNodeWithText("Continue to Print").assertIsDisplayed()
        runOnIdle { compact = true }
        val narrowRaster = onNodeWithTag("physical-canvas").getUnclippedBoundsInRoot()
        assertTrue(narrowRaster.bottom - narrowRaster.top >= 48.dp, "Small-window raster: $narrowRaster")
        onNodeWithContentDescription("Gamma").performScrollTo().assertIsDisplayed()
        onNodeWithText("Continue to Print").assertIsDisplayed()
    }

    @Test fun savedImageEditsRestoreAllFieldsAndRejectCorruptOrOversizedState() {
        val fields = listOf("1", "2", "3", "4", "17", "37", "160", "170", "BAYER", "90", "6", "RIGHT")
        val restored = assertNotNull(ImagePrintEditsSaver.restore(fields))
        assertEquals(ImagePrintEdits(cropX = "1", cropY = "2", cropWidth = "3", cropHeight = "4", widthDots = "17",
            brightness = 37, contrastPercent = 160, gammaHundredths = 170,
            algorithm = com.circuitnext.rastrio.core.raster.DitherAlgorithm.BAYER, threshold = 90, orientation = 6,
            alignment = com.circuitnext.rastrio.core.document.Alignment.RIGHT), restored)
        assertNull(ImagePrintEditsSaver.restore(fields.dropLast(1)))
        for ((index, invalid) in listOf(0 to "9".repeat(13), 5 to "256", 6 to "401", 7 to "0",
            8 to "UNKNOWN", 9 to "-1", 10 to "9", 11 to "UNKNOWN")) {
            assertNull(ImagePrintEditsSaver.restore(fields.toMutableList().also { it[index] = invalid }))
        }
    }

    @Test fun recreationRetainsAdjustmentsForRetainedSourceAndNewImageResetsThem() = runComposeUiTest {
        val platform = FixturePlatform()
        var registry by mutableStateOf(SaveableStateRegistry(null) { true })
        var visible by mutableStateOf(true)
        setContent {
            CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                if (visible) RastrIOApp(printingPlatform = platform)
            }
        }
        fun recreate() {
            var saved: Map<String, List<Any?>> = emptyMap()
            runOnIdle { saved = registry.performSave(); visible = false }
            waitForIdle()
            runOnIdle { registry = SaveableStateRegistry(saved) { true }; visible = true }
            waitForIdle()
        }
        onNodeWithText("Open Image Print").performClick()
        onNodeWithText("Choose image").performClick()
        onNodeWithText("Print width (dots)").performScrollTo().performTextInput("17")
        onNodeWithContentDescription("Brightness").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(37f) }
        onNodeWithText("Bayer").performScrollTo().performClick()
        recreate()
        onNodeWithTag("image-print-list").performScrollToNode(hasText("Image adjustments"))
        onNodeWithText("Dithering: Bayer").performScrollTo().assertExists()
        onNodeWithContentDescription("Brightness").performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "37"))
        onNodeWithText("Print width (dots)").performScrollTo().assertTextContains("17")
        onNodeWithText("Continue to Print").performClick()
        waitUntil { onAllNodesWithTag("physical-canvas").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("More print options").performClick()
        onNodeWithText("Dithering: Bayer").performScrollTo().assertExists()
        recreate()
        waitUntil { onAllNodesWithTag("physical-canvas").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Dithering: Bayer").performScrollTo().assertExists()
        onNodeWithTag("confirm-print").assertIsEnabled()
        onNodeWithText("Back", substring = false).performClick()
        onNodeWithText("Choose image").performClick()
        onNodeWithText("Dithering: Threshold").performScrollTo().assertExists()
        onNodeWithContentDescription("Brightness").performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "0"))
        onNodeWithText("Print width (dots)").performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        assertEquals(0, platform.transport.connections)
    }

    @Test fun slidersAlgorithmsAndResetRetainOneCompleteAdjustmentState() = runComposeUiTest {
        var latest = ImagePrintEdits()
        setContent {
            var state by remember { mutableStateOf(ImagePrintState()) }
            MaterialTheme {
                ImageAdjustmentControls(state, { latest = it; state = state.copy(edits = it) })
            }
        }
        onNodeWithContentDescription("Brightness").performSemanticsAction(SemanticsActions.SetProgress) { it(37f) }
        onNodeWithContentDescription("Contrast").performSemanticsAction(SemanticsActions.SetProgress) { it(160f) }
        onNodeWithContentDescription("Gamma").performSemanticsAction(SemanticsActions.SetProgress) { it(170f) }
        onNodeWithContentDescription("Threshold").performSemanticsAction(SemanticsActions.SetProgress) { it(90f) }
        runOnIdle {
            assertEquals(37, latest.brightness)
            assertEquals(160, latest.contrastPercent)
            assertEquals(170, latest.gammaHundredths)
            assertEquals(90, latest.threshold)
        }
        for (algorithm in listOf("Bayer", "Atkinson", "Floyd–Steinberg", "Threshold")) {
            onNodeWithText(algorithm).performClick()
            onNodeWithText("Dithering: $algorithm").assertExists()
        }
        runOnIdle { assertEquals(37, latest.brightness); assertEquals(90, latest.threshold) }
        onNodeWithText("Reset image adjustments").performClick()
        runOnIdle { assertEquals(ImagePrintEdits(), latest) }
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
