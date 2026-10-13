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
import com.circuitnext.rastrio.core.printer.*
import com.circuitnext.rastrio.core.escpos.*
import kotlin.test.*

class CalibrationFixturesTest {
    private val candidate = ProfileDraft.fromProfile(SyntheticReferenceProfiles.narrow.copy(profileId = "org.rastrio.fixture.calibration"))
    @Test fun unknownGeometryNeverProducesAnExperimentalPlan() {
        val draft = ProfileDraft.fresh("user.unknown")
        assertIs<ProfileValidationResult.Failure>(CalibrationFixtures.create(draft, CalibrationRequest(CalibrationProcedure.GEOMETRY, 384)))
    }
    @Test fun proceduresRejectOverLimitAndInvalidInputBeforeAllocatingOrPreparing() {
        for (request in listOf(CalibrationRequest(CalibrationProcedure.RASTER, 1025),
            CalibrationRequest(CalibrationProcedure.RASTER, 8, 65),
            CalibrationRequest(CalibrationProcedure.BUFFER_CHUNK, 8, bufferLines = 17),
            CalibrationRequest(CalibrationProcedure.RASTER_QR, 8, qrPayload = "x".repeat(129)),
            CalibrationRequest(CalibrationProcedure.RASTER_QR, 8, qrPayload = "\u001b"))) {
            assertIs<ProfileValidationResult.Failure>(CalibrationFixtures.create(candidate, request))
        }
    }
    @Test fun rasterPatternUsesProductionPreparedArtifactsAndBoundedEncoderWithoutPromotion() {
        val fixture = assertIs<ProfileValidationResult.Success<CalibrationFixture>>(CalibrationFixtures.create(candidate,
            CalibrationRequest(CalibrationProcedure.RASTER, 383))).value
        val prepared = assertIs<PreparationResult.Success>(fixture.prepare()).print
        assertTrue(prepared.operations.any { it is PreparedRasterGraphic })
        assertTrue(assertIs<EncodingResult.Success>(EscPosEncoder(fixture.encodingPolicy).validate(prepared)).bytesEmitted <= 65536)
        assertIs<ProfileValidationResult.Failure>(candidate.validate())
        assertEquals(CutIntent.NONE, fixture.options.cutIntent)
    }
    @Test fun nativeQrStaysDisabledUntilAnExplicitCandidateAndNeverPromotesEvidence() {
        assertIs<ProfileValidationResult.Failure>(CalibrationFixtures.create(candidate,
            CalibrationRequest(CalibrationProcedure.NATIVE_QR, 200)))
        val qrCandidate = ProfileDraft.fromProfile(SyntheticReferenceProfiles.wide80mm.copy(profileId = "org.rastrio.fixture.native-qr-calibration"))
        val fixture = assertIs<ProfileValidationResult.Success<CalibrationFixture>>(CalibrationFixtures.create(qrCandidate,
            CalibrationRequest(CalibrationProcedure.NATIVE_QR, 150))).value
        assertTrue(assertIs<PreparationResult.Success>(fixture.prepare()).print.operations.any { it is NativeQr })
        assertIs<ProfileValidationResult.Failure>(qrCandidate.validate())
    }
    @Test fun nativeCalibrationRequiresExplicitFontAndPageAndCannotUseRasterFallback() {
        assertIs<ProfileValidationResult.Failure>(CalibrationFixtures.create(candidate,
            CalibrationRequest(CalibrationProcedure.CP437, 384)))
        val fixture = assertIs<ProfileValidationResult.Success<CalibrationFixture>>(CalibrationFixtures.create(candidate,
            CalibrationRequest(CalibrationProcedure.CP437, 384, nativeFontId = "font-a", codePageId = "cp437"))).value
        val prepared = assertIs<PreparationResult.Success>(fixture.prepare()).print
        assertTrue(prepared.operations.any { it is NativeText })
        assertFalse(prepared.operations.any { it is PreparedRasterGraphic })
        assertIs<PreparationResult.Failure>(fixture.prepare { true })
    }
    @Test fun geometryIntervalsRemainEightPrinterDotsAcrossUnequalCandidateAxes() {
        for ((horizontal, vertical) in listOf(203 to 200, 203 to 250, 250 to 203)) {
            val draft = ProfileDraft.fromProfile(SyntheticReferenceProfiles.narrow.copy(profileId = "org.rastrio.fixture.axis-grid",
                geometry = PrinterGeometry(horizontal, vertical, 384)))
            val fixture = assertIs<ProfileValidationResult.Success<CalibrationFixture>>(CalibrationFixtures.create(draft,
                CalibrationRequest(CalibrationProcedure.GEOMETRY, 383))).value
            val result = fixture.prepare()
            val print = assertIs<PreparationResult.Success>(result,
                "$horizontal/$vertical: ${(result as? PreparationResult.Failure)?.diagnostics}").print
            val bands = print.operations.filterIsInstance<RasterOperation>()
            bands.forEach { assertEquals(383, it.graphic.widthDots, "$horizontal/$vertical") }
            assertEquals(64, bands.sumOf { it.graphic.heightDots })
            val origin = bands.minOf { it.bounds.y }
            fun black(x: Int, y: Int): Boolean {
                val row = origin + y
                val band = bands.first { row >= it.bounds.y && row < it.bounds.bottom }
                return band.graphic.isBlack(x, row - band.bounds.y)
            }
            assertTrue(black(8, 2)); assertFalse(black(7, 2)); assertFalse(black(9, 2))
            assertTrue(black(2, 8)); assertFalse(black(2, 7)); assertFalse(black(2, 9))
            assertTrue(black(382, 32)); assertTrue(black(100, 63))
        }
    }

    @Test fun extremeAxesAndRasterWidthCannotAmplifyOrRescaleKnownDotPatterns() {
        val extreme = ProfileDraft.fromProfile(SyntheticReferenceProfiles.narrow.copy(profileId = "org.rastrio.fixture.extreme-axis",
            geometry = PrinterGeometry(1, 600, 384)))
        val over = assertIs<ProfileValidationResult.Failure>(CalibrationFixtures.create(extreme,
            CalibrationRequest(CalibrationProcedure.GEOMETRY, 383)))
        assertTrue(over.diagnostics.any { it.path == "$.calibration.sourcePixels" })
        val narrowRaster = candidate.withField("raster.maxWidthDots", "200")
        val scaled = assertIs<ProfileValidationResult.Failure>(CalibrationFixtures.create(narrowRaster,
            CalibrationRequest(CalibrationProcedure.GEOMETRY, 383)))
        assertTrue(scaled.diagnostics.any { it.path == "$.calibration.widthDots" })
    }

    @Test fun oversampledSourcePixelBudgetAcceptsTheBoundaryAndRejectsAmplification() {
        fun axis(vertical: Int) = ProfileDraft.fromProfile(SyntheticReferenceProfiles.narrow.copy(
            profileId = "org.rastrio.fixture.pixel-budget", geometry = PrinterGeometry(100, vertical, 1024),
            raster = SyntheticReferenceProfiles.narrow.raster.copy(maxWidthDots = 1024)))
        val request = CalibrationRequest(CalibrationProcedure.GEOMETRY, 1024)
        val accepted = assertIs<ProfileValidationResult.Success<CalibrationFixture>>(CalibrationFixtures.create(axis(400), request)).value
        val source = accepted.resolvedImages.values.single().image
        assertEquals(1_048_576, source.width * source.height)
        val rejected = assertIs<ProfileValidationResult.Failure>(CalibrationFixtures.create(axis(401), request))
        assertTrue(rejected.diagnostics.any { it.path == "$.calibration.sourcePixels" })
    }

    @Test fun unsupportedMotionUnitDpiFailsEncoderPreflightWithoutAnOutputPath() {
        val draft = ProfileDraft.fromProfile(SyntheticReferenceProfiles.narrow.copy(profileId = "org.rastrio.fixture.motion-limit",
            geometry = PrinterGeometry(203, 406, 384)))
        val fixture = assertIs<ProfileValidationResult.Success<CalibrationFixture>>(CalibrationFixtures.create(draft,
            CalibrationRequest(CalibrationProcedure.GEOMETRY, 383))).value
        val failure = assertIs<PreparationResult.Failure>(fixture.prepare())
        assertTrue(failure.diagnostics.any { it.message.contains("ESC") })
    }

}
