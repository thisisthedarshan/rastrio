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

/** Portable observations only; inspection has no access to print commands or a PrintSession. */
enum class DeviceDiscoveryStatus { IDLE, LOADING, READY, PERMISSION_REQUIRED, DISABLED, UNAVAILABLE, FAILED }
data class DeviceDiscoveryState(val status: DeviceDiscoveryStatus = DeviceDiscoveryStatus.IDLE)
enum class ServiceDiscoveryStatus { AVAILABLE, CACHED, UNAVAILABLE, TIMEOUT, MALFORMED, UNSUPPORTED }
enum class ServicePresence { PRESENT, ABSENT, UNKNOWN }
enum class InspectionEvidence { DEVICE_REPORTED, MANUFACTURER_DOCUMENTED, OPERATOR_REPORTED, INFERRED, PHYSICALLY_VERIFIED }

data class ProfileSuggestion(
    val path: String,
    val value: String,
    val source: InspectionEvidence,
    val sourceDescription: String,
    val deviceLocator: String,
) {
    init {
        require(path.length in 1..128 && value.length <= 1024 && sourceDescription.length in 1..512)
        require(deviceLocator.length in 1..1024)
    }
}

data class PrinterInspection(
    val device: PairedPrinterDevice,
    val serviceDiscovery: ServiceDiscoveryStatus,
    val spp: ServicePresence,
    val explanation: String,
    val suggestions: List<ProfileSuggestion> = emptyList(),
) {
    init { require(explanation.length <= 1024 && suggestions.size <= 32) }
}

/** Metadata is a naming suggestion, never a model match or capability proof. */
fun metadataInspection(device: PairedPrinterDevice, status: ServiceDiscoveryStatus = ServiceDiscoveryStatus.UNSUPPORTED) =
    PrinterInspection(device, status, ServicePresence.UNKNOWN,
        if (device.kind == BluetoothDeviceKind.BLE_ONLY)
            "This device uses Bluetooth Low Energy. RastrIO's current printing connection requires Bluetooth Classic SPP."
        else "Printer identification queries are unavailable. Resolution, dot width and print commands need separate evidence.",
        listOf(ProfileSuggestion("display.name", device.displayName, InspectionEvidence.DEVICE_REPORTED,
            "Android paired-device name; editable and not proof of printer model", device.locator)))

/** Explicit operator-selected worksheet template; no model match or verified capabilities. */
fun genericTwoInchSuggestions(device: PairedPrinterDevice): List<ProfileSuggestion> = listOf(
    "worksheet.paperWidthMm" to "58", "worksheet.printableWidthMm" to "48",
    "worksheet.charset" to "CP437", "worksheet.nativeFont" to "12×24",
).map { (path, value) -> ProfileSuggestion(path, value, InspectionEvidence.INFERRED,
    "Generic 2-inch starter suggestion; review against your printer. Resolution, dot width and host commands remain unknown.", device.locator) }
