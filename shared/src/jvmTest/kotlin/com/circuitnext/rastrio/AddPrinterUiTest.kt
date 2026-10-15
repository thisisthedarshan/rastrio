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

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import com.circuitnext.rastrio.printing.*
import com.circuitnext.rastrio.core.profile.PrinterProfile
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.*

private class SetupPlatform : PrintingPlatform {
    val classic = PairedPrinterDevice("classic", "BillQuick-Go", BluetoothDeviceKind.CLASSIC)
    private val allDevices = listOf(classic,
        PairedPrinterDevice("other", "Other Classic", BluetoothDeviceKind.DUAL),
        PairedPrinterDevice("ble", "Low Energy fixture", BluetoothDeviceKind.BLE_ONLY))
    override val pairedDevices = MutableStateFlow(allDevices)
    override val discoveryState = MutableStateFlow(DeviceDiscoveryState(DeviceDiscoveryStatus.READY))
    override val profile = MutableStateFlow<PrinterProfile?>(null)
    override val printers = MutableStateFlow(emptyList<PrinterInstance>())
    override val status = MutableStateFlow("BLUETOOTH_TEST_DIAGNOSTIC: fixture details")
    override val foreground = MutableStateFlow(true)
    val transport = FakePrinterTransport()
    var inspections = 0
    var cancelled = false
    var waitForCancellation = false
    override suspend fun inspectPrinter(device: PairedPrinterDevice): PrinterInspection {
        inspections++
        if (waitForCancellation) try { awaitCancellation() } finally { cancelled = true }
        return metadataInspection(device)
    }
    var refreshes = 0
    override fun refreshPairedPrinters() { refreshes++; pairedDevices.value = allDevices; discoveryState.value = DeviceDiscoveryState(DeviceDiscoveryStatus.READY) }
    override fun importProfile() = Unit
    override fun selectPrinter(instance: PrinterInstance) = Unit
    override fun createTransport(): PrinterTransport = transport
}

@OptIn(ExperimentalTestApi::class)
class AddPrinterUiTest {
    @Test fun selectionCollapsesOtherDevicesAndChangeRetainsAccessibleSelection() = runComposeUiTest {
        val platform = SetupPlatform()
        setContent { RastrIOApp(printingPlatform = platform) }
        onNodeWithText("Open Printers").performClick()
        onNodeWithText("Add Printer").performScrollTo().performClick()
        onNodeWithText("Low Energy fixture").assertDoesNotExist()
        onNodeWithTag("device-row-classic").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).performClick()
        onNodeWithText("Other Classic").assertDoesNotExist()
        onNodeWithTag("selected-device-name").assertTextEquals("BillQuick-Go")
        onNodeWithText("Inspect Printer").assertExists()
        onNodeWithText("BLUETOOTH_TEST_DIAGNOSTIC:", substring = true).assertDoesNotExist()
        onNodeWithText("Change device").performClick()
        onNodeWithTag("device-row-classic").assertIsSelected()
        onNodeWithText("Other Classic").assertExists()
        assertEquals(0, platform.inspections)
        assertEquals(0, platform.transport.connections)
        assertEquals(0, platform.transport.byteCount)
    }

    @Test fun searchFiltersRowsAndBleExplanationIsHumanReadable() = runComposeUiTest {
        val platform = SetupPlatform()
        setContent { RastrIOApp(printingPlatform = platform) }
        onNodeWithText("Open Printers").performClick()
        onNodeWithText("Add Printer").performScrollTo().performClick()
        onNodeWithTag("device-search").performTextReplacement("BILLquick")
        onNodeWithText("Other Classic").assertDoesNotExist()
        onNodeWithTag("device-row-classic").assertExists()
        onNodeWithTag("device-search").performTextReplacement("")
        onNodeWithTag("printer-setup-content").performScrollToNode(hasText("Low Energy devices (1)"))
        onNodeWithText("Low Energy devices (1)").performClick()
        onNodeWithTag("printer-setup-content").performScrollToNode(hasTestTag("incompatible-device-row"))
        onNodeWithTag("incompatible-device-row").assertExists().assertHasNoClickAction()
        onNodeWithText("BLUETOOTH_BLE_INCOMPATIBLE", substring = true).assertDoesNotExist()
    }

    @Test fun inspectionNeedsConsentAndCancellationNeverPrints() = runComposeUiTest {
        val platform = SetupPlatform().also { it.waitForCancellation = true }
        setContent { RastrIOApp(printingPlatform = platform) }
        onNodeWithText("Open Printers").performClick()
        onNodeWithText("Add Printer").performScrollTo().performClick()
        onNodeWithTag("device-row-classic").performClick()
        onNodeWithText("Inspect Printer").performClick()
        assertEquals(0, platform.inspections)
        onNodeWithText("Approve inspection").performClick()
        waitUntil { platform.inspections == 1 }
        onNodeWithText("Cancel inspection").performClick()
        waitUntil { platform.cancelled }
        onNodeWithText("Inspection cancelled").assertExists()
        assertEquals(0, platform.transport.connections)
        assertEquals(0, platform.transport.byteCount)
    }

    @Test fun selectionAndCollapsedStepRestoreAcrossActivityRecreation() = runComposeUiTest {
        val platform = SetupPlatform()
        var registry by mutableStateOf(SaveableStateRegistry(null) { true })
        var visible by mutableStateOf(true)
        setContent { CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
            if (visible) RastrIOApp(printingPlatform = platform)
        } }
        onNodeWithText("Open Printers").performClick()
        onNodeWithText("Add Printer").performScrollTo().performClick()
        onNodeWithTag("device-row-classic").performClick()
        val saved = runOnIdle { registry.performSave().also { visible = false } }
        waitForIdle()
        runOnIdle {
            platform.pairedDevices.value = emptyList()
            platform.discoveryState.value = DeviceDiscoveryState()
            registry = SaveableStateRegistry(saved) { true }; visible = true
        }
        waitUntil { platform.refreshes == 2 }
        onNodeWithTag("selected-device-name").assertTextEquals("BillQuick-Go")
        onNodeWithTag("printer-setup-step").assertTextContains("Inspect device", substring = true)
        onNodeWithText("Other Classic").assertDoesNotExist()
        assertEquals(0, platform.inspections)
        assertEquals(0, platform.transport.connections)
    }

    @Test fun keyboardCanSelectRowAndContinueIntoProfileSetup() = runComposeUiTest {
        val platform = SetupPlatform()
        setContent { RastrIOApp(printingPlatform = platform) }
        onNodeWithText("Open Printers").performClick()
        onNodeWithText("Add Printer").performScrollTo().performClick()
        onNodeWithTag("device-row-classic").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        onNodeWithTag("device-row-classic").performKeyInput { pressKey(Key.Enter) }
        onNodeWithTag("selected-device-name").assertTextEquals("BillQuick-Go")
        onNodeWithText("Continue").performClick()
        onNodeWithText("Create Printer Profile").performClick()
        onNodeWithTag("profile-field-display.name").assertTextEquals("Name", "BillQuick-Go")
        onNodeWithText("Back to printers").performClick()
        onNodeWithTag("selected-device-name").assertTextEquals("BillQuick-Go")
        assertEquals(0, platform.transport.connections)
    }
    @Test fun smallViewportAtDoubleFontScaleKeepsProgressionUsable() = runComposeUiTest {
        val platform = SetupPlatform()
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                Box(Modifier.size(360.dp, 640.dp)) { RastrIOApp(printingPlatform = platform) }
            }
        }
        onNodeWithText("Open Printers").performScrollTo().performClick()
        onNodeWithText("Add Printer").performScrollTo().performClick()
        onNodeWithTag("printer-setup-content").performScrollToNode(hasTestTag("device-row-classic"))
        onNodeWithTag("device-row-classic").performClick()
        onNodeWithText("Continue").assertIsDisplayed().performClick()
        onNodeWithTag("printer-setup-content").performScrollToNode(hasText("Create Printer Profile"))
        onNodeWithText("Create Printer Profile").assertIsDisplayed()
        assertEquals(0, platform.transport.connections)
    }
}
