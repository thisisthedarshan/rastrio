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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.circuitnext.rastrio.core.profile.PrinterProfile
import com.circuitnext.rastrio.core.profile.SyntheticReferenceProfiles
import com.circuitnext.rastrio.printing.*

@Composable
internal fun PrinterManagementScreen(profile: PrinterProfile, printingControls: @Composable () -> Unit,
    onBack: () -> Unit, onLab: () -> Unit, onProfile: (PrinterProfile) -> Unit,
    onCreate: () -> Unit, drafts: List<ProfileDraft>, onEdit: (ProfileDraft) -> Unit,
    onDuplicate: () -> Unit, onDuplicateDraft: (ProfileDraft) -> Unit,
    pairedDevices: List<PairedPrinterDevice>, addingPrinter: Boolean, pendingDevice: PairedPrinterDevice?,
    physicalProfile: PrinterProfile?, onAdd: () -> Unit, onDevice: (PairedPrinterDevice) -> Unit, onSavePrinter: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().testTag("printer-management-list").padding(16.dp).widthIn(max = 960.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Printers and profiles", style = MaterialTheme.typography.headlineMedium)
            AuthoringButton("Back to Home", onClick = onBack)
            Text("Selected preview profile: ${profile.display.name}", style = MaterialTheme.typography.titleMedium)
            Text("${profile.profileId} · revision ${profile.profileRevision}")
            Text("${profile.geometry.printableWidthDots} dots · ${profile.geometry.horizontalDpi} × ${profile.geometry.verticalDpi} dpi")
            Text(if (isSyntheticProfile(profile)) "Synthetic preview only · no physical capability verification"
                else "Imported configuration · physical capabilities unverified")
            Text("Preparation validates this configuration and creates the effective physical plan. A printer connection is only needed to transmit it.")
        }
        item {
            AuthoringButton("Add Printer", onClick = onAdd)
            if (addingPrinter) {
                Text("1. Select Device → 2. Create/Select Profile → 3. Validate/Calibrate → 4. Save Printer")
                Text("Pair in Android settings first. Selection does not connect or print. Bluetooth Classic SPP is required; a BLE-only device needs a separately verified GATT transport.")
                if (pairedDevices.isEmpty()) Text("No paired devices available. Check Nearby devices permission, enable Bluetooth and refresh.")
                for (device in pairedDevices) {
                    AuthoringButton("Select ${device.displayName}", device.supportsClassic) { onDevice(device) }
                    if (!device.supportsClassic) Text("BLUETOOTH_BLE_INCOMPATIBLE: BLE-only device; Classic SPP is unavailable")
                }
                if (pendingDevice != null) Text("Selected device: ${pendingDevice.displayName}")
                Text(if (physicalProfile == null) "Create or import a valid profile before saving this printer" else "Selected profile: ${physicalProfile.display.name}")
                AuthoringButton("Save Printer", pendingDevice?.supportsClassic == true && physicalProfile != null, onSavePrinter)
            }
            AuthoringButton("Create Printer Profile", onClick = onCreate)
            AuthoringButton("Duplicate selected profile", onClick = onDuplicate)
            for (draft in drafts) {
                AuthoringButton("Edit ${draft["display.name"].ifBlank { draft.draftId }}") { onEdit(draft) }
                AuthoringButton("Duplicate draft ${draft["display.name"].ifBlank { draft.draftId }}") { onDuplicateDraft(draft) }
            }
        }
        item { printingControls() }
        item {
            Text("Software preview targets", style = MaterialTheme.typography.titleMedium)
            for (candidate in SyntheticReferenceProfiles.all) AuthoringButton(candidate.display.name) { onProfile(candidate) }
            Text("Synthetic values belong to test fixtures. They are not Helett H50i specifications and cannot authorize physical printing.")
            AuthoringButton("Open Printer Lab", onClick = onLab)
        }
    }
}

/** Evidence and bounded test drafts only; this screen never opens a transport or prints. */
@Composable
internal fun PrinterLabScreen(onBack: () -> Unit, onQrFixture: () -> Unit, calibrationControls: @Composable () -> Unit = {}) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp).widthIn(max = 960.dp).testTag("printer-lab-list"),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Printer Lab", style = MaterialTheme.typography.headlineMedium)
            AuthoringButton("Back to printers", onClick = onBack)
            Text("Helett H50i BillQuick Go", style = MaterialTheme.typography.titleLarge)
            Text("Provisional draft · APP141: Mandatory geometry and reliable host-command evidence are missing.",
                color = LocalAuthoringColors.current.warning)
            Text("Operator-supplied physical self-test, 2026-10-13; corroborates the 2026-10-02 inventory. No RastrIO hardware run is claimed.")
            Text("Reported: 2 inches / 58 mm paper; 48 mm print width; default font 12×24; CP437; USB and Bluetooth; QR and EAN13 firmware capabilities.")
            Text("Unknown: horizontal DPI, vertical DPI, printable dot width, font/code-page selectors, raster strategy and band limits, native QR/barcode host commands, transport chunk size and pacing.")
            Text("Density 5 is not DPI. The draft enables no cutter and cannot be selected as a usable physical profile or exported as .tcfg.")
        }
        item {
            Text("Calibration prerequisites", style = MaterialTheme.typography.titleMedium)
            Text("HW-035: capture self-test inventory. HW-037 / HW-012: independently establish both DPI axes and reliable printable dot width. Millimetres alone cannot supply dot geometry.")
            Text("HW-001/002/004: verify native font cells, advance and styles. HW-036: verify the documented CP437 selector and a bounded character grid.")
            Text("HW-007: verify raster packing and command compatibility. HW-018/019: print raster QR once and scan externally. HW-017: native QR requires independently verified host parameters.")
            Text("HW-029: measure Bluetooth write chunks and pacing separately from printer raster bands. EAN13 host-command testing waits for a prepared barcode implementation.")
            Text("Record the actual unit, profile/revision/overrides, transport, captured bytes and observed paper. Bytes transmitted do not prove physical output. Inspect ambiguous output before any explicitly confirmed new attempt. Never promote capabilities automatically.")
            Text("Detailed procedures and evidence are maintained in docs/HARDWARE_TESTS.md and docs/ESC_POS_NOTES.md.")
        }
        item { calibrationControls() }
        item {
            Text("Bounded QR test draft", style = MaterialTheme.typography.titleMedium)
            Text("Create the maintained HW-018 30 mm High-EC raster QR draft. Its example.invalid URL is opaque test data and is never opened. Prepare, inspect the exact-dot preview and explicitly print once using an evidence-backed associated profile. Synthetic preview requires no connected printer.")
            AuthoringButton("Create raster QR test draft", onClick = onQrFixture)
        }
    }
}
