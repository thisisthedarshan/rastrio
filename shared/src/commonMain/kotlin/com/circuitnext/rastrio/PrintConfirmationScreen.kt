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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.circuitnext.rastrio.core.printer.*
import com.circuitnext.rastrio.core.profile.PrinterProfile
import com.circuitnext.rastrio.presentation.PhysicalPreviewResult
import com.circuitnext.rastrio.printing.*

/** Keyboard resizing must not move controls between compositions and discard input focus. */
internal fun compactPrintLayout(width: Dp, height: Dp, keyboardHeight: Dp): Boolean =
    height + keyboardHeight < 360.dp && width > height + keyboardHeight

/** Displays the accepted physical result; all preparation and transport stay in their existing owners. */
@Composable
internal fun PrintConfirmationScreen(
    printers: List<PrinterInstance>, selected: PrinterInstance?, profile: PrinterProfile?,
    result: PhysicalPreviewResult, preparing: Boolean, eligible: Boolean,
    session: PrintSession?, state: PrintSessionState, presence: PrinterPresence,
    onSelect: (PrinterInstance) -> Unit, onRefresh: () -> Unit, onPrint: () -> Unit,
    onBack: () -> Unit, onManage: () -> Unit, onAdd: () -> Unit,
    options: PrintOptions, onOptions: (PrintOptions) -> Unit,
    imageOptions: (@Composable () -> Unit)? = null, showQrOptions: Boolean = false,
    contentError: String? = null, configurationStatus: String = "",
) {
    var menu by remember { mutableStateOf(false) }
    var more by rememberSaveable { mutableStateOf(false) }
    var details by rememberSaveable { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    val configuration: @Composable () -> Unit = {
        Column {
            if (printers.isEmpty()) {
                Text("No configured printers", style = MaterialTheme.typography.titleMedium)
                Text("Add a printer and assign its profile before printing.")
                TextButton(onClick = onAdd) { Text("Add Printer") }
            } else {
                Box {
                    WorkspaceOutlinedButton(onClick = { menu = true }, enabled = !state.isActive,
                        modifier = Modifier.fillMaxWidth().testTag("configured-printer-selector")) {
                        Text(selected?.displayName ?: "Choose a configured printer")
                    }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        printers.forEach { printer -> DropdownMenuItem(text = { Text(printer.displayName) }, onClick = {
                            menu = false; onSelect(printer)
                        }) }
                    }
                }
                if (selected != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(presenceMessage(presence), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = onRefresh, enabled = !state.isActive && presence != PrinterPresence.CHECKING) { Text("Refresh") }
                }
                if (profile == null) Text("Printer profile unavailable. Manage printers to repair the assignment.")
                else Text("Imported capabilities · physical behavior requires verification", style = MaterialTheme.typography.bodySmall)
            }
            if (configurationStatus.isNotBlank()) Text(configurationStatus.replace(Regex("^[A-Z][A-Z0-9_]*: ?"), ""),
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            if (contentError != null) Text(contentError, color = MaterialTheme.colorScheme.error)
            if (preparing) Text("Updating preview…", modifier = Modifier.testTag("preview-updating"))
            when (result) {
                is PhysicalPreviewResult.PreparationFailed -> result.diagnostics.forEach {
                    Text(it.message + (it.sourceBlockIndex?.let { index -> " (block ${index + 1})" } ?: ""), color = MaterialTheme.colorScheme.error)
                }
                is PhysicalPreviewResult.PreviewFailed -> result.diagnostics.forEach { Text(it.message, color = MaterialTheme.colorScheme.error) }
                is PhysicalPreviewResult.Ready -> result.preview.diagnostics.firstOrNull()?.let { Text(it.message, style = MaterialTheme.typography.bodySmall) }
                PhysicalPreviewResult.NotPrepared -> if (!preparing && selected != null && contentError == null) Text("No current prepared output")
            }
        }
    }
    val preview: @Composable (Modifier) -> Unit = { modifier ->
        BoxWithConstraints(modifier) {
            val ready = result as? PhysicalPreviewResult.Ready
            if (ready != null && !preparing) {
                PhysicalPreviewCanvas(ready.preview, minOf(1f, maxWidth.value / ready.preview.widthDots) * zoom,
                    Modifier.fillMaxSize(), showDiagnostics = false)
            }
        }
    }
    val previewZoom: @Composable () -> Unit = {
        if (result is PhysicalPreviewResult.Ready && !preparing) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = { zoom = 1f }) { Text("Fit") }
            TextButton(onClick = { zoom = (zoom / 1.25f).coerceAtLeast(1f) }, enabled = zoom > 1f) { Text("Zoom out") }
            TextButton(onClick = { zoom = (zoom * 1.25f).coerceAtMost(3f) }, enabled = zoom < 3f) { Text("Zoom in") }
        }
    }
    val printOptions: @Composable () -> Unit = {
        if (profile != null) {
            TextButton(onClick = { more = !more }) { Text(if (more) "Hide print options" else "More print options") }
            if (more) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PrintOptionsControls(profile, options, !state.isActive, onOptions, showQrOptions)
                imageOptions?.invoke()
            }
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        WorkspaceHeader("Print", "Back", onBack, compact = true)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val keyboardHeight = with(LocalDensity.current) { WindowInsets.ime.getBottom(this).toDp() }
            if (compactPrintLayout(maxWidth, maxHeight, keyboardHeight)) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                        configuration()
                        previewZoom()
                        printOptions()
                    }
                    preview(Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                val configurationHeight = minOf(180.dp, maxHeight * 0.25f)
                val controlsHeight = maxHeight * 0.4f
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Column(Modifier.heightIn(max = configurationHeight).verticalScroll(rememberScrollState())) { configuration() }
                    preview(Modifier.weight(1f).fillMaxWidth())
                    Column(Modifier.heightIn(max = controlsHeight).verticalScroll(rememberScrollState())) {
                        previewZoom()
                        printOptions()
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().heightIn(max = 200.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally) {
            if (state.phase != PrintSessionPhase.IDLE) Text(printSessionMessage(state), style = MaterialTheme.typography.bodySmall)
            state.diagnosticCode?.let { Text(printDiagnosticMessage(it), color = MaterialTheme.colorScheme.error) }
            WorkspacePrimaryButton("Print", onClick = onPrint, enabled = eligible && !preparing && !state.isActive && !state.outputMayExist,
                modifier = Modifier.fillMaxWidth().testTag("confirm-print"))
            if (state.isActive) TextButton(onClick = { session?.cancel() }) { Text("Cancel print") }
            if (state.outputMayExist && !state.isActive) TextButton(onClick = { session?.requestRetry() }, enabled = eligible) { Text("Request another print") }
            TextButton(onClick = onManage, enabled = !state.isActive) { Text("Manage printers") }
            if (state.diagnosticCode != null) {
                TextButton(onClick = { details = !details }) { Text("Technical details") }
                if (details) Text(state.diagnosticCode, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (state.phase == PrintSessionPhase.AWAITING_RETRY_CONFIRMATION) AlertDialog(
        onDismissRequest = { session?.dismissRetry() }, title = { Text("Earlier paper output may exist") },
        text = { Text("Some or all bytes may already have reached the printer. Another attempt can print duplicate content. Check the paper before continuing.") },
        confirmButton = { TextButton(enabled = eligible && !preparing, onClick = {
            if (session?.confirmRetry() == true) onPrint()
        }) { Text("I understand; start new attempt") } },
        dismissButton = { TextButton(onClick = { session?.dismissRetry() }) { Text("Keep previous result") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PrintOptionsControls(profile: PrinterProfile, options: PrintOptions, enabled: Boolean,
    onOptions: (PrintOptions) -> Unit, showQr: Boolean = false) {
    Text("After printing", style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = options.cutIntent == CutIntent.NONE, onClick = { onOptions(options.copy(cutIntent = CutIntent.NONE)) }, enabled = enabled, label = { Text("No cut") })
        FilterChip(selected = options.cutIntent == CutIntent.MANUAL_GUIDE, onClick = { onOptions(options.copy(cutIntent = CutIntent.MANUAL_GUIDE)) }, enabled = enabled, label = { Text("Manual guide") })
        if (profile.cutter.supported) for (mode in CutMode.entries) if (mode.name.lowercase() in profile.cutter.modes) {
            FilterChip(selected = options.cutIntent == CutIntent.AUTOMATIC && options.cutMode == mode,
                onClick = { onOptions(options.copy(cutIntent = CutIntent.AUTOMATIC, cutMode = mode)) }, enabled = enabled,
                label = { Text("Automatic ${mode.name.lowercase()}") })
        }
    }
    var feed by remember(options.finalFeedDots) { mutableStateOf(options.finalFeedDots.toString()) }
    val feedValue = feed.toIntOrNull()
    OutlinedTextField(feed, { value ->
        feed = value.take(6)
    }, label = { Text("Final feed (dots)") }, enabled = enabled, singleLine = true,
        isError = feedValue == null || feedValue !in 0..PrinterPrintDefaults.MAX_FINAL_FEED_DOTS)
    TextButton(enabled = enabled && feedValue != null && feedValue in 0..PrinterPrintDefaults.MAX_FINAL_FEED_DOTS && feedValue != options.finalFeedDots,
        onClick = { feedValue?.let { onOptions(options.copy(finalFeedDots = it)) } }) { Text("Apply feed") }
    if (showQr) {
        Text("QR output", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (strategy in QrStrategy.entries) {
                val supported = strategy != QrStrategy.NATIVE || profile.nativeQr.supported
                if (supported) FilterChip(selected = options.qrStrategy == strategy, onClick = { onOptions(options.copy(qrStrategy = strategy)) },
                    enabled = enabled, label = { Text(strategy.name.lowercase().replaceFirstChar { it.uppercase() }) })
            }
        }
    }
}

private fun presenceMessage(presence: PrinterPresence): String = when (presence) {
    PrinterPresence.NOT_CHECKED -> "Presence not checked"
    PrinterPresence.CHECKING -> "Checking…"
    PrinterPresence.DETECTED -> "Detected during discovery"
    PrinterPresence.NOT_DETECTED -> "Not detected · connection may still work"
    PrinterPresence.BLUETOOTH_DISABLED -> "Bluetooth disabled"
    PrinterPresence.PERMISSION_NEEDED -> "Permission needed to check presence"
    PrinterPresence.UNABLE_TO_CHECK -> "Unable to check presence"
}
