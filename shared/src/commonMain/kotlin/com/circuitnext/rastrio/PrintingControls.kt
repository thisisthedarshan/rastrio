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
import androidx.compose.ui.unit.dp
import com.circuitnext.rastrio.printing.*

@Composable
internal fun PrinterConnectionDetails(status: String, selected: PrinterInstance?, onRefresh: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (selected != null) Text("Selected printer: ${selected.displayName}")
        Text(status, style = MaterialTheme.typography.bodySmall)
        Text("Paired devices are setup candidates; pairing does not prove a nearby or connected printer.",
            style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onRefresh) { Text("Refresh paired printers") }
    }
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

internal fun printDiagnosticMessage(code: String): String = when (code) {
    "BLUETOOTH_PERMISSION_REQUIRED" -> "Allow Nearby devices permission, then refresh paired printers."
    "BLUETOOTH_UNAVAILABLE" -> "This Android device has no available Bluetooth adapter."
    "BLUETOOTH_DISABLED" -> "Enable Bluetooth in Android settings, then refresh."
    "BLUETOOTH_DEVICE_ABSENT" -> "The selected printer is not paired or is no longer available. Pair it in Android settings."
    "BLUETOOTH_BLE_INCOMPATIBLE" -> "This device is BLE-only. Printing requires Classic SPP; a verified GATT transport is not configured."
    "BLUETOOTH_SPP_CONNECTION_FAILED" -> "Classic SPP socket connection failed. Check printer power, pairing and whether another app is connected. SPP compatibility is not confirmed."
    "TRANSPORT_INCOMPATIBLE" -> "The selected printer transport cannot use this connection."
    "PROFILE_MISMATCH" -> "The prepared profile differs from the printer assignment. Prepare again with the assigned profile."
    "TRANSPORT_TIMEOUT" -> "Printer communication timed out. Inspect paper output before requesting another attempt."
    else -> "Open technical details for more information about this failed attempt."
}
