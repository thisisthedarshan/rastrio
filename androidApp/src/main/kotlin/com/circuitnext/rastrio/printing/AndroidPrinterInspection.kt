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

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

internal const val MAX_INSPECTION_UUIDS = 64
private val SPP_SERVICE = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")

/** This boundary exposes SDP only; it cannot open sockets or transmit printer commands. */
internal interface SdpAccess {
    fun cached(): List<UUID>?
    fun listen(callback: (String?, List<UUID>?) -> Unit): () -> Unit
    fun request(): Boolean
}

internal suspend fun inspectBluetoothServices(
    device: PairedPrinterDevice,
    access: SdpAccess,
    timeoutMillis: Long = 10_000,
): PrinterInspection {
    require(timeoutMillis in 1..10_000)
    if (device.kind == BluetoothDeviceKind.BLE_ONLY) return metadataInspection(device)
    val replies = Channel<List<UUID>?>(1)
    val stop = access.listen { locator, uuids ->
        if (locator == device.locator) replies.trySend(uuids)
    }
    try {
        if (!access.request()) return serviceInspection(device, access.cached(), ServiceDiscoveryStatus.UNAVAILABLE)
        val reply = withTimeoutOrNull(timeoutMillis) { ServiceReply(replies.receive()) }
            ?: return serviceInspection(device, access.cached(), ServiceDiscoveryStatus.TIMEOUT)
        // Android ACTION_UUID can report its cache after SDP failure; it cannot prove freshness.
        return serviceInspection(device, reply.uuids, ServiceDiscoveryStatus.CACHED)
    } finally {
        stop()
        replies.close()
    }
}

private data class ServiceReply(val uuids: List<UUID>?)

private fun serviceInspection(
    device: PairedPrinterDevice,
    uuids: List<UUID>?,
    status: ServiceDiscoveryStatus,
): PrinterInspection {
    if (uuids != null && uuids.size > MAX_INSPECTION_UUIDS)
        return metadataInspection(device, ServiceDiscoveryStatus.MALFORMED)
    if (uuids == null) return metadataInspection(device,
        if (status == ServiceDiscoveryStatus.CACHED) ServiceDiscoveryStatus.MALFORMED else status)
    val presence = if (SPP_SERVICE in uuids) ServicePresence.PRESENT else ServicePresence.UNKNOWN
    return metadataInspection(device).copy(
        serviceDiscovery = if (status == ServiceDiscoveryStatus.UNAVAILABLE) ServiceDiscoveryStatus.CACHED else status,
        spp = presence,
        explanation = (if (presence == ServicePresence.PRESENT)
            "Android's service cache advertises Classic SPP. Freshness and connection compatibility remain unverified. "
        else "Android's service records do not establish Classic SPP support. ") +
            "Printer identification queries are unsupported; resolution, dot width and print commands remain unknown.")
}

internal class AndroidSdpAccess(private val context: Context, private val device: BluetoothDevice) : SdpAccess {
    override fun cached(): List<UUID>? = try {
        device.uuids?.let { values -> values.take(MAX_INSPECTION_UUIDS + 1).map { it.uuid } }
    } catch (_: SecurityException) { null }
    @Suppress("DEPRECATION")
    override fun listen(callback: (String?, List<UUID>?) -> Unit): () -> Unit {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != BluetoothDevice.ACTION_UUID) return
                var locator: String? = null
                try {
                    val remote = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
                    locator = remote.address
                    if (locator != device.address) return
                    val values = intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID)
                    val uuids = if (values == null || values.size > MAX_INSPECTION_UUIDS || values.any { it !is ParcelUuid }) null
                        else values.map { (it as ParcelUuid).uuid }
                    callback(locator, uuids)
                } catch (_: RuntimeException) { callback(locator, null) }
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(BluetoothDevice.ACTION_UUID),
            ContextCompat.RECEIVER_EXPORTED)
        return { context.unregisterReceiver(receiver) }
    }
    override fun request(): Boolean = try {
        if (Build.VERSION.SDK_INT >= 37) device.fetchUuids(BluetoothDevice.TRANSPORT_BREDR)
        else device.fetchUuidsWithSdp()
    } catch (_: SecurityException) { false }
}

internal fun discoveryStatus(failure: TransportFailure): DeviceDiscoveryStatus = when (failure) {
    TransportFailure.BLUETOOTH_PERMISSION_REQUIRED -> DeviceDiscoveryStatus.PERMISSION_REQUIRED
    TransportFailure.BLUETOOTH_UNAVAILABLE -> DeviceDiscoveryStatus.UNAVAILABLE
    TransportFailure.BLUETOOTH_DISABLED -> DeviceDiscoveryStatus.DISABLED
    else -> DeviceDiscoveryStatus.FAILED
}

internal fun boundedPairedInventory(devices: List<PairedPrinterDevice>): List<PairedPrinterDevice> =
    devices.asSequence().distinctBy { it.locator }.take(100)
        .sortedWith(compareBy<PairedPrinterDevice> { it.kind == BluetoothDeviceKind.BLE_ONLY }.thenBy { it.displayName })
        .toList()
