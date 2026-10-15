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
import com.circuitnext.rastrio.core.profile.PrinterProfile
import com.circuitnext.rastrio.core.profile.SyntheticReferenceProfiles
import com.circuitnext.rastrio.presentation.createAuthoringSession
import com.circuitnext.rastrio.printing.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class TemplateWorkspaceNavigationTest {
    private class FixturePlatform : PrintingPlatform {
        override val profile = MutableStateFlow<PrinterProfile?>(SyntheticReferenceProfiles.narrow.copy(
            profileId = "org.rastrio.fixture.workspace"))
        val printer = PrinterInstance("workspace-fixture", "Workspace printer", PrinterTransportKind.DEVELOPMENT,
            "workspace-locator", profile.value!!.profileId, profile.value!!.profileRevision)
        override val selectedPrinter = MutableStateFlow<PrinterInstance?>(printer)
        override val selectedProfile = MutableStateFlow<PrinterProfile?>(profile.value)
        override val printers = MutableStateFlow(listOf(printer))
        override val status = MutableStateFlow("Development fixture; hardware unverified")
        override val foreground = MutableStateFlow(true)
        val transport = FakePrinterTransport()
        override fun refreshPairedPrinters() = Unit
        override fun importProfile() = Unit
        override fun selectPrinter(instance: PrinterInstance) { selectedPrinter.value = instance }
        override fun createTransport(): PrinterTransport = transport
    }

    @Test fun markdownPlanCannotBecomeAnUnbuiltTemplatePreviewOrPrint() = runComposeUiTest {
        val input = createAuthoringSession().also { it.edit("Markdown receipt"); it.compile() }
        val platform = FixturePlatform()
        setContent { RastrIOApp(input, platform) }
        onNodeWithText("Open Markdown").performClick()
        onNodeWithText("Printer preview").performClick()
        waitUntil { onAllNodesWithTag("physical-canvas").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("confirm-print").assertIsEnabled()
        onNodeWithText("Back", substring = false).performClick()
        onNodeWithText("Back to Home").performClick()
        onNodeWithText("Open Templates").performClick()
        onNodeWithText("Quick Note").performClick()
        onNodeWithTag("physical-canvas").assertDoesNotExist()
        onNodeWithText("Open physical preview").assertDoesNotExist()
        onNodeWithText("Print prepared document").assertDoesNotExist()
        onNodeWithText("Wide preview").assertDoesNotExist()
        onNodeWithText("QR raster").assertDoesNotExist()
        onNodeWithText("Prepare selected printer").assertDoesNotExist()
        onNodeWithTag("template-Note").performTextInput("Fresh template")
        onNodeWithText("Preview", substring = false).performScrollTo().performClick()
        waitUntil { onAllNodesWithTag("physical-canvas").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Fresh template").assertExists()
        onNodeWithText("Markdown receipt").assertDoesNotExist()
        onNodeWithText("Preview settings").performScrollTo().performClick()
        onNodeWithText("Wide preview").performScrollTo().performClick()
        waitUntil { onAllNodesWithText("Physical preview ready.", substring = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("QR raster").performScrollTo().performClick()
        waitUntil { onAllNodesWithText("Physical preview ready.", substring = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Print prepared document").assertDoesNotExist()
        onNodeWithText("Prepare selected printer").performScrollTo().performClick()
        waitUntil { onAllNodesWithText("Physical preview ready.", substring = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Back to preview").performScrollTo().performClick()
        onNodeWithText("Fresh template").assertExists()
        onNodeWithText("Markdown receipt").assertDoesNotExist()
        onNodeWithText("Print", substring = false).performClick()
        waitUntil { onAllNodesWithTag("physical-canvas").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("confirm-print").assertIsEnabled()
        onNodeWithText("Fresh template").assertExists()
        onNodeWithText("Markdown receipt").assertDoesNotExist()
        assertEquals(0, platform.transport.connections)
    }

    @Test fun bothWorkspaceTransitionsRequireTheirOwnFreshDocumentAndRetainDrafts() = runComposeUiTest {
        val input = createAuthoringSession().also { it.edit("Markdown receipt"); it.compile() }
        val platform = FixturePlatform()
        setContent { RastrIOApp(input, platform) }
        onNodeWithText("Open Templates").performClick()
        onNodeWithText("Quick Note").performClick()
        onNodeWithTag("template-Note").performTextInput("Template receipt")
        onNodeWithText("Preview", substring = false).performScrollTo().performClick()
        waitUntil { onAllNodesWithTag("physical-canvas").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Preview settings").performScrollTo().performClick()
        onNodeWithText("Prepare selected printer").performScrollTo().performClick()
        waitUntil { onAllNodesWithText("Physical preview ready.", substring = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Back to preview").performScrollTo().performClick()
        onNodeWithText("Template receipt").assertExists()
        onNodeWithText("Markdown receipt").assertDoesNotExist()
        onNodeWithText("Print", substring = false).performClick()
        waitUntil { onAllNodesWithTag("physical-canvas").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("confirm-print").assertIsEnabled()
        onNodeWithText("Template receipt").assertExists()
        onNodeWithText("Markdown receipt").assertDoesNotExist()
        onNodeWithText("Back", substring = false).performClick()
        onNodeWithText("Back to templates").performClick()
        onNodeWithContentDescription("Back to Templates").performClick()
        onNodeWithContentDescription("Back to Home").performClick()
        onNodeWithText("Open Markdown").performClick()
        onNodeWithTag("markdown-source").assertTextContains("Markdown receipt")
        onNodeWithText("Printer preview").performClick()
        waitUntil { onAllNodesWithTag("physical-canvas").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("confirm-print").assertIsEnabled()
        onNodeWithText("Markdown receipt").assertExists()
        onNodeWithText("Template receipt").assertDoesNotExist()
        onNodeWithText("Back", substring = false).performClick()
        onNodeWithText("Back to Home").performClick()
        onNodeWithText("Open Templates").performClick()
        onNodeWithText("Quick Note").performClick()
        onNodeWithTag("template-Note").assertTextContains("Template receipt")
        onNodeWithTag("physical-canvas").assertDoesNotExist()
        onNodeWithText("Print prepared document").assertDoesNotExist()
        onNodeWithText("Preview", substring = false).performScrollTo().performClick()
        waitUntil { onAllNodesWithTag("physical-canvas").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Template receipt").assertExists()
        onNodeWithText("Markdown receipt").assertDoesNotExist()
        assertEquals(0, platform.transport.connections)
    }

    @Test fun profileAndQrChoicesPrepareOnlyTheCurrentTemplate() = runComposeUiTest {
        val input = createAuthoringSession().also { it.edit("Markdown receipt"); it.compile() }
        setContent { RastrIOApp(input) }
        onNodeWithText("Open Templates").performClick()
        onNodeWithText("QR Message").performClick()
        onNodeWithTag("template-QR payload").performTextInput("Template QR ".repeat(8))
        onNodeWithText("Wide preview").assertDoesNotExist()
        onNodeWithText("Print prepared document").assertDoesNotExist()
        onNodeWithText("Preview", substring = false).performScrollTo().performClick()
        waitUntil { onAllNodesWithTag("physical-canvas").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Preview settings").performScrollTo().performClick()
        onNodeWithText("Wide preview").performScrollTo().performClick()
        waitUntil { onAllNodesWithText("Physical preview ready.", substring = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("QR native").performScrollTo().performClick()
        waitUntil { onAllNodesWithText("PRN141", substring = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Preview target: ${SyntheticReferenceProfiles.wide80mm.display.name}").assertExists()
        onNodeWithTag("physical-canvas").assertDoesNotExist()
        onNodeWithText("QR raster").performScrollTo().performClick()
        waitUntil { onAllNodesWithText("Physical preview ready.", substring = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Back to preview").performScrollTo().performClick()
        waitUntil { onAllNodesWithTag("physical-canvas").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("physical-canvas").assertContentDescriptionEquals("Physical preview, 576 prepared dots wide")
        onNodeWithText("Markdown receipt").assertDoesNotExist()
    }
}
