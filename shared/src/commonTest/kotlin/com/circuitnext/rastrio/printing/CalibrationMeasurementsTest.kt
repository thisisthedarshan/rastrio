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

import com.circuitnext.rastrio.core.printer.*
import com.circuitnext.rastrio.core.profile.*
import kotlin.test.*

class CalibrationMeasurementsTest {
    private val draft = ProfileDraft.fromProfile(SyntheticReferenceProfiles.narrow.copy(profileId = "user.measurement"))
    private fun plan() = assertIs<PreparationResult.Success>(assertIs<ProfileValidationResult.Success<CalibrationFixture>>(
        CalibrationFixtures.create(draft, CalibrationRequest(CalibrationProcedure.GEOMETRY, 384))).value.prepare()).print

    @Test fun independentAxesApplyOperatorEvidenceWithoutChangingHeadWidth() {
        val plan = plan()
        val sample = CalibrationMeasurements.capture(draft, plan, "device-a", 384, 64)!!
        val result = sample.measure(48, 6.0, 48, 6.096)!!
        assertEquals(203.2, result.horizontalDpi, 0.001)
        assertEquals(200.0, result.verticalDpi, 0.001)
        val applied = sample.apply(draft, plan, "device-a", result)!!
        assertEquals("203", applied["geometry.horizontalDpi"])
        assertEquals("200", applied["geometry.verticalDpi"])
        assertEquals("384", applied["geometry.printableWidthDots"])
        assertEquals(ProfileEvidence.HARDWARE_OBSERVED, applied.evidence["geometry"])
        assertTrue(applied.suggestions.all { it.source == InspectionEvidence.OPERATOR_REPORTED && it.deviceLocator == "device-a" })
        assertIs<ProfileValidationResult.Failure>(applied.validate())
    }

    @Test fun invalidSpansCountsAndOutOfRangeDpiRejectBeforeApply() {
        val sample = CalibrationMeasurements.capture(draft, plan(), "device-a", 384, 64)!!
        for (mm in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, 1025.0, 0.001))
            assertNull(sample.measure(48, mm, 48, 6.0))
        for (dots in listOf(0, -8, 7, 384)) assertNull(sample.measure(dots, 6.0, 48, 6.0))
        assertNull(sample.measure(48, 6.0, 64, 6.0))
    }

    @Test fun stalePlanDraftAndDeviceCannotApplyMeasurements() {
        val plan = plan()
        val sample = CalibrationMeasurements.capture(draft, plan, "device-a", 384, 64)!!
        val measured = sample.measure(48, 6.0, 48, 6.0)!!
        assertNull(sample.apply(draft.withField("geometry.printableWidthDots", "383"), plan, "device-a", measured))
        assertNull(sample.apply(draft, plan(), "device-a", measured))
        assertNull(sample.apply(draft, plan, "device-b", measured))
        assertNull(CalibrationMeasurements.capture(draft.withField("profileId", "user.other"), plan, "device-a", 384, 64))
        assertNull(CalibrationMeasurements.capture(draft, plan, "", 384, 64))
    }

    @Test fun evidenceStorageBoundsPreservePriorWorksheet() {
        val full = draft.copy(evidenceNotes = "n".repeat(4096))
        val plan = plan()
        val sample = CalibrationMeasurements.capture(full, plan, "device-a", 384, 64)!!
        assertNull(sample.apply(full, plan, "device-a", sample.measure(48, 6.0, 48, 6.0)!!))
        assertEquals(4096, full.evidenceNotes.length)
    }

    @Test fun experimentalStarterRequiresCommandAcknowledgementAndRemainsIsolated() {
        val unknown = ProfileDraft.fresh("user.unknown")
        assertNull(CalibrationFixtures.experimentalStarter(unknown, false))
        val trial = CalibrationFixtures.experimentalStarter(unknown, true)!!
        assertEquals("", unknown["geometry.horizontalDpi"])
        assertEquals("203", trial["geometry.horizontalDpi"])
        assertEquals("false", trial["nativeText.supported"])
        val fixture = assertIs<ProfileValidationResult.Success<CalibrationFixture>>(CalibrationFixtures.create(trial, CalibrationRequest(CalibrationProcedure.GEOMETRY, 64))).value
        val plan = assertIs<PreparationResult.Success>(fixture.prepare()).print
        assertTrue(plan.operations.all { it is RasterOperation || it is Feed })
        assertTrue(plan.operations.any { it is Feed }, "Logical image spacing must remain acceptable outside the known-dot raster")
        val sample = CalibrationMeasurements.capture(unknown, plan, "device-a", 64, 64)!!
        val measured = sample.measure(48, 6.0, 48, 6.0)!!
        val applied = sample.apply(unknown, plan, "device-a", measured)!!
        assertEquals("", applied["protocol.dialectId"])
        assertEquals("", applied["raster.supported"])
        assertEquals("", applied["geometry.printableWidthDots"])
        assertIs<ProfileValidationResult.Failure>(trial.validate())
    }
}
