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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.circuitnext.rastrio.core.profile.PrinterProfile
import com.circuitnext.rastrio.presentation.PhysicalPreviewResult
import com.circuitnext.rastrio.printing.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PrintingControls(
    platform: PrintingPlatform,
    printers: List<PrinterInstance>,
    profile: PrinterProfile?,
    platformStatus: String,
    foreground: Boolean,
    selected: PrinterInstance?,
    session: PrintSession?,
    state: PrintSessionState,
    ready: PhysicalPreviewResult.Ready?,
    preparedProfile: PrinterProfile,
    onSelect: (PrinterInstance) -> Unit,
    onPrepare: () -> Unit,
    onPrint: () -> Unit,
    showSelection: Boolean = true,
    prepareLabel: String = "Preview imported profile",
) {
    val active = state.isActive
    val eligible = foreground && canPrintPrepared(selected, profile, ready?.print, preparedProfile) && session != null
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Bluetooth printer", style = MaterialTheme.typography.titleMedium)
        Text(platformStatus, style = MaterialTheme.typography.bodySmall)
        if (showSelection) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AuthoringButton("Import printer profile", !active, platform::importProfile)
            if (platform.supportsProfileExport) AuthoringButton("Export printer profile", !active && profile != null, platform::exportProfile)
            AuthoringButton(prepareLabel, !active && profile != null, onPrepare)
            AuthoringButton("Refresh paired printers", !active, platform::refreshPairedPrinters)
        }
        if (showSelection && profile == null) Text("APP140: No validated physical printer profile selected. Synthetic preview remains available.",
            style = MaterialTheme.typography.bodySmall)
        if (showSelection && profile != null) Text("Imported profile: ${profile.display.name}", style = MaterialTheme.typography.bodySmall)
        if (showSelection && profile != null) Text("Imported configuration · physical capabilities unverified",
            style = MaterialTheme.typography.bodySmall)
        if (showSelection) for (printer in printers) AuthoringButton("Use ${printer.displayName}", !active, onClick = { onSelect(printer) })
        if (selected != null) {
            Text("Selected printer: ${selected.displayName}")
            if (showSelection) AuthoringButton("Prepare selected printer", !active && profile != null, onPrepare)
        }
        if (!eligible) Text("Select a printer and prepare its validated profile before printing.",
            style = MaterialTheme.typography.bodySmall)
        Text(printSessionMessage(state), style = MaterialTheme.typography.bodySmall)
        if (state.outputMayExist && !active) Text("Locally acknowledged: ${state.bytesTransferred} bytes",
            style = MaterialTheme.typography.bodySmall)
        if (state.diagnosticCode != null) Text("Print diagnostic: ${state.diagnosticCode}", color = LocalAuthoringColors.current.error)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AuthoringButton("Print prepared document", eligible && !active && !state.outputMayExist, onPrint)
            AuthoringButton("Cancel print", active, onClick = { session?.cancel() })
            if (state.outputMayExist && !active) AuthoringButton("Request another print", eligible,
                onClick = { session?.requestRetry() })
        }
    }
    if (state.phase == PrintSessionPhase.AWAITING_RETRY_CONFIRMATION) AlertDialog(
        onDismissRequest = { session?.dismissRetry() },
        title = { Text("Earlier paper output may exist") },
        text = { Text("Some or all bytes may already have reached the printer. Another attempt can print duplicate content. Check the paper before continuing.") },
        confirmButton = {
            AuthoringButton("I understand; start new attempt", eligible, onClick = {
                if (session?.confirmRetry() == true) onPrint()
            })
        },
        dismissButton = { AuthoringButton("Keep previous result", onClick = { session?.dismissRetry() }) },
    )
}

internal fun printSessionMessage(state: PrintSessionState): String = when (state.phase) {
    PrintSessionPhase.IDLE -> "Ready to start a print attempt"
    PrintSessionPhase.VALIDATING -> "Validating the prepared print"
    PrintSessionPhase.CONNECTING -> "Connecting to the selected printer"
    PrintSessionPhase.READY -> "Connected; ready to transmit"
    PrintSessionPhase.SENDING -> "Sending · ${state.bytesTransferred} bytes acknowledged locally"
    PrintSessionPhase.TRANSMITTED_UNCONFIRMED -> "Local transmission completed · physical printing is not confirmed"
    PrintSessionPhase.FAILED_BEFORE_TRANSMISSION -> "Print failed before transmission"
    PrintSessionPhase.FAILED_PARTIAL_OR_AMBIGUOUS -> "Print failed · earlier paper output may exist"
    PrintSessionPhase.CANCELLED_BEFORE_TRANSMISSION -> "Print cancelled before transmission"
    PrintSessionPhase.CANCELLED_PARTIAL_OR_AMBIGUOUS -> "Print cancelled · earlier paper output may exist"
    PrintSessionPhase.DISCONNECTED -> "Printer disconnected"
    PrintSessionPhase.AWAITING_RETRY_CONFIRMATION -> "Confirm duplicate-print risk before another attempt"
    PrintSessionPhase.RETRY_REQUESTED -> "New attempt explicitly confirmed"
}
