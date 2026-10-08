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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BaselineTrustedProfileRegistryTest {
    private val registry = BaselineTrustedProfileRegistry
    private val validator = PrinterProfileValidator(registry, PrinterProfileValidationLimits(
        maxDpi = 600, maxPrintableWidthDots = 1024, maxFontDimensionDots = 256,
        maxTextScale = 8, maxRasterBandHeightDots = 256, maxRasterBandBytes = 32768,
        maxQrModuleSize = 16, maxBurstBytes = 65536, maxPauseMs = 1000, maxCollectionEntries = 16,
    ))
    private fun candidate(fontParameter: Int = 0, pageParameter: Int = 0) = PrinterProfile(
        profileId = "test.registry", profileRevision = 1, display = ProfileDisplay("Registry test"),
        geometry = PrinterGeometry(203, 203, 384),
        nativeText = NativeTextCapability(true,
            profileListOf(NativeFontCapability("font-a", 12, 24, 24,
                RegisteredSelector("escpos.font.esc-m", fontParameter))),
            NativeStyleCapability(false, false, profileListOf(1), profileListOf(1)),
            profileListOf(CodePageCapability("cp437", "cp437",
                RegisteredSelector("escpos.code-page.esc-t", pageParameter)))),
        raster = RasterCapability(false), nativeQr = NativeQrCapability(false),
        nativeBarcode = NativeBarcodeCapability(false), cutter = CutterCapability(false),
        statusQuery = StatusQueryCapability(false),
        protocol = EscPosProtocolProfile("escpos.generic", "escpos.init.standard"),
    )
    private fun rejects(candidate: PrinterProfile, code: String, path: String) {
        val errors = assertIs<ProfileValidationResult.Failure>(validator.validate(candidate)).diagnostics
        assertTrue(errors.any { it.code == code && it.path == path }, errors.toString())
    }

    @Test fun baselineStrategiesHaveExactPurposesAndGenericDialectCompatibility() {
        val purposes = mapOf(
            "escpos.init.standard" to StrategyPurpose.INITIALIZATION,
            "escpos.font.esc-m" to StrategyPurpose.FONT_SELECTION,
            "escpos.code-page.esc-t" to StrategyPurpose.CODE_PAGE_SELECTION,
            "escpos.raster.gs-v-0" to StrategyPurpose.RASTER,
            "escpos.qr.model2" to StrategyPurpose.QR,
            "escpos.barcode.gs-k" to StrategyPurpose.BARCODE,
            "escpos.cut.gs-v" to StrategyPurpose.CUT,
            "escpos.status.dle-eot" to StrategyPurpose.STATUS_QUERY,
        )
        for ((id, purpose) in purposes) {
            val definition = assertNotNull(registry.strategy(id))
            assertEquals(purpose, definition.purpose)
            assertEquals(listOf("escpos.generic"), definition.dialectIds)
            assertFalse(definition.fontLineAdvanceFromCellHeight)
            if (purpose != StrategyPurpose.FONT_SELECTION && purpose != StrategyPurpose.CODE_PAGE_SELECTION)
                assertNull(definition.selectorParameterRange)
        }
        assertEquals(DialectDefinition(), registry.dialect("escpos.generic"))
    }

    @Test fun knownVocabularyIsSmallAndUnknownIdentifiersFailClosed() {
        assertTrue(registry.knowsRepertoire("cp437"))
        assertTrue(registry.knowsQrModel("model2"))
        for (id in listOf("code128", "ean13")) assertTrue(registry.knowsBarcodeSymbology(id))
        for (id in listOf("", "unknown", "CP437", "H50i", "escpos.raster.requires-feed-after-band")) {
            assertNull(registry.dialect(id))
            assertNull(registry.strategy(id))
            assertFalse(registry.knowsRepertoire(id))
            assertFalse(registry.knowsQrModel(id))
            assertFalse(registry.knowsBarcodeSymbology(id))
            assertNull(registry.quirk(id))
        }
        assertNull(registry.strategy("escpos.generic"))
        assertNull(registry.dialect("escpos.init.standard"))
        assertFalse(registry.knowsQrModel("model1"))
        assertFalse(registry.knowsBarcodeSymbology("upca"))
    }

    @Test fun selectorDomainsAreEnforcedByTheProductionValidator() {
        assertEquals(0..1, registry.strategy("escpos.font.esc-m")?.selectorParameterRange)
        assertEquals(0..255, registry.strategy("escpos.code-page.esc-t")?.selectorParameterRange)
        for (font in listOf(0, 1)) for (page in listOf(0, 255))
            assertIs<ProfileValidationResult.Success<ValidatedPrinterProfile>>(validator.validate(candidate(font, page)))
        for (font in listOf(-1, 2, 48, Int.MAX_VALUE))
            rejects(candidate(fontParameter = font), "PRF108", "$.nativeText.fonts[0].selector.parameter")
        for (page in listOf(-1, 256, Int.MAX_VALUE))
            rejects(candidate(pageParameter = page), "PRF108", "$.nativeText.codePages[0].selector.parameter")
    }

    @Test fun genericDialectCannotSupplyInferredDefaultsOrAcceptWrongPurposes() {
        val p = candidate()
        rejects(p.copy(nativeText = p.nativeText.copy(fonts = profileListOf(
            p.nativeText.fonts[0].copy(selector = null)))), "PRF108", "$.nativeText.fonts[0].selector")
        rejects(p.copy(nativeText = p.nativeText.copy(fonts = profileListOf(
            p.nativeText.fonts[0].copy(lineAdvanceDots = null)))), "PRF108", "$.nativeText.fonts[0].lineAdvanceDots")
        rejects(p.copy(nativeText = p.nativeText.copy(codePages = profileListOf(
            p.nativeText.codePages[0].copy(selector = null)))), "PRF108", "$.nativeText.codePages[0].selector")
        rejects(p.copy(protocol = EscPosProtocolProfile("escpos.generic", "escpos.raster.gs-v-0")),
            "PRF107", "$.protocol.initializationStrategy")
        rejects(p.copy(protocol = EscPosProtocolProfile("escpos.unknown", "escpos.init.standard")),
            "PRF106", "$.protocol.dialectId")
        rejects(p.copy(protocol = EscPosProtocolProfile("escpos.generic", "escpos.unknown")),
            "PRF106", "$.protocol.initializationStrategy")
        rejects(p.copy(quirks = profileListOf("escpos.raster.requires-feed-after-band")), "PRF106", "$.quirks[0]")
    }
}
