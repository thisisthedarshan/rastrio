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
internal fun CalibrationControls(draft: ProfileDraft?, platform: PrintingPlatform?, onEdit: () -> Unit) {
    var procedure by remember { mutableStateOf(CalibrationProcedure.GEOMETRY) }
    var width by remember(draft) { mutableStateOf(draft?.get("geometry.printableWidthDots").orEmpty()) }
    var height by remember { mutableStateOf("64") }
    var font by remember { mutableStateOf("") }
    var page by remember { mutableStateOf("") }
    var payload by remember { mutableStateOf("RastrIO") }
    var lines by remember { mutableStateOf("8") }
    var chunk by remember { mutableStateOf("128") }
    var pause by remember { mutableStateOf("0") }
    var device by remember { mutableStateOf<PairedPrinterDevice?>(null) }
    var prepared by remember { mutableStateOf<PreparedPrint?>(null) }
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
    val invalidate = { task?.cancel(); task = null; session?.cancel(); prepared = null; preview = null; confirm = false; status = "" }
    DisposableEffect(session) { onDispose { task?.cancel(); session?.cancel() } }
    LaunchedEffect(foreground, draft) { invalidate() }
    val chunkSize = chunk.toIntOrNull()?.takeIf { it in 1..4096 }
    val pauseMillis = pause.toLongOrNull()?.takeIf { it in 0..1000 }
    val ready = foreground && prepared != null && device in paired && device?.supportsClassic == true &&
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
        var procedureMenu by remember { mutableStateOf(false) }
        Box {
            AuthoringButton("Procedure: ${procedure.title}") { procedureMenu = true }
            DropdownMenu(procedureMenu, onDismissRequest = { procedureMenu = false }) {
                for (candidate in CalibrationProcedure.entries) DropdownMenuItem(text = { Text(candidate.title) }, onClick = {
                    invalidate(); procedure = candidate; procedureMenu = false
                })
            }
        }
        Text("${procedure.hardwareTestId}: ${procedure.instructions}")
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
        Text("Transport test policy · operator trial values, not printer capabilities")
        val effectiveChunk = chunkSize?.let { minOf(it, transport?.constraints?.maxWriteBytes ?: 4096) }
        val effectivePause = pauseMillis?.let { maxOf(it, transport?.constraints?.minWriteIntervalMillis ?: 0) }
        Text("Effective maximum write: ${effectiveChunk ?: "invalid"} bytes; pause: ${effectivePause ?: "invalid"} ms. Platform ceilings can reduce requested chunks; record these effective values and actual partial writes.",
            Modifier.testTag("calibration-effective-policy"))
        OutlinedTextField(chunk, { if (it.length <= 128) { session?.cancel(); confirm = false; chunk = it } }, label = { Text("Maximum write chunk bytes (1–4096)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(pause, { if (it.length <= 128) { session?.cancel(); confirm = false; pause = it } }, label = { Text("Pause between writes (0–1000 ms)") }, modifier = Modifier.fillMaxWidth())
        AuthoringButton("Prepare calibration preview", draft != null && !printState.isActive && task?.isActive != true) {
            invalidate()
            if (draft != null) {
                val request = CalibrationRequest(procedure, width.toIntOrNull() ?: 0, height.toIntOrNull() ?: 0,
                    font.ifBlank { null }, page.ifBlank { null }, payload, lines.toIntOrNull() ?: 0)
                when (val created = CalibrationFixtures.create(draft, request)) {
                    is ProfileValidationResult.Failure -> status = created.diagnostics.joinToString("\n") { "${it.code} ${it.path}: ${it.message}" }
                    is ProfileValidationResult.Success -> {
                        val candidate = created.value
                        task = scope.launch {
                            val job = currentCoroutineContext().job
                            val result = withContext(Dispatchers.Default) { candidate.prepare { !job.isActive } }
                            when (result) {
                                is PreparationResult.Failure -> status = result.diagnostics.joinToString("\n") { "${it.code}: ${it.message}" }
                                is PreparationResult.Success -> when (val projected = result.print.toPrintPreview()) {
                                    is PrintPreviewResult.Failure -> status = projected.diagnostics.joinToString("\n") { "${it.code}: ${it.message}" }
                                    is PrintPreviewResult.Success -> {
                                        prepared = result.print; preview = projected.preview
                                        status = "Prepared and encoder-preflight checked; no connection or output has occurred"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (status.isNotBlank()) Text(status, Modifier.testTag("calibration-status"))
        if (preview != null) {
            Text("Experimental preview · ${preview!!.widthDots} dots · candidate geometry")
            PhysicalPreviewCanvas(preview!!, modifier = Modifier.height(240.dp).fillMaxWidth())
        }
        if (platform != null) AuthoringButton("Refresh calibration devices", !printState.isActive, platform::refreshPairedPrinters)
        for (candidate in paired) AuthoringButton("Calibrate ${candidate.displayName}", candidate.supportsClassic && !printState.isActive) {
            device = candidate; confirm = false
        }
        if (device != null) Text("Experimental device: ${device!!.displayName}")
        Text(printSessionMessage(printState))
        if (printState.diagnosticCode != null) Text(printDiagnosticMessage(printState.diagnosticCode))
        AuthoringButton("Print one experimental sample", ready) { confirm = true }
        AuthoringButton("Cancel calibration", task?.isActive == true || printState.isActive, onClick = invalidate)
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Experimental printer output") },
        text = { Text("Candidate values are unverified. Inspect the preview and protocol procedure before this single bounded sample. Earlier paper output may exist; another sample may duplicate it. This does not save or verify a production profile.") },
        confirmButton = { AuthoringButton("I understand; print one sample", ready) {
            confirm = false
            if (printState.outputMayExist) { session?.requestRetry(); session?.confirmRetry() }
            printOnce()
        } }, dismissButton = { AuthoringButton("Keep preview") { confirm = false } })
}
