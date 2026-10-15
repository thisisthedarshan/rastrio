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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import com.circuitnext.rastrio.core.profile.PrinterProfile
import com.circuitnext.rastrio.core.profile.SyntheticReferenceProfiles
import com.circuitnext.rastrio.printing.*
import com.circuitnext.rastrio.presentation.createAuthoringSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class QrPreviewIntegrationTest {
    private class OfflinePlatform : PrintingPlatform {
        override val profile = MutableStateFlow<PrinterProfile?>(SyntheticReferenceProfiles.narrow.copy(
            profileId = "org.rastrio.fixture.offline-preview"))
        override val selectedPrinter = MutableStateFlow<PrinterInstance?>(null)
        override val selectedProfile = MutableStateFlow<PrinterProfile?>(null)
        override val printers = MutableStateFlow(emptyList<PrinterInstance>())
        override val status = MutableStateFlow("Imported fixture; hardware unverified")
        override val foreground = MutableStateFlow(true)
        val transport = FakePrinterTransport()
        var exportRequests = 0
        override val supportsProfileExport = true
        override fun exportProfile() { exportRequests++ }
        override fun refreshPairedPrinters() = Unit
        override fun importProfile() = Unit
        override fun selectPrinter(instance: PrinterInstance) { selectedPrinter.value = instance; selectedProfile.value = profile.value }
        override fun createTransport(): PrinterTransport = transport
    }

    @Test fun profileExportRequiresASelectedConfigurationAndDoesNotConnect() = runComposeUiTest {
        val platform = OfflinePlatform()
        setContent { RastrIOApp(printingPlatform = platform) }
        onNodeWithText("Open Printers").performClick()
        onNodeWithText("Manage profiles").performScrollTo().performClick()
        onNodeWithText("Export printer profile").assertIsEnabled().performClick()
        assertEquals(1, platform.exportRequests)
        platform.profile.value = null
        waitForIdle()
        onNodeWithText("Manage profiles").performScrollTo().performClick()
        onNodeWithText("Export printer profile").assertIsNotEnabled()
        assertEquals(0, platform.transport.connections)
    }

    @Test fun profileReplacementPreparesTheNewGeometryWithoutDeviceSelection() = runComposeUiTest {
        val platform = OfflinePlatform()
        setContent { RastrIOApp(printingPlatform = platform) }
        onNodeWithText("Open Templates").performClick()
        onNodeWithText("QR Message").performClick()
        onNodeWithTag("template-QR payload").performTextInput("QR")
        onNodeWithText("Preview", substring = false).performScrollTo().performClick()
        waitUntil { onAllNodesWithTag("physical-canvas").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Preview settings").performScrollTo().performClick()
        onNodeWithText("Preview imported profile").performScrollTo().performClick()
        waitUntil { onAllNodesWithText("Physical preview ready.", substring = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Back to preview").performScrollTo().performClick()
        onNodeWithContentDescription("Selected printer profile org.rastrio.fixture.offline-preview").assertExists()
        platform.profile.value = SyntheticReferenceProfiles.wide80mm.copy(profileId = "org.rastrio.fixture.replacement", profileRevision = 2)
        // Importing another profile does not authorize a printer or overwrite the displayed immutable plan.
        onNodeWithText("Preview settings").performScrollTo().performClick()
        onNodeWithText("Preview imported profile").performScrollTo().performClick()
        waitUntil { onAllNodesWithText("Physical preview ready.", substring = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Back to preview").performScrollTo().performClick()
        onNodeWithTag("physical-canvas").assertContentDescriptionEquals("Physical preview, 576 prepared dots wide")
        onNodeWithContentDescription("Selected printer profile org.rastrio.fixture.replacement").assertExists()
        onNodeWithText("Print", substring = false).performClick()
        onNodeWithText("Add Printer").assertExists()
        onNodeWithTag("confirm-print").assertIsNotEnabled()
        onNodeWithTag("physical-canvas").assertDoesNotExist()
        assertEquals(0, platform.transport.connections)
    }

    @Test fun qrPreviewActionOpensFinalizedRasterInsteadOfALogicalPlaceholder() = runComposeUiTest {
        setContent { RastrIOApp() }
        onNodeWithText("Open Templates").performClick()
        onNodeWithText("QR Message").performClick()
        onNodeWithTag("template-QR payload").performTextInput("QR हिन्दी")
        onNodeWithText("Preview", substring = false).performScrollTo().performClick()
        waitUntil { onAllNodesWithText("Physical preview").fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithContentDescription("Raster QR, exact prepared dots")[0].assertExists()
        val pixels = onNodeWithTag("physical-canvas").captureToImage().toPixelMap()
        assertTrue((0 until pixels.height).any { y -> (0 until pixels.width).any { x -> pixels[x, y] == Color.Black } },
            "The finalized QR must paint black pixels instead of a blank preview")
        onNodeWithText("Back to templates").performClick()
        onNodeWithTag("template-QR payload").assertTextContains("QR हिन्दी")
    }

    @Test fun selectingPrinterInManagementCannotPrepareRetainedMarkdown() = runComposeUiTest {
        val platform = OfflinePlatform()
        val profile = platform.profile.value!!
        platform.printers.value = listOf(PrinterInstance("management-device", "Management fixture",
            PrinterTransportKind.DEVELOPMENT, "management-locator", profile.profileId, profile.profileRevision))
        val input = createAuthoringSession().also { it.edit("Retained receipt"); it.compile() }
        setContent { RastrIOApp(session = input, printingPlatform = platform) }
        onNodeWithText("Open Printers").performClick()
        onNodeWithText("Management fixture").performScrollTo().performClick()
        onNodeWithText("Print prepared document").assertDoesNotExist()
        onNodeWithTag("physical-canvas").assertDoesNotExist()
        assertEquals(0, platform.transport.connections)
    }

    @Test fun printerManagementExposesGenericLabDetailsWithoutConnecting() = runComposeUiTest {
        val platform = OfflinePlatform().also { it.profile.value = null }
        setContent { RastrIOApp(printingPlatform = platform) }
        onNodeWithText("Open Printers").performClick()
        onNodeWithText("APP140", substring = true).assertDoesNotExist()
        onNodeWithTag("printer-management-list").performScrollToNode(hasText("No validated physical printer profile selected.", substring = true))
        onNodeWithText("No validated physical printer profile selected.", substring = true).assertExists()
        onNodeWithText("Manage profiles").performScrollTo().performClick()
        onNodeWithText("Import printer profile").assertIsEnabled()
        onNodeWithText("Import printer profile").performClick()
        onNodeWithTag("printer-management-list").performScrollToNode(hasText("Open Printer Lab"))
        onNodeWithText("Open Printer Lab").performClick()
        onNodeWithText("Helett H50i BillQuick Go", substring = true).assertDoesNotExist()
        onNodeWithText("APP141", substring = true).assertDoesNotExist()
        onNodeWithTag("printer-lab-list").performScrollToNode(hasText("Reference and procedure details"))
        onNodeWithText("Reference and procedure details").performClick()
        onNodeWithText("Printer evidence").assertExists()
        onNodeWithText("A self-test can report paper width, fonts and charsets.", substring = true).assertExists()
        onNodeWithText("Helett H50i BillQuick Go", substring = true).assertDoesNotExist()
        onNodeWithText("Print prepared document").assertDoesNotExist()
        onNodeWithTag("printer-lab-list").performScrollToNode(hasText("Create raster QR test draft"))
        onNodeWithText("Create raster QR test draft").performClick()
        onNodeWithTag("template-QR payload").assertTextContains("https://example.invalid/rastrio/hw-018")
        onNodeWithText("QR options").performScrollTo().performClick()
        onNodeWithTag("template-Requested size (mm, optional)").assertTextContains("30")
        onNodeWithText("Print prepared document").assertDoesNotExist()
        onNodeWithText("Preview", substring = false).performScrollTo().performClick()
        waitUntil { onAllNodesWithTag("physical-canvas").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Print", substring = false).performClick()
        onNodeWithText("Add Printer").assertExists()
        onNodeWithTag("confirm-print").assertIsNotEnabled()
        onNodeWithTag("physical-canvas").assertDoesNotExist()
        assertEquals(0, platform.transport.connections)
    }

    @Test fun importedProfileCanPreviewQrWithoutASelectedOrConnectedPrinter() = runComposeUiTest {
        val platform = OfflinePlatform()
        setContent { RastrIOApp(printingPlatform = platform) }
        onNodeWithText("Open Templates").performClick()
        onNodeWithText("QR Message").performClick()
        onNodeWithTag("template-QR payload").performTextInput("Offline हिन्दी QR")
        onNodeWithText("Preview", substring = false).performScrollTo().performClick()
        waitUntil { onAllNodesWithTag("physical-canvas").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Preview settings").performScrollTo().performClick()
        onNodeWithText("Preview imported profile").performScrollTo().assertIsEnabled().performClick()
        waitUntil { onAllNodesWithText("Physical preview ready.", substring = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Back to preview").performScrollTo().performClick()
        onAllNodesWithContentDescription("Raster QR, exact prepared dots")[0].assertExists()
        onNodeWithContentDescription("Selected printer profile org.rastrio.fixture.offline-preview").assertExists()
        onNodeWithText("Print", substring = false).performClick()
        onNodeWithText("Add Printer").assertExists()
        onNodeWithTag("confirm-print").assertIsNotEnabled()
        onNodeWithTag("physical-canvas").assertDoesNotExist()
        assertEquals(0, platform.transport.connections)
    }

    @Test fun replacingABoundProfileInvalidatesItsOldTemplateConfirmation() = runComposeUiTest {
        val platform = OfflinePlatform()
        val profile = platform.profile.value!!
        val printer = PrinterInstance("preview-device", "Preview fixture", PrinterTransportKind.DEVELOPMENT,
            "preview-locator", profile.profileId, profile.profileRevision)
        platform.printers.value = listOf(printer)
        platform.selectedPrinter.value = printer
        platform.selectedProfile.value = profile
        setContent { RastrIOApp(printingPlatform = platform) }
        onNodeWithText("Open Templates").performClick()
        onNodeWithText("QR Message").performClick()
        onNodeWithTag("template-QR payload").performTextInput("QR")
        onNodeWithText("Preview", substring = false).performScrollTo().performClick()
        waitUntil { onAllNodesWithTag("physical-canvas").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Print", substring = false).performClick()
        waitUntil { onAllNodesWithTag("physical-canvas").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("confirm-print").assertIsEnabled()
        // An unrelated imported profile must not replace this saved exact binding.
        platform.profile.value = SyntheticReferenceProfiles.wide80mm.copy(profileId = "org.rastrio.fixture.unrelated")
        waitForIdle()
        onNodeWithTag("confirm-print").assertIsEnabled()
        onNodeWithTag("physical-canvas").assertContentDescriptionEquals("Physical preview, 384 prepared dots wide")
        platform.selectedProfile.value = null
        waitUntil { onAllNodesWithTag("physical-canvas").fetchSemanticsNodes().isEmpty() }
        onNodeWithTag("confirm-print").assertIsNotEnabled()
        onNodeWithText("Printer profile unavailable. Manage printers to repair the assignment.").assertExists()
        assertEquals(0, platform.transport.connections)
    }
}
