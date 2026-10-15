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

enum class PrinterTransportKind { BLUETOOTH_CLASSIC, DEVELOPMENT }

enum class BluetoothDeviceKind { CLASSIC, DUAL, BLE_ONLY, UNKNOWN }

/** Discovery metadata only; choosing a device does not connect or activate a profile. */
data class PairedPrinterDevice(val locator: String, val displayName: String, val kind: BluetoothDeviceKind) {
    init { require(validMetadata(locator, 1024) && validMetadata(displayName, 256)) }
    val supportsClassic: Boolean get() = kind != BluetoothDeviceKind.BLE_ONLY
    override fun toString(): String = "PairedPrinterDevice(kind=$kind)"
}

enum class TransportFailure {
    BLUETOOTH_PERMISSION_REQUIRED, BLUETOOTH_UNAVAILABLE, BLUETOOTH_DISABLED,
    BLUETOOTH_DEVICE_ABSENT, BLUETOOTH_BLE_INCOMPATIBLE, BLUETOOTH_SPP_CONNECTION_FAILED,
    TRANSPORT_INCOMPATIBLE,
}

/** Only trusted codes reach diagnostics; exception details may contain private platform metadata. */
class PrinterTransportException(val failure: TransportFailure) : Exception(failure.name)

/** Local application state; never exported as capability data or included in diagnostic messages. */
data class PrinterInstance(
    val id: String,
    val displayName: String,
    val transportKind: PrinterTransportKind,
    val transportLocator: String,
    val profileId: String,
    val profileRevision: Int,
    val defaults: PrinterPrintDefaults = PrinterPrintDefaults(),
    val overrides: com.circuitnext.rastrio.core.profile.PrinterProfileOverride? = null,
) {
    init {
        require(validMetadata(id, 128) && validMetadata(displayName, 256))
        require(validMetadata(transportLocator, 1024) && validMetadata(profileId, 128))
        require(profileRevision > 0)
    }

    override fun toString(): String = "PrinterInstance(transportKind=$transportKind)"
}

private fun validMetadata(value: String, limit: Int): Boolean = value.isNotBlank() &&
    value.length <= limit && value.none { it.code in 0..31 || it.code in 127..159 || it == '\u2028' || it == '\u2029' }

data class TransportConstraints(
    val maxWriteBytes: Int,
    val writeTimeoutMillis: Long = 15_000,
    val connectTimeoutMillis: Long = 15_000,
    val minWriteIntervalMillis: Long = 0,
) {
    init {
        require(maxWriteBytes > 0)
        require(writeTimeoutMillis > 0 && connectTimeoutMillis > 0 && minWriteIntervalMillis >= 0)
    }
}

/** Trusted guidance supplied by the application; null preserves unknown printer buffering/pacing. */
data class PrinterWriteGuidance(val maxWriteBytes: Int? = null, val minWriteIntervalMillis: Long? = null) {
    init {
        require(maxWriteBytes == null || maxWriteBytes > 0)
        require(minWriteIntervalMillis == null || minWriteIntervalMillis >= 0)
    }
}

/** Safety ceilings are application policy, not measured printer capabilities. */
data class PrintSessionPolicy(
    val maxWriteBytes: Int = 64 * 1024,
    val maxEncoderChunkBytes: Int = 64 * 1024,
    val maxEncodedBytes: Long = 128L * 1024 * 1024,
    val writeTimeoutMillis: Long = 60_000,
    val connectTimeoutMillis: Long = 60_000,
) {
    init {
        require(maxWriteBytes in 1..64 * 1024 && maxEncoderChunkBytes in 1..64 * 1024)
        require(maxEncodedBytes in 1..128L * 1024 * 1024)
        require(writeTimeoutMillis in 1..60_000 && connectTimeoutMillis in 1..60_000)
    }

    fun intersect(platform: TransportConstraints, printer: PrinterWriteGuidance): TransportConstraints =
        TransportConstraints(
            minOf(maxWriteBytes, platform.maxWriteBytes, printer.maxWriteBytes ?: Int.MAX_VALUE),
            minOf(writeTimeoutMillis, platform.writeTimeoutMillis),
            minOf(connectTimeoutMillis, platform.connectTimeoutMillis),
            maxOf(platform.minWriteIntervalMillis, printer.minWriteIntervalMillis ?: 0),
        )
}

/** Writes complete before the next chunk is produced; close must unblock pending platform I/O. */
interface PrinterTransport {
    val constraints: TransportConstraints
    suspend fun connect(instance: PrinterInstance)
    suspend fun write(bytes: ByteArray, offset: Int, length: Int): Int
    fun close()
}
