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
        override val printers = MutableStateFlow(emptyList<PrinterInstance>())
        override val status = MutableStateFlow("Imported fixture; hardware unverified")
        override val foreground = MutableStateFlow(true)
        val transport = FakePrinterTransport()
        var exportRequests = 0
        override val supportsProfileExport = true
        override fun exportProfile() { exportRequests++ }
        override fun refreshPairedPrinters() = Unit
        override fun importProfile() = Unit
        override fun selectPrinter(instance: PrinterInstance) = Unit
        override fun createTransport(): PrinterTransport = transport
    }

    @Test fun profileExportRequiresASelectedConfigurationAndDoesNotConnect() = runComposeUiTest {
        val platform = OfflinePlatform()
        setContent { RastrIOApp(printingPlatform = platform) }
        onNodeWithText("Open Printers").performClick()
        onNodeWithText("Export printer profile").performScrollTo().assertIsEnabled().performClick()
        assertEquals(1, platform.exportRequests)
        platform.profile.value = null
        waitForIdle()
        onNodeWithText("Export printer profile").performScrollTo().assertIsNotEnabled()
        assertEquals(0, platform.transport.connections)
    }

    @Test fun profileReplacementPreparesTheNewGeometryWithoutDeviceSelection() = runComposeUiTest {
        val platform = OfflinePlatform()
        setContent { RastrIOApp(printingPlatform = platform) }
        onNodeWithText("Open Templates").performClick()
        onNodeWithText("QR Message").performClick()
        onNodeWithTag("template-QR payload").performTextInput("QR")
        onNodeWithTag("template-list").performScrollToIndex(3)
        onNodeWithText("Preview imported profile").performScrollTo().performClick()
        onNodeWithTag("template-list").performScrollToIndex(2)
        onNodeWithText("Preview template").performScrollTo().performClick()
        waitUntil { onAllNodesWithText("Physical preview ready.", substring = true).fetchSemanticsNodes().isNotEmpty() }
        platform.profile.value = SyntheticReferenceProfiles.wide80mm.copy(profileId = "org.rastrio.fixture.replacement", profileRevision = 2)
        waitForIdle()
        onNodeWithText("Open physical preview").performScrollTo().assertIsNotEnabled()
        onNodeWithText("Preview template").performScrollTo().performClick()
        waitUntil { onAllNodesWithText("Physical preview ready.", substring = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Open physical preview").performScrollTo().performClick()
        onNodeWithTag("physical-canvas").assertContentDescriptionEquals("Physical preview, 576 prepared dots wide")
        onNodeWithContentDescription("Selected printer profile org.rastrio.fixture.replacement").assertExists()
        assertEquals(0, platform.transport.connections)
    }

    @Test fun qrPreviewActionOpensFinalizedRasterInsteadOfALogicalPlaceholder() = runComposeUiTest {
        setContent { RastrIOApp() }
        onNodeWithText("Open Templates").performClick()
        onNodeWithText("QR Message").performClick()
        onNodeWithTag("template-QR payload").performTextInput("QR हिन्दी")
        onNodeWithText("Preview QR symbol").performScrollTo().performClick()
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
        onNodeWithText("Use Management fixture").performScrollTo().performClick()
        waitUntil { onAllNodesWithText("${profile.profileId} · revision 1").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Print prepared document").performScrollTo().assertIsNotEnabled()
        assertEquals(0, platform.transport.connections)
    }

    @Test fun printerManagementExposesTheBlockedH50iDraftWithoutConnecting() = runComposeUiTest {
        val platform = OfflinePlatform().also { it.profile.value = null }
        setContent { RastrIOApp(printingPlatform = platform) }
        onNodeWithText("Open Printers").performClick()
        onNodeWithText("APP140", substring = true).assertExists()
        onNodeWithText("Import printer profile").performScrollTo().assertIsEnabled()
        onNodeWithText("Open Printer Lab").performScrollTo().performClick()
        onNodeWithText("Helett H50i BillQuick Go").assertExists()
        onNodeWithText("Provisional draft", substring = true).assertExists()
        onNodeWithText("Print prepared document").assertDoesNotExist()
        onNodeWithTag("printer-lab-list").performScrollToIndex(2)
        onNodeWithText("Create raster QR test draft").performScrollTo().performClick()
        onNodeWithTag("template-QR payload").assertTextContains("https://example.invalid/rastrio/hw-018")
        onNodeWithTag("template-Requested size (mm, optional)").assertTextContains("30")
        onNodeWithTag("template-list").performScrollToIndex(3)
        onNodeWithText("Print prepared document").performScrollTo().assertIsNotEnabled()
        assertEquals(0, platform.transport.connections)
    }

    @Test fun importedProfileCanPreviewQrWithoutASelectedOrConnectedPrinter() = runComposeUiTest {
        val platform = OfflinePlatform()
        setContent { RastrIOApp(printingPlatform = platform) }
        onNodeWithText("Open Templates").performClick()
        onNodeWithText("QR Message").performClick()
        onNodeWithTag("template-QR payload").performTextInput("Offline हिन्दी QR")
        onNodeWithTag("template-list").performScrollToIndex(3)
        onNodeWithText("Preview imported profile").performScrollTo().assertIsEnabled().performClick()
        onNodeWithTag("template-list").performScrollToIndex(2)
        onNodeWithText("Preview template").performScrollTo().performClick()
        waitUntil { onAllNodesWithText("Physical preview ready.", substring = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Open physical preview").performScrollTo().performClick()
        onAllNodesWithContentDescription("Raster QR, exact prepared dots")[0].assertExists()
        onNodeWithContentDescription("Selected printer profile org.rastrio.fixture.offline-preview").assertExists()
        onNodeWithText("Print prepared document").performScrollTo().assertIsNotEnabled()
        assertEquals(0, platform.transport.connections)
    }

    @Test fun replacingAnImportedProfileInvalidatesItsOldTemplatePreview() = runComposeUiTest {
        val platform = OfflinePlatform()
        val profile = platform.profile.value!!
        platform.printers.value = listOf(PrinterInstance("preview-device", "Preview fixture",
            PrinterTransportKind.DEVELOPMENT, "preview-locator", profile.profileId, profile.profileRevision))
        setContent { RastrIOApp(printingPlatform = platform) }
        onNodeWithText("Open Templates").performClick()
        onNodeWithText("QR Message").performClick()
        onNodeWithTag("template-QR payload").performTextInput("QR")
        onNodeWithTag("template-list").performScrollToIndex(3)
        onNodeWithText("Use Preview fixture").performScrollTo().performClick()
        onNodeWithTag("template-list").performScrollToIndex(2)
        onNodeWithText("Preview template").performScrollTo().performClick()
        waitUntil { onAllNodesWithText("Physical preview ready.", substring = true).fetchSemanticsNodes().isNotEmpty() }
        platform.profile.value = null
        waitForIdle()
        onNodeWithText("Open physical preview").performScrollTo().assertIsNotEnabled()
        onNodeWithTag("template-list").performScrollToIndex(3)
        onNodeWithText("Print prepared document").performScrollTo().assertIsNotEnabled()
    }
}
