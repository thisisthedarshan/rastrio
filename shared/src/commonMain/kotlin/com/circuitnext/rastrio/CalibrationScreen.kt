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
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.circuitnext.rastrio.core.escpos.EscPosEncoder
import com.circuitnext.rastrio.core.printer.*
import com.circuitnext.rastrio.core.preview.*
import com.circuitnext.rastrio.core.profile.*
import com.circuitnext.rastrio.printing.*
import kotlinx.coroutines.*

/** A separate bounded session exposes candidate experiments only after explicit preview and consent. */
@Composable
internal fun CalibrationControls(draft: ProfileDraft?, platform: PrintingPlatform?, onEdit: () -> Unit, setupDevice: PairedPrinterDevice? = null, onDraft: ((ProfileDraft) -> Unit)? = null) {
    var experimentalStarter by remember(draft) { mutableStateOf(false) }
    var commandAcknowledged by remember(draft) { mutableStateOf(false) }
    var horizontalDots by remember { mutableStateOf("48") }
    var verticalDots by remember { mutableStateOf("48") }
    var horizontalMm by remember { mutableStateOf("") }
    var verticalMm by remember { mutableStateOf("") }
    var paperObserved by remember { mutableStateOf(false) }
    var measurement by remember { mutableStateOf<CalibrationMeasurements.Result?>(null) }
    var measurementReview by remember { mutableStateOf(false) }
    var procedure by remember { mutableStateOf(CalibrationProcedure.GEOMETRY) }
    var width by remember(draft) { mutableStateOf(draft?.get("geometry.printableWidthDots").orEmpty()) }
    var height by remember { mutableStateOf("64") }
    var font by remember { mutableStateOf("") }
    var page by remember { mutableStateOf("") }
    var payload by remember { mutableStateOf("RastrIO") }
    var lines by remember { mutableStateOf("8") }
    var chunk by remember { mutableStateOf("128") }
    var pause by remember { mutableStateOf("0") }
    var device by remember(platform, setupDevice) { mutableStateOf(setupDevice) }
    var devicePicker by remember(setupDevice) { mutableStateOf(setupDevice == null) }
    var deviceSearch by remember { mutableStateOf("") }
    var technicalDetails by remember { mutableStateOf(false) }
    var transportDetails by remember { mutableStateOf(false) }
    var prepared by remember { mutableStateOf<PreparedPrint?>(null) }
    var preparedDraft by remember { mutableStateOf<ProfileDraft?>(null) }
    var preparedDeviceLocator by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<PrintPreview?>(null) }
    var status by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    var task by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val paired = platform?.pairedDevices?.collectAsState()?.value.orEmpty()
    val foreground = platform?.foreground?.collectAsState()?.value ?: true
    val transport = remember(platform) { platform?.createTransport() }
    val session = remember(transport) { transport?.let { PrintSession(it,
        PrintSessionPolicy(maxWriteBytes = 4096, maxEncoderChunkBytes = 4096, maxEncodedBytes = 65536),
        encoder = EscPosEncoder(com.circuitnext.rastrio.core.escpos.EncodingResourcePolicy(maxChunkBytes = 4096, maxEncodedBytes = 65536))) } }
    val printState = session?.state?.collectAsState()?.value ?: PrintSessionState()
    val invalidate = { task?.cancel(); task = null; session?.cancel(); prepared = null; preparedDraft = null; preparedDeviceLocator = null; preview = null; confirm = false; status = ""; measurement = null; measurementReview = false; paperObserved = false }
    DisposableEffect(session) { onDispose { task?.cancel(); session?.cancel() } }
    LaunchedEffect(foreground, draft, setupDevice) { invalidate() }
    val candidateDraft = remember(draft, experimentalStarter, commandAcknowledged) {
        if (experimentalStarter) draft?.let { CalibrationFixtures.experimentalStarter(it, commandAcknowledged) } else draft
    }
    val sample = remember(preparedDraft, prepared, preparedDeviceLocator, procedure) {
        if (procedure == CalibrationProcedure.GEOMETRY && preparedDraft != null && prepared != null && preparedDeviceLocator != null)
            CalibrationMeasurements.capture(preparedDraft!!, prepared!!, preparedDeviceLocator!!, width.toIntOrNull() ?: 0, height.toIntOrNull() ?: 0) else null
    }
    val chunkSize = chunk.toIntOrNull()?.takeIf { it in 1..4096 }
    val pauseMillis = pause.toLongOrNull()?.takeIf { it in 0..1000 }
    val ready = foreground && prepared != null && preparedDraft == draft && preparedDeviceLocator == device?.locator && device in paired && device?.supportsClassic == true &&
        session != null && chunkSize != null && pauseMillis != null && !printState.isActive
    val printOnce: () -> Unit = {
        val plan = prepared
        val selected = device
        if (ready && plan != null && selected != null && session != null) scope.launch {
            session.print(PrinterInstance("experimental-calibration", selected.displayName,
                PrinterTransportKind.BLUETOOTH_CLASSIC, selected.locator, plan.target.profileId, plan.target.profileRevision), plan,
                PrinterWriteGuidance(chunkSize, pauseMillis))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Complete a profile by calibration", style = MaterialTheme.typography.titleLarge)
        if (draft == null) {
            Text("Create or edit a local profile draft first. Enter candidate values supported by reliable protocol evidence; unknown mandatory geometry stays blank.")
        } else Text("Candidate draft: ${draft["display.name"].ifBlank { draft.draftId }} · isolated experimental output")
        AuthoringButton("Edit calibration profile", onClick = onEdit)
        if (draft != null) {
            Row {
                Checkbox(experimentalStarter, onCheckedChange = { invalidate(); experimentalStarter = it; commandAcknowledged = false
                    if (it) { width = "64"; height = "64" } })
                Text("Use experimental raster starter", Modifier.weight(1f))
            }
            if (experimentalStarter) {
                Text("Trial hypotheses: 203 × 203 DPI, 384-dot candidate head width, generic ESC/POS initialization and GS v 0 raster only. This small sample does not establish head width. No native text, QR, barcode, cutter or status support.")
                Row {
                    Checkbox(commandAcknowledged, onCheckedChange = { invalidate(); commandAcknowledged = it })
                    Text("I knowingly select this registered command trial; compatibility and geometry are unverified. If I cannot identify or select this path, I must stop.", Modifier.weight(1f))
                }
            }
        }
        var procedureMenu by remember { mutableStateOf(false) }
        Box {
            AuthoringButton("Procedure: ${procedure.title}") { procedureMenu = true }
            DropdownMenu(procedureMenu, onDismissRequest = { procedureMenu = false }) {
                for (candidate in CalibrationProcedure.entries) DropdownMenuItem(text = { Text(candidate.title) }, onClick = {
                    invalidate(); procedure = candidate; procedureMenu = false
                })
            }
        }
        Text(procedure.instructions)
        Text("This procedure needs measured paper evidence. Its preview and resource checks can run without a printer.", style = MaterialTheme.typography.bodySmall)
        Text("No values or capabilities are promoted automatically. Record measured results in the draft and permanent hardware evidence before ordinary activation.")
        fun edit(update: (String) -> Unit): (String) -> Unit = { if (it.length <= 128) { invalidate(); update(it) } }
        OutlinedTextField(width, edit { width = it }, label = { Text("Candidate pattern/QR width (1–1024 dots)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(height, edit { height = it }, label = { Text("Pattern height (1–64 dots)") }, modifier = Modifier.fillMaxWidth())
        if (procedure in listOf(CalibrationProcedure.NATIVE_FONT, CalibrationProcedure.CP437, CalibrationProcedure.BUFFER_CHUNK)) {
            OutlinedTextField(font, edit { font = it }, label = { Text("Candidate native font ID") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(page, edit { page = it }, label = { Text("Candidate code page ID") }, modifier = Modifier.fillMaxWidth())
        }
        if (procedure in listOf(CalibrationProcedure.NATIVE_QR, CalibrationProcedure.RASTER_QR))
            OutlinedTextField(payload, edit { payload = it }, label = { Text("Opaque QR test payload") }, modifier = Modifier.fillMaxWidth())
        if (procedure == CalibrationProcedure.BUFFER_CHUNK)
            OutlinedTextField(lines, edit { lines = it }, label = { Text("Bounded sample lines (1–16)") }, modifier = Modifier.fillMaxWidth())
        val effectiveChunk = chunkSize?.let { minOf(it, transport?.constraints?.maxWriteBytes ?: 4096) }
        val effectivePause = pauseMillis?.let { maxOf(it, transport?.constraints?.minWriteIntervalMillis ?: 0) }
        Text("Effective maximum write: ${effectiveChunk ?: "invalid"} bytes; pause: ${effectivePause ?: "invalid"} ms. Platform ceilings can reduce requested chunks; record these effective values and actual partial writes.",
            Modifier.testTag("calibration-effective-policy"))
        TextButton(onClick = { transportDetails = !transportDetails }) { Text(if (transportDetails) "Hide transport trial settings" else "Transport trial settings") }
        if (transportDetails) {
            Text("Operator trial values, not verified printer capabilities")
            OutlinedTextField(chunk, { if (it.length <= 128) { session?.cancel(); confirm = false; chunk = it } }, label = { Text("Maximum write chunk bytes (1–4096)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(pause, { if (it.length <= 128) { session?.cancel(); confirm = false; pause = it } }, label = { Text("Pause between writes (0–1000 ms)") }, modifier = Modifier.fillMaxWidth())
        }
        AuthoringButton("Prepare calibration preview", candidateDraft != null && !printState.isActive && task?.isActive != true) {
            invalidate()
            if (candidateDraft != null) {
                val request = CalibrationRequest(procedure, width.toIntOrNull() ?: 0, height.toIntOrNull() ?: 0,
                    font.ifBlank { null }, page.ifBlank { null }, payload, lines.toIntOrNull() ?: 0)
                when (val created = CalibrationFixtures.create(candidateDraft, request)) {
                    is ProfileValidationResult.Failure -> status = created.diagnostics.joinToString("\n") { "${it.code} ${it.path}: ${it.message}" }
                    is ProfileValidationResult.Success -> {
                        val candidate = created.value
                        val sourceDraft = draft
                        val sourceDeviceLocator = device?.locator
                        task = scope.launch {
                            val job = currentCoroutineContext().job
                            val result = withContext(Dispatchers.Default) { candidate.prepare { !job.isActive } }
                            when (result) {
                                is PreparationResult.Failure -> status = result.diagnostics.joinToString("\n") { "${it.code}: ${it.message}" }
                                is PreparationResult.Success -> when (val projected = result.print.toPrintPreview()) {
                                    is PrintPreviewResult.Failure -> status = projected.diagnostics.joinToString("\n") { "${it.code}: ${it.message}" }
                                    is PrintPreviewResult.Success -> {
                                        prepared = result.print; preparedDraft = sourceDraft; preparedDeviceLocator = sourceDeviceLocator; preview = projected.preview
                                        status = "Prepared and encoder-preflight checked; no connection or output has occurred"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (status.isNotBlank()) Text(status.lineSequence().first().replace(Regex("^[A-Z]{3}[0-9]{3}[^:]*: ?"), ""), Modifier.testTag("calibration-status"))
        TextButton(onClick = { technicalDetails = !technicalDetails }) { Text(if (technicalDetails) "Hide calibration technical details" else "Calibration technical details") }
        if (technicalDetails) {
            Text("${procedure.hardwareTestId}: ${procedure.instructions}", style = MaterialTheme.typography.bodySmall)
            if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
            printState.diagnosticCode?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        if (preview != null) {
            Text("Experimental preview · ${preview!!.widthDots} dots · candidate geometry")
            PhysicalPreviewCanvas(preview!!, modifier = Modifier.height(240.dp).fillMaxWidth())
        }
        if (platform != null) AuthoringButton("Refresh calibration devices", !printState.isActive, platform::refreshPairedPrinters)
        if (device != null && !devicePicker) {
            Text("Experimental device: ${device!!.displayName}", style = MaterialTheme.typography.titleMedium)
            Text(deviceTypeLabel(device!!.kind), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { invalidate(); devicePicker = true }) { Text("Change calibration device") }
        } else {
            OutlinedTextField(deviceSearch, { if (it.length <= 256) deviceSearch = it }, label = { Text("Search calibration devices") }, modifier = Modifier.fillMaxWidth())
            val candidates = deviceCandidates(paired, deviceSearch).filter { it.supportsClassic }
            if (candidates.isEmpty()) Text("No paired Classic candidates. Pair a device, allow Nearby devices and refresh.")
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 240.dp).selectableGroup().testTag("calibration-device-list"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(candidates, key = { it.locator }) { candidate -> DeviceRow(candidate, candidate == device,
                    if (printState.isActive) null else { {
                        invalidate(); device = candidate; devicePicker = false
                    } }) }
            }
        }
        if (procedure == CalibrationProcedure.GEOMETRY && preview != null) {
            Text("Measure the printed sample", style = MaterialTheme.typography.titleMedium)
            Text("Measure centre to centre between eight-dot tick marks. Six spaces are 48 dots. Measure horizontal and vertical spans independently; edge clipping and reliable head width need separate confirmation.")
            fun measuredEdit(update: (String) -> Unit): (String) -> Unit = { if (it.length <= 32) { measurement = null; measurementReview = false; update(it) } }
            OutlinedTextField(horizontalDots, measuredEdit { horizontalDots = it }, label = { Text("Horizontal known dots (multiples of 8)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(horizontalMm, measuredEdit { horizontalMm = it }, label = { Text("Measured horizontal span (mm)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(verticalDots, measuredEdit { verticalDots = it }, label = { Text("Vertical known dots (multiples of 8)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(verticalMm, measuredEdit { verticalMm = it }, label = { Text("Measured vertical span (mm)") }, modifier = Modifier.fillMaxWidth())
            Row {
                Checkbox(paperObserved, onCheckedChange = { paperObserved = it; measurement = null; measurementReview = false })
                Text("I measured paper from this exact sample and selected printer.", Modifier.weight(1f))
            }
            AuthoringButton("Review measured DPI", sample != null && preparedDraft == draft && preparedDeviceLocator == device?.locator && paperObserved && foreground && !printState.isActive) {
                measurement = sample?.measure(horizontalDots.toIntOrNull() ?: 0, horizontalMm.toDoubleOrNull() ?: Double.NaN,
                    verticalDots.toIntOrNull() ?: 0, verticalMm.toDoubleOrNull() ?: Double.NaN)
                measurementReview = measurement != null
                if (measurement == null) status = "Enter finite positive spans up to 1024 mm and known tick intervals inside this sample; each measured DPI must be 1–600."
            }
        }
        Text(printSessionMessage(printState))
        if (printState.diagnosticCode != null) Text(printDiagnosticMessage(printState.diagnosticCode))
        if (!ready) Text("Prepare a valid candidate preview, choose an available device and valid transport settings before printing.", style = MaterialTheme.typography.bodySmall)
        AuthoringButton("Print one experimental sample", ready) { confirm = true }
        AuthoringButton("Cancel calibration", task?.isActive == true || printState.isActive, onClick = invalidate)
    }
    if (measurementReview && measurement != null) AlertDialog(onDismissRequest = { measurementReview = false },
        title = { Text("Review measured DPI") },
        text = { Text("Horizontal: ${measurement!!.horizontalDpi} DPI; vertical: ${measurement!!.verticalDpi} DPI. The worksheet stores rounded integers with operator source notes. Printable head width is unchanged. Repeat measurements and verify protocol and clipping separately; this does not activate or verify a profile.") },
        confirmButton = { AuthoringButton("Apply measurements to worksheet", onDraft != null && paperObserved && foreground && !printState.isActive) {
            val updated = if (draft != null && prepared != null && device != null)
                sample?.apply(draft, prepared!!, device!!.locator, measurement!!) else null
            measurementReview = false
            if (updated != null) { invalidate(); onDraft?.invoke(updated); status = "Measured DPI applied as operator observations; head width and capability verification remain separate." }
            else status = "Measurements no longer match this draft, sample or printer, or worksheet storage is full. Prepare and measure again."
        } }, dismissButton = { AuthoringButton("Keep measuring") { measurementReview = false } })
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Experimental printer output") },
        text = { Text("Candidate values are unverified. Inspect the preview and protocol procedure before this single bounded sample. Earlier paper output may exist; another sample may duplicate it. This does not save or verify a production profile.") },
        confirmButton = { AuthoringButton("I understand; print one sample", ready) {
            confirm = false
            if (printState.outputMayExist) { session?.requestRetry(); session?.confirmRetry() }
            printOnce()
        } }, dismissButton = { AuthoringButton("Keep preview") { confirm = false } })
}
