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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class ProfileValidationTest {
    private val limits = PrinterProfileValidationLimits(
        maxDpi = 600, maxPrintableWidthDots = 1024, maxFontDimensionDots = 256,
        maxTextScale = 8, maxRasterBandHeightDots = 256, maxRasterBandBytes = 32768,
        maxQrModuleSize = 16, maxBurstBytes = 65536, maxPauseMs = 1000,
        maxCollectionEntries = 16,
    )
    private val registry = object : TrustedProfileRegistry {
        override fun dialect(id: String) = when (id) {
            "test.dialect" -> DialectDefinition("default-font", "test.repertoire", true)
            "test.other" -> DialectDefinition()
            else -> null
        }
        override fun strategy(id: String): StrategyDefinition? {
            val purpose = when (id) {
                "test.init" -> StrategyPurpose.INITIALIZATION
                "test.font" -> StrategyPurpose.FONT_SELECTION
                "test.page" -> StrategyPurpose.CODE_PAGE_SELECTION
                "test.raster", "test.raster-alt" -> StrategyPurpose.RASTER
                "test.qr" -> StrategyPurpose.QR
                "test.barcode" -> StrategyPurpose.BARCODE
                "test.cut" -> StrategyPurpose.CUT
                "test.status" -> StrategyPurpose.STATUS_QUERY
                else -> return null
            }
            return StrategyDefinition(purpose, profileListOf("test.dialect"),
                if (purpose == StrategyPurpose.FONT_SELECTION || purpose == StrategyPurpose.CODE_PAGE_SELECTION) 0..2 else null,
                fontLineAdvanceFromCellHeight = purpose == StrategyPurpose.FONT_SELECTION)
        }
        override fun knowsRepertoire(id: String) = id == "test.repertoire"
        override fun knowsQrModel(id: String) = id == "model2"
        override fun knowsBarcodeSymbology(id: String) = id == "test.barcode-kind"
        override fun quirk(id: String) = if (id == "test.quirk") QuirkDefinition(profileListOf("test.dialect")) else null
    }
    private val validator get() = PrinterProfileValidator(registry, limits)
    private fun native() = NativeTextCapability(true,
        profileListOf(NativeFontCapability("default-font", 12, 24)),
        NativeStyleCapability(false, false, profileListOf(1), profileListOf(1)),
        profileListOf(CodePageCapability("page", "test.repertoire")))
    private fun raster() = RasterCapability(true, profileListOf("test.raster"), "test.raster", 384, RasterBandHeight(1, 64, 128))
    private fun profile() = PrinterProfile(
        profileId = "test.synthetic", profileRevision = 1,
        display = ProfileDisplay("Synthetic"), geometry = PrinterGeometry(203, 300, 384),
        nativeText = native(), raster = RasterCapability(false),
        nativeQr = NativeQrCapability(false), nativeBarcode = NativeBarcodeCapability(false),
        cutter = CutterCapability(false), statusQuery = StatusQueryCapability(false),
        protocol = EscPosProtocolProfile("test.dialect", "test.init"))
    private fun validated(p: PrinterProfile = profile()) = assertIs<ProfileValidationResult.Success<ValidatedPrinterProfile>>(validator.validate(p)).value
    private fun rejects(p: PrinterProfile, code: String, path: String) {
        val errors = assertIs<ProfileValidationResult.Failure>(validator.validate(p)).diagnostics
        assertEquals(true, errors.any { it.code == code && it.path == path }, errors.toString())
    }

    @Test fun validNativeRasterAndCombinedProfiles() {
        validated()
        validated(profile().copy(nativeText = NativeTextCapability(false), raster = raster()))
        validated(profile().copy(raster = raster()))
    }

    @Test fun rejectsInvalidIdentityGeometryAndOutputPath() {
        rejects(profile().copy(format = "other"), "PRF100", "$.format")
        rejects(profile().copy(schemaVersion = 2), "PRF100", "$.schemaVersion")
        rejects(profile().copy(profileId = "../bad"), "PRF101", "$.profileId")
        rejects(profile().copy(profileRevision = 0), "PRF103", "$.profileRevision")
        rejects(profile().copy(geometry = PrinterGeometry(0, 300, 384)), "PRF103", "$.geometry.horizontalDpi")
        rejects(profile().copy(geometry = PrinterGeometry(601, 300, 384)), "PRF120", "$.geometry.horizontalDpi")
        rejects(profile().copy(geometry = PrinterGeometry(203, 0, 384)), "PRF103", "$.geometry.verticalDpi")
        rejects(profile().copy(geometry = PrinterGeometry(203, 300, 0)), "PRF103", "$.geometry.printableWidthDots")
        rejects(profile().copy(geometry = PrinterGeometry(203, 300, Int.MAX_VALUE)), "PRF120", "$.geometry.printableWidthDots")
        rejects(profile().copy(nativeText = NativeTextCapability(false)), "PRF104", "$")
    }

    @Test fun displayIsBoundedSafeUnicodeWithoutContentEcho() {
        for (value in listOf("bad\u0000", "bad\u007f", "bad\ud800"))
            rejects(profile().copy(display = ProfileDisplay(value)), "PRF102", "$.display.name")
        validated(profile().copy(display = ProfileDisplay("RastrIO\t印刷\n")))
        rejects(profile().copy(display = ProfileDisplay("é".repeat(32769))), "PRF120", "$.display.name")
        rejects(profile().copy(display = ProfileDisplay("ok", model = "\u0001")), "PRF102", "$.display.model")
        rejects(profile().copy(display = ProfileDisplay("ok", manufacturer = "\u0001")), "PRF102", "$.display.manufacturer")
    }

    @Test fun nativeTextDependenciesGeometryAndScales() {
        val n = native()
        rejects(profile().copy(nativeText = n.copy(supported = false)), "PRF104", "$.nativeText.fonts")
        rejects(profile().copy(nativeText = n.copy(fonts = profileListOf())), "PRF104", "$.nativeText.fonts")
        rejects(profile().copy(nativeText = n.copy(codePages = profileListOf())), "PRF104", "$.nativeText.codePages")
        rejects(profile().copy(nativeText = n.copy(fonts = profileListOf(n.fonts[0], n.fonts[0]))), "PRF105", "$.nativeText.fonts[1].id")
        rejects(profile().copy(nativeText = n.copy(codePages = profileListOf(n.codePages[0], n.codePages[0]))), "PRF105", "$.nativeText.codePages[1].id")
        for ((field, font) in listOf(
            "id" to n.fonts[0].copy(id = "bad id"),
            "cellWidthDots" to n.fonts[0].copy(cellWidthDots = 0),
            "cellHeightDots" to n.fonts[0].copy(cellHeightDots = -1),
            "lineAdvanceDots" to n.fonts[0].copy(lineAdvanceDots = 0)))
            rejects(profile().copy(nativeText = n.copy(fonts = profileListOf(font))), if (field == "id") "PRF101" else "PRF103", "$.nativeText.fonts[0]." + field)
        for ((field, styles) in listOf(
            "widthScales" to n.styles.copy(widthScales = profileListOf()),
            "heightScales" to n.styles.copy(heightScales = profileListOf())))
            rejects(profile().copy(nativeText = n.copy(styles = styles)), "PRF104", "$.nativeText.styles." + field)
        rejects(profile().copy(nativeText = n.copy(styles = n.styles.copy(widthScales = profileListOf(1, 1)))), "PRF105", "$.nativeText.styles.widthScales[1]")
        rejects(profile().copy(nativeText = n.copy(styles = n.styles.copy(heightScales = profileListOf(0)))), "PRF103", "$.nativeText.styles.heightScales[0]")
        rejects(profile().copy(nativeText = n.copy(styles = n.styles.copy(widthScales = profileListOf(9)))), "PRF120", "$.nativeText.styles.widthScales[0]")
        // Input order is preserved; canonical ordering belongs to the later serializer.
        validated(profile().copy(nativeText = n.copy(styles = n.styles.copy(widthScales = profileListOf(2, 1)))))
    }

    @Test fun selectorsDefaultsRepertoiresAndStrategyPurposeAreTrusted() {
        val n = native()
        for ((selector, code, suffix) in listOf(
            Triple(RegisteredSelector("test.unknown", 0), "PRF106", "strategy"),
            Triple(RegisteredSelector("test.page", 0), "PRF107", "strategy"),
            Triple(RegisteredSelector("test.font", 3), "PRF108", "parameter"))) {
            rejects(profile().copy(nativeText = n.copy(fonts = profileListOf(n.fonts[0].copy(selector = selector)))), code, "$.nativeText.fonts[0].selector." + suffix)
        }
        validated(profile().copy(nativeText = n.copy(fonts = profileListOf(n.fonts[0].copy(id = "selected", selector = RegisteredSelector("test.font", 2))))))
        rejects(profile().copy(nativeText = n.copy(fonts = profileListOf(n.fonts[0].copy(id = "not-default")))), "PRF108", "$.nativeText.fonts[0].selector")
        rejects(profile().copy(nativeText = n.copy(codePages = profileListOf(CodePageCapability("page", "unknown")))), "PRF106", "$.nativeText.codePages[0].repertoire")
        rejects(profile().copy(nativeText = n.copy(codePages = profileListOf(n.codePages[0].copy(selector = RegisteredSelector("test.page", -1))))), "PRF108", "$.nativeText.codePages[0].selector.parameter")
        rejects(profile().copy(protocol = EscPosProtocolProfile("test.other", "test.init")), "PRF107", "$.protocol.initializationStrategy")
    }

    @Test fun rasterDependenciesAndBandLimits() {
        val r = raster()
        for ((value, code, path) in listOf(
            Triple(r.copy(supported = false), "PRF104", "$.raster.strategies"),
            Triple(r.copy(strategies = profileListOf()), "PRF104", "$.raster.strategies"),
            Triple(r.copy(strategies = profileListOf("unknown")), "PRF106", "$.raster.strategies[0]"),
            Triple(r.copy(preferredStrategy = null), "PRF104", "$.raster.preferredStrategy"),
            Triple(r.copy(preferredStrategy = "test.raster-alt"), "PRF104", "$.raster.preferredStrategy"),
            Triple(r.copy(maxWidthDots = 385), "PRF104", "$.raster.maxWidthDots"),
            Triple(r.copy(maxWidthDots = null), "PRF104", "$.raster.maxWidthDots"),
            Triple(r.copy(bandHeightDots = null), "PRF104", "$.raster.bandHeightDots"),
            Triple(r.copy(bandHeightDots = RasterBandHeight(3, 2, 4)), "PRF104", "$.raster.bandHeightDots.preferred"),
            Triple(r.copy(bandHeightDots = RasterBandHeight(1, 5, 4)), "PRF104", "$.raster.bandHeightDots.preferred"),
            Triple(r.copy(bandHeightDots = RasterBandHeight(0, 1, 4)), "PRF103", "$.raster.bandHeightDots.min"),
            Triple(r.copy(bandHeightDots = RasterBandHeight(1, 64, 257)), "PRF120", "$.raster.bandHeightDots.max")))
            rejects(profile().copy(raster = value), code, path)
        rejects(profile().copy(raster = r.copy(strategies = ProfileList(List(17) { "test.raster" }))), "PRF120", "$.raster.strategies")
    }

    @Test fun qrBarcodeCutterAndStatusDependencies() {
        val q = NativeQrCapability(true, profileListOf("test.qr"), "test.qr", profileListOf("model2"), QrModuleSize(1, 8), profileListOf("L", "M", "Q", "H"))
        val b = NativeBarcodeCapability(true, profileListOf("test.barcode"), "test.barcode", profileListOf("test.barcode-kind"))
        val c = CutterCapability(true, profileListOf("full", "partial"), "test.cut")
        val t = StatusQueryCapability(true, profileListOf("test.status"), "test.status")
        validated(profile().copy(nativeQr = q, nativeBarcode = b, cutter = c, statusQuery = t, printerBuffer = PrinterBufferGuidance(4096, 0)))
        for ((v, code, path) in listOf(
            Triple(q.copy(supported = false), "PRF104", "$.nativeQr.models"),
            Triple(q.copy(models = profileListOf()), "PRF104", "$.nativeQr.models"),
            Triple(q.copy(models = profileListOf("future")), "PRF106", "$.nativeQr.models[0]"),
            Triple(q.copy(models = profileListOf("model2", "model2")), "PRF105", "$.nativeQr.models[1]"),
            Triple(q.copy(moduleSize = null), "PRF104", "$.nativeQr.moduleSize"),
            Triple(q.copy(moduleSize = QrModuleSize(2, 1)), "PRF104", "$.nativeQr.moduleSize.max"),
            Triple(q.copy(moduleSize = QrModuleSize(0, 1)), "PRF103", "$.nativeQr.moduleSize.min"),
            Triple(q.copy(moduleSize = QrModuleSize(1, 17)), "PRF120", "$.nativeQr.moduleSize.max"),
            Triple(q.copy(errorCorrection = profileListOf("X")), "PRF106", "$.nativeQr.errorCorrection[0]"),
            Triple(q.copy(errorCorrection = profileListOf("L", "L")), "PRF105", "$.nativeQr.errorCorrection[1]"),
            Triple(q.copy(errorCorrection = profileListOf()), "PRF104", "$.nativeQr.errorCorrection"),
            Triple(q.copy(strategies = profileListOf("unknown")), "PRF106", "$.nativeQr.strategies[0]"),
            Triple(q.copy(preferredStrategy = "missing"), "PRF104", "$.nativeQr.preferredStrategy")))
            rejects(profile().copy(nativeQr = v), code, path)
        for ((v, code, path) in listOf(
            Triple(b.copy(supported = false), "PRF104", "$.nativeBarcode.symbologies"),
            Triple(b.copy(symbologies = profileListOf()), "PRF104", "$.nativeBarcode.symbologies"),
            Triple(b.copy(symbologies = profileListOf("unknown")), "PRF106", "$.nativeBarcode.symbologies[0]"),
            Triple(b.copy(symbologies = profileListOf("test.barcode-kind", "test.barcode-kind")), "PRF105", "$.nativeBarcode.symbologies[1]"),
            Triple(b.copy(strategies = profileListOf("unknown")), "PRF106", "$.nativeBarcode.strategies[0]"),
            Triple(b.copy(preferredStrategy = "missing"), "PRF104", "$.nativeBarcode.preferredStrategy")))
            rejects(profile().copy(nativeBarcode = v), code, path)
        for ((v, code, path) in listOf(
            Triple(c.copy(supported = false), "PRF104", "$.cutter.modes"),
            Triple(c.copy(modes = profileListOf()), "PRF104", "$.cutter.modes"),
            Triple(c.copy(modes = profileListOf("none")), "PRF106", "$.cutter.modes[0]"),
            Triple(c.copy(modes = profileListOf("full", "full")), "PRF105", "$.cutter.modes[1]"),
            Triple(c.copy(strategy = null), "PRF104", "$.cutter.strategy"),
            Triple(c.copy(strategy = "unknown"), "PRF106", "$.cutter.strategy")))
            rejects(profile().copy(cutter = v), code, path)
        rejects(profile().copy(statusQuery = t.copy(supported = false)), "PRF104", "$.statusQuery.strategies")
        rejects(profile().copy(statusQuery = t.copy(strategies = profileListOf("unknown"))), "PRF106", "$.statusQuery.strategies[0]")
        rejects(profile().copy(statusQuery = t.copy(preferredStrategy = "missing")), "PRF104", "$.statusQuery.preferredStrategy")
    }

    @Test fun protocolQuirkAndBufferValidation() {
        rejects(profile().copy(protocol = EscPosProtocolProfile("unknown", "test.init")), "PRF106", "$.protocol.dialectId")
        rejects(profile().copy(protocol = EscPosProtocolProfile("test.dialect", "unknown")), "PRF106", "$.protocol.initializationStrategy")
        rejects(profile().copy(quirks = profileListOf("unknown")), "PRF106", "$.quirks[0]")
        rejects(profile().copy(quirks = profileListOf("test.quirk", "test.quirk")), "PRF105", "$.quirks[1]")
        rejects(profile().copy(quirks = profileListOf("bad id")), "PRF101", "$.quirks[0]")
        rejects(profile().copy(quirks = profileListOf("test.quirk"), protocol = EscPosProtocolProfile("test.other", "test.init")), "PRF107", "$.quirks[0]")
        for ((buffer, code, field) in listOf(
            Triple(PrinterBufferGuidance(0, 0), "PRF103", "recommendedMaxBurstBytes"),
            Triple(PrinterBufferGuidance(1, -1), "PRF103", "recommendedPauseAfterBurstMs"),
            Triple(PrinterBufferGuidance(Int.MAX_VALUE, 0), "PRF120", "recommendedMaxBurstBytes"),
            Triple(PrinterBufferGuidance(1, Int.MAX_VALUE), "PRF120", "recommendedPauseAfterBurstMs")))
            rejects(profile().copy(printerBuffer = buffer), code, "$.printerBuffer." + field)
    }

    @Test fun overridesReplaceInheritDisableEnableAndRevalidate() {
        val base = validated(profile().copy(raster = raster(), quirks = profileListOf("test.quirk")))
        val inherited = assertIs<ProfileValidationResult.Success<EffectivePrinterProfile>>(validator.resolve(base, PrinterProfileOverride())).value
        assertEquals(base.profile, inherited.profile)
        val override = PrinterProfileOverride(geometry = PrinterGeometry(300, 203, 512),
            nativeText = NativeTextCapability(false), quirks = profileListOf(),
            printerBuffer = PrinterBufferOverride.Replace(null))
        val effective = assertIs<ProfileValidationResult.Success<EffectivePrinterProfile>>(validator.resolve(base, override)).value
        assertEquals(512, effective.profile.geometry.printableWidthDots)
        assertEquals(false, effective.profile.nativeText.supported)
        assertEquals(emptyList(), effective.profile.quirks)
        assertEquals(384, base.profile.geometry.printableWidthDots)
        assertEquals(true, base.profile.nativeText.supported)
        assertIs<ProfileValidationResult.Failure>(validator.resolve(base, PrinterProfileOverride(geometry = PrinterGeometry(203, 203, 100))))
        assertIs<ProfileValidationResult.Failure>(validator.resolve(base, PrinterProfileOverride(nativeText = native().copy(fonts = profileListOf()))))
        assertIs<ProfileValidationResult.Failure>(validator.resolve(base, PrinterProfileOverride(raster = raster().copy(strategies = profileListOf("unknown")))))
        val rasterBase = validated(profile().copy(nativeText = NativeTextCapability(false), raster = raster()))
        assertIs<ProfileValidationResult.Success<EffectivePrinterProfile>>(validator.resolve(rasterBase, PrinterProfileOverride(nativeText = native())))
        assertIs<ProfileValidationResult.Failure>(validator.resolve(rasterBase, PrinterProfileOverride(raster = RasterCapability(false))))
        // A base accepted under a different trusted policy must still pass the current policy.
        val restrictive = PrinterProfileValidator(registry, limits.copy(maxDpi = 100))
        assertIs<ProfileValidationResult.Failure>(restrictive.resolve(base, PrinterProfileOverride()))
    }

    @Test fun collectionSnapshotsAndRepeatedDiagnosticsAreStable() {
        val fonts = mutableListOf(native().fonts[0])
        val quirks = mutableListOf("test.quirk")
        val candidate = profile().copy(nativeText = native().copy(fonts = ProfileList(fonts)), quirks = ProfileList(quirks))
        val value = validated(candidate)
        fonts.clear(); quirks.clear()
        assertEquals(1, value.profile.nativeText.fonts.size)
        assertEquals(listOf("test.quirk"), value.profile.quirks)
        assertEquals(candidate, validated(candidate).profile)
        val invalid = profile().copy(format = "bad", schemaVersion = 9, profileId = "Bad", profileRevision = 0)
        val diagnostics = assertIs<ProfileValidationResult.Failure>(validator.validate(invalid)).diagnostics
        assertEquals(listOf("$.format", "$.schemaVersion", "$.profileId", "$.profileRevision"), diagnostics.map { it.path })
        repeat(20) { assertEquals(diagnostics, assertIs<ProfileValidationResult.Failure>(validator.validate(invalid)).diagnostics) }
        assertFailsWith<IllegalArgumentException> { PrinterProfileValidator(registry, limits.copy(maxCollectionEntries = 0)) }
    }

    @Test fun everyStrategyPurposeRejectsMissingDuplicateWrongPurposeAndIncompatibleDialect() {
        val p = profile()
        val q = NativeQrCapability(true, profileListOf("test.qr"), "test.qr", profileListOf("model2"), QrModuleSize(1, 8), profileListOf("L"))
        val b = NativeBarcodeCapability(true, profileListOf("test.barcode"), "test.barcode", profileListOf("test.barcode-kind"))
        val t = StatusQueryCapability(true, profileListOf("test.status"), "test.status")
        val values = listOf(
            Triple(p.copy(raster = raster().copy(strategies = profileListOf())), "PRF104", "$.raster.strategies"),
            Triple(p.copy(nativeQr = q.copy(strategies = profileListOf())), "PRF104", "$.nativeQr.strategies"),
            Triple(p.copy(nativeBarcode = b.copy(strategies = profileListOf())), "PRF104", "$.nativeBarcode.strategies"),
            Triple(p.copy(statusQuery = t.copy(strategies = profileListOf())), "PRF104", "$.statusQuery.strategies"),
            Triple(p.copy(raster = raster().copy(strategies = profileListOf("test.raster", "test.raster"))), "PRF105", "$.raster.strategies[1]"),
            Triple(p.copy(nativeQr = q.copy(strategies = profileListOf("test.qr", "test.qr"))), "PRF105", "$.nativeQr.strategies[1]"),
            Triple(p.copy(nativeBarcode = b.copy(strategies = profileListOf("test.barcode", "test.barcode"))), "PRF105", "$.nativeBarcode.strategies[1]"),
            Triple(p.copy(statusQuery = t.copy(strategies = profileListOf("test.status", "test.status"))), "PRF105", "$.statusQuery.strategies[1]"),
            Triple(p.copy(raster = raster().copy(strategies = profileListOf("test.init"))), "PRF107", "$.raster.strategies[0]"),
            Triple(p.copy(nativeQr = q.copy(strategies = profileListOf("test.init"))), "PRF107", "$.nativeQr.strategies[0]"),
            Triple(p.copy(nativeBarcode = b.copy(strategies = profileListOf("test.init"))), "PRF107", "$.nativeBarcode.strategies[0]"),
            Triple(p.copy(statusQuery = t.copy(strategies = profileListOf("test.init"))), "PRF107", "$.statusQuery.strategies[0]"))
        for ((candidate, code, path) in values) rejects(candidate, code, path)
        val all = p.copy(raster = raster(), nativeQr = q, nativeBarcode = b,
            cutter = CutterCapability(true, profileListOf("full"), "test.cut"), statusQuery = t,
            nativeText = native().copy(fonts = profileListOf(native().fonts[0].copy(selector = RegisteredSelector("test.font", 0))),
                codePages = profileListOf(native().codePages[0].copy(selector = RegisteredSelector("test.page", 0)))),
            protocol = EscPosProtocolProfile("test.other", "test.init"))
        for (path in listOf("$.nativeText.fonts[0].selector.strategy", "$.nativeText.codePages[0].selector.strategy",
            "$.raster.strategies[0]", "$.nativeQr.strategies[0]", "$.nativeBarcode.strategies[0]", "$.cutter.strategy", "$.statusQuery.strategies[0]"))
            rejects(all, "PRF107", path)
    }

    @Test fun unsupportedCapabilitiesRejectEveryStrayOptionalField() {
        for ((candidate, path) in listOf(
            profile().copy(raster = RasterCapability(false, preferredStrategy = "test.raster")) to "$.raster.preferredStrategy",
            profile().copy(raster = RasterCapability(false, maxWidthDots = 384)) to "$.raster.maxWidthDots",
            profile().copy(raster = RasterCapability(false, bandHeightDots = RasterBandHeight(1, 2, 3))) to "$.raster.bandHeightDots",
            profile().copy(nativeQr = NativeQrCapability(false, preferredStrategy = "test.qr")) to "$.nativeQr.preferredStrategy",
            profile().copy(nativeQr = NativeQrCapability(false, moduleSize = QrModuleSize(1, 8))) to "$.nativeQr.moduleSize",
            profile().copy(nativeQr = NativeQrCapability(false, errorCorrection = profileListOf("L"))) to "$.nativeQr.errorCorrection",
            profile().copy(nativeBarcode = NativeBarcodeCapability(false, preferredStrategy = "test.barcode")) to "$.nativeBarcode.preferredStrategy",
            profile().copy(cutter = CutterCapability(false, strategy = "test.cut")) to "$.cutter.strategy",
            profile().copy(statusQuery = StatusQueryCapability(false, preferredStrategy = "test.status")) to "$.statusQuery.preferredStrategy"))
            rejects(candidate, "PRF104", path)
    }

    @Test fun trustedDefaultsAndDialectScaleConstraintsCannotBeGuessed() {
        val restrictiveRegistry = object : TrustedProfileRegistry by registry {
            override fun dialect(id: String) = registry.dialect(id)?.copy(defaultRepertoire = null,
                defaultFontLineAdvanceFromCellHeight = false, maxWidthScale = 1, maxHeightScale = 1)
            override fun strategy(id: String) = registry.strategy(id)?.copy(fontLineAdvanceFromCellHeight = false)
        }
        val validator = PrinterProfileValidator(restrictiveRegistry, limits)
        val bad = profile().copy(nativeText = native().copy(styles = native().styles.copy(widthScales = profileListOf(2), heightScales = profileListOf(2))))
        val diagnostics = assertIs<ProfileValidationResult.Failure>(validator.validate(bad)).diagnostics
        for (path in listOf("$.nativeText.fonts[0].lineAdvanceDots", "$.nativeText.codePages[0].selector", "$.nativeText.styles.widthScales[0]", "$.nativeText.styles.heightScales[0]"))
            assertTrue(diagnostics.any { it.code == "PRF108" && it.path == path })
        val explicit = profile().copy(nativeText = native().copy(
            fonts = profileListOf(native().fonts[0].copy(lineAdvanceDots = 24, selector = RegisteredSelector("test.font", 0))),
            codePages = profileListOf(native().codePages[0].copy(selector = RegisteredSelector("test.page", 0)))))
        assertIs<ProfileValidationResult.Success<ValidatedPrinterProfile>>(validator.validate(explicit))
        assertIs<ProfileValidationResult.Failure>(validator.validate(explicit.copy(nativeText = explicit.nativeText.copy(
            fonts = profileListOf(explicit.nativeText.fonts[0].copy(lineAdvanceDots = null))))))
    }

    @Test fun numericalBoundariesAndWidenedArithmetic() {
        for (dpi in listOf(-1, 0, 1, 599, 600, 601, Int.MAX_VALUE)) {
            val result = validator.validate(profile().copy(geometry = PrinterGeometry(dpi, dpi, 384)))
            assertEquals(dpi in 1..600, result is ProfileValidationResult.Success)
        }
        val permissive = limits.copy(maxPrintableWidthDots = Int.MAX_VALUE, maxFontDimensionDots = Int.MAX_VALUE,
            maxTextScale = Int.MAX_VALUE, maxRasterBandHeightDots = Int.MAX_VALUE)
        val huge = profile().copy(nativeText = NativeTextCapability(false),
            geometry = PrinterGeometry(203, 300, Int.MAX_VALUE),
            raster = RasterCapability(true, profileListOf("test.raster"), "test.raster", Int.MAX_VALUE, RasterBandHeight(1, 1, Int.MAX_VALUE)))
        val rasterErrors = assertIs<ProfileValidationResult.Failure>(PrinterProfileValidator(registry, permissive).validate(huge)).diagnostics
        assertTrue(rasterErrors.any { it.code == "PRF120" && it.path == "$.raster.bandHeightDots.max" })
        val scaled = profile().copy(nativeText = native().copy(fonts = profileListOf(native().fonts[0].copy(cellWidthDots = Int.MAX_VALUE)),
            styles = native().styles.copy(widthScales = profileListOf(2))))
        val scaleErrors = assertIs<ProfileValidationResult.Failure>(PrinterProfileValidator(registry, permissive).validate(scaled)).diagnostics
        assertTrue(scaleErrors.any { it.code == "PRF120" && it.path == "$.nativeText.fonts[0].cellWidthDots" })
        val odd = profile().copy(raster = raster().copy(maxWidthDots = 9, bandHeightDots = RasterBandHeight(1, 1, 2)))
        assertIs<ProfileValidationResult.Success<ValidatedPrinterProfile>>(PrinterProfileValidator(registry, limits.copy(maxRasterBandBytes = 4)).validate(odd))
        assertIs<ProfileValidationResult.Failure>(PrinterProfileValidator(registry, limits.copy(maxRasterBandBytes = 3)).validate(odd))
    }

    @Test fun collectionAndStringPoliciesRejectBeforeTraversalAndNeverEchoContent() {
        val p = profile()
        val longId = "sensitive".repeat(20000)
        val errors = assertIs<ProfileValidationResult.Failure>(validator.validate(p.copy(quirks = profileListOf(longId, longId)))).diagnostics
        assertEquals(listOf("PRF101", "PRF101"), errors.map { it.code })
        assertFalse(errors.any { it.message.contains("sensitive") || it.path.contains("sensitive") })
        val restrictive = PrinterProfileValidator(registry, limits.copy(maxCollectionEntries = 1))
        val candidate = p.copy(nativeText = native().copy(fonts = profileListOf(native().fonts[0], native().fonts[0])))
        assertEquals(listOf("PRF120" to "$.nativeText.fonts"),
            assertIs<ProfileValidationResult.Failure>(restrictive.validate(candidate)).diagnostics.map { it.code to it.path })
    }

    private fun assertReadOnly(values: List<String>) {
        val cast = values as? MutableList<String>
        if (cast != null) assertFailsWith<UnsupportedOperationException> { cast.clear() }
    }

    @Test fun metadataNeverSelectsHardwareBehaviorAndNestedCollectionsAreSnapshots() {
        for (name in listOf("H50i", "Unknown vendor", "<script>not executed</script>")) {
            val p = profile().copy(display = ProfileDisplay(name, name, name))
            assertEquals(p, validated(p).profile)
        }
        val scales = mutableListOf(1, 2)
        val ids = mutableListOf("test.raster", "test.raster-alt")
        val base = validated(profile().copy(nativeText = native().copy(styles = native().styles.copy(widthScales = ProfileList(scales))),
            raster = raster().copy(strategies = ProfileList(ids))))
        val overrideIds = mutableListOf("test.raster-alt")
        val override = PrinterProfileOverride(raster = raster().copy(strategies = ProfileList(overrideIds), preferredStrategy = "test.raster-alt"))
        val effective = assertIs<ProfileValidationResult.Success<EffectivePrinterProfile>>(validator.resolve(base, override)).value
        scales.clear(); ids.clear(); overrideIds.clear()
        assertEquals(listOf(1, 2), base.profile.nativeText.styles.widthScales)
        assertEquals(listOf("test.raster", "test.raster-alt"), base.profile.raster.strategies)
        assertEquals(listOf("test.raster-alt"), effective.profile.raster.strategies)
        assertReadOnly(effective.profile.raster.strategies)
    }

    @Test fun printerBufferRecommendationsAreIndependentlyOptional() {
        validated(profile().copy(printerBuffer = PrinterBufferGuidance(recommendedMaxBurstBytes = 4096)))
        validated(profile().copy(printerBuffer = PrinterBufferGuidance(recommendedPauseAfterBurstMs = 0)))
        validated(profile().copy(printerBuffer = PrinterBufferGuidance()))
        val base = validated(profile().copy(printerBuffer = PrinterBufferGuidance(4096, 10)))
        val inherited = assertIs<ProfileValidationResult.Success<EffectivePrinterProfile>>(validator.resolve(base, PrinterProfileOverride())).value
        assertEquals(PrinterBufferGuidance(4096, 10), inherited.profile.printerBuffer)
        val cleared = assertIs<ProfileValidationResult.Success<EffectivePrinterProfile>>(validator.resolve(base,
            PrinterProfileOverride(printerBuffer = PrinterBufferOverride.Replace(null)))).value
        assertEquals(null, cleared.profile.printerBuffer)
        val replaced = assertIs<ProfileValidationResult.Success<EffectivePrinterProfile>>(validator.resolve(base,
            PrinterProfileOverride(printerBuffer = PrinterBufferOverride.Replace(PrinterBufferGuidance(recommendedMaxBurstBytes = 1024))))).value
        assertEquals(PrinterBufferGuidance(recommendedMaxBurstBytes = 1024), replaced.profile.printerBuffer)
    }

    @Test fun malformedIdsNeverReachTrustedLookups() {
        val checked = object : TrustedProfileRegistry by registry {
            override fun dialect(id: String): DialectDefinition? {
                assertEquals("test.dialect", id)
                return registry.dialect(id)
            }
        }
        assertIs<ProfileValidationResult.Failure>(PrinterProfileValidator(checked, limits).validate(
            profile().copy(protocol = EscPosProtocolProfile("bad id", "test.init"))))
    }

    @Test fun nativeSelectorsAndCollectionCeilingsCoverBothLists() {
        val n = native()
        rejects(profile().copy(nativeText = n.copy(supported = false)), "PRF104", "$.nativeText.codePages")
        rejects(profile().copy(nativeText = n.copy(codePages = profileListOf(n.codePages[0].copy(id = "bad id")))), "PRF101", "$.nativeText.codePages[0].id")
        rejects(profile().copy(nativeText = n.copy(codePages = profileListOf(n.codePages[0].copy(selector = RegisteredSelector("unknown", 0))))), "PRF106", "$.nativeText.codePages[0].selector.strategy")
        rejects(profile().copy(nativeText = n.copy(fonts = ProfileList(List(17) { n.fonts[0] }))), "PRF120", "$.nativeText.fonts")
        rejects(profile().copy(nativeText = n.copy(codePages = ProfileList(List(17) { n.codePages[0] }))), "PRF120", "$.nativeText.codePages")
        rejects(profile().copy(nativeText = n.copy(fonts = profileListOf(n.fonts[0].copy(cellHeightDots = 257)))), "PRF120", "$.nativeText.fonts[0].cellHeightDots")
        rejects(profile().copy(nativeText = n.copy(styles = n.styles.copy(widthScales = ProfileList(List(17) { 1 })))), "PRF120", "$.nativeText.styles.widthScales")
        rejects(profile().copy(quirks = ProfileList(List(17) { "test.quirk" })), "PRF120", "$.quirks")
        val noParameterDomain = object : TrustedProfileRegistry by registry {
            override fun strategy(id: String) = registry.strategy(id)?.copy(selectorParameterRange = null)
        }
        val result = PrinterProfileValidator(noParameterDomain, limits).validate(profile().copy(nativeText = n.copy(
            fonts = profileListOf(n.fonts[0].copy(selector = RegisteredSelector("test.font", 0))))))
        assertTrue(assertIs<ProfileValidationResult.Failure>(result).diagnostics.any {
            it.code == "PRF108" && it.path == "$.nativeText.fonts[0].selector.parameter"
        })
    }
}
