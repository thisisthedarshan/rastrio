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

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.circuitnext.rastrio.printing.*

internal enum class PrinterSetupStep(val label: String) { DEVICE("Select device"), INSPECT("Inspect device"), PROFILE("Choose profile"), VERIFY("Verify and save") }
internal fun deviceCandidates(devices: List<PairedPrinterDevice>, search: String): List<PairedPrinterDevice> =
    devices.take(512).distinctBy { it.locator }.filter { it.displayName.contains(search.trim(), ignoreCase = true) }
        .sortedWith(compareBy<PairedPrinterDevice> { when (it.kind) {
            BluetoothDeviceKind.CLASSIC, BluetoothDeviceKind.DUAL -> 0
            BluetoothDeviceKind.UNKNOWN -> 1
            BluetoothDeviceKind.BLE_ONLY -> 2
        } }.thenBy { it.displayName.lowercase() })

internal fun deviceTypeLabel(kind: BluetoothDeviceKind) = when (kind) {
    BluetoothDeviceKind.CLASSIC -> "Bluetooth Classic"
    BluetoothDeviceKind.DUAL -> "Bluetooth Classic and Low Energy"
    BluetoothDeviceKind.BLE_ONLY -> "Bluetooth Low Energy"
    BluetoothDeviceKind.UNKNOWN -> "Bluetooth type unknown"
}

internal fun deviceKindLabel(kind: BluetoothDeviceKind) = when (kind) {
    BluetoothDeviceKind.CLASSIC -> "Bluetooth Classic · SPP not yet inspected"
    BluetoothDeviceKind.DUAL -> "Classic and Low Energy · SPP not yet inspected"
    BluetoothDeviceKind.BLE_ONLY -> "Bluetooth Low Energy · current connection unsupported"
    BluetoothDeviceKind.UNKNOWN -> "Bluetooth type unknown · service inspection needed"
}

@Composable
internal fun AddPrinterScreen(
    devices: List<PairedPrinterDevice>, discovery: DeviceDiscoveryState, selected: PairedPrinterDevice?,
    step: PrinterSetupStep, onStep: (PrinterSetupStep) -> Unit, listExpanded: Boolean,
    onExpand: (Boolean) -> Unit, onDevice: (PairedPrinterDevice) -> Unit, onRefresh: () -> Unit,
    inspection: PrinterInspection?, inspecting: Boolean, inspectionStatus: String, onInspect: () -> Unit,
    onCancelInspection: () -> Unit, profileName: String?, onCreate: () -> Unit, onImport: () -> Unit,
    drafts: List<ProfileDraft>, onEdit: (ProfileDraft) -> Unit, onLab: () -> Unit, onSelfTestDraft: () -> Unit,
    onSavePrinter: () -> Unit, onBack: () -> Unit, technicalStatus: String,
) {
    var search by rememberSaveable { mutableStateOf("") }
    var showBle by rememberSaveable { mutableStateOf(false) }
    var details by rememberSaveable { mutableStateOf(false) }
    var consent by remember { mutableStateOf(false) }
    val roles = LocalAuthoringColors.current
    val actionColors = ButtonDefaults.buttonColors(disabledContainerColor = roles.disabledSurface, disabledContentColor = roles.disabledText)
    val selectedAvailable = selected != null && selected.supportsClassic && devices.any { it.locator == selected.locator }
    val candidates = remember(devices, search) { deviceCandidates(devices, search) }
    Column(Modifier.fillMaxSize().widthIn(max = 960.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Add Printer", style = MaterialTheme.typography.headlineMedium)
        Text("Step ${step.ordinal + 1} of 4 · ${step.label}", style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag("printer-setup-step"))
        LazyColumn(Modifier.weight(1f).fillMaxWidth().selectableGroup().testTag("printer-setup-content"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                if (selected != null && !listExpanded) {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(selected.displayName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("selected-device-name"))
                            Text(deviceTypeLabel(selected.kind), style = MaterialTheme.typography.bodySmall)
                            Text(if (!selectedAvailable) "Selected device is unavailable. Restore Bluetooth permission or pairing, then refresh."
                                else if (inspection?.spp == ServicePresence.PRESENT) "Cached SPP service advertised; connection not tested"
                                else "No printer connection or print output started", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { onExpand(true); onStep(PrinterSetupStep.DEVICE) }) { Text("Change device") }
                        }
                    }
                }
            }
            if (listExpanded || selected == null) {
                item {
                    OutlinedTextField(search, { if (it.length <= 256) search = it }, singleLine = true,
                        label = { Text("Search paired devices") }, modifier = Modifier.fillMaxWidth().testTag("device-search"))
                    Text("Choose a paired device. A name or Bluetooth type does not prove printer compatibility.", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onRefresh, enabled = discovery.status != DeviceDiscoveryStatus.LOADING) { Text("Refresh paired devices") }
                    when (discovery.status) {
                        DeviceDiscoveryStatus.LOADING -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Loading paired devices…") }
                        DeviceDiscoveryStatus.PERMISSION_REQUIRED -> Text("Allow Nearby devices, then refresh the list.")
                        DeviceDiscoveryStatus.DISABLED -> Text("Turn on Bluetooth in Android settings, then refresh.")
                        DeviceDiscoveryStatus.UNAVAILABLE -> Text("Bluetooth is unavailable on this device.")
                        DeviceDiscoveryStatus.FAILED -> Text("The device list could not be read. Refresh to try again.")
                        else -> if (devices.isEmpty()) Text("No paired devices. Pair your printer in Android settings, then refresh.")
                    }
                    if (devices.isNotEmpty() && candidates.isEmpty()) Text("No devices match your search.")
                }
                items(candidates.filter { it.kind != BluetoothDeviceKind.BLE_ONLY }, key = { it.locator }) { device ->
                    DeviceRow(device, selected?.locator == device.locator) { onDevice(device); onExpand(false); onStep(PrinterSetupStep.INSPECT) }
                }
                val ble = candidates.filter { it.kind == BluetoothDeviceKind.BLE_ONLY }
                if (ble.isNotEmpty()) {
                    item { TextButton(onClick = { showBle = !showBle }) { Text(if (showBle) "Hide Low Energy devices (${ble.size})" else "Low Energy devices (${ble.size})") } }
                    if (showBle) {
                        item { Text("These devices use Bluetooth Low Energy. RastrIO's current printing connection requires Bluetooth Classic SPP.", style = MaterialTheme.typography.bodyMedium) }
                        items(ble, key = { it.locator }) { device -> DeviceRow(device, false, null) }
                    }
                }
            } else when (step) {
                PrinterSetupStep.DEVICE -> item { Text("Your device selection is retained. Continue to inspection.") }
                PrinterSetupStep.INSPECT -> {
                    item {
                        Text("Find what the device can report", style = MaterialTheme.typography.titleLarge)
                        if (inspection == null) Text("Check paired metadata and advertised services. Print geometry and commands need separate evidence.")
                        else {
                            Text(if (inspection.spp == ServicePresence.PRESENT) "Cached SPP advertisement found. Connection compatibility is unverified."
                                else "SPP support has not been established.")
                            Text("Resolution, printable dots and host commands still need evidence.")
                            Text("Printer identification queries are unsupported.", style = MaterialTheme.typography.bodySmall)
                        }
                        if (inspectionStatus.isNotBlank()) Text(inspectionStatus)
                        if (inspecting) { LinearProgressIndicator(Modifier.fillMaxWidth()); TextButton(onCancelInspection) { Text("Cancel inspection") } }
                        else Button(onClick = { consent = true }, enabled = selectedAvailable, colors = actionColors) { Text("Inspect Printer") }
                        Text("You can continue without inspection and save an incomplete draft.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                PrinterSetupStep.PROFILE -> {
                    item {
                        Text("Create or choose a profile", style = MaterialTheme.typography.titleLarge)
                        Text("Keep unknown values blank. Detected names are suggestions; capability values need documented or physical evidence.")
                        if (profileName != null) Text("Current validated configuration: $profileName")
                        Button(onCreate, colors = actionColors) { Text("Create Printer Profile") }
                        TextButton(onImport) { Text("Import existing profile") }
                        Text("Start with suggested 2-inch paper, font and charset values. Review them for your printer; geometry and commands remain unknown.", style = MaterialTheme.typography.bodySmall)
                        TextButton(onSelfTestDraft) { Text("Start with a 2-inch draft") }
                    }
                    items(drafts, key = { it.draftId }) { draft -> TextButton({ onEdit(draft) }) { Text("Edit ${draft["display.name"].ifBlank { "saved draft" }}") } }
                }
                PrinterSetupStep.VERIFY -> item {
                    Text("Verify before ordinary printing", style = MaterialTheme.typography.titleLarge)
                    Text(if (profileName == null) "A validated profile is required to save this printer. Save your draft and establish the missing properties in Printer Lab."
                        else "Selected configuration: $profileName. Importing a profile does not prove this physical device supports its commands.")
                    Text("Resolution and printable dots determine geometry. Font and charset selectors determine native text. Lab tests that transmit a sample require separate approval and paper inspection.")
                    if (technicalStatus.contains("could not be saved")) Text("The printer association could not be saved. Your draft is retained; try saving again.")
                    if (!selectedAvailable) Text("Restore the selected device's pairing and Nearby devices permission before saving.")
                    TextButton(onLab) { Text("Open guided calibration") }
                    if (profileName == null) TextButton(onCreate) { Text("Create or complete profile") }
                }
            }
            item {
                TextButton(onClick = { details = !details }) { Text(if (details) "Hide technical details" else "Technical details") }
                if (details) {
                    Text(technicalStatus, style = MaterialTheme.typography.bodySmall)
                    inspection?.let { Text(it.explanation, style = MaterialTheme.typography.bodySmall) }
                    Text("Discovery: ${discovery.status}; services: ${inspection?.serviceDiscovery ?: "not inspected"}; SPP: ${inspection?.spp ?: "unknown"}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = {
                if (step == PrinterSetupStep.DEVICE) onBack()
                else { onStep(PrinterSetupStep.entries[step.ordinal - 1]); if (step == PrinterSetupStep.INSPECT) onExpand(true) }
            }, modifier = Modifier.weight(1f).authoringFocus()) { Text("Back") }
            if (step == PrinterSetupStep.VERIFY && !listExpanded) Button(onSavePrinter,
                enabled = selectedAvailable && profileName != null, colors = actionColors, modifier = Modifier.weight(1f).authoringFocus()) { Text("Save Printer") }
            else Button(onClick = { onExpand(false); onStep(PrinterSetupStep.entries[minOf(step.ordinal + 1, 3)]) },
                enabled = selected != null && !inspecting, colors = actionColors, modifier = Modifier.weight(1f).authoringFocus()) { Text("Continue") }
        }
    }
    if (consent) AlertDialog(onDismissRequest = { consent = false }, title = { Text("Inspect this device?") },
        text = { Text("Android will request advertised Bluetooth services for the selected device. This may contact it. No printer commands, reset, feed, cut or print bytes are sent. Results may be cached. Inspection stops after ten seconds and can be cancelled.") },
        confirmButton = { TextButton(onClick = { consent = false; onInspect() }) { Text("Approve inspection") } },
        dismissButton = { TextButton(onClick = { consent = false }) { Text("Cancel") } })
}

@Composable
internal fun DeviceRow(device: PairedPrinterDevice, selected: Boolean, onSelect: (() -> Unit)?) {
    Surface(color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).then(if (onSelect != null) Modifier.authoringFocus().selectable(
            selected = selected, onClick = onSelect, role = Role.RadioButton).testTag("device-row-${device.locator}")
            else Modifier.testTag("incompatible-device-row")).padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (onSelect != null) RadioButton(selected, onClick = null)
            Column(Modifier.weight(1f)) {
                Text(device.displayName, style = MaterialTheme.typography.titleSmall)
                Text(deviceKindLabel(device.kind), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
