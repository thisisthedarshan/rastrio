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

package com.circuitnext.rastrio.core.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

// Explicit test policy, never a limit selected by the reference profile itself.
internal val syntheticReferenceTestLimits = PrinterProfileValidationLimits(
    maxDpi = 600, maxPrintableWidthDots = 1024, maxFontDimensionDots = 256, maxTextScale = 8,
    maxRasterBandHeightDots = 1024, maxRasterBandBytes = 131072, maxQrModuleSize = 16,
    maxBurstBytes = 65536, maxPauseMs = 1000, maxCollectionEntries = 16,
)

class SyntheticReferenceProfilesTest {
    private val validator = PrinterProfileValidator(BaselineTrustedProfileRegistry, syntheticReferenceTestLimits)
    private val codec = TcfgCodec(BaselineTrustedProfileRegistry, syntheticReferenceTestLimits)

    @Test fun allReferenceCandidatesPassProductionValidationAndCanonicalRoundTrip() {
        assertEquals(3, SyntheticReferenceProfiles.all.size)
        assertEquals(3, SyntheticReferenceProfiles.all.map { it.profileId }.distinct().size)
        for (candidate in SyntheticReferenceProfiles.all) {
            val validated = assertIs<ProfileValidationResult.Success<ValidatedPrinterProfile>>(validator.validate(candidate)).value
            val bytes = assertIs<ProfileValidationResult.Success<ByteArray>>(codec.encode(validated)).value
            val decoded = assertIs<ProfileValidationResult.Success<ValidatedPrinterProfile>>(codec.decode(bytes)).value
            assertEquals(candidate, decoded.profile)
            assertTrue(bytes.contentEquals(assertIs<ProfileValidationResult.Success<ByteArray>>(codec.encode(decoded)).value))
        }
    }

    @Test fun referenceCapabilitiesProvideNarrowWideAndRasterOnlyContrasts() {
        val narrow = SyntheticReferenceProfiles.narrow
        val wide = SyntheticReferenceProfiles.wide80mm
        val rasterOnly = SyntheticReferenceProfiles.rasterOnly
        assertEquals(384, narrow.geometry.printableWidthDots)
        assertEquals(576, wide.geometry.printableWidthDots)
        assertTrue(narrow.nativeText.supported && wide.nativeText.supported)
        assertFalse(narrow.nativeQr.supported || narrow.nativeBarcode.supported || narrow.cutter.supported || narrow.statusQuery.supported)
        assertTrue(wide.nativeQr.supported && wide.nativeBarcode.supported && wide.cutter.supported && wide.statusQuery.supported)
        assertFalse(rasterOnly.nativeText.supported)
        assertTrue(rasterOnly.nativeText.fonts.isEmpty() && rasterOnly.nativeText.codePages.isEmpty())
        assertTrue(SyntheticReferenceProfiles.all.all { it.raster.supported })
        assertEquals(listOf(128, 192, 64), SyntheticReferenceProfiles.all.map { it.raster.bandHeightDots?.preferred })
    }

    @Test fun repositoryOwnershipDoesNotBypassApplicationLimits() {
        val bounded = PrinterProfileValidator(BaselineTrustedProfileRegistry,
            syntheticReferenceTestLimits.copy(maxPrintableWidthDots = 384))
        assertIs<ProfileValidationResult.Success<ValidatedPrinterProfile>>(bounded.validate(SyntheticReferenceProfiles.narrow))
        val failure = assertIs<ProfileValidationResult.Failure>(bounded.validate(SyntheticReferenceProfiles.wide80mm))
        assertTrue(failure.diagnostics.any { it.code == "PRF120" && it.path == "$.geometry.printableWidthDots" })
    }
}
