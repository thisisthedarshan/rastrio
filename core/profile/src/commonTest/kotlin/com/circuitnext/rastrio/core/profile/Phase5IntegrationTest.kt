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
import kotlin.test.assertIs
import kotlin.test.assertTrue

class Phase5IntegrationTest {
    private val validator = PrinterProfileValidator(BaselineTrustedProfileRegistry, syntheticReferenceTestLimits)
    private val codec = TcfgCodec(BaselineTrustedProfileRegistry, syntheticReferenceTestLimits)

    private fun encoded(candidate: PrinterProfile): ByteArray {
        val validated = assertIs<ProfileValidationResult.Success<ValidatedPrinterProfile>>(validator.validate(candidate)).value
        return assertIs<ProfileValidationResult.Success<ByteArray>>(codec.encode(validated)).value
    }

    private fun decoded(candidate: PrinterProfile) =
        assertIs<ProfileValidationResult.Success<ValidatedPrinterProfile>>(codec.decode(encoded(candidate))).value

    @Test fun typedOverridesResolveAgainstEveryDecodedMaintainedProfile() {
        for (candidate in SyntheticReferenceProfiles.all) {
            val base = decoded(candidate)
            val geometry = candidate.geometry.copy(printableWidthDots = candidate.geometry.printableWidthDots + 8)
            val effective = assertIs<ProfileValidationResult.Success<EffectivePrinterProfile>>(validator.resolve(base,
                PrinterProfileOverride(geometry = geometry, printerBuffer = PrinterBufferOverride.Replace(null)))).value
            assertEquals(candidate.copy(geometry = geometry, printerBuffer = null), effective.profile)
            assertEquals(candidate, base.profile)

            val invalid = assertIs<ProfileValidationResult.Failure>(validator.resolve(base,
                PrinterProfileOverride(geometry = geometry.copy(printableWidthDots = 0))))
            assertTrue(invalid.diagnostics.any { it.code == "PRF103" && it.path == "$.geometry.printableWidthDots" })

            // A positive width alone is insufficient: inherited raster geometry must still fit.
            val inconsistent = assertIs<ProfileValidationResult.Failure>(validator.resolve(base,
                PrinterProfileOverride(geometry = geometry.copy(printableWidthDots = candidate.raster.maxWidthDots!! - 1))))
            assertTrue(inconsistent.diagnostics.any { it.code == "PRF104" && it.path == "$.raster.maxWidthDots" })
        }
    }

    @Test fun overrideResolutionRevalidatesInheritedDataUnderCurrentApplicationPolicy() {
        val base = decoded(SyntheticReferenceProfiles.wide80mm)
        val stricter = PrinterProfileValidator(BaselineTrustedProfileRegistry,
            syntheticReferenceTestLimits.copy(maxPrintableWidthDots = SyntheticReferenceProfiles.narrow.geometry.printableWidthDots))
        val failure = assertIs<ProfileValidationResult.Failure>(stricter.resolve(base, PrinterProfileOverride()))
        assertTrue(failure.diagnostics.any { it.code == "PRF120" && it.path == "$.geometry.printableWidthDots" })
    }

    @Test fun unknownProtocolDataFailsClosedThroughProductionCodec() {
        for (candidate in SyntheticReferenceProfiles.all) {
            val source = encoded(candidate).decodeToString()
            for ((original, replacement, code, path) in listOf(
                listOf("escpos.init.standard", "unknown.init", "PRF106", "$.protocol.initializationStrategy"),
                listOf("escpos.raster.gs-v-0", "unknown.raster", "PRF106", "$.raster.strategies[0]"),
                listOf("escpos.generic", "unknown.dialect", "PRF106", "$.protocol.dialectId"),
                listOf("\"family\":\"escpos\"", "\"family\":\"unknown\"", "PRF137", "$.protocol.family"),
            )) {
                assertTrue(original in source)
                val failure = assertIs<ProfileValidationResult.Failure>(codec.decode(source.replace(original, replacement).encodeToByteArray()))
                assertTrue(failure.diagnostics.any { it.code == code && it.path == path }, failure.diagnostics.toString())
            }
        }
    }

    @Test fun modelMetadataCannotChangeCapabilitiesOrOverrideDiagnostics() {
        for (candidate in SyntheticReferenceProfiles.all) {
            val ordinary = decoded(candidate)
            val renamed = decoded(candidate.copy(display = ProfileDisplay("Development candidate", "Unknown", "H50i")))
            assertEquals(ordinary.profile, renamed.profile.copy(display = ordinary.profile.display))
            val override = PrinterProfileOverride(geometry = candidate.geometry.copy(printableWidthDots = 0))
            assertEquals(
                assertIs<ProfileValidationResult.Failure>(validator.resolve(ordinary, override)).diagnostics,
                assertIs<ProfileValidationResult.Failure>(validator.resolve(renamed, override)).diagnostics,
            )
            assertEquals(ordinary.profile,
                assertIs<ProfileValidationResult.Success<EffectivePrinterProfile>>(validator.resolve(renamed,
                    PrinterProfileOverride())).value.profile.copy(display = ordinary.profile.display))
        }
    }

    @Test fun anotherCapabilitySetRequiresOnlyDataAndSelection() {
        val narrow = SyntheticReferenceProfiles.narrow
        val additional = narrow.copy(profileId = "org.rastrio.test.additional", display = ProfileDisplay("Additional synthetic"),
            geometry = narrow.geometry.copy(printableWidthDots = 448), raster = narrow.raster.copy(maxWidthDots = 448))
        val inputs = (SyntheticReferenceProfiles.all + additional).map(::encoded)
        val selected = inputs.map { bytes ->
            val base = assertIs<ProfileValidationResult.Success<ValidatedPrinterProfile>>(codec.decode(bytes)).value
            assertIs<ProfileValidationResult.Success<EffectivePrinterProfile>>(validator.resolve(base, PrinterProfileOverride())).value.profile
        }
        assertEquals(SyntheticReferenceProfiles.all + additional, selected)
        assertEquals(448, selected.last().geometry.printableWidthDots)
    }
}
