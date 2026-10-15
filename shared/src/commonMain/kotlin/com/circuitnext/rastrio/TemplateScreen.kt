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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
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

@Composable
internal fun TemplateGallery(onSelect: (TemplateKind) -> Unit, onBack: () -> Unit, busy: Boolean = false) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp).widthIn(max = 960.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { TemplateHeader("Templates", "Back to Home", true, onBack) }
        item { Text("Start with a list, a note, or a message.", style = MaterialTheme.typography.bodyLarge) }
        for (kind in TemplateKind.entries) item {
            ListItem(headlineContent = { Text(kind.label) }, supportingContent = {
                Text(when (kind) {
                    TemplateKind.TODO -> "Keep track of things to do"
                    TemplateKind.SHOPPING -> "Items, quantities, and units"
                    TemplateKind.NOTE -> "A title and a quick thought"
                    TemplateKind.MEMO -> "A message for someone"
                    TemplateKind.QR -> "A scannable message or link"
                })
            }, trailingContent = { Text("›", style = MaterialTheme.typography.headlineSmall) },
                modifier = Modifier.clickable(enabled = !busy) { onSelect(kind) }.authoringFocus())
            HorizontalDivider()
        }
    }
}

/** Delegates semantic content to preparation; printer controls belong to the preview flow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TemplateScreen(
    forms: TemplateForms, onForms: (TemplateForms) -> Unit, onPreview: (ThermalDocument) -> Unit,
    onBack: () -> Unit, busy: Boolean = false,
    previewStatus: String = "",
    onQrPreview: ((ThermalDocument) -> Unit)? = null,
) {
    var diagnostics by remember(forms.selected) { mutableStateOf(emptyList<TemplateDiagnostic>()) }
    var sheetIndex by remember(forms.selected) { mutableStateOf<Int?>(null) }
    var itemText by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var customUnit by remember { mutableStateOf(false) }
    var sheetError by remember { mutableStateOf<String?>(null) }
    var qrOptions by rememberSaveable { mutableStateOf(false) }
    val units = listOf("", "pcs", "kg", "g", "L", "mL", "pack", "box")
    fun edit(next: TemplateForms): Boolean {
        if (next.withinSaveLimit()) { diagnostics = emptyList(); onForms(next); return true }
        diagnostics = listOf(TemplateDiagnostic("TPL120", "form", "Draft limit reached: 128 items per list and 32768 characters across all templates"))
        return false
    }
    fun error(field: String) = diagnostics.firstOrNull { it.field == field }?.message
    fun openItem(index: Int) {
        itemText = if (forms.selected == TemplateKind.TODO) forms.todo.items.getOrNull(index)?.text.orEmpty()
            else forms.shopping.items.getOrNull(index)?.description.orEmpty()
        quantity = forms.shopping.items.getOrNull(index)?.quantity.orEmpty()
        unit = forms.shopping.items.getOrNull(index)?.unit.orEmpty()
        customUnit = unit !in units
        sheetError = null
        sheetIndex = index
    }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp).widthIn(max = 960.dp).testTag("template-list"),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TemplateHeader(forms.selected.label, "Back to Templates", true, onBack) }
        when (forms.selected) {
            TemplateKind.TODO -> {
                item { TemplateField("Title", forms.todo.title, !busy, error("title")) { edit(forms.copy(todo = forms.todo.copy(title = it))) } }
                itemsIndexed(forms.todo.items) { index, entry ->
                    TemplateItemRow(index, entry.text, "", entry.checked, !busy,
                        { checked -> edit(forms.copy(todo = forms.todo.copy(items = forms.todo.items.mapIndexed { i, v -> if (i == index) v.copy(checked = checked) else v }))) },
                        { openItem(index) },
                        { edit(forms.copy(todo = forms.todo.copy(items = forms.todo.items.filterIndexed { i, _ -> i != index }))) },
                        forms.todo.items.size,
                        { direction -> edit(forms.copy(todo = forms.todo.copy(items = forms.todo.items.moveTemplateItem(index, direction)))) })
                }
                item { TextButton({ openItem(-1) }, enabled = !busy && forms.todo.items.size < TemplateForms.MAX_FORM_ITEMS) { Text("Add item") } }
            }
            TemplateKind.SHOPPING -> {
                item { TemplateField("List title", forms.shopping.title, !busy, error("title")) { edit(forms.copy(shopping = forms.shopping.copy(title = it))) } }
                item { TemplateField("Date (optional)", forms.shopping.date, !busy, error("date")) { edit(forms.copy(shopping = forms.shopping.copy(date = it))) } }
                item { HorizontalDivider() }
                itemsIndexed(forms.shopping.items) { index, entry ->
                    TemplateItemRow(index, entry.description, listOf(entry.quantity, entry.unit).filter { it.isNotBlank() }.joinToString(" "), entry.checked, !busy,
                        { checked -> edit(forms.copy(shopping = forms.shopping.copy(items = forms.shopping.items.mapIndexed { i, v -> if (i == index) v.copy(checked = checked) else v }))) },
                        { openItem(index) },
                        { edit(forms.copy(shopping = forms.shopping.copy(items = forms.shopping.items.filterIndexed { i, _ -> i != index }))) },
                        forms.shopping.items.size,
                        { direction -> edit(forms.copy(shopping = forms.shopping.copy(items = forms.shopping.items.moveTemplateItem(index, direction)))) })
                }
                item { TextButton({ openItem(-1) }, enabled = !busy && forms.shopping.items.size < TemplateForms.MAX_FORM_ITEMS) { Text("Add item") } }
            }
            TemplateKind.NOTE -> {
                item { TemplateField("Title", forms.note.title, !busy, error("title")) { edit(forms.copy(note = forms.note.copy(title = it))) } }
                item { TemplateField("Note", forms.note.body, !busy, error("body"), multiline = true) { edit(forms.copy(note = forms.note.copy(body = it))) } }
            }
            TemplateKind.MEMO -> {
                item { TemplateField("Subject", forms.memo.subject, !busy, error("subject")) { edit(forms.copy(memo = forms.memo.copy(subject = it))) } }
                item { TemplateField("To", forms.memo.recipient, !busy, error("recipient")) { edit(forms.copy(memo = forms.memo.copy(recipient = it))) } }
                item { TemplateField("Date (optional)", forms.memo.date, !busy, error("date")) { edit(forms.copy(memo = forms.memo.copy(date = it))) } }
                item { HorizontalDivider() }
                item { TemplateField("Message", forms.memo.body, !busy, error("body"), multiline = true) { edit(forms.copy(memo = forms.memo.copy(body = it))) } }
                item { TemplateField("Signature", forms.memo.signature, !busy, error("signature")) { edit(forms.copy(memo = forms.memo.copy(signature = it))) } }
            }
            TemplateKind.QR -> {
                item { TemplateField("QR payload", forms.qr.payload, !busy, error("payload"), multiline = true) { edit(forms.copy(qr = forms.qr.copy(payload = it))) } }
                item { TemplateField("Caption", forms.qr.caption, !busy, error("caption"), multiline = true) { edit(forms.copy(qr = forms.qr.copy(caption = it))) } }
                item { TextButton({ qrOptions = !qrOptions }, enabled = !busy) { Text(if (qrOptions) "Hide QR options" else "QR options") } }
                if (qrOptions) item {
                    TemplateField("Requested size (mm, optional)", forms.qrSizeMm, !busy, error("requestedSize")) { edit(forms.copy(qrSizeMm = it)) }
                    TemplateChoice("Error correction", forms.qr.errorCorrection.name, QrErrorCorrection.entries.map { it.name }, !busy) {
                        edit(forms.copy(qr = forms.qr.copy(errorCorrection = QrErrorCorrection.valueOf(it))))
                    }
                }
            }
        }
        item {
            Button(onClick = {
                val result = forms.build()
                diagnostics = result.diagnostics
                result.document?.let { document ->
                    if (forms.selected == TemplateKind.QR && onQrPreview != null) onQrPreview(document) else onPreview(document)
                }
            }, enabled = !busy, modifier = Modifier.fillMaxWidth().authoringFocus()) { Text("Preview") }
            for (diagnostic in diagnostics) Text("${diagnostic.field}: ${diagnostic.message}",
                color = LocalAuthoringColors.current.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            if (previewStatus.isNotEmpty()) Text(previewStatus, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
    sheetIndex?.let { index ->
        ModalBottomSheet(onDismissRequest = { sheetIndex = null }) {
            Column(Modifier.fillMaxWidth().padding(20.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (index < 0) "Add item" else "Edit item", style = MaterialTheme.typography.titleLarge)
                TemplateField("Item", itemText, !busy, sheetError) {
                    if (it.length <= TemplateForms.MAX_SAVED_CHARACTERS) { itemText = it; sheetError = null }
                    else sheetError = "Draft limit reached"
                }
                if (forms.selected == TemplateKind.SHOPPING) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        TemplateField("Quantity", quantity, !busy, null, modifier = Modifier.weight(1f)) { if (it.length <= TemplateForms.MAX_SAVED_CHARACTERS) quantity = it else sheetError = "Draft limit reached" }
                        TemplateChoice("Unit", if (customUnit) "Custom" else unit.ifEmpty { "None" }, units.map { it.ifEmpty { "None" } } + "Custom", !busy, Modifier.width(130.dp)) {
                            customUnit = it == "Custom"
                            unit = if (customUnit) unit else if (it == "None") "" else it
                        }
                    }
                    if (customUnit) TemplateField("Custom unit", unit, !busy, null) {
                        if (it.length <= TemplateForms.MAX_SAVED_CHARACTERS) unit = it else sheetError = "Draft limit reached"
                    }
                }
                Button(onClick = {
                    if (itemText.isBlank()) sheetError = "Enter an item"
                    else {
                        val next = if (forms.selected == TemplateKind.TODO) {
                            val entry = TodoItem(itemText, forms.todo.items.getOrNull(index)?.checked ?: false)
                            forms.copy(todo = forms.todo.copy(items = if (index < 0) forms.todo.items + entry else forms.todo.items.mapIndexed { i, v -> if (i == index) entry else v }))
                        } else {
                            val entry = ShoppingItem(itemText, quantity, unit, forms.shopping.items.getOrNull(index)?.checked ?: false)
                            forms.copy(shopping = forms.shopping.copy(items = if (index < 0) forms.shopping.items + entry else forms.shopping.items.mapIndexed { i, v -> if (i == index) entry else v }))
                        }
                        if (edit(next)) sheetIndex = null else sheetError = "Draft limit reached"
                    }
                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Save item") }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun TemplateHeader(title: String, backLabel: String, enabled: Boolean, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(onBack, enabled = enabled, modifier = Modifier.semantics { contentDescription = backLabel }) { Text("←", style = MaterialTheme.typography.headlineSmall) }
        Text(title, style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
private fun TemplateField(label: String, value: String, enabled: Boolean, error: String?, multiline: Boolean = false,
    modifier: Modifier = Modifier, onValue: (String) -> Unit) {
    OutlinedTextField(value, onValue, label = { Text(label) }, enabled = enabled, singleLine = !multiline,
        minLines = if (multiline) 5 else 1, isError = error != null,
        supportingText = if (error != null) ({ Text(error) }) else null,
        modifier = modifier.fillMaxWidth().testTag("template-$label"))
}

@Composable
private fun TemplateChoice(label: String, value: String, choices: List<String>, enabled: Boolean,
    modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton({ expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Column { Text(label, style = MaterialTheme.typography.labelSmall); Text(value) }
        }
        DropdownMenu(expanded, { expanded = false }) {
            for (choice in choices) DropdownMenuItem(text = { Text(choice) }, onClick = { expanded = false; onSelect(choice) })
        }
    }
}

@Composable
private fun TemplateItemRow(index: Int, name: String, quantity: String, checked: Boolean, enabled: Boolean,
    onChecked: (Boolean) -> Unit, edit: () -> Unit, remove: () -> Unit, count: Int, move: (Int) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Column {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            Checkbox(checked, onChecked, enabled = enabled, modifier = Modifier.semantics { contentDescription = "Item ${index + 1} completed" })
            Text(name, modifier = Modifier.weight(1f).padding(top = 12.dp).clickable(enabled = enabled, onClick = edit), style = MaterialTheme.typography.bodyLarge)
            if (quantity.isNotEmpty()) Text(quantity, modifier = Modifier.widthIn(max = 100.dp).padding(start = 8.dp, top = 12.dp), style = MaterialTheme.typography.bodyLarge)
            Box {
                IconButton({ menu = true }, enabled = enabled, modifier = Modifier.semantics { contentDescription = "Item ${index + 1} actions" }) { Text("⋮", style = MaterialTheme.typography.headlineSmall) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; edit() })
                    DropdownMenuItem(text = { Text("Move up") }, enabled = index > 0, onClick = { menu = false; move(-1) })
                    DropdownMenuItem(text = { Text("Move down") }, enabled = index + 1 < count, onClick = { menu = false; move(1) })
                    DropdownMenuItem(text = { Text("Remove") }, onClick = { menu = false; remove() })
                }
            }
        }
        HorizontalDivider()
    }
}
