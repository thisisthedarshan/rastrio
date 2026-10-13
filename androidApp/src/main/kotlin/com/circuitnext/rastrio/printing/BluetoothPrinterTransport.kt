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
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.circuitnext.rastrio.printing.PrinterInstance
import com.circuitnext.rastrio.printing.PrinterTransport
import com.circuitnext.rastrio.printing.TransportConstraints
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal fun bluetoothPermission(apiLevel: Int): String? =
    if (apiLevel >= 31) Manifest.permission.BLUETOOTH_CONNECT else null

internal fun safeDeviceName(name: String?): String =
    name.orEmpty().filter { !it.isISOControl() && it != '\u2028' && it != '\u2029' && it !in '\u202a'..'\u202e' && it !in '\u2066'..'\u2069' }
        .take(80).ifBlank { "Paired Bluetooth device" }

internal interface PrinterSocket {
    fun connect()
    fun write(bytes: ByteArray, offset: Int, length: Int)
    fun close()
}

/** SPP is an explicit transport choice; its UUID is not a printer capability. */
class BluetoothPrinterTransport internal constructor(
    private val openSocket: (PrinterInstance) -> PrinterSocket,
    override val constraints: TransportConstraints,
) : PrinterTransport {
    private val busy = AtomicBoolean(false)
    private val lock = Any()
    private var socket: PrinterSocket? = null
    private var generation = 0L

    constructor(context: Context, constraints: TransportConstraints) : this({ instance -> try {
        val adapter = requireBluetooth(context)
        val device = adapter.bondedDevices.firstOrNull { it.address == instance.transportLocator }
            ?: throw PrinterTransportException(TransportFailure.BLUETOOTH_DEVICE_ABSENT)
        requireClassicDevice(bluetoothDeviceKind(device.type))
        val handle = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"))
        object : PrinterSocket {
            override fun connect() {
                try { handle.connect() }
                catch (_: SecurityException) { throw PrinterTransportException(TransportFailure.BLUETOOTH_PERMISSION_REQUIRED) }
                catch (_: IOException) { throw PrinterTransportException(TransportFailure.BLUETOOTH_SPP_CONNECTION_FAILED) }
            }
            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                try {
                    requireBluetooth(context)
                    handle.outputStream.write(bytes, offset, length)
                } catch (_: SecurityException) {
                    throw PrinterTransportException(TransportFailure.BLUETOOTH_PERMISSION_REQUIRED)
                }
            }
            override fun close() = handle.close()
        }
    } catch (_: SecurityException) {
        throw PrinterTransportException(TransportFailure.BLUETOOTH_PERMISSION_REQUIRED)
    } catch (_: IOException) {
        throw PrinterTransportException(TransportFailure.BLUETOOTH_SPP_CONNECTION_FAILED)
    } }, constraints)

    override suspend fun connect(instance: PrinterInstance) {
        close()
        if (instance.transportKind != PrinterTransportKind.BLUETOOTH_CLASSIC)
            throw PrinterTransportException(TransportFailure.TRANSPORT_INCOMPATIBLE)
        val token = synchronized(lock) { generation }
        blocking {
            val candidate = openSocket(instance)
            synchronized(lock) {
                if (token != generation) {
                    candidate.close()
                    throw IOException("Connection cancelled")
                }
                socket = candidate
            }
            candidate.connect()
            synchronized(lock) { if (token != generation) throw IOException("Connection cancelled") }
        }
    }

    override suspend fun write(bytes: ByteArray, offset: Int, length: Int): Int {
        require(offset >= 0 && length in 1..constraints.maxWriteBytes && offset <= bytes.size - length)
        val connected = synchronized(lock) { socket } ?: throw IOException("Printer is disconnected")
        return blocking { connected.write(bytes, offset, length); length }
    }

    override fun close() {
        val old = synchronized(lock) { generation++; socket.also { socket = null } }
        try { old?.close() } catch (_: IOException) { /* Closing must remain idempotent after disconnect. */ }
    }

    // Cancellation closes the socket immediately; waiting on a blocking IO dispatcher would stall it.
    private suspend fun <T> blocking(operation: () -> T): T = suspendCancellableCoroutine { continuation ->
        if (!busy.compareAndSet(false, true)) {
            continuation.resumeWithException(IOException("A Bluetooth operation is already active"))
        } else {
            continuation.invokeOnCancellation { close() }
            Thread({
                val result = try {
                    if (!continuation.isActive) return@Thread
                    Result.success(operation())
                } catch (failure: Exception) {
                    close()
                    Result.failure<T>(failure)
                } finally { busy.set(false) }
                if (continuation.isActive) result.fold(continuation::resume, continuation::resumeWithException)
            }, "RastrIO Bluetooth IO").apply { isDaemon = true }.start()
        }
    }
}

internal fun requireBluetooth(context: Context): BluetoothAdapter {
    val permission = bluetoothPermission(Build.VERSION.SDK_INT)
    if (permission != null && context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED)
        throw PrinterTransportException(TransportFailure.BLUETOOTH_PERMISSION_REQUIRED)
    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        ?: throw PrinterTransportException(TransportFailure.BLUETOOTH_UNAVAILABLE)
    if (!adapter.isEnabled) throw PrinterTransportException(TransportFailure.BLUETOOTH_DISABLED)
    return adapter
}

internal fun bluetoothDeviceKind(type: Int): BluetoothDeviceKind = when (type) {
    BluetoothDevice.DEVICE_TYPE_CLASSIC -> BluetoothDeviceKind.CLASSIC
    BluetoothDevice.DEVICE_TYPE_DUAL -> BluetoothDeviceKind.DUAL
    BluetoothDevice.DEVICE_TYPE_LE -> BluetoothDeviceKind.BLE_ONLY
    else -> BluetoothDeviceKind.UNKNOWN
}

internal fun requireClassicDevice(kind: BluetoothDeviceKind) {
    if (kind == BluetoothDeviceKind.BLE_ONLY)
        throw PrinterTransportException(TransportFailure.BLUETOOTH_BLE_INCOMPATIBLE)
}

internal fun pairedPrinters(context: Context): List<PairedPrinterDevice> = try {
    requireBluetooth(context).bondedDevices.take(100).map {
        PairedPrinterDevice(it.address, safeDeviceName(it.name), bluetoothDeviceKind(it.type))
    }.sortedBy { it.displayName }
} catch (_: SecurityException) {
    throw PrinterTransportException(TransportFailure.BLUETOOTH_PERMISSION_REQUIRED)
}
