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

package com.circuitnext.rastrio.printing

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull

internal fun bluetoothPresencePermissions(api: Int): Array<String> = if (api >= 31)
    arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
    else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

/** Discovery only; this boundary deliberately has no socket or printer-command API. */
internal interface BluetoothPresenceAccess {
    fun register(foundOrFinished: (String?) -> Unit): () -> Unit
    fun start(): Boolean
    fun stop()
}

internal suspend fun checkBluetoothPresence(locator: String, access: BluetoothPresenceAccess, timeoutMillis: Long = 10_000): PrinterPresence {
    require(timeoutMillis in 1..10_000)
    val replies = Channel<PrinterPresence>(1)
    val release = access.register { found ->
        if (found == locator) replies.trySend(PrinterPresence.DETECTED)
        else if (found == null) replies.trySend(PrinterPresence.NOT_DETECTED)
    }
    try {
        if (!access.start()) return PrinterPresence.UNABLE_TO_CHECK
        return withTimeoutOrNull(timeoutMillis) { replies.receive() } ?: PrinterPresence.NOT_DETECTED
    } finally {
        try { access.stop() } finally { release(); replies.close() }
    }
}

internal class AndroidBluetoothPresenceAccess(private val context: Context, private val adapter: BluetoothAdapter) : BluetoothPresenceAccess {
    @Suppress("DEPRECATION")
    override fun register(foundOrFinished: (String?) -> Unit): () -> Unit {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                try {
                    when (intent?.action) {
                        BluetoothDevice.ACTION_FOUND -> intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                            ?.address?.let(foundOrFinished)
                        BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> foundOrFinished(null)
                    }
                } catch (_: RuntimeException) { /* Malformed discovery metadata cannot establish presence. */ }
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND); addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }, ContextCompat.RECEIVER_EXPORTED)
        return { context.unregisterReceiver(receiver) }
    }
    override fun start(): Boolean = try { adapter.startDiscovery() } catch (_: SecurityException) {
        throw PrinterTransportException(TransportFailure.BLUETOOTH_PERMISSION_REQUIRED)
    }
    override fun stop() { try { adapter.cancelDiscovery() } catch (_: SecurityException) { } }
}
