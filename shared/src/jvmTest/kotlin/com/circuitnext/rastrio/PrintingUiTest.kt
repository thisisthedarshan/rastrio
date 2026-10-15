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
class PrintingUiTest {
    private class FixturePlatform(val transport: FakePrinterTransport = FakePrinterTransport()) : PrintingPlatform {
        override val profile = MutableStateFlow<PrinterProfile?>(SyntheticReferenceProfiles.narrow.copy(
            profileId = "org.rastrio.fixture.print-ui"))
        val printer = PrinterInstance("ui-fixture", "Fixture printer", PrinterTransportKind.DEVELOPMENT,
            "ui-opaque-locator", profile.value!!.profileId, profile.value!!.profileRevision)
        override val printers = MutableStateFlow(listOf(printer))
        override val selectedPrinter = MutableStateFlow<PrinterInstance?>(printer)
        override val status = MutableStateFlow("Development fixture; hardware unverified")
        override val foreground = MutableStateFlow(true)
        var selections = 0
        override fun refreshPairedPrinters() = Unit
        override fun importProfile() = Unit
        override fun selectPrinter(instance: PrinterInstance) { selections++; selectedPrinter.value = instance }
        override fun createTransport(): PrinterTransport = transport
    }

    private fun authoring() = createAuthoringSession().also { it.edit("Receipt"); it.compile() }

    @Test fun selectionPreparesImportedProfileAndPrintUsesThatPreview() = runComposeUiTest {
        val platform = FixturePlatform()
        val input = authoring()
        setContent { RastrIOApp(input, platform) }
        onNodeWithText("Open Markdown").performClick()
        onNodeWithText("Printer preview").performClick()
        waitUntil { onAllNodesWithTag("physical-canvas").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("confirm-print").assertIsEnabled()
        onNodeWithText("Fixture printer").assertExists()
        onNodeWithTag("physical-canvas").assertExists()
        onNodeWithTag("confirm-print").performClick()
        waitUntil { platform.transport.byteCount > 0 && !platform.transport.connected }
        onNodeWithText("Local transmission completed · physical printing is not confirmed").assertExists()
        assertEquals(0, platform.selections)
        assertEquals(1, platform.transport.connections)
    }

    @Test fun failureRequiresSeparateWarningAndConfirmationAcrossNavigation() = runComposeUiTest {
        val platform = FixturePlatform(FakePrinterTransport(failAfterBytes = 4))
        val input = authoring()
        setContent { RastrIOApp(input, platform) }
        onNodeWithText("Open Markdown").performClick()
        onNodeWithText("Printer preview").performClick()
        waitUntil { onAllNodesWithTag("physical-canvas").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("confirm-print").performClick()
        waitUntil { platform.transport.byteCount == 4 && !platform.transport.connected }
        onNodeWithText("Print failed · earlier paper output may exist").assertExists()
        onNodeWithTag("confirm-print").assertIsNotEnabled()
        onNodeWithText("Back").performClick()
        onNodeWithText("Printer preview").performClick()
        waitUntil { onAllNodesWithTag("physical-canvas").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Request another print").performScrollTo().performClick()
        onNodeWithText("Earlier paper output may exist").assertExists()
        assertEquals(1, platform.transport.connections)
        onNodeWithText("Keep previous result").performClick()
        assertEquals(1, platform.transport.connections)
        onNodeWithText("Request another print").performScrollTo().performClick()
        onNodeWithText("I understand; start new attempt").performClick()
        waitUntil { platform.transport.connections == 2 }
    }

    @Test fun changingSourceDisablesPrintingUntilAFreshPreparation() = runComposeUiTest {
        val platform = FixturePlatform()
        val input = authoring()
        setContent { RastrIOApp(input, platform) }
        onNodeWithText("Open Markdown").performClick()
        onNodeWithText("Printer preview").performClick()
        waitUntil { onAllNodesWithTag("physical-canvas").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("confirm-print").assertIsEnabled()
        onNodeWithText("Back").performClick()
        onNodeWithTag("markdown-source").performTextReplacement("Changed")
        onNodeWithText("Printer preview").assertIsNotEnabled()
        onNodeWithText("Compile").performClick()
        onNodeWithText("Printer preview").performClick()
        waitUntil { onAllNodesWithTag("physical-canvas").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("confirm-print").assertIsEnabled()
        assertEquals(0, platform.transport.connections)
    }

    @Test fun foregroundLossCancelsAnInFlightPrintWithoutReplay() = runComposeUiTest {
        val platform = FixturePlatform(FakePrinterTransport(writeDelayMillis = 10_000))
        val input = authoring()
        setContent { RastrIOApp(input, platform) }
        onNodeWithText("Open Markdown").performClick()
        onNodeWithText("Printer preview").performClick()
        waitUntil { onAllNodesWithTag("physical-canvas").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("confirm-print").performClick()
        waitUntil { platform.transport.writes == 1 }
        runOnIdle { platform.foreground.value = false }
        waitUntil { !platform.transport.connected }
        onNodeWithText("Print cancelled · earlier paper output may exist").assertExists()
        onNodeWithTag("confirm-print").assertIsNotEnabled()
        assertEquals(1, platform.transport.connections)
        assertEquals(0, platform.transport.byteCount)
    }
}
