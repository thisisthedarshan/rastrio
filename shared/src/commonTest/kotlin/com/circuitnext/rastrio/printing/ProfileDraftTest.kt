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

import com.circuitnext.rastrio.core.profile.*
import kotlin.test.*

class ProfileDraftTest {
    private val fixture = SyntheticReferenceProfiles.wide80mm.copy(profileId = "org.rastrio.fixture.editor")
    private fun verified(draft: ProfileDraft): ProfileDraft = ProfileDraftFields.evidenceAreas.fold(
        draft.copy(evidenceNotes = "Automated fixture only; no physical printer claim")) { value, area ->
        value.recordVerification(area, "Automated regression fixture", "Expected fixture bytes; no real printer claim", "test-device")
    }

    @Test fun incompleteUnknownGeometrySurvivesSaveAndLoadWithoutActivation() {
        val draft = ProfileDraft.fresh("user.unknown").withField("display.name", "Helett H50i")
            .withField("display.manufacturer", "Helett").withField("geometry.horizontalDpi", "unknown")
        val storage = MemoryProfileDraftStorage()
        assertTrue(storage.saveDrafts(listOf(draft)))
        assertEquals(draft, storage.loadDrafts().single())
        assertEquals("", draft["geometry.printableWidthDots"])
        assertEquals("", draft["geometry.verticalDpi"])
        assertEquals("", draft["nativeQr.supported"])
        val invalid = assertIs<ProfileValidationResult.Failure>(draft.validate())
        assertTrue(invalid.diagnostics.any { it.path == "$.geometry.horizontalDpi" })
    }

    @Test fun everyV1CapabilitySurvivesDraftRoundTripAndDuplication() {
        val draft = verified(ProfileDraft.fromProfile(fixture, "user.duplicate"))
        val restored = assertNotNull(ProfileDraft.decodeLocal(draft.encodeLocal()))
        assertEquals(draft, restored)
        val profile = assertIs<ProfileValidationResult.Success<ValidatedPrinterProfile>>(restored.validate()).value.profile
        assertEquals(fixture.copy(profileId = "user.duplicate"), profile)
        assertEquals(2, profile.nativeText.fonts.size)
        val exported = assertIs<ProfileValidationResult.Success<ByteArray>>(PrinterProfileFiles.encode(profile)).value
        assertEquals(profile, assertIs<ProfileValidationResult.Success<ValidatedPrinterProfile>>(PrinterProfileFiles.decode(exported)).value.profile)
        assertFalse(exported.decodeToString().contains("evidence"))
        assertFalse(exported.decodeToString().contains("draftId"))
        assertFalse(exported.decodeToString().contains("transportLocator"))
    }

    @Test fun inferredAndObservedValuesCannotActivateButLabCanValidateCandidate() {
        val draft = ProfileDraft.fromProfile(fixture).copy(evidenceNotes = "Self-test observations")
        assertIs<ProfileValidationResult.Success<ValidatedPrinterProfile>>(draft.validateCandidate())
        for (evidence in listOf(ProfileEvidence.UNKNOWN, ProfileEvidence.USER_ENTERED, ProfileEvidence.INFERRED, ProfileEvidence.HARDWARE_OBSERVED)) {
            val result = assertIs<ProfileValidationResult.Failure>(draft.copy(
                evidence = ProfileDraftFields.evidenceAreas.associateWith { evidence }).validate())
            assertTrue(result.diagnostics.any { it.path == "$.geometry" })
            assertTrue(result.diagnostics.any { it.path == "$.nativeQr" })
        }
    }

    @Test fun changingAnOutputFieldInvalidatesItsVerifiedEvidence() {
        val draft = verified(ProfileDraft.fromProfile(fixture))
        val changed = draft.withField("geometry.horizontalDpi", "204")
        assertEquals(ProfileEvidence.UNKNOWN, changed.evidence["geometry"])
        assertEquals(ProfileEvidence.VERIFIED, changed.evidence["nativeQr"])
        assertIs<ProfileValidationResult.Failure>(changed.validate())
        assertEquals(ProfileEvidence.VERIFIED, draft.withField("display.name", "New name").evidence["geometry"])
        assertEquals(ProfileEvidence.VERIFIED, draft.withField("geometry.horizontalDpi", "203").evidence["geometry"])
    }

    @Test fun nativeQrAndOtherHostCommandsRequireSpecificVerifiedEvidence() {
        val draft = verified(ProfileDraft.fromProfile(fixture)).withEvidence("nativeQr", ProfileEvidence.HARDWARE_OBSERVED)
        val result = assertIs<ProfileValidationResult.Failure>(draft.validate())
        assertTrue(result.diagnostics.any { it.path == "$.nativeQr" })
        val invalidSelector = verified(draft.withField("nativeText.codePages[0].selector.parameter", "256"))
        assertIs<ProfileValidationResult.Failure>(invalidSelector.validate())
        val unknownStrategy = verified(draft.withField("raster.preferredStrategy", "exec.attacker"))
        assertIs<ProfileValidationResult.Failure>(unknownStrategy.validate())
    }

    @Test fun syntheticIdentityCannotBecomeHardwareByDuplicatingIt() {
        val draft = verified(ProfileDraft.fromProfile(SyntheticReferenceProfiles.narrow, "user.disguised"))
        assertIs<ProfileValidationResult.Failure>(draft.validate())
        assertIs<ProfileValidationResult.Failure>(assertNotNull(ProfileDraft.decodeLocal(draft.encodeLocal())).validate())
    }

    @Test fun strictNumbersAndResourceLimitsProduceFieldDiagnostics() {
        val draft = verified(ProfileDraft.fromProfile(fixture))
        for (value in listOf("NaN", "2147483648", "1.5", "", "-1", "601")) {
            val result = assertIs<ProfileValidationResult.Failure>(verified(draft.withField("geometry.horizontalDpi", value)).validate())
            assertTrue(result.diagnostics.any { it.path == "$.geometry.horizontalDpi" })
        }
        assertIs<ProfileValidationResult.Failure>(draft.withField("nativeText.fontCount", "99999999").validateCandidate())
        assertIs<ProfileValidationResult.Failure>(draft.withField("nativeText.supported", "arbitrary").validateCandidate())
    }

    @Test fun malformedLocalRecordsAreRejectedBeforeRestoringDrafts() {
        val encoded = ProfileDraft.fresh("user.local").encodeLocal()
        for (record in listOf(encoded + "extra", encoded.dropLast(1), encoded.replace("local-draft-2", "local-draft-3"),
            "RastrIO-local-draft-1:99999:x", "RastrIO-local-draft-1:-1:x", "x".repeat(65537))) {
            assertNull(ProfileDraft.decodeLocal(record))
        }
        val unknown = ProfileDraft.fresh("user.local").withField("commandTemplate", "ESC reset")
        assertFailsWith<IllegalArgumentException> { unknown.encodeLocal() }
        assertIs<ProfileValidationResult.Failure>(unknown.validate())
        assertFalse(MemoryProfileDraftStorage().saveDrafts(listOf(unknown)))
    }

    @Test fun draftStorageEnforcesCountAndIdentityAndPreservesPriorStateOnFailure() {
        val storage = MemoryProfileDraftStorage()
        val draft = ProfileDraft.fresh("user.saved")
        assertTrue(storage.saveDrafts(listOf(draft)))
        assertFalse(storage.saveDrafts(listOf(draft, draft)))
        assertFalse(storage.saveDrafts((0..32).map { ProfileDraft.fresh("user.$it") }))
        assertFalse(storage.saveDrafts(listOf(draft.copy(evidenceNotes = "x".repeat(4097)))))
        assertEquals(listOf(draft), storage.loadDrafts())
    }

    @Test fun verifiedValuesRequireARecordedProcedureAndResult() {
        val draft = verified(ProfileDraft.fromProfile(fixture)).copy(verifications = emptyList())
        assertTrue(assertIs<ProfileValidationResult.Failure>(draft.validate()).diagnostics.any { it.path == "$.geometry" })
    }
    @Test fun framedStorageLimitAndDanglingSelectorCannotLoseInput() {
        val fields = ProfileDraftFields.all.take(48).associate { it.path to "x".repeat(1024) }
        val long = ProfileDraft("user.boundary", fields, evidenceNotes = "x".repeat(4096))
        val store = MemoryProfileDraftStorage()
        if (runCatching { long.encodeLocal() }.isSuccess) {
            assertTrue(store.saveDrafts(listOf(long)))
            assertEquals(long, store.loadDrafts().single())
        } else assertFalse(store.saveDrafts(listOf(long)))
        val dangling = ProfileDraft.fromProfile(fixture).withField("nativeText.codePages[0].selector.strategy", "")
        val result = assertIs<ProfileValidationResult.Failure>(dangling.validateCandidate())
        assertTrue(result.diagnostics.any { it.path == "$.nativeText.codePages[0].selector.parameter" })
    }

    @Test fun labelAloneCannotVerifyEvenWithFreeformNotes() {
        val draft = ProfileDraft.fromProfile(fixture).copy(
            evidence = ProfileDraftFields.evidenceAreas.associateWith { ProfileEvidence.VERIFIED },
            evidenceNotes = "Someone chose verified")
        assertIs<ProfileValidationResult.Failure>(draft.validate())
        assertIs<ProfileValidationResult.Success<ValidatedPrinterProfile>>(draft.validateCandidate())
    }

    @Test fun emptyNumbersHaveOneWorksheetErrorPerPathWithoutCoreZeroCascades() {
        val draft = ProfileDraft.fresh("user.empty")
        assertTrue(MemoryProfileDraftStorage().saveDrafts(listOf(draft)))
        val errors = assertIs<ProfileValidationResult.Failure>(draft.validateCandidate()).diagnostics
        assertEquals(1, errors.count { it.path == "$.geometry.horizontalDpi" })
        assertTrue(errors.none { it.code == "PRF103" })
        assertEquals("", draft["geometry.horizontalDpi"])
    }

    @Test fun suggestionsPreserveProvenanceConflictsAndUserChoice() {
        val suggestion = ProfileSuggestion("display.name", "Reported name", InspectionEvidence.DEVICE_REPORTED, "Paired metadata", "one-device")
        val draft = ProfileDraft.fresh("user.suggestion").withField("display.name", "My name").withSuggestions(listOf(suggestion))
        assertEquals("My name", draft["display.name"])
        assertEquals(draft, ProfileDraft.decodeLocal(draft.encodeLocal()))
        val accepted = draft.acceptSuggestion(suggestion)
        assertEquals("Reported name", accepted["display.name"])
        assertEquals(suggestion, accepted.suggestions.single())
        assertTrue(accepted.verifications.isEmpty())
        assertFailsWith<IllegalArgumentException> { draft.withSuggestions(List(33) { suggestion }) }
    }

    @Test fun verifiedRecordsAreBoundToFieldsAndPrinterIdentity() {
        val draft = verified(ProfileDraft.fromProfile(fixture))
        assertIs<ProfileValidationResult.Success<ValidatedPrinterProfile>>(draft.validate("test-device"))
        assertIs<ProfileValidationResult.Failure>(draft.validate("other-device"))
        assertTrue(draft.withField("quirks", "changed").verifications.isEmpty())
        assertIs<ProfileValidationResult.Failure>(draft.copy(fields = draft.fields + ("quirks" to "changed")).validate())
        assertFalse(draft.withField("geometry.printableWidthDots", "400").verifications.any { it.area == "geometry" })
        assertIs<ProfileValidationResult.Failure>(draft.copy(fields = draft.fields + ("geometry.printableWidthDots" to "400")).validate())
        assertFailsWith<IllegalArgumentException> { draft.recordVerification("geometry", "", "Observed", "test-device") }
    }

    @Test fun versionOneLocalDraftMigratesWithoutInventingVerification() {
        val parts = listOf("user.old", "Legacy notes", "false", "1", "display.name", "Old name", "1", "geometry", "VERIFIED")
        val legacy = "RastrIO-local-draft-1:" + parts.joinToString("") { "${it.length}:$it" }
        val draft = assertNotNull(ProfileDraft.decodeLocal(legacy))
        assertEquals("Old name", draft["display.name"])
        assertTrue(draft.verifications.isEmpty())
        assertIs<ProfileValidationResult.Failure>(draft.validate())
        assertEquals(draft, ProfileDraft.decodeLocal(draft.encodeLocal()))
    }

}
