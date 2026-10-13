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
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.circuitnext.rastrio.core.profile.*
import com.circuitnext.rastrio.printing.*

@Composable
internal fun ProfileEditorScreen(draft: ProfileDraft, onDraft: (ProfileDraft) -> Unit,
    onSave: () -> Unit, onActivate: (PrinterProfile) -> Unit, onBack: () -> Unit,
    onLab: () -> Unit, status: String) {
    var checked by remember(draft) { mutableStateOf<ProfileValidationResult<ValidatedPrinterProfile>?>(null) }
    var expanded by remember { mutableStateOf<String?>(null) }
    val errors = (checked as? ProfileValidationResult.Failure)?.diagnostics.orEmpty()
    val valid = (checked as? ProfileValidationResult.Success)?.value?.profile
    val field: @Composable (ProfileDraftField) -> Unit = { descriptor ->
        val value = draft[descriptor.path]
        val diagnostics = errors.filter { it.path == "$.${descriptor.path}" }
        if (descriptor.kind == ProfileDraftFieldKind.BOOLEAN) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Checkbox(value == "true", onCheckedChange = { onDraft(draft.withField(descriptor.path, it.toString())) },
                    modifier = Modifier.testTag("profile-field-${descriptor.path}"))
                Text(descriptor.label, Modifier.weight(1f).padding(top = 12.dp))
            }
        } else OutlinedTextField(value, { if (it.length <= 1024) onDraft(draft.withField(descriptor.path, it)) },
            label = { Text(descriptor.label) }, modifier = Modifier.fillMaxWidth().testTag("profile-field-${descriptor.path}"),
            isError = diagnostics.isNotEmpty(), singleLine = true,
            supportingText = { if (diagnostics.isNotEmpty()) Text(diagnostics.joinToString("\n") { it.message })
                else if (descriptor.kind == ProfileDraftFieldKind.LIST) Text("Comma-separated registered values") })
    }
    LazyColumn(Modifier.fillMaxSize().testTag("profile-editor-list").padding(16.dp).widthIn(max = 960.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Create Printer Profile", style = MaterialTheme.typography.headlineMedium)
            AuthoringButton("Back to printers", onClick = onBack)
            Text(if (valid != null) "Validated · ready to save" else "Incomplete local draft · ordinary printing unavailable")
            Text("Leave unknown values blank. Paper width in millimetres and density do not establish DPI or dot width. Optional capabilities stay disabled until supported by recorded evidence.")
            if (status.isNotBlank()) Text(status)
        }
        items(ProfileDraftFields.basic.size) { field(ProfileDraftFields.basic[it]) }
        for (area in ProfileDraftFields.evidenceAreas) {
            item { AuthoringButton(if (expanded == area) "Hide $area fields" else "Edit $area fields") { expanded = if (expanded == area) null else area } }
            if (expanded == area) {
                val descriptors = ProfileDraftFields.advanced.filter { it.path.substringBefore('.') == area }
                items(descriptors.size) { field(descriptors[it]) }
                if (area == "nativeText") {
                    val fonts = draft["nativeText.fontCount"].toIntOrNull()?.coerceIn(0, 16) ?: 0
                    val pages = draft["nativeText.codePageCount"].toIntOrNull()?.coerceIn(0, 16) ?: 0
                    for (index in 0 until fonts) {
                        item { Text("Native font ${index + 1}", style = MaterialTheme.typography.titleMedium) }
                        val descriptors = ProfileDraftFields.fontFields(index)
                        items(descriptors.size) { field(descriptors[it]) }
                    }
                    for (index in 0 until pages) {
                        item { Text("Code page ${index + 1}", style = MaterialTheme.typography.titleMedium) }
                        val descriptors = ProfileDraftFields.codePageFields(index)
                        items(descriptors.size) { field(descriptors[it]) }
                    }
                }
                item {
                    Text("$area evidence: ${(draft.evidence[area] ?: ProfileEvidence.UNKNOWN).name.lowercase().replace('_', ' ')}")
                    var evidenceMenu by remember { mutableStateOf(false) }
                    Box {
                        AuthoringButton("Record $area evidence") { evidenceMenu = true }
                        DropdownMenu(evidenceMenu, onDismissRequest = { evidenceMenu = false }) {
                            for (evidence in ProfileEvidence.entries) DropdownMenuItem(
                                text = { Text(evidence.name.lowercase().replace('_', ' ')) },
                                onClick = { onDraft(draft.withEvidence(area, evidence)); evidenceMenu = false })
                        }
                    }
                    Text("Verified means an actual procedure and observed result support these host-print values; selecting a label does not run a hardware test.")
                }
            }
        }
        item {
            OutlinedTextField(draft["quirks"], { if (it.length <= 1024) onDraft(draft.withField("quirks", it)) },
                label = { Text("Registered quirks (optional)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(draft.evidenceNotes, { if (it.length <= 4096) onDraft(draft.copy(evidenceNotes = it)) },
                label = { Text("Description, test procedures and observed evidence") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            Text("Only built-in strategies are accepted. Generic ESC/POS IDs include escpos.generic, escpos.init.standard, escpos.raster.gs-v-0, escpos.font.esc-m and escpos.code-page.esc-t. These describe software support and do not prove printer compatibility. CP437 selector parameters must come from evidence.")
            AuthoringButton("Save local draft", onClick = onSave)
            AuthoringButton("Validate profile") { checked = draft.validate() }
            for (diagnostic in errors) Text("${diagnostic.code} ${diagnostic.path}: ${diagnostic.message}", color = LocalAuthoringColors.current.error)
            AuthoringButton("Save and select validated profile", valid != null) { if (valid != null) onActivate(valid) }
            AuthoringButton("Calibrate draft in Printer Lab", onClick = onLab)
        }
    }
}
