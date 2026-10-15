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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.circuitnext.rastrio.core.profile.*
import com.circuitnext.rastrio.printing.*

@Composable
internal fun ProfileEditorScreen(draft: ProfileDraft, onDraft: (ProfileDraft) -> Unit,
    onSave: () -> Unit, onActivate: (PrinterProfile) -> Unit, onBack: () -> Unit,
    onLab: () -> Unit, status: String, selectedDevice: PairedPrinterDevice? = null,
    suggestions: List<ProfileSuggestion> = emptyList()) {
    var checked by remember(draft) { mutableStateOf<ProfileValidationResult<ValidatedPrinterProfile>?>(null) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var details by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    val errors = (checked as? ProfileValidationResult.Failure)?.diagnostics.orEmpty()
    val valid = (checked as? ProfileValidationResult.Success)?.value?.profile
    val diagnosticCode = Regex("\\b[A-Z]{3}\\d{3}\\s*")
    val statusMessage = status.replace(diagnosticCode, "").trimStart(':', ' ')
    val technicalStatus = status.takeIf { diagnosticCode.containsMatchIn(it) }
    val proposals = (draft.suggestions + suggestions).distinct().take(32)
    val labels = mapOf("protocol" to "Print command protocol", "nativeText" to "Native text and charsets",
        "raster" to "Raster printing", "nativeQr" to "Native QR", "nativeBarcode" to "Native barcodes",
        "cutter" to "Automatic cutter", "statusQuery" to "Printer status", "printerBuffer" to "Buffering and pacing", "geometry" to "Print geometry")
    val field: @Composable (ProfileDraftField) -> Unit = { descriptor ->
        val value = draft[descriptor.path]
        val diagnostics = errors.filter { it.path == "$.${descriptor.path}" }
        if (descriptor.kind == ProfileDraftFieldKind.BOOLEAN) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Checkbox(value == "true", onCheckedChange = { onDraft(draft.withField(descriptor.path, it.toString())) },
                    modifier = Modifier.testTag("profile-field-${descriptor.path}"))
                Text(descriptor.label, Modifier.weight(1f).padding(top = 12.dp))
            }
        } else ProfileChoiceField(descriptor, value, profileChoices(descriptor.path), diagnostics.firstOrNull()?.message) {
            onDraft(draft.withField(descriptor.path, it))
        }
    }
    LazyColumn(Modifier.fillMaxSize().testTag("profile-editor-list").padding(16.dp).widthIn(max = 960.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Create Printer Profile", style = MaterialTheme.typography.headlineMedium)
            AuthoringButton("Back to printers", onClick = onBack)
            Text(if (valid != null) "Validated · ready to save" else "Local draft · save at any time", style = MaterialTheme.typography.titleMedium)
            selectedDevice?.let { Text("Selected device: ${it.displayName}", style = MaterialTheme.typography.bodyMedium) }
            Text("Keep unknown values blank. Use Printer Lab to establish print geometry and host command compatibility.")
            if (statusMessage.isNotBlank()) Text(statusMessage)
        }
        items(ProfileDraftFields.basic.filter { it.path.startsWith("display.") }.size, key = { ProfileDraftFields.basic.filter { field -> field.path.startsWith("display.") }[it].path }) {
            field(ProfileDraftFields.basic.filter { it.path.startsWith("display.") }[it])
        }
        items(ProfileDraftFields.worksheet.size, key = { ProfileDraftFields.worksheet[it].path }) { field(ProfileDraftFields.worksheet[it]) }
        item { Text("Printable dot geometry", style = MaterialTheme.typography.titleMedium) }
        items(ProfileDraftFields.basic.filter { it.path.startsWith("geometry.") }.size, key = { ProfileDraftFields.basic.filter { field -> field.path.startsWith("geometry.") }[it].path }) {
            field(ProfileDraftFields.basic.filter { it.path.startsWith("geometry.") }[it])
        }
        item {
            Text("Capabilities", style = MaterialTheme.typography.titleMedium)
            Text("${draft.verifications.size} recorded verification procedures. Reported or inferred capabilities remain experimental.")
            AuthoringButton("Save local draft", onClick = onSave)
            AuthoringButton("Validate profile") { checked = draft.validate(selectedDevice?.locator) }
            if (errors.isNotEmpty()) {
                Text("Profile needs more information before activation", style = MaterialTheme.typography.titleMedium)
                Text("${errors.map { it.path }.distinct().size} fields or evidence areas need attention. Your draft can still be saved.")
                Text(errors.first().message)
            }
            if (errors.isNotEmpty() || technicalStatus != null) {
                AuthoringButton(if (details) "Hide Technical Details" else "Technical Details") { details = !details }
                if (details) {
                    technicalStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    errors.forEach { Text("${it.code} ${it.path}: ${it.message}", style = MaterialTheme.typography.bodySmall) }
                }
            }
            AuthoringButton("Save and select validated profile", valid != null) { if (valid != null) onActivate(valid) }
            if (valid == null) Text("Validate explicit geometry and recorded printer evidence to activate ordinary printing.")
            AuthoringButton("Calibrate draft in Printer Lab", onClick = onLab)
        }
        if (proposals.isNotEmpty()) {
            item { Text("Detected and reported information", style = MaterialTheme.typography.titleMedium) }
            items(proposals.size, key = { "suggestion-$it-${proposals[it].path}" }) { index ->
                val proposal = proposals[index]
                val label = ProfileDraftFields.all.firstOrNull { it.path == proposal.path }?.label ?: "Unsupported suggestion"
                val existing = draft[proposal.path]
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("$label: ${proposal.value}", style = MaterialTheme.typography.titleSmall)
                    Text("${proposal.source.name.lowercase().replace('_', ' ')} · ${proposal.sourceDescription}")
                    if (existing.isNotBlank() && existing != proposal.value) Text("Your value: $existing. Choose whether to replace it.")
                    Text("This source does not verify host printing.", style = MaterialTheme.typography.bodySmall)
                    AuthoringButton(if (existing == proposal.value) "Keep this reported value" else "Use suggested $label",
                        proposal.path in ProfileDraftFields.all.map { it.path } && (selectedDevice == null || selectedDevice.locator == proposal.deviceLocator)) {
                        val withProposals = draft.withSuggestions(proposals.filter { it.path in ProfileDraftFields.all.map { field -> field.path } })
                        onDraft(withProposals.acceptSuggestion(proposal))
                    }
                }
            }
        }
        item { AuthoringButton(if (advanced) "Hide Advanced configuration" else "Advanced configuration") { advanced = !advanced } }
        if (advanced) {
            item { Text("Registered values describe software support; they do not prove printer compatibility.") }
            items(2, key = { ProfileDraftFields.basic[it].path }) { field(ProfileDraftFields.basic[it]) }
            for (area in ProfileDraftFields.evidenceAreas) {
                item(key = "advanced-$area") { AuthoringButton(labels.getValue(area)) { expanded = if (expanded == area) null else area } }
                if (expanded == area) {
                    val descriptors = ProfileDraftFields.advanced.filter { it.path.substringBefore('.') == area }
                    items(descriptors.size, key = { descriptors[it].path }) { field(descriptors[it]) }
                    if (area == "nativeText") {
                        val fonts = draft["nativeText.fontCount"].toIntOrNull()?.coerceIn(0, 16) ?: 0
                        val pages = draft["nativeText.codePageCount"].toIntOrNull()?.coerceIn(0, 16) ?: 0
                        for (index in 0 until fonts) {
                            item { Text("Native font ${index + 1}", style = MaterialTheme.typography.titleMedium) }
                            val fields = ProfileDraftFields.fontFields(index)
                            items(fields.size, key = { fields[it].path }) { field(fields[it]) }
                        }
                        for (index in 0 until pages) {
                            item { Text("Code page ${index + 1}", style = MaterialTheme.typography.titleMedium) }
                            val fields = ProfileDraftFields.codePageFields(index)
                            items(fields.size, key = { fields[it].path }) { field(fields[it]) }
                        }
                    }
                    item(key = "evidence-$area") {
                        var procedure by rememberSaveable(area) { mutableStateOf("") }
                        var observation by rememberSaveable(area) { mutableStateOf("") }
                        Text("Record ${labels.getValue(area).lowercase()} evidence", style = MaterialTheme.typography.titleMedium)
                        Text("Record a procedure you actually performed and what you observed. Selecting a label never verifies a capability.")
                        OutlinedTextField(procedure, { if (it.length <= 256) procedure = it }, label = { Text("Procedure performed") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(observation, { if (it.length <= 1024) observation = it }, label = { Text("Observed result") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                        AuthoringButton("Record verification", selectedDevice != null && procedure.isNotBlank() && observation.isNotBlank()) {
                            selectedDevice?.let { onDraft(draft.recordVerification(area, procedure, observation, it.locator)) }
                        }
                        if (selectedDevice == null) Text("Select the physical printer before recording verification.")
                        draft.verifications.firstOrNull { it.area == area }?.let { Text("Recorded: ${it.procedure} — ${it.observation}") }
                    }
                }
            }
            item {
                field(ProfileDraftFields.advanced.first { it.path == "quirks" })
                OutlinedTextField(draft.evidenceNotes, { if (it.length <= 4096) onDraft(draft.copy(evidenceNotes = it)) },
                    label = { Text("Profile evidence notes") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            }
        }
    }
}

/** Vocabulary is software support only; selection never fills selectors or verifies hardware. */
private fun profileChoices(path: String): List<String> {
    val registry = BaselineTrustedProfileRegistry
    val purpose = when {
        path == "protocol.initializationStrategy" -> StrategyPurpose.INITIALIZATION
        path.startsWith("nativeText.fonts[") && path.endsWith(".selector.strategy") -> StrategyPurpose.FONT_SELECTION
        path.startsWith("nativeText.codePages[") && path.endsWith(".selector.strategy") -> StrategyPurpose.CODE_PAGE_SELECTION
        path in listOf("raster.strategies", "raster.preferredStrategy") -> StrategyPurpose.RASTER
        path in listOf("nativeQr.strategies", "nativeQr.preferredStrategy") -> StrategyPurpose.QR
        path in listOf("nativeBarcode.strategies", "nativeBarcode.preferredStrategy") -> StrategyPurpose.BARCODE
        path == "cutter.strategy" -> StrategyPurpose.CUT
        path in listOf("statusQuery.strategies", "statusQuery.preferredStrategy") -> StrategyPurpose.STATUS_QUERY
        else -> null
    }
    if (purpose != null) return listOf("escpos.init.standard", "escpos.font.esc-m", "escpos.code-page.esc-t",
        "escpos.raster.gs-v-0", "escpos.qr.model2", "escpos.barcode.gs-k", "escpos.cut.gs-v", "escpos.status.dle-eot")
        .filter { registry.strategy(it)?.purpose == purpose }
    return when {
        path == "protocol.dialectId" -> listOf("escpos.generic").filter { registry.dialect(it) != null }
        path == "worksheet.charset" -> listOf("CP437")
        path == "worksheet.nativeFont" -> listOf("12×24")
        path.startsWith("nativeText.codePages[") && path.endsWith(".repertoire") -> listOf("cp437").filter(registry::knowsRepertoire)
        path == "nativeQr.models" -> listOf("model2").filter(registry::knowsQrModel)
        path == "nativeBarcode.symbologies" -> listOf("code128", "ean13").filter(registry::knowsBarcodeSymbology)
        path == "nativeQr.errorCorrection" -> listOf("L", "M", "Q", "H")
        path == "cutter.modes" -> listOf("full", "partial")
        else -> emptyList()
    }
}

@Composable
private fun ProfileChoiceField(descriptor: ProfileDraftField, value: String, choices: List<String>,
    error: String?, onValue: (String) -> Unit) {
    var open by remember(descriptor.path) { mutableStateOf(false) }
    val multiple = descriptor.kind == ProfileDraftFieldKind.LIST
    val selected = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(value, { if (it.length <= 1024) onValue(it) }, label = { Text(descriptor.label) },
            modifier = Modifier.fillMaxWidth().testTag("profile-field-${descriptor.path}"), isError = error != null,
            singleLine = true,
            trailingIcon = if (choices.isEmpty()) null else ({
                TextButton(onClick = { open = !open }, modifier = Modifier.testTag("profile-choice-${descriptor.path}")) { Text("Choose") }
            }),
            supportingText = {
                when {
                    error != null -> Text(error)
                    descriptor.path.startsWith("geometry") -> Text("Leave blank until established; millimetres do not establish resolution.")
                    descriptor.path.startsWith("worksheet") -> Text("Reported information only; choose or enter a custom value. Does not enable printer commands.")
                    choices.isNotEmpty() -> Text("Registered software choices; verify support on your printer. Existing values stay editable.")
                    multiple -> Text("Comma-separated registered values")
                }
            })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Leave unknown / clear") }, onClick = { onValue(""); open = false })
            choices.forEach { choice ->
                DropdownMenuItem(text = { Text(choice) }, leadingIcon = if (multiple) ({
                    Checkbox(checked = choice in selected, onCheckedChange = null)
                }) else null, onClick = {
                    val updated = if (multiple) (if (choice in selected) selected - choice else selected + choice).joinToString(",") else choice
                    if (updated.length <= 1024) onValue(updated)
                    open = false
                })
            }
        }
    }
}
