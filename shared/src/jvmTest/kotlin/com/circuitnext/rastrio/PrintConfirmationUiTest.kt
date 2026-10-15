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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.v2.runComposeUiTest
import com.circuitnext.rastrio.core.profile.PrinterProfile
import com.circuitnext.rastrio.core.profile.SyntheticReferenceProfiles
import com.circuitnext.rastrio.core.printer.PrintOptions
import com.circuitnext.rastrio.presentation.createAuthoringSession
import com.circuitnext.rastrio.printing.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class PrintConfirmationUiTest {
    private class Platform : PrintingPlatform {
        val transport = FakePrinterTransport()
        override val profile = MutableStateFlow<PrinterProfile?>(SyntheticReferenceProfiles.narrow.copy(profileId = "org.rastrio.fixture.confirm"))
        val printer = PrinterInstance("saved", "Saved printer", PrinterTransportKind.DEVELOPMENT, "fixture", profile.value!!.profileId, 1)
        override val selectedProfile = MutableStateFlow(profile.value)
        val secondProfile = SyntheticReferenceProfiles.wide80mm.copy(profileId = "org.rastrio.fixture.second")
        val second = PrinterInstance("second", "Second printer", PrinterTransportKind.DEVELOPMENT, "fixture-second",
            secondProfile.profileId, secondProfile.profileRevision, defaults = PrinterPrintDefaults(finalFeedDots = 17))
        override val selectedPrinter = MutableStateFlow<PrinterInstance?>(printer)
        override val printers = MutableStateFlow(listOf(printer))
        override val status = MutableStateFlow("")
        override val foreground = MutableStateFlow(true)
        override fun refreshPairedPrinters() = Unit
        override fun importProfile() = Unit
        override fun selectPrinter(instance: PrinterInstance) {
            selectedPrinter.value = instance
            selectedProfile.value = if (instance.id == second.id) secondProfile else profile.value
        }
        override fun createTransport(): PrinterTransport = transport
    }

    @Test fun keyboardResizePreservesTheChosenLayoutAndInputComposition() {
        assertFalse(compactPrintLayout(411.dp, 600.dp, 0.dp))
        assertFalse(compactPrintLayout(411.dp, 280.dp, 320.dp))
        assertTrue(compactPrintLayout(900.dp, 220.dp, 0.dp))
        assertTrue(compactPrintLayout(900.dp, 100.dp, 120.dp))
    }

    @Test fun landscapeConfirmationKeepsPreviewVisibleWithExpandedOptions() = runComposeUiTest {
        val platform = Platform()
        val image = com.circuitnext.rastrio.presentation.ImagePrintSession()
        image.select(ImageInput(com.circuitnext.rastrio.core.raster.DecodedImage(2, 2, IntArray(4) { -1 })))
        image.prepare()
        var portrait by mutableStateOf(false)
        setContent { CompositionLocalProvider(LocalDensity provides Density(1f, if (portrait) 1.3f else 1f)) {
            MaterialTheme { Box(Modifier.requiredSize(if (portrait) 360.dp else 900.dp, if (portrait) 600.dp else 365.dp)) {
            PrintConfirmationScreen(platform.printers.value, platform.printer, platform.profile.value,
                image.state.result, false, true, null, PrintSessionState(), PrinterPresence.NOT_CHECKED,
                {}, {}, {}, {}, {}, {}, PrintOptions(), {})
        } } } }
        fun assertUsefulPreview() {
            val bounds = onNodeWithTag("physical-canvas").getUnclippedBoundsInRoot()
            assertTrue(bounds.bottom - bounds.top >= 60.dp, "Landscape confirmation raster: $bounds")
            onNodeWithTag("confirm-print").assertIsDisplayed()
        }
        assertUsefulPreview()
        onNodeWithText("More print options").performClick()
        assertUsefulPreview()
        onNodeWithText("Manual guide").performScrollTo().assertIsDisplayed()
        runOnIdle { portrait = true }
        assertUsefulPreview()
        onNodeWithText("Manual guide").performScrollTo().assertIsDisplayed()
    }

    @Test fun rememberedPrinterAutomaticallyPreviewsAndExplicitPrintSendsOnce() = runComposeUiTest {
        val platform = Platform()
        val input = createAuthoringSession().also { it.edit("Receipt"); it.compile() }
        setContent { RastrIOApp(input, platform) }
        onNodeWithText("Open Markdown").performClick()
        onNodeWithText("Printer preview").performClick()
        waitUntil { onAllNodesWithTag("physical-canvas").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("confirm-print").assertIsEnabled()
        onNodeWithText("Saved printer").assertExists()
        onNodeWithText("Manage printers").assertExists()
        onNodeWithText("Import printer profile").assertDoesNotExist()
        onNodeWithText("Synthetic 384 dots").assertDoesNotExist()
        onNodeWithText("Cancel print").assertDoesNotExist()
        assertEquals(0, platform.transport.connections)
        onNodeWithTag("confirm-print").performClick()
        waitUntil { platform.transport.byteCount > 0 && !platform.transport.connected }
        onNodeWithTag("confirm-print").assertIsNotEnabled()
        assertEquals(1, platform.transport.connections)
    }

    @Test fun absentConfigurationOffersAddPrinterWithoutSyntheticTarget() = runComposeUiTest {
        val platform = Platform().also { it.printers.value = emptyList(); it.selectedPrinter.value = null }
        val input = createAuthoringSession().also { it.edit("Receipt"); it.compile() }
        setContent { RastrIOApp(input, platform) }
        onNodeWithText("Open Markdown").performClick()
        onNodeWithText("Printer preview").performClick()
        onNodeWithText("Add Printer").assertExists()
        onNodeWithTag("confirm-print").assertIsNotEnabled()
        onNodeWithTag("physical-canvas").assertDoesNotExist()
        assertEquals(0, platform.transport.connections)
    }
    @Test fun switchingConfiguredPrinterUsesItsGeometryAndDefaultsWhileImportsStayIsolated() = runComposeUiTest {
        val platform = Platform().also { it.printers.value = listOf(it.printer, it.second) }
        val input = createAuthoringSession().also { it.edit("Receipt"); it.compile() }
        setContent { RastrIOApp(input, platform) }
        onNodeWithText("Open Markdown").performClick()
        onNodeWithText("Printer preview").performClick()
        waitUntil { onAllNodesWithTag("physical-canvas").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("physical-canvas").assertContentDescriptionEquals("Physical preview, 384 prepared dots wide")
        onNodeWithTag("configured-printer-selector").performClick()
        onNodeWithText("Second printer").performClick()
        waitUntil { onAllNodesWithContentDescription("Physical preview, 576 prepared dots wide").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("More print options").performClick()
        onNodeWithText("17").assertExists()
        runOnIdle { platform.profile.value = platform.profile.value!!.copy(profileRevision = 99) }
        onNodeWithTag("physical-canvas").assertContentDescriptionEquals("Physical preview, 576 prepared dots wide")
        onNodeWithTag("confirm-print").assertIsEnabled()
        assertEquals(0, platform.transport.connections)
    }

    @Test fun changingOptionsInvalidatesThenReplacesTheDisplayedPlanWithoutSending() = runComposeUiTest {
        val platform = Platform()
        val input = createAuthoringSession().also { it.edit("Receipt"); it.compile() }
        setContent { RastrIOApp(input, platform) }
        onNodeWithText("Open Markdown").performClick()
        onNodeWithText("Printer preview").performClick()
        waitUntil { onAllNodesWithTag("physical-canvas").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("More print options").performClick()
        onNodeWithText("Manual guide").performScrollTo().performClick()
        waitUntil { onAllNodesWithContentDescription("Manual cut guide, printed content").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("confirm-print").assertIsEnabled()
        assertEquals(0, platform.transport.connections)
    }

}
