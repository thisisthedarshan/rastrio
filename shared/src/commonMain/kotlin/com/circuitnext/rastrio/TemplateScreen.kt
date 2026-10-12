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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.circuitnext.rastrio.core.document.QrErrorCorrection
import com.circuitnext.rastrio.core.document.ThermalDocument
import com.circuitnext.rastrio.core.templates.*
import com.circuitnext.rastrio.presentation.*

internal val TemplateFormsSaver = listSaver<TemplateForms, String>(
    save = { it.snapshot() }, restore = { TemplateForms.restore(it) },
)

@Composable
internal fun rememberTemplateFormsState(): MutableState<TemplateForms> =
    rememberSaveable(stateSaver = TemplateFormsSaver) { mutableStateOf(TemplateForms()) }

/** Delegates the semantic document to the existing preparation/preview workflow. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TemplateScreen(
    forms: TemplateForms, onForms: (TemplateForms) -> Unit, onPreview: (ThermalDocument) -> Unit,
    onBack: () -> Unit, busy: Boolean = false, targetName: String = "",
    profileControls: @Composable () -> Unit = {}, printingControls: @Composable () -> Unit = {},
    previewStatus: String = "", onLogicalPreview: ((ThermalDocument) -> Unit)? = null,
) {
    var diagnostics by remember(forms.selected) { mutableStateOf(emptyList<TemplateDiagnostic>()) }
    fun edit(next: TemplateForms) {
        if (next.withinSaveLimit()) { diagnostics = emptyList(); onForms(next) }
        else diagnostics = listOf(TemplateDiagnostic("TPL120", "form", "Draft limit reached: 128 items per list and 32768 characters across all templates"))
    }
    fun error(field: String) = diagnostics.firstOrNull { it.field == field }?.message
    LazyColumn(Modifier.fillMaxSize().padding(16.dp).widthIn(max = 960.dp).testTag("template-list"),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Templates", style = MaterialTheme.typography.headlineMedium)
            AuthoringButton("Back to Home", !busy, onBack)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (kind in TemplateKind.entries) {
                    FilterChip(forms.selected == kind, { edit(forms.copy(selected = kind)) },
                        label = { Text(kind.label) }, enabled = !busy, modifier = Modifier.authoringFocus())
                }
            }
            Text(forms.selected.label, style = MaterialTheme.typography.titleLarge)
        }
        when (forms.selected) {
            TemplateKind.TODO -> {
                item { TemplateField("Title", forms.todo.title, !busy, error("title")) { edit(forms.copy(todo = forms.todo.copy(title = it))) } }
                itemsIndexed(forms.todo.items) { index, entry ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row {
                            Checkbox(entry.checked, { checked -> edit(forms.copy(todo = forms.todo.copy(items = forms.todo.items.mapIndexed { i, v -> if (i == index) v.copy(checked = checked) else v }))) },
                                enabled = !busy, modifier = Modifier.semantics { contentDescription = "Item ${index + 1} completed" })
                            TemplateField("Item ${index + 1}", entry.text, !busy, error("items[$index].text")) { value ->
                                edit(forms.copy(todo = forms.todo.copy(items = forms.todo.items.mapIndexed { i, v -> if (i == index) v.copy(text = value) else v })))
                            }
                        }
                        TemplateItemActions(index, forms.todo.items.size, !busy,
                            { edit(forms.copy(todo = forms.todo.copy(items = forms.todo.items.filterIndexed { i, _ -> i != index }))) },
                            { direction -> edit(forms.copy(todo = forms.todo.copy(items = forms.todo.items.moveTemplateItem(index, direction)))) })
                    }
                }
                item { AuthoringButton("Add item", !busy && forms.todo.items.size < TemplateForms.MAX_FORM_ITEMS) {
                    edit(forms.copy(todo = forms.todo.copy(items = forms.todo.items + TodoItem())))
                } }
            }
            TemplateKind.SHOPPING -> {
                item { TemplateField("Title", forms.shopping.title, !busy, error("title")) { edit(forms.copy(shopping = forms.shopping.copy(title = it))) } }
                itemsIndexed(forms.shopping.items) { index, entry ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TemplateField("Item ${index + 1}", entry.description, !busy, error("items[$index].description")) { value ->
                            edit(forms.copy(shopping = forms.shopping.copy(items = forms.shopping.items.mapIndexed { i, v -> if (i == index) v.copy(description = value) else v })))
                        }
                        TemplateField("Quantity ${index + 1}", entry.quantity, !busy, error("items[$index].quantity")) { value ->
                            edit(forms.copy(shopping = forms.shopping.copy(items = forms.shopping.items.mapIndexed { i, v -> if (i == index) v.copy(quantity = value) else v })))
                        }
                        TemplateItemActions(index, forms.shopping.items.size, !busy,
                            { edit(forms.copy(shopping = forms.shopping.copy(items = forms.shopping.items.filterIndexed { i, _ -> i != index }))) },
                            { direction -> edit(forms.copy(shopping = forms.shopping.copy(items = forms.shopping.items.moveTemplateItem(index, direction)))) })
                    }
                }
                item { AuthoringButton("Add item", !busy && forms.shopping.items.size < TemplateForms.MAX_FORM_ITEMS) {
                    edit(forms.copy(shopping = forms.shopping.copy(items = forms.shopping.items + ShoppingItem())))
                } }
            }
            TemplateKind.NOTE -> item {
                TemplateField("Title", forms.note.title, !busy, error("title")) { edit(forms.copy(note = forms.note.copy(title = it))) }
                TemplateField("Note", forms.note.body, !busy, error("body"), multiline = true) { edit(forms.copy(note = forms.note.copy(body = it))) }
            }
            TemplateKind.MEMO -> item {
                TemplateField("Subject", forms.memo.subject, !busy, error("subject")) { edit(forms.copy(memo = forms.memo.copy(subject = it))) }
                TemplateField("Recipient", forms.memo.recipient, !busy, error("recipient")) { edit(forms.copy(memo = forms.memo.copy(recipient = it))) }
                TemplateField("Memo body", forms.memo.body, !busy, error("body"), multiline = true) { edit(forms.copy(memo = forms.memo.copy(body = it))) }
                TemplateField("Signature", forms.memo.signature, !busy, error("signature")) { edit(forms.copy(memo = forms.memo.copy(signature = it))) }
            }
            TemplateKind.QR -> item {
                TemplateField("QR payload", forms.qr.payload, !busy, error("payload"), multiline = true) { edit(forms.copy(qr = forms.qr.copy(payload = it))) }
                TemplateField("Caption", forms.qr.caption, !busy, error("caption"), multiline = true) { edit(forms.copy(qr = forms.qr.copy(caption = it))) }
                TemplateField("Requested size (mm, optional)", forms.qrSizeMm, !busy, error("requestedSize")) { edit(forms.copy(qrSizeMm = it)) }
                Text("QR error correction")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (correction in QrErrorCorrection.entries) FilterChip(forms.qr.errorCorrection == correction,
                        { edit(forms.copy(qr = forms.qr.copy(errorCorrection = correction))) },
                        label = { Text(correction.name) }, enabled = !busy, modifier = Modifier.authoringFocus())
                }
            }
        }
        item {
            if (targetName.isNotEmpty()) Text("Preview target: $targetName")
            profileControls()
            AuthoringButton("Preview template", !busy) {
                val result = forms.build()
                diagnostics = result.diagnostics
                result.document?.let(onPreview)
            }
            if (onLogicalPreview != null) AuthoringButton("Open logical preview", !busy) {
                val result = forms.build()
                diagnostics = result.diagnostics
                result.document?.let(onLogicalPreview)
            }
            for (diagnostic in diagnostics) Text("${diagnostic.field}: ${diagnostic.message}",
                color = LocalAuthoringColors.current.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            if (previewStatus.isNotEmpty()) Text(previewStatus, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        item { printingControls() }
    }
}

@Composable
private fun TemplateField(label: String, value: String, enabled: Boolean, error: String?, multiline: Boolean = false, onValue: (String) -> Unit) {
    OutlinedTextField(value, onValue, label = { Text(label) }, enabled = enabled, singleLine = !multiline,
        minLines = if (multiline) 3 else 1, isError = error != null,
        supportingText = if (error != null) ({ Text(error) }) else null,
        modifier = Modifier.fillMaxWidth().testTag("template-$label"))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TemplateItemActions(index: Int, count: Int, enabled: Boolean, remove: () -> Unit, move: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AuthoringButton("Remove item ${index + 1}", enabled, remove)
        AuthoringButton("Move item ${index + 1} up", enabled && index > 0) { move(-1) }
        AuthoringButton("Move item ${index + 1} down", enabled && index + 1 < count) { move(1) }
    }
}
