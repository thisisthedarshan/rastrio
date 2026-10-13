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

import com.circuitnext.rastrio.core.profile.PrinterProfile
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow

/** OS access is explicit; ordinary selection and job UI remain shared. */
interface PrintingPlatform {
    val pairedDevices: StateFlow<List<PairedPrinterDevice>> get() = EmptyPrintingState.devices
    val selectedPrinter: StateFlow<PrinterInstance?> get() = EmptyPrintingState.selected
    val profileDraftStorage: ProfileDraftStorage get() = EmptyPrintingState.drafts
    fun activateProfile(profile: PrinterProfile): Boolean = false
    fun savePrinter(device: PairedPrinterDevice, profile: PrinterProfile) = Unit
    val printers: StateFlow<List<PrinterInstance>>
    val profile: StateFlow<PrinterProfile?>
    val status: StateFlow<String>
    val foreground: StateFlow<Boolean>
    fun refreshPairedPrinters()
    fun importProfile()
    val supportsProfileExport: Boolean get() = false
    fun exportProfile() = Unit
    fun selectPrinter(instance: PrinterInstance)
    fun createTransport(): PrinterTransport
}

private object EmptyPrintingState {
    val devices: StateFlow<List<PairedPrinterDevice>> = MutableStateFlow(emptyList())
    val selected: StateFlow<PrinterInstance?> = MutableStateFlow(null)
    val drafts: ProfileDraftStorage = MemoryProfileDraftStorage()
}
