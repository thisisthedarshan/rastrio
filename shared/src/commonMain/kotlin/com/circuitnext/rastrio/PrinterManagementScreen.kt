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
import androidx.compose.material3.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
    onAdd: () -> Unit, printers: List<PrinterInstance> = emptyList(),
    onSelect: (PrinterInstance) -> Unit = {}, importedProfile: PrinterProfile? = null,
    onImport: () -> Unit = {}, onExport: (() -> Unit)? = null,
    onDeleteDraft: (ProfileDraft) -> Unit = {}, onDeleteProfile: (() -> Unit)? = null,
    managementStatus: String = "", selectedPrinter: PrinterInstance? = null,
    selectedProfile: PrinterProfile? = null,
    onDefaults: (PrinterInstance, PrinterPrintDefaults) -> Unit = { _, _ -> },
    onRemove: (PrinterInstance) -> Unit = {},
    onAssignProfile: (PrinterInstance, PrinterProfile) -> Unit = { _, _ -> }) {
    var tools by rememberSaveable { mutableStateOf(false) }
    var previewTargets by rememberSaveable { mutableStateOf(false) }
    var profileMenu by remember { mutableStateOf(false) }
    var deletingDraft by remember { mutableStateOf<ProfileDraft?>(null) }
    var deletingProfile by remember { mutableStateOf(false) }
    var deletingPrinter by remember { mutableStateOf<PrinterInstance?>(null) }
    if (deletingPrinter != null) AlertDialog(onDismissRequest = { deletingPrinter = null },
        title = { Text("Remove printer?") }, text = { Text("Remove this saved connection and defaults? Bluetooth pairing and profile drafts are preserved.") },
        confirmButton = { TextButton(onClick = { deletingPrinter?.let(onRemove); deletingPrinter = null }) { Text("Remove") } },
        dismissButton = { TextButton(onClick = { deletingPrinter = null }) { Text("Cancel") } })
    if (deletingDraft != null || deletingProfile) AlertDialog(
        onDismissRequest = { deletingDraft = null; deletingProfile = false },
        title = { Text("Delete profile?") },
        text = { Text(if (deletingProfile) "Delete the saved profile and remove printer assignments using this profile? Other printers, drafts and Bluetooth pairing are preserved."
            else "Delete this local draft? This cannot be undone.") },
        confirmButton = { TextButton(onClick = {
            val draft = deletingDraft
            deletingDraft = null
            val active = deletingProfile
            deletingProfile = false
            if (draft != null) onDeleteDraft(draft) else if (active) onDeleteProfile?.invoke()
        }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { deletingDraft = null; deletingProfile = false }) { Text("Cancel") } })
    LazyColumn(Modifier.fillMaxSize().testTag("printer-management-list").padding(horizontal = 24.dp).widthIn(max = 960.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
        item {
            TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) { Text("Back to Home") }
            Text("Printers and profiles", style = MaterialTheme.typography.headlineSmall)
            if (managementStatus.isNotBlank()) Text(managementStatus, style = MaterialTheme.typography.bodySmall)
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Printers", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
                AuthoringButton("Add Printer", onClick = onAdd)
            }
            if (printers.isEmpty()) {
                Text("No printers added", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                Text("Pair a printer, then add its connection and profile.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        for (printer in printers) item(key = "printer-${printer.id}") {
            ListItem(headlineContent = { Text(printer.displayName) },
                supportingContent = { Text("${printer.transportKind.name.lowercase().replace('_', ' ')} connection") },
                modifier = Modifier.clickable(role = Role.Button, onClick = { onSelect(printer) }).authoringFocus(),
                trailingContent = { TextButton(onClick = { deletingPrinter = printer }) { Text("Remove") } })
            if (selectedPrinter == printer && importedProfile != null) TextButton(onClick = { onAssignProfile(printer, importedProfile) }) {
                Text("Assign imported profile")
            }
            if (selectedPrinter == printer && selectedProfile != null) {
                var editingDefaults by remember(printer) { mutableStateOf(false) }
                var options by remember(printer) { mutableStateOf(printer.defaults.toPrintOptions()) }
                TextButton(onClick = { editingDefaults = !editingDefaults }) { Text("Printer defaults") }
                if (editingDefaults) {
                    PrintOptionsControls(selectedProfile, options, true, { options = it })
                    TextButton(enabled = options != printer.defaults.toPrintOptions(), onClick = {
                        onDefaults(printer, PrinterPrintDefaults(options.cutIntent, options.cutMode, options.finalFeedDots))
                    }) { Text("Save printer defaults") }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Profiles", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
                Box {
                    TextButton(onClick = { profileMenu = true }) { Text("Manage profiles") }
                    DropdownMenu(profileMenu, onDismissRequest = { profileMenu = false }) {
                        DropdownMenuItem(text = { Text("Import printer profile") }, onClick = { profileMenu = false; onImport() })
                        DropdownMenuItem(text = { Text("Duplicate selected profile") }, onClick = { profileMenu = false; onDuplicate() })
                        if (onExport != null) DropdownMenuItem(text = { Text("Export printer profile") }, enabled = importedProfile != null,
                            onClick = { profileMenu = false; onExport() })
                        if (onDeleteProfile != null) DropdownMenuItem(text = { Text("Delete saved profile") }, enabled = importedProfile != null,
                            onClick = { profileMenu = false; deletingProfile = true })
                    }
                }
            }
            TextButton(onClick = onCreate) { Text("Create Printer Profile") }
            if (importedProfile == null) Text("No validated physical printer profile selected.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            else Text("Selected: ${importedProfile.display.name}", style = MaterialTheme.typography.bodyMedium)
        }
        for (draft in drafts) item(key = "draft-${draft.draftId}") {
            var menu by remember { mutableStateOf(false) }
            val name = draft["display.name"].ifBlank { "Untitled profile" }
            ListItem(headlineContent = { Text("Edit $name") }, supportingContent = { Text("Local draft") },
                modifier = Modifier.clickable(role = Role.Button, onClick = { onEdit(draft) }).authoringFocus(),
                trailingContent = {
                    Box {
                        TextButton(onClick = { menu = true }) { Text("More") }
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Duplicate draft $name") }, onClick = { menu = false; onDuplicateDraft(draft) })
                            DropdownMenuItem(text = { Text("Delete draft $name") }, onClick = { menu = false; deletingDraft = draft })
                        }
                    }
                })
        }
        item {
            HorizontalDivider()
            ListItem(headlineContent = { Text("Open Printer Lab") }, supportingContent = { Text("Print test samples and record measurements") },
                trailingContent = { Text("›", style = MaterialTheme.typography.titleLarge) },
                modifier = Modifier.clickable(role = Role.Button, onClick = onLab).authoringFocus())
        }
        item {
            TextButton(onClick = { previewTargets = !previewTargets }) { Text(if (previewTargets) "Hide software preview targets" else "Software preview targets") }
            if (previewTargets) {
                Text("Selected: ${profile.display.name}", style = MaterialTheme.typography.bodyMedium)
                Text("Test fixtures support software preview. They do not establish printer compatibility.", style = MaterialTheme.typography.bodySmall)
                for (candidate in SyntheticReferenceProfiles.all) TextButton(onClick = { onProfile(candidate) }) { Text(candidate.display.name) }
            }
            TextButton(onClick = { tools = !tools }) { Text(if (tools) "Hide connection details" else "Connection details") }
            if (tools) printingControls()
        }
    }
}

/** Evidence and bounded test drafts only; this screen never opens a transport or prints. */
@Composable
internal fun PrinterLabScreen(onBack: () -> Unit, onQrFixture: () -> Unit, calibrationControls: @Composable () -> Unit = {}) {
    var details by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp).widthIn(max = 960.dp).testTag("printer-lab-list"),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Printer Lab", style = MaterialTheme.typography.headlineMedium)
            AuthoringButton("Back to printers", onClick = onBack)
            Text("Establish one unknown printer property at a time. Preparing a preview is software-only; printing a calibration sample needs separate approval and physical measurement.")
            Text("Drafts stay editable and never activate themselves after an experiment.", style = MaterialTheme.typography.bodyMedium)
        }
        item { TextButton(onClick = { details = !details }) { Text(if (details) "Hide reference and procedure details" else "Reference and procedure details") } }
        if (details) item {
            Text("Printer evidence", style = MaterialTheme.typography.titleMedium)
            Text("A self-test can report paper width, fonts and charsets. It does not establish DPI, dot width or host command compatibility.")
            Text("Geometry: HW-037 / HW-012; fonts: HW-001/002/004; CP437: HW-036; raster: HW-007; QR: HW-017/018/019; buffers/pacing: HW-029. EAN13 host-command testing waits for a prepared barcode implementation.")
            Text("Record unit identity, procedure, captured bytes and observed paper in the draft and permanent hardware-test record. Local transmission does not prove paper output. Inspect ambiguous output before any new user-confirmed attempt.")
        }
        item { calibrationControls() }
        item {
            Text("Bounded QR test draft", style = MaterialTheme.typography.titleMedium)
            Text("Create a software raster QR test draft. Prepare and inspect the preview before any explicitly approved print. Its example.invalid URL is opaque test data and is never opened.")
            AuthoringButton("Create raster QR test draft", onClick = onQrFixture)
        }
    }
}
