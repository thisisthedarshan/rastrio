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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import java.util.UUID
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidPrinterInspectionTest {
    private val device = PairedPrinterDevice("selected", "BillQuick Go", BluetoothDeviceKind.CLASSIC)
    private val spp = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
    private class Access : SdpAccess {
        var callback: ((String?, List<UUID>?) -> Unit)? = null
        var requested = 0
        var removed = 0
        var accepted = true
        var records: List<UUID>? = null
        override fun cached() = records
        override fun listen(callback: (String?, List<UUID>?) -> Unit): () -> Unit {
            this.callback = callback
            return { removed++ }
        }
        override fun request(): Boolean { requested++; return accepted }
    }

    @Test fun classicDualAndUnknownNeedServiceEvidenceAndNeverInventGeometry() = runTest {
        for (kind in listOf(BluetoothDeviceKind.CLASSIC, BluetoothDeviceKind.DUAL, BluetoothDeviceKind.UNKNOWN)) {
            val access = Access()
            val inspection = async { inspectBluetoothServices(device.copy(kind = kind), access, 100) }
            runCurrent()
            access.callback!!("selected", listOf(spp))
            val result = inspection.await()
            assertEquals(ServiceDiscoveryStatus.CACHED, result.serviceDiscovery)
            assertEquals(ServicePresence.PRESENT, result.spp)
            assertTrue(result.explanation.contains("unverified"))
            assertEquals(listOf("display.name"), result.suggestions.map { it.path })
            assertEquals(InspectionEvidence.DEVICE_REPORTED, result.suggestions.single().source)
            assertEquals("selected", result.suggestions.single().deviceLocator)
            assertEquals(1, access.requested)
            assertEquals(1, access.removed)
        }
    }

    @Test fun bleOnlyNeverRequestsSdpOrCreatesAnyCommandPath() = runTest {
        val access = Access()
        val result = inspectBluetoothServices(device.copy(kind = BluetoothDeviceKind.BLE_ONLY), access)
        assertEquals(ServiceDiscoveryStatus.UNSUPPORTED, result.serviceDiscovery)
        assertEquals(ServicePresence.UNKNOWN, result.spp)
        assertEquals(0, access.requested)
        assertNull(access.callback)
        assertTrue(result.explanation.contains("Bluetooth Low Energy"))
        // SdpAccess has no connection, write, reset, feed or printer-query method.
        assertEquals(setOf("cached", "listen", "request"), SdpAccess::class.java.declaredMethods.map { it.name }.toSet())
    }

    @Test fun unavailableAndCachedRecordsRemainQualified() = runTest {
        val unavailable = Access().apply { accepted = false }
        assertEquals(ServiceDiscoveryStatus.UNAVAILABLE, inspectBluetoothServices(device, unavailable).serviceDiscovery)
        assertEquals(1, unavailable.removed)
        val cached = Access().apply { accepted = false; records = listOf(spp) }
        val result = inspectBluetoothServices(device, cached)
        assertEquals(ServiceDiscoveryStatus.CACHED, result.serviceDiscovery)
        assertTrue(result.explanation.contains("cache"))
    }

    @Test fun unrelatedAndMissingIdentityCallbacksCannotCompleteInspection() = runTest {
        val access = Access()
        val inspection = async { inspectBluetoothServices(device, access, 100) }
        runCurrent()
        access.callback!!("other", listOf(spp))
        access.callback!!(null, listOf(spp))
        assertFalse(inspection.isCompleted)
        assertEquals(ServiceDiscoveryStatus.TIMEOUT, inspection.await().serviceDiscovery)
        assertEquals(1, access.removed)
        assertEquals(1, access.requested)
    }

    @Test fun malformedAndOverLimitRepliesAreRejectedWithoutCapabilities() = runTest {
        for (records in listOf(null, List(MAX_INSPECTION_UUIDS + 1) { spp })) {
            val access = Access()
            val inspection = async { inspectBluetoothServices(device, access, 100) }
            runCurrent()
            access.callback!!("selected", records)
            val result = inspection.await()
            assertEquals(ServiceDiscoveryStatus.MALFORMED, result.serviceDiscovery)
            assertEquals(ServicePresence.UNKNOWN, result.spp)
            assertEquals(listOf("display.name"), result.suggestions.map { it.path })
            assertEquals(1, access.removed)
        }
    }

    @Test fun emptyOrUnrelatedServiceRecordsCannotProveSppAbsentOrPresent() = runTest {
        val access = Access()
        val inspection = async { inspectBluetoothServices(device, access, 100) }
        runCurrent()
        access.callback!!("selected", emptyList())
        assertEquals(ServicePresence.UNKNOWN, inspection.await().spp)
    }

    @Test fun cancellationUnregistersReceiverWithoutRetryAndLateCallbackIsIgnored() = runTest {
        val access = Access()
        val inspection = async { inspectBluetoothServices(device, access) }
        runCurrent()
        inspection.cancel()
        inspection.join()
        assertTrue(inspection.isCancelled)
        assertEquals(1, access.removed)
        access.callback!!("selected", listOf(spp))
        assertEquals(1, access.requested)
    }

    @Test fun discoveryErrorsHaveDedicatedStatesAndInventoryIsBoundedDeduplicatedAndSorted() {
        assertEquals(DeviceDiscoveryStatus.PERMISSION_REQUIRED, discoveryStatus(TransportFailure.BLUETOOTH_PERMISSION_REQUIRED))
        assertEquals(DeviceDiscoveryStatus.DISABLED, discoveryStatus(TransportFailure.BLUETOOTH_DISABLED))
        assertEquals(DeviceDiscoveryStatus.UNAVAILABLE, discoveryStatus(TransportFailure.BLUETOOTH_UNAVAILABLE))
        assertEquals(DeviceDiscoveryStatus.FAILED, discoveryStatus(TransportFailure.BLUETOOTH_SPP_CONNECTION_FAILED))
        val devices = listOf(device.copy(locator = "ble", kind = BluetoothDeviceKind.BLE_ONLY)) +
            (0..150).flatMap { listOf(device.copy(locator = it.toString()), device.copy(locator = it.toString())) }
        val inventory = boundedPairedInventory(devices)
        assertEquals(100, inventory.size)
        assertEquals(inventory.size, inventory.map { it.locator }.distinct().size)
        assertEquals(BluetoothDeviceKind.BLE_ONLY, inventory.last().kind)
    }
}
