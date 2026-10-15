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

import com.circuitnext.rastrio.printing.*
import kotlin.test.*

class DeviceCandidatesTest {
    private fun device(id: String, name: String, kind: BluetoothDeviceKind) = PairedPrinterDevice(id, name, kind)
    @Test fun candidatesSortClassicAndDualBeforeUnknownAndBleWithoutInferringServices() {
        val devices = listOf(device("b", "Alpha LE", BluetoothDeviceKind.BLE_ONLY), device("u", "Unknown", BluetoothDeviceKind.UNKNOWN),
            device("c", "Zebra", BluetoothDeviceKind.CLASSIC), device("d", "Alpha dual", BluetoothDeviceKind.DUAL))
        assertEquals(listOf("d", "c", "u", "b"), deviceCandidates(devices, "").map { it.locator })
        assertTrue(deviceKindLabel(BluetoothDeviceKind.CLASSIC).contains("not yet inspected"))
    }
    @Test fun searchIsCaseInsensitiveAndDuplicatesCannotRepeatRows() {
        val device = device("one", "BillQuick-Go", BluetoothDeviceKind.CLASSIC)
        assertEquals(listOf(device), deviceCandidates(listOf(device, device, device("two", "Headset", BluetoothDeviceKind.CLASSIC)), "  BILLquick  "))
    }
    @Test fun hostileInventoryIsBoundedAndEmptySearchWorks() {
        assertEquals(512, deviceCandidates((0 until 10000).map { device("d$it", "Printer $it", BluetoothDeviceKind.CLASSIC) }, "").size)
        assertTrue(deviceCandidates(emptyList(), "").isEmpty())
    }
    @Test fun metadataPrefillCannotInventCapabilitiesOrVerifiedEvidence() {
        for (kind in BluetoothDeviceKind.entries) {
            val result = metadataInspection(device("test", "Printer", kind))
            assertEquals(ServicePresence.UNKNOWN, result.spp)
            assertEquals(ServiceDiscoveryStatus.UNSUPPORTED, result.serviceDiscovery)
            assertEquals(listOf("display.name"), result.suggestions.map { it.path })
            assertEquals(InspectionEvidence.DEVICE_REPORTED, result.suggestions.single().source)
            assertEquals("test", result.suggestions.single().deviceLocator)
        }
    }
    @Test fun genericStarterIsOnlyAnExplicitUnverifiedWorksheetSuggestion() {
        val proposals = genericTwoInchSuggestions(device("unit", "Unrelated name", BluetoothDeviceKind.UNKNOWN))
        assertEquals(4, proposals.size)
        assertTrue(proposals.all { it.source == InspectionEvidence.INFERRED && it.deviceLocator == "unit" })
        assertTrue(proposals.all { it.path.startsWith("worksheet.") })
        assertEquals(listOf("58", "48", "CP437", "12×24"), proposals.map { it.value })
        var draft = ProfileDraft.fresh("user.generic").withSuggestions(proposals)
        proposals.forEach { draft = draft.acceptSuggestion(it) }
        assertEquals("", draft["display.manufacturer"])
        assertEquals("", draft["display.model"])
        assertEquals("", draft["protocol.initializationStrategy"])
        assertTrue(draft.verifications.isEmpty())
        assertEquals(proposals, draft.suggestions)
        assertFalse(proposals.any { it.path.startsWith("geometry.") || it.path.endsWith(".supported") || it.path.contains("selector") })
    }
}
