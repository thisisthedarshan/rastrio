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
class PhysicalNavigationTest {
    private class Platform : PrintingPlatform {
        val narrow = SyntheticReferenceProfiles.narrow.copy(profileId = "org.rastrio.fixture.physical.narrow")
        val wide = SyntheticReferenceProfiles.wide80mm.copy(profileId = "org.rastrio.fixture.physical.wide")
        val first = PrinterInstance("first", "Fixture 384", PrinterTransportKind.DEVELOPMENT, "fixture384", narrow.profileId, narrow.profileRevision)
        val second = PrinterInstance("second", "Fixture 576", PrinterTransportKind.DEVELOPMENT, "fixture576", wide.profileId, wide.profileRevision)
        override val printers = MutableStateFlow(listOf(first, second))
        override val selectedPrinter = MutableStateFlow<PrinterInstance?>(first)
        override val profile = MutableStateFlow<PrinterProfile?>(narrow)
        override val selectedProfile = MutableStateFlow<PrinterProfile?>(narrow)
        override val status = MutableStateFlow("Development fixture")
        override val foreground = MutableStateFlow(true)
        override fun refreshPairedPrinters() = Unit
        override fun importProfile() = Unit
        override fun selectPrinter(instance: PrinterInstance) {
            selectedPrinter.value = instance
            selectedProfile.value = if (instance == second) wide else narrow
        }
        override fun createTransport(): PrinterTransport = FakePrinterTransport()
    }

    @Test fun physicalRouteSelectsConfiguredWidthsAndRetainsSourceForLogicalPreview() = runComposeUiTest {
        setContent { RastrIOApp(printingPlatform = Platform()) }
        onNodeWithText("Open Markdown").performClick()
        onNodeWithText("Printer preview").assertIsNotEnabled()
        onNodeWithTag("markdown-source").performTextInput("Receipt")
        onNodeWithText("Compile").performClick()
        onNodeWithText("Printer preview").performClick()
        waitUntil { onAllNodesWithTag("physical-canvas").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("physical-canvas").assertContentDescriptionEquals("Physical preview, 384 prepared dots wide")
        onNodeWithText("Synthetic 576 dots").assertDoesNotExist()
        onNodeWithTag("configured-printer-selector").performClick()
        onNodeWithText("Fixture 576").performClick()
        waitUntil { onAllNodesWithContentDescription("Physical preview, 576 prepared dots wide").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Back").performClick()
        onNodeWithTag("markdown-source").assertTextContains("Receipt")
        onNodeWithText("Open logical preview").performClick()
        onNodeWithTag("logical-canvas").assertExists()
        onNodeWithText("Receipt").assertExists()
    }

    @Test fun editingSourceInvalidatesPhysicalAndLogicalRoutes() = runComposeUiTest {
        setContent { RastrIOApp(printingPlatform = Platform()) }
        onNodeWithText("Open Markdown").performClick()
        onNodeWithTag("markdown-source").performTextInput("Old")
        onNodeWithText("Compile").performClick()
        onNodeWithText("Printer preview").performClick()
        onNodeWithText("Back").performClick()
        onNodeWithTag("markdown-source").performTextReplacement("New")
        onNodeWithText("Printer preview").assertIsNotEnabled()
        onNodeWithText("Open logical preview").assertIsNotEnabled()
        onNodeWithText("Compile").performClick()
        onNodeWithText("Printer preview").performClick()
        waitUntil { onAllNodesWithTag("physical-canvas").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("New").assertExists()
        onNodeWithText("Old").assertDoesNotExist()
    }

    @Test fun cp437PhysicalPreviewRemainsUsableWhenLogicalLayoutRejectsTheSource() = runComposeUiTest {
        val session = createAuthoringSession().also { it.edit("Café"); it.compile() }
        setContent { RastrIOApp(session, Platform()) }
        onNodeWithText("Open Markdown").performClick()
        onNodeWithText("Layout failed").assertExists()
        onNodeWithText("Open logical preview").assertIsNotEnabled()
        onNodeWithText("Printer preview").assertIsEnabled().performClick()
        waitUntil { onAllNodesWithTag("physical-canvas").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Café").assertExists()
        onNodeWithText("Back").performClick()
        onNodeWithTag("markdown-source").assertTextContains("Café")
    }

    @Test fun unsupportedPreparationShowsFailureAndRetainsSourceAcrossHome() = runComposeUiTest {
        val session = createAuthoringSession().also { it.edit("世界"); it.compile() }
        setContent { RastrIOApp(session, Platform()) }
        onNodeWithText("Open Markdown").performClick()
        onNodeWithText("Printer preview").performClick()
        waitUntil { onAllNodesWithTag("preview-updating").fetchSemanticsNodes().isEmpty() }
        onNodeWithTag("confirm-print").assertIsNotEnabled()
        onNodeWithTag("physical-canvas").assertDoesNotExist()
        onNodeWithText("Back").performClick()
        onNodeWithTag("markdown-source").assertTextContains("世界")
        onNodeWithText("Back to Home").performClick()
        onNodeWithText("Open Markdown").performClick()
        onNodeWithTag("markdown-source").assertTextContains("世界")
        onNodeWithText("Printer preview").assertIsEnabled()
    }

    @Test fun manualAndSupportedAutomaticCutChoicesDisplayTheirPreparedSemantics() = runComposeUiTest {
        val session = createAuthoringSession().also { it.edit("Receipt"); it.compile() }
        setContent { RastrIOApp(session, Platform()) }
        onNodeWithText("Open Markdown").performClick()
        onNodeWithText("Printer preview").performClick()
        waitUntil { onAllNodesWithTag("physical-canvas").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("More print options").performClick()
        onNodeWithText("Automatic full").assertDoesNotExist()
        onNodeWithText("Manual guide").performScrollTo().performClick()
        waitUntil { onAllNodesWithContentDescription("Manual cut guide, printed content").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("configured-printer-selector").performClick()
        onNodeWithText("Fixture 576").performClick()
        waitUntil { onAllNodesWithContentDescription("Physical preview, 576 prepared dots wide").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Automatic partial").performScrollTo().performClick()
        waitUntil { onAllNodesWithContentDescription("Automatic partial cut, non-printing annotation").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithContentDescription("Manual cut guide, printed content").assertDoesNotExist()
    }
}
