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

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import kotlin.test.*

class PrinterInstanceStoreTest {
    private fun instance() = PrinterInstance("private-id", "Printer", PrinterTransportKind.BLUETOOTH_CLASSIC,
        "private-locator", "documented.target", 2)

    private class Preferences {
        val values = mutableMapOf<String, Any?>()
        var commitResult = true
        private val editor = Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)) { proxy, method, args ->
            when (method.name) {
                "putInt", "putString" -> { values[args[0] as String] = args[1]; proxy }
                "clear" -> { values.clear(); proxy }
                "commit" -> commitResult
                else -> error("Unexpected editor call: ${method.name}")
            }
        } as SharedPreferences.Editor
        val preferences = Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "getAll" -> values.toMap()
                "edit" -> editor
                "getInt" -> (values[args[0] as String] ?: args[1]) as Int
                "getString" -> (values[args[0] as String] ?: args[1]) as String?
                else -> error("Unexpected preferences call: ${method.name}")
            }
        } as SharedPreferences
    }

    @Test fun selectionRoundTripsOnlyNecessaryPrivateMetadata() {
        val memory = Preferences()
        val store = PrinterInstanceStore(memory.preferences)
        assertNull(store.load())
        assertTrue(store.save(instance()))
        assertEquals(instance(), PrinterInstanceStore(memory.preferences).load())
        assertEquals(setOf("version", "id", "name", "locator", "profile", "revision"), memory.values.keys)
        assertFalse(instance().toString().contains("private-"))
    }

    @Test fun deletionPersistsAndFailedDeletionRestoresPriorSelection() {
        val memory = Preferences()
        val store = PrinterInstanceStore(memory.preferences)
        assertTrue(store.save(instance()))
        memory.commitResult = false
        assertFalse(store.clear())
        assertEquals(instance(), store.load())
        memory.commitResult = true
        assertTrue(store.clear())
        assertNull(PrinterInstanceStore(memory.preferences).load())
        assertTrue(memory.values.isEmpty())
    }

    @Test fun unknownVersionAndCorruptPersistedMetadataFailClosed() {
        val memory = Preferences()
        val store = PrinterInstanceStore(memory.preferences)
        store.save(instance())
        memory.values["version"] = 2
        assertNull(store.load())
        memory.values["version"] = "invalid type"
        assertNull(store.load())
        memory.values["version"] = 1
        memory.values["revision"] = 0
        assertNull(store.load())
        memory.values["revision"] = 2
        memory.values["locator"] = "untrusted\u001blocator"
        assertNull(store.load())
    }

    @Test fun failedPersistenceAndDevelopmentTransportAreNotAccepted() {
        val memory = Preferences()
        memory.commitResult = false
        val store = PrinterInstanceStore(memory.preferences)
        assertFalse(store.save(instance()))
        assertFailsWith<IllegalArgumentException> { store.save(instance().copy(transportKind = PrinterTransportKind.DEVELOPMENT)) }
    }
    @Test fun profileDraftsRoundTripPrivatelyAndRejectMalformedStoredData() {
        val memory = Preferences()
        val storage = AndroidProfileDraftStorage(memory.preferences)
        val draft = ProfileDraft.fresh("local-draft").withField("display.name", "Unknown printer")
        assertTrue(storage.saveDrafts(listOf(draft)))
        assertEquals(listOf(draft), AndroidProfileDraftStorage(memory.preferences).loadDrafts())
        assertFalse(draft.encodeLocal().contains("private-locator"))
        memory.values["draft0"] = "x".repeat(65537)
        assertTrue(storage.loadDrafts().isEmpty())
        memory.values["draft0"] = draft.encodeLocal()
        memory.values["count"] = 33
        assertTrue(storage.loadDrafts().isEmpty())
        memory.values["count"] = "invalid type"
        assertTrue(storage.loadDrafts().isEmpty())
    }

    @Test fun failedDraftDeletionRetainsPreviouslyPersistedDrafts() {
        val memory = Preferences()
        val storage = AndroidProfileDraftStorage(memory.preferences)
        val draft = ProfileDraft.fresh("keep")
        assertTrue(storage.saveDrafts(listOf(draft)))
        memory.commitResult = false
        assertFalse(storage.saveDrafts(emptyList()))
        assertEquals(listOf(draft), AndroidProfileDraftStorage(memory.preferences).loadDrafts())
    }

    @Test fun profileDraftStorageLimitsAndCommitFailureDoNotActivateAnything() {
        val memory = Preferences()
        val storage = AndroidProfileDraftStorage(memory.preferences)
        val draft = ProfileDraft.fresh("local-draft")
        assertFalse(storage.saveDrafts(List(2) { draft }))
        assertFalse(storage.saveDrafts(List(33) { ProfileDraft.fresh("draft$it") }))
        assertFalse(storage.saveDrafts(listOf(draft.withField("display.name", "x".repeat(1025)))))
        assertTrue(memory.values.isEmpty())
        memory.commitResult = false
        assertFalse(storage.saveDrafts(listOf(draft)))
    }

    @Test fun failedCommitCannotBecomeAConfirmedSelectionThroughLaterLoads() {
        val memory = Preferences()
        val store = PrinterInstanceStore(memory.preferences)
        val prior = instance()
        assertTrue(store.save(prior))
        memory.commitResult = false
        assertFalse(store.save(prior.copy(displayName = "New selection", profileRevision = 3)))
        assertEquals(prior, store.load())
    }

    @Test fun configuredPrintersKeepExactProfilesAndDefaultsAcrossRestart() {
        val memory = Preferences()
        val store = PrinterInstanceStore(memory.preferences)
        val profile = (PrinterProfileFiles.decode(java.io.File("../test-fixtures/hardware/HELETT-OCT2026-candidate.tcfg").readBytes())
            as com.circuitnext.rastrio.core.profile.ProfileValidationResult.Success).value.profile
        val first = ConfiguredPrinter(instance().copy(profileId = profile.profileId, profileRevision = profile.profileRevision), profile)
        val second = ConfiguredPrinter(first.instance.copy(id = "second", transportLocator = "second-locator",
            defaults = PrinterPrintDefaults(finalFeedDots = 25)), profile.copy(geometry = profile.geometry.copy(printableWidthDots = 384)))
        assertTrue(store.saveConfiguration(listOf(first, second), second.instance.id))
        val restarted = PrinterInstanceStore(memory.preferences).loadConfiguration()
        assertEquals(listOf(first, second), restarted!!.printers)
        assertEquals(second.instance.id, restarted.selectedId)
        memory.commitResult = false
        assertFalse(store.saveConfiguration(listOf(first), first.instance.id))
        assertEquals(restarted, store.loadConfiguration())
        memory.commitResult = true
        assertTrue(store.saveConfiguration(listOf(first), null))
        assertEquals(listOf(first), store.loadConfiguration()!!.printers)
    }

    @Test fun migrationRequiresExactLegacyProfileAndPreservesFailedOrCorruptState() {
        val memory = Preferences()
        val store = PrinterInstanceStore(memory.preferences)
        val profile = (PrinterProfileFiles.decode(java.io.File("../test-fixtures/hardware/HELETT-OCT2026-candidate.tcfg").readBytes())
            as com.circuitnext.rastrio.core.profile.ProfileValidationResult.Success).value.profile
        val legacy = instance().copy(profileId = profile.profileId, profileRevision = profile.profileRevision)
        assertTrue(store.save(legacy))
        assertNull(store.migrate(profile.copy(profileRevision = profile.profileRevision + 1)))
        assertEquals(legacy, store.load())
        memory.commitResult = false
        assertNull(store.migrate(profile))
        assertEquals(legacy, store.load())
        memory.commitResult = true
        assertEquals(legacy, store.migrate(profile)!!.printers.single().instance)
        val confirmed = memory.values.toMap()
        assertFalse(store.saveConfiguration(List(33) { ConfiguredPrinter(legacy.copy(id = "p$it"), profile) }, null))
        assertEquals(confirmed, memory.values)
        memory.values["count"] = 33
        assertNull(store.loadConfiguration())
    }

    private fun physicalProfile() = (PrinterProfileFiles.decode(java.io.File("../test-fixtures/hardware/HELETT-OCT2026-candidate.tcfg").readBytes())
        as com.circuitnext.rastrio.core.profile.ProfileValidationResult.Success).value.profile

    @Test fun overridesRoundTripIncludingExplicitClearedBufferAndPersistedValidation() {
        val memory = Preferences()
        val store = PrinterInstanceStore(memory.preferences)
        val profile = physicalProfile()
        val overrides = com.circuitnext.rastrio.core.profile.PrinterProfileOverride(
            geometry = profile.geometry.copy(printableWidthDots = 384),
            printerBuffer = com.circuitnext.rastrio.core.profile.PrinterBufferOverride.Replace(null))
        val printer = ConfiguredPrinter(instance().copy(profileId = profile.profileId, profileRevision = profile.profileRevision,
            overrides = overrides, defaults = PrinterPrintDefaults(finalFeedDots = 10)), profile)
        assertTrue(store.saveConfiguration(listOf(printer), printer.instance.id))
        assertEquals(printer, PrinterInstanceStore(memory.preferences).loadConfiguration()!!.printers.single())
        memory.values["p0.feed"] = PrinterPrintDefaults.MAX_FINAL_FEED_DOTS + 1
        assertNull(store.loadConfiguration())
        val corrupt = memory.values.toMap()
        assertFalse(store.saveConfiguration(emptyList(), null))
        assertEquals(corrupt, memory.values)
    }

    @Test fun duplicateIdentityInvalidSelectionUnsupportedCutAndRevisionMismatchCannotReplaceState() {
        val memory = Preferences()
        val store = PrinterInstanceStore(memory.preferences)
        val profile = physicalProfile()
        val printer = ConfiguredPrinter(instance().copy(profileId = profile.profileId, profileRevision = profile.profileRevision), profile)
        assertTrue(store.saveConfiguration(listOf(printer), printer.instance.id))
        val confirmed = memory.values.toMap()
        assertFalse(store.saveConfiguration(listOf(printer, printer), null))
        assertFalse(store.saveConfiguration(listOf(printer), "missing"))
        assertFalse(store.saveConfiguration(listOf(printer.copy(instance = printer.instance.copy(profileRevision = profile.profileRevision + 1))), null))
        assertFalse(store.saveConfiguration(listOf(printer.copy(instance = printer.instance.copy(
            defaults = PrinterPrintDefaults(cutIntent = com.circuitnext.rastrio.core.printer.CutIntent.AUTOMATIC)))), null))
        assertEquals(confirmed, memory.values)
    }

    @Test fun deletionAndEmptyRestartDoNotResurrectLegacySelection() {
        val memory = Preferences()
        val store = PrinterInstanceStore(memory.preferences)
        val profile = physicalProfile()
        val legacy = instance().copy(profileId = profile.profileId, profileRevision = profile.profileRevision)
        assertTrue(store.save(legacy))
        assertNotNull(store.migrate(profile))
        assertTrue(store.saveConfiguration(emptyList(), null))
        assertEquals(emptyList(), PrinterInstanceStore(memory.preferences).loadConfiguration()!!.printers)
        assertEquals(emptyList(), store.migrate(profile)!!.printers)
        assertNull(store.load())
    }

    @Test fun profileDeletionRemovesMatchingBindingsAtomicallyAndFailurePreservesEverything() {
        val memory = Preferences()
        val store = PrinterInstanceStore(memory.preferences)
        val profile = physicalProfile()
        val first = ConfiguredPrinter(instance().copy(profileId = profile.profileId, profileRevision = profile.profileRevision), profile)
        val otherProfile = profile.copy(profileId = "other.physical")
        val other = ConfiguredPrinter(first.instance.copy(id = "other", transportLocator = "other",
            profileId = otherProfile.profileId), otherProfile)
        assertTrue(store.saveConfiguration(listOf(first, other), first.instance.id))
        assertTrue(store.saveImportedProfile(profile))
        val before = store.loadConfiguration()
        memory.commitResult = false
        assertNull(store.deleteImportedProfile())
        assertEquals(before, store.loadConfiguration())
        memory.commitResult = true
        val after = store.deleteImportedProfile()!!
        assertEquals(listOf(other), after.printers)
        assertNull(after.selectedId)
        assertNull(after.importedProfile)
        assertEquals(after, PrinterInstanceStore(memory.preferences).loadConfiguration())
        assertEquals(after, store.migrate(profile))
    }

    @Test fun importingNewRevisionDoesNotRetargetExistingPrinterAndCorruptVersionCannotMigrate() {
        val memory = Preferences()
        val store = PrinterInstanceStore(memory.preferences)
        val profile = physicalProfile()
        val printer = ConfiguredPrinter(instance().copy(profileId = profile.profileId, profileRevision = profile.profileRevision), profile)
        assertTrue(store.saveConfiguration(listOf(printer), printer.instance.id))
        val imported = profile.copy(profileRevision = profile.profileRevision + 1)
        assertTrue(store.saveImportedProfile(imported))
        assertEquals(listOf(printer), store.loadConfiguration()!!.printers)
        assertEquals(imported, store.loadConfiguration()!!.importedProfile)
        assertTrue(store.saveImportedProfile(imported))
        assertEquals(1, store.loadConfiguration()!!.printers.size)
        memory.values["count"] = 33
        val corrupt = memory.values.toMap()
        assertNull(store.migrate(profile))
        assertEquals(corrupt, memory.values)
    }

}
