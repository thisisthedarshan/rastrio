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

import androidx.compose.ui.semantics.SemanticsProperties
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
        override val printers = MutableStateFlow(listOf(printer))
        override val status = MutableStateFlow("Development fixture; hardware unverified")
        override val foreground = MutableStateFlow(true)
        val transport = FakePrinterTransport()
        override fun refreshPairedPrinters() = Unit
        override fun importProfile() = Unit
        override fun selectPrinter(instance: PrinterInstance) = Unit
        override fun createTransport(): PrinterTransport = transport
    }

    @Test fun markdownPlanCannotBecomeAnUnbuiltTemplatePreviewOrPrint() = runComposeUiTest {
        val input = createAuthoringSession().also { it.edit("Markdown receipt"); it.compile() }
        val platform = FixturePlatform()
        setContent { RastrIOApp(input, platform) }
        onNodeWithText("Open Markdown").performClick()
        onNodeWithText("Printer preview").performClick()
        onNodeWithText("Use Workspace printer").performScrollTo().performClick()
        waitUntil { onAllNodesWithText("Print prepared document").fetchSemanticsNodes().any {
            !it.config.contains(SemanticsProperties.Disabled)
        } }
        onNodeWithText("Print prepared document").performScrollTo().assertIsEnabled()
        onNodeWithText("Back to Markdown").performScrollTo().performClick()
        onNodeWithText("Back to Home").performClick()
        onNodeWithText("Open Templates").performClick()
        onNodeWithText("Open physical preview").performScrollTo().assertIsNotEnabled()
        onNodeWithText("Print prepared document").performScrollTo().assertIsNotEnabled()
        onNodeWithText("Wide preview").performScrollTo().performClick()
        onNodeWithText("QR raster").performScrollTo().performClick()
        onNodeWithText("Open physical preview").assertIsNotEnabled()
        onNodeWithText("Preview template to prepare its current content").assertExists()
        onNodeWithText("Print prepared document").performScrollTo().assertIsNotEnabled()
        onNodeWithText("Prepare selected printer").performScrollTo().performClick()
        onNodeWithText("Open physical preview").performScrollTo().assertIsNotEnabled()
        onNodeWithText("Print prepared document").performScrollTo().assertIsNotEnabled()
        assertEquals(0, platform.transport.connections)
    }

    @Test fun bothWorkspaceTransitionsRequireTheirOwnFreshDocumentAndRetainDrafts() = runComposeUiTest {
        val input = createAuthoringSession().also { it.edit("Markdown receipt"); it.compile() }
        val platform = FixturePlatform()
        setContent { RastrIOApp(input, platform) }
        onNodeWithText("Open Templates").performClick()
        onNodeWithText("Quick Note").performClick()
        onNodeWithTag("template-Note").performTextInput("Template receipt")
        onNodeWithText("Use Workspace printer").performScrollTo().performClick()
        onNodeWithText("Preview template").performScrollTo().performClick()
        waitUntil { onAllNodesWithText("Physical preview ready.", substring = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Open physical preview").performScrollTo().performClick()
        onNodeWithText("Template receipt").assertExists()
        onNodeWithText("Markdown receipt").assertDoesNotExist()
        onNodeWithText("Print prepared document").performScrollTo().assertIsEnabled()
        onNodeWithText("Back to templates").performClick()
        onNodeWithText("Back to Home").performScrollTo().performClick()
        onNodeWithText("Open Markdown").performClick()
        onNodeWithTag("markdown-source").assertTextContains("Markdown receipt")
        onNodeWithText("Printer preview").performClick()
        onNodeWithText("Open physical preview").performScrollTo().performClick()
        onNodeWithText("Markdown receipt").assertExists()
        onNodeWithText("Template receipt").assertDoesNotExist()
        onNodeWithText("Back to printer setup").performClick()
        onNodeWithText("Back to Markdown").performScrollTo().performClick()
        onNodeWithText("Back to Home").performClick()
        onNodeWithText("Open Templates").performClick()
        onNodeWithTag("template-Note").assertTextContains("Template receipt")
        onNodeWithText("Open physical preview").performScrollTo().assertIsNotEnabled()
        onNodeWithText("Print prepared document").performScrollTo().assertIsNotEnabled()
        onNodeWithText("Preview template").performScrollTo().performClick()
        waitUntil { onAllNodesWithText("Physical preview ready.", substring = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Open physical preview").performScrollTo().performClick()
        onNodeWithText("Template receipt").assertExists()
        onNodeWithText("Markdown receipt").assertDoesNotExist()
        assertEquals(0, platform.transport.connections)
    }

    @Test fun prebuildProfileAndQrChoicesAreRetainedWithoutPreparingMarkdown() = runComposeUiTest {
        val input = createAuthoringSession().also { it.edit("Markdown receipt"); it.compile() }
        setContent { RastrIOApp(input) }
        onNodeWithText("Open Templates").performClick()
        onNodeWithText("Wide preview").performScrollTo().performClick()
        onNodeWithText("QR native").performScrollTo().performClick()
        onNodeWithText("Open physical preview").assertIsNotEnabled()
        onNodeWithText("Preview target: ${SyntheticReferenceProfiles.wide80mm.display.name}").assertExists()
        onNodeWithTag("template-list").performScrollToIndex(0)
        onNodeWithText("QR Message").performClick()
        onNodeWithTag("template-QR payload").performTextInput("Template QR ".repeat(8))
        onNodeWithText("Preview template").performScrollTo().performClick()
        waitUntil { onAllNodesWithText("PRN141", substring = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Open physical preview").performScrollTo().assertIsNotEnabled()
        onNodeWithText("QR raster").performScrollTo().performClick()
        waitUntil { onAllNodesWithText("Physical preview ready.", substring = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Open physical preview").performScrollTo().performClick()
        onNodeWithTag("physical-canvas").assertContentDescriptionEquals("Physical preview, 576 prepared dots wide")
    }
}
