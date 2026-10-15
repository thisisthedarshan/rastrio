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

import androidx.compose.ui.test.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.*
import com.circuitnext.rastrio.core.profile.*
import com.circuitnext.rastrio.printing.*
import kotlinx.coroutines.flow.MutableStateFlow

@OptIn(ExperimentalTestApi::class)
class ProfileCreationUiTest {
    private class DeletionPlatform(val storage: ProfileDraftStorage) : PrintingPlatform {
        override val profileDraftStorage = storage
        override val profile = MutableStateFlow<PrinterProfile?>(null)
        override val printers = MutableStateFlow(emptyList<PrinterInstance>())
        override val status = MutableStateFlow("")
        override val foreground = MutableStateFlow(true)
        override val supportsProfileDeletion = true
        val transport = FakePrinterTransport()
        override fun deleteProfile() { profile.value = null; printers.value = emptyList() }
        override fun refreshPairedPrinters() = Unit
        override fun importProfile() = Unit
        override fun selectPrinter(instance: PrinterInstance) = Unit
        override fun createTransport(): PrinterTransport = transport
    }

    @Test fun appDeletionPersistsAndFailedStorageKeepsTheDraft() = runComposeUiTest {
        val memory = MemoryProfileDraftStorage()
        memory.saveDrafts(listOf(ProfileDraft.fresh("one").withField("display.name", "Temporary")))
        var reject = true
        val platform = DeletionPlatform(object : ProfileDraftStorage {
            override fun loadDrafts() = memory.loadDrafts()
            override fun saveDrafts(drafts: List<ProfileDraft>) = !reject && memory.saveDrafts(drafts)
        })
        setContent { RastrIOApp(printingPlatform = platform) }
        onNodeWithText("Open Printers").performClick()
        onNodeWithText("More").performScrollTo().performClick()
        onNodeWithText("Delete draft Temporary").performClick()
        onNodeWithText("Delete", substring = false).performClick()
        onNodeWithText("Edit Temporary").assertExists()
        assertEquals(1, memory.loadDrafts().size)
        runOnIdle { reject = false }
        onNodeWithText("More").performScrollTo().performClick()
        onNodeWithText("Delete draft Temporary").performClick()
        onNodeWithText("Delete", substring = false).performClick()
        onNodeWithText("Edit Temporary").assertDoesNotExist()
        assertTrue(memory.loadDrafts().isEmpty())
        assertEquals(0, platform.transport.connections)
    }

    @Test fun activeProfileDeletionClearsSelectionWithoutPrinting() = runComposeUiTest {
        val platform = DeletionPlatform(MemoryProfileDraftStorage())
        platform.profile.value = SyntheticReferenceProfiles.narrow.copy(profileId = "operator.test")
        setContent { RastrIOApp(printingPlatform = platform) }
        onNodeWithText("Open Printers").performClick()
        onNodeWithText("Manage profiles").performScrollTo().performClick()
        onNodeWithText("Delete saved profile").performClick()
        assertNotNull(platform.profile.value)
        onNodeWithText("Delete", substring = false).performClick()
        assertNull(platform.profile.value)
        onNodeWithText("No validated physical printer profile selected.").assertExists()
        assertEquals(0, platform.transport.connections)
    }

    @Test fun deletingDraftRequiresConfirmationAndRetainsOthers() = runComposeUiTest {
        val storage = MemoryProfileDraftStorage()
        storage.saveDrafts(listOf(ProfileDraft.fresh("one").withField("display.name", "Temporary"),
            ProfileDraft.fresh("two").withField("display.name", "Keep")))
        setContent { PrinterManagementScreen(SyntheticReferenceProfiles.narrow, {}, {}, {}, {}, {},
            storage.loadDrafts(), {}, {}, {}, {}, onDeleteDraft = { draft ->
                storage.saveDrafts(storage.loadDrafts().filterNot { it.draftId == draft.draftId })
            }) }
        onNodeWithTag("printer-management-list").performScrollToNode(hasText("Edit Temporary"))
        onAllNodesWithText("More")[0].performClick()
        onNodeWithText("Delete draft Temporary").performClick()
        assertEquals(2, storage.loadDrafts().size)
        onNodeWithText("Cancel").performClick()
        onAllNodesWithText("More")[0].performClick()
        onNodeWithText("Delete draft Temporary").performClick()
        onNodeWithText("Delete", substring = false).performClick()
        assertEquals(listOf("two"), storage.loadDrafts().map { it.draftId })
    }

    @Test fun printerManagementCreatesAndResumesAnIncompleteDraftWithoutPrinting() = runComposeUiTest {
        setContent { RastrIOApp() }
        onNodeWithText("Open Printers").performClick()
        onNodeWithText("Create Printer Profile").performScrollTo().performClick()
        onNodeWithTag("profile-field-display.name").performTextReplacement("My printer")
        onNodeWithTag("profile-editor-list").performScrollToNode(hasText("Save local draft"))
        onNodeWithText("Save local draft").performClick()
        onNodeWithTag("profile-editor-list").performScrollToNode(hasText("Back to printers"))
        onNodeWithText("Back to printers").performClick()
        onNodeWithTag("printer-management-list").performScrollToNode(hasText("Edit My printer"))
        onNodeWithText("Edit My printer").performClick()
        onNodeWithTag("profile-field-display.name").assertTextEquals("Name", "My printer")
        onNodeWithTag("profile-editor-list").performScrollToNode(hasText("Validate profile"))
        onNodeWithText("Validate profile").performClick()
        onNodeWithTag("profile-editor-list").performScrollToNode(hasText("Save and select validated profile"))
        onNodeWithText("Save and select validated profile").assertIsNotEnabled()
    }
    @Test fun deviceSelectionBeforeProfileAndCalibrationNeverOpensATransport() = runComposeUiTest {
        val transport = FakePrinterTransport()
        val platform = object : PrintingPlatform {
            override val pairedDevices = MutableStateFlow(listOf(PairedPrinterDevice("private", "My Classic printer", BluetoothDeviceKind.CLASSIC),
                PairedPrinterDevice("private-ble", "BLE printer", BluetoothDeviceKind.BLE_ONLY)))
            override val profile = MutableStateFlow<PrinterProfile?>(null)
            override val printers = MutableStateFlow(emptyList<PrinterInstance>())
            override val status = MutableStateFlow("Choose a device before creating its profile")
            override val foreground = MutableStateFlow(true)
            override fun refreshPairedPrinters() = Unit
            override fun importProfile() = Unit
            override fun selectPrinter(instance: PrinterInstance) = Unit
            override fun createTransport(): PrinterTransport = transport
        }
        setContent { RastrIOApp(printingPlatform = platform) }
        onNodeWithText("Open Printers").performClick()
        onNodeWithText("Add Printer").performScrollTo().performClick()
        onNodeWithTag("device-row-private").performClick()
        onNodeWithText("BLE printer").assertDoesNotExist()
        onNodeWithText("Continue").performClick()
        onNodeWithText("Create Printer Profile").performScrollTo().performClick()
        onNodeWithTag("profile-editor-list").performScrollToNode(hasText("Calibrate draft in Printer Lab"))
        onNodeWithText("Calibrate draft in Printer Lab").performClick()
        onAllNodes(hasText("APP141", substring = true)).assertCountEquals(0)
        onNodeWithTag("printer-lab-list").performScrollToNode(hasText("Experimental device: My Classic printer"))
        onNodeWithText("Experimental device: My Classic printer").assertExists()
        onNodeWithText("Calibrate My Classic printer").assertDoesNotExist()
        onNodeWithTag("printer-lab-list").performScrollToNode(hasText("Prepare calibration preview"))
        onNodeWithTag("printer-lab-list").performScrollToNode(hasText("Transport trial settings"))
        onNodeWithText("Transport trial settings").performClick()
        onNodeWithTag("printer-lab-list").performScrollToNode(hasText("Maximum write chunk bytes (1–4096)"))
        onNodeWithText("Maximum write chunk bytes (1–4096)").performTextReplacement("4096")
        onNodeWithTag("calibration-effective-policy").assertTextContains("Effective maximum write: 1024 bytes", substring = true)
        onNodeWithTag("printer-lab-list").performScrollToNode(hasText("Prepare calibration preview"))
        onNodeWithText("Prepare calibration preview").performClick()
        onNodeWithTag("calibration-status").assertExists()
        onAllNodes(hasText("PRF151", substring = true)).assertCountEquals(0)
        onNodeWithTag("printer-lab-list").performScrollToNode(hasText("Print one experimental sample"))
        onNodeWithText("Print one experimental sample").assertIsNotEnabled()
        assertEquals(0, transport.connections)
        assertEquals(0, transport.byteCount)
    }

    @Test fun editorRestoresUnknownWorksheetAcrossRecreation() = runComposeUiTest {
        var registry by mutableStateOf(SaveableStateRegistry(null) { true })
        var visible by mutableStateOf(true)
        setContent { CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
            if (visible) RastrIOApp()
        } }
        onNodeWithText("Open Printers").performClick()
        onNodeWithText("Create Printer Profile").performScrollTo().performClick()
        onNodeWithTag("profile-field-display.name").performTextReplacement("Unsaved printer")
        val saved = runOnIdle { registry.performSave().also { visible = false } }
        waitForIdle()
        runOnIdle { registry = SaveableStateRegistry(saved) { true }; visible = true }
        waitForIdle()
        onNodeWithTag("profile-editor-list").performScrollToNode(hasTestTag("profile-field-display.name"))
        onNodeWithTag("profile-field-display.name").assertTextEquals("Name", "Unsaved printer")
        onNodeWithText("Back to printers").performScrollTo().performClick()
        onNodeWithText("Printers and profiles").assertExists()
    }

    @Test fun failedWorksheetPersistenceCannotActivateOrHideTheDraft() = runComposeUiTest {
        val draft = ProfileDraft.fromProfile(SyntheticReferenceProfiles.narrow.copy(profileId = "org.rastrio.fixture.persist-failure"))
            .let { original -> ProfileDraftFields.evidenceAreas.fold(original) { value, area ->
                value.recordVerification(area, "Automated persistence fixture", "Synthetic recorded result for software test only", "fixture-device")
            } }
        var activations = 0
        val platform = object : PrintingPlatform {
            override val profileDraftStorage = object : ProfileDraftStorage {
                override fun loadDrafts() = listOf(draft)
                override fun saveDrafts(drafts: List<ProfileDraft>) = false
            }
            override val profile = MutableStateFlow<PrinterProfile?>(null)
            override val printers = MutableStateFlow(emptyList<PrinterInstance>())
            override val status = MutableStateFlow("")
            override val foreground = MutableStateFlow(true)
            override fun refreshPairedPrinters() = Unit
            override fun importProfile() = Unit
            override fun selectPrinter(instance: PrinterInstance) = Unit
            override fun activateProfile(profile: PrinterProfile): Boolean { activations++; return true }
            override fun createTransport(): PrinterTransport = FakePrinterTransport()
        }
        setContent { RastrIOApp(printingPlatform = platform) }
        onNodeWithText("Open Printers").performClick()
        onNodeWithTag("printer-management-list").performScrollToNode(hasText("Edit ${draft["display.name"]}"))
        onNodeWithText("Edit ${draft["display.name"]}").performClick()
        onNodeWithTag("profile-editor-list").performScrollToNode(hasText("Validate profile"))
        onNodeWithText("Validate profile").performClick()
        onNodeWithTag("profile-editor-list").performScrollToNode(hasText("Save and select validated profile"))
        onNodeWithText("Save and select validated profile").performClick()
        onNodeWithTag("profile-editor-list").performScrollToNode(hasText("Draft could not be saved", substring = true))
        onNodeWithText("Draft could not be saved", substring = true).assertExists()
        assertEquals(0, activations)
    }

    @Test fun classicPrintingWaitsForConfirmedPrivateAssociation() = runComposeUiTest {
        val profile = SyntheticReferenceProfiles.narrow.copy(profileId = "org.rastrio.fixture.classic-assignment")
        val printer = PrinterInstance("classic-association", "Classic fixture", PrinterTransportKind.BLUETOOTH_CLASSIC,
            "private-classic", profile.profileId, profile.profileRevision)
        val saved = MutableStateFlow<PrinterInstance?>(null)
        val transport = FakePrinterTransport()
        val platform = object : PrintingPlatform {
            override val selectedPrinter = saved
            override val profile = MutableStateFlow<PrinterProfile?>(profile)
            override val printers = MutableStateFlow(listOf(printer))
            override val status = MutableStateFlow("Private association has not been saved")
            override val foreground = MutableStateFlow(true)
            override fun refreshPairedPrinters() = Unit
            override fun importProfile() = Unit
            override fun selectPrinter(instance: PrinterInstance) = Unit
            override fun createTransport(): PrinterTransport = transport
        }
        val input = com.circuitnext.rastrio.presentation.createAuthoringSession().also { it.edit("Receipt"); it.compile() }
        setContent { RastrIOApp(input, platform) }
        onNodeWithText("Open Markdown").performClick()
        onNodeWithText("Printer preview").performClick()
        onNodeWithTag("configured-printer-selector").performClick()
        onNodeWithText("Classic fixture").performClick()
        onNodeWithTag("confirm-print").assertIsNotEnabled()
        onNodeWithTag("physical-canvas").assertDoesNotExist()
        runOnIdle { saved.value = printer }
        waitUntil { onAllNodesWithTag("physical-canvas").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("confirm-print").assertIsEnabled()
        assertEquals(0, transport.connections)
        assertEquals(0, transport.byteCount)
    }

}
