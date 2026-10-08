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

/** Pure validation; each invocation owns its diagnostics and traverses fields in schema order. */
class PrinterProfileValidator(
    private val registry: TrustedProfileRegistry,
    private val limits: PrinterProfileValidationLimits,
) {
    init {
        require(listOf(limits.maxDpi, limits.maxPrintableWidthDots, limits.maxFontDimensionDots,
            limits.maxTextScale, limits.maxRasterBandHeightDots, limits.maxQrModuleSize,
            limits.maxBurstBytes, limits.maxCollectionEntries, limits.maxStringBytes).all { it > 0 })
        require(limits.maxPauseMs >= 0 && limits.maxRasterBandBytes > 0)
    }

    fun validate(candidate: PrinterProfile): ProfileValidationResult<ValidatedPrinterProfile> {
        val diagnostics = Validation(registry, limits).validate(candidate)
        return if (diagnostics.isEmpty()) ProfileValidationResult.Success(ValidatedPrinterProfile(candidate))
        else ProfileValidationResult.Failure(ProfileList(diagnostics))
    }

    fun resolve(base: ValidatedPrinterProfile, override: PrinterProfileOverride): ProfileValidationResult<EffectivePrinterProfile> {
        val p = base.profile
        val candidate = p.copy(
            geometry = override.geometry ?: p.geometry,
            nativeText = override.nativeText ?: p.nativeText,
            raster = override.raster ?: p.raster,
            nativeQr = override.nativeQr ?: p.nativeQr,
            nativeBarcode = override.nativeBarcode ?: p.nativeBarcode,
            cutter = override.cutter ?: p.cutter,
            printerBuffer = when (val buffer = override.printerBuffer) {
                PrinterBufferOverride.Inherit -> p.printerBuffer
                is PrinterBufferOverride.Replace -> buffer.value
            },
            statusQuery = override.statusQuery ?: p.statusQuery,
            protocol = override.protocol ?: p.protocol,
            quirks = override.quirks ?: p.quirks,
        )
        return when (val result = validate(candidate)) {
            is ProfileValidationResult.Success -> ProfileValidationResult.Success(EffectivePrinterProfile(result.value.profile))
            is ProfileValidationResult.Failure -> result
        }
    }
}

private class Validation(private val registry: TrustedProfileRegistry, private val limits: PrinterProfileValidationLimits) {
    private val diagnostics = mutableListOf<ProfileDiagnostic>()
    private fun error(code: String, path: String, message: String) { diagnostics += ProfileDiagnostic(code, path, message) }
    private fun dependency(valid: Boolean, path: String) {
        if (!valid) error("PRF104", path, "Inconsistent or missing capability data")
    }
    private fun number(value: Int, maximum: Int, path: String, minimum: Int = 1) {
        if (value < minimum) error("PRF103", path, "Value below semantic minimum")
        else if (value > maximum) error("PRF120", path, "Trusted numerical limit exceeded")
    }
    private fun identifierSyntax(value: String): Boolean =
        value.length in 1..128 && value[0].let { it in 'a'..'z' || it in '0'..'9' } &&
            value.all { it in 'a'..'z' || it in '0'..'9' || it == '.' || it == '_' || it == '-' }

    private fun identifier(value: String, path: String): Boolean {
        val valid = identifierSyntax(value)
        if (!valid) error("PRF101", path, "Invalid ASCII identifier")
        else if (value.length > limits.maxStringBytes) error("PRF120", path, "Trusted string limit exceeded")
        return valid && value.length <= limits.maxStringBytes
    }
    private fun display(value: String, path: String) {
        if (value.length > limits.maxStringBytes) {
            error("PRF120", path, "Trusted UTF-8 string limit exceeded")
            return
        }
        var bytes = 0L
        var i = 0
        while (i < value.length) {
            val c = value[i++].code
            if ((c < 32 && c != 9 && c != 10 && c != 13) || c in 127..159) {
                error("PRF102", path, "Unsafe display control character")
                return
            }
            bytes += when {
                c < 128 -> 1
                c < 2048 -> 2
                c in 0xD800..0xDBFF -> {
                    if (i >= value.length || value[i].code !in 0xDC00..0xDFFF) {
                        error("PRF102", path, "Invalid Unicode scalar sequence")
                        return
                    }
                    i++
                    4
                }
                c in 0xDC00..0xDFFF -> {
                    error("PRF102", path, "Invalid Unicode scalar sequence")
                    return
                }
                else -> 3
            }
            if (bytes > limits.maxStringBytes) {
                error("PRF120", path, "Trusted UTF-8 string limit exceeded")
                return
            }
        }
    }
    private fun bounded(values: List<*>, path: String): Boolean {
        if (values.size <= limits.maxCollectionEntries) return true
        error("PRF120", path, "Trusted collection limit exceeded")
        return false
    }
    private fun <T : Any> entries(values: List<T>, path: String, action: (T, String) -> Unit) {
        if (!bounded(values, path)) return
        val seen = mutableSetOf<Any>()
        values.forEachIndexed { i, value ->
            val itemPath = "$path[$i]"
            // Invalid oversized IDs are diagnosed without hashing their untrusted contents.
            if ((value !is String || value.length <= 128) && !seen.add(value))
                error("PRF105", itemPath, "Duplicate capability value")
            action(value, itemPath)
        }
    }
    private fun strategy(id: String, purpose: StrategyPurpose, dialectId: String, path: String): StrategyDefinition? {
        if (!identifier(id, path)) return null
        val definition = registry.strategy(id)
        if (definition == null) error("PRF106", path, "Unregistered strategy")
        else if (definition.purpose != purpose || dialectId !in definition.dialectIds)
            error("PRF107", path, "Strategy purpose or dialect is incompatible")
        return definition
    }
    private fun selector(value: RegisteredSelector, purpose: StrategyPurpose, dialectId: String, path: String): StrategyDefinition? {
        val definition = strategy(value.strategy, purpose, dialectId, "$path.strategy")
        if (definition != null && (definition.selectorParameterRange == null || value.parameter !in definition.selectorParameterRange))
            error("PRF108", "$path.parameter", "Selector parameter outside trusted domain")
        return definition
    }
    private fun strategies(supported: Boolean, values: List<String>, preferred: String?, purpose: StrategyPurpose, dialectId: String, path: String) {
        dependency(if (supported) values.isNotEmpty() else values.isEmpty(), "$path.strategies")
        dependency(if (supported) preferred != null else preferred == null, "$path.preferredStrategy")
        if (bounded(values, "$path.strategies")) {
            entries(values, "$path.strategies") { value, itemPath -> strategy(value, purpose, dialectId, itemPath) }
            if (preferred != null && preferred.length <= 128) dependency(preferred in values, "$path.preferredStrategy")
        }
        if (preferred != null) strategy(preferred, purpose, dialectId, "$path.preferredStrategy")
    }

    fun validate(p: PrinterProfile): List<ProfileDiagnostic> {
        if (p.format != "rastrio-tcfg") error("PRF100", "$.format", "Unsupported profile format")
        if (p.schemaVersion != 1) error("PRF100", "$.schemaVersion", "Unsupported schema version")
        identifier(p.profileId, "$.profileId")
        number(p.profileRevision, Int.MAX_VALUE, "$.profileRevision")
        display(p.display.name, "$.display.name")
        p.display.manufacturer?.let { display(it, "$.display.manufacturer") }
        p.display.model?.let { display(it, "$.display.model") }
        number(p.geometry.horizontalDpi, limits.maxDpi, "$.geometry.horizontalDpi")
        number(p.geometry.verticalDpi, limits.maxDpi, "$.geometry.verticalDpi")
        number(p.geometry.printableWidthDots, limits.maxPrintableWidthDots, "$.geometry.printableWidthDots")
        // Protocol lookup supplies metadata; its diagnostics still appear in schema field order.
        val protocol = when (val value = p.protocol) { is EscPosProtocolProfile -> value }
        val dialect = if (identifierSyntax(protocol.dialectId) && protocol.dialectId.length <= limits.maxStringBytes)
            registry.dialect(protocol.dialectId) else null
        nativeText(p.nativeText, protocol.dialectId, dialect)
        raster(p.raster, p.geometry.printableWidthDots, protocol.dialectId)
        qr(p.nativeQr, protocol.dialectId)
        barcode(p.nativeBarcode, protocol.dialectId)
        cutter(p.cutter, protocol.dialectId)
        p.printerBuffer?.let {
            it.recommendedMaxBurstBytes?.let { bytes -> number(bytes, limits.maxBurstBytes, "$.printerBuffer.recommendedMaxBurstBytes") }
            it.recommendedPauseAfterBurstMs?.let { pause -> number(pause, limits.maxPauseMs, "$.printerBuffer.recommendedPauseAfterBurstMs", 0) }
        }
        strategies(p.statusQuery.supported, p.statusQuery.strategies, p.statusQuery.preferredStrategy,
            StrategyPurpose.STATUS_QUERY, protocol.dialectId, "$.statusQuery")
        if (identifier(protocol.dialectId, "$.protocol.dialectId") && dialect == null)
            error("PRF106", "$.protocol.dialectId", "Unregistered protocol dialect")
        strategy(protocol.initializationStrategy, StrategyPurpose.INITIALIZATION, protocol.dialectId, "$.protocol.initializationStrategy")
        entries(p.quirks, "$.quirks") { id, path ->
            if (identifier(id, path)) {
                val quirk = registry.quirk(id)
                if (quirk == null) error("PRF106", path, "Unregistered quirk")
                else if (protocol.dialectId !in quirk.dialectIds) error("PRF107", path, "Quirk incompatible with protocol dialect")
            }
        }
        dependency(p.nativeText.supported || p.raster.supported, "$")
        return diagnostics
    }

    private fun nativeText(n: NativeTextCapability, dialectId: String, dialect: DialectDefinition?) {
        dependency(if (n.supported) n.fonts.isNotEmpty() else n.fonts.isEmpty(), "$.nativeText.fonts")
        dependency(if (n.supported) n.codePages.isNotEmpty() else n.codePages.isEmpty(), "$.nativeText.codePages")
        if (bounded(n.fonts, "$.nativeText.fonts")) {
            val seen = mutableSetOf<String>()
            n.fonts.forEachIndexed { i, f ->
                val path = "$.nativeText.fonts[$i]"
                identifier(f.id, "$path.id")
                if (f.id.length <= 128 && !seen.add(f.id)) error("PRF105", "$path.id", "Duplicate font ID")
                number(f.cellWidthDots, limits.maxFontDimensionDots, "$path.cellWidthDots")
                number(f.cellHeightDots, limits.maxFontDimensionDots, "$path.cellHeightDots")
                f.lineAdvanceDots?.let { number(it, limits.maxFontDimensionDots, "$path.lineAdvanceDots") }
                val definition = f.selector?.let { selector(it, StrategyPurpose.FONT_SELECTION, dialectId, "$path.selector") }
                if (f.selector == null) dependencyDefault(dialect?.defaultFontId == f.id, "$path.selector")
                if (f.lineAdvanceDots == null) dependencyDefault(
                    if (f.selector == null) dialect?.defaultFontLineAdvanceFromCellHeight == true
                    else definition?.fontLineAdvanceFromCellHeight == true, "$path.lineAdvanceDots")
                scaledGeometry(f.cellWidthDots, n.styles.widthScales, "$path.cellWidthDots")
                scaledGeometry(f.cellHeightDots, n.styles.heightScales, "$path.cellHeightDots")
                scaledGeometry(f.lineAdvanceDots ?: f.cellHeightDots, n.styles.heightScales, "$path.lineAdvanceDots")
            }
        }
        scales(n.styles.widthScales, n.supported, dialect?.maxWidthScale, "$.nativeText.styles.widthScales")
        scales(n.styles.heightScales, n.supported, dialect?.maxHeightScale, "$.nativeText.styles.heightScales")
        if (bounded(n.codePages, "$.nativeText.codePages")) {
            val seen = mutableSetOf<String>()
            n.codePages.forEachIndexed { i, c ->
                val path = "$.nativeText.codePages[$i]"
                identifier(c.id, "$path.id")
                if (c.id.length <= 128 && !seen.add(c.id)) error("PRF105", "$path.id", "Duplicate code-page ID")
                if (identifier(c.repertoire, "$path.repertoire") && !registry.knowsRepertoire(c.repertoire))
                    error("PRF106", "$path.repertoire", "Unregistered repertoire")
                if (c.selector == null) dependencyDefault(dialect?.defaultRepertoire == c.repertoire, "$path.selector")
                else selector(c.selector, StrategyPurpose.CODE_PAGE_SELECTION, dialectId, "$path.selector")
            }
        }
    }
    private fun dependencyDefault(valid: Boolean, path: String) {
        if (!valid) error("PRF108", path, "No unambiguous registered default")
    }
    private fun scales(values: List<Int>, supported: Boolean, dialectMaximum: Int?, path: String) {
        dependency(!supported || values.isNotEmpty(), path)
        entries(values, path) { value, itemPath ->
            number(value, limits.maxTextScale, itemPath)
            if (dialectMaximum != null && value > dialectMaximum) error("PRF108", itemPath, "Scale exceeds trusted dialect constraint")
        }
    }
    private fun scaledGeometry(dots: Int, scales: List<Int>, path: String) {
        if (dots <= 0 || scales.size > limits.maxCollectionEntries) return
        if (scales.any { it > 0 && dots.toLong() * it > Int.MAX_VALUE })
            error("PRF120", path, "Scaled native geometry exceeds safe integer range")
    }
    private fun raster(r: RasterCapability, width: Int, dialectId: String) {
        strategies(r.supported, r.strategies, r.preferredStrategy, StrategyPurpose.RASTER, dialectId, "$.raster")
        dependency(if (r.supported) r.maxWidthDots != null else r.maxWidthDots == null, "$.raster.maxWidthDots")
        dependency(if (r.supported) r.bandHeightDots != null else r.bandHeightDots == null, "$.raster.bandHeightDots")
        r.maxWidthDots?.let {
            number(it, limits.maxPrintableWidthDots, "$.raster.maxWidthDots")
            dependency(it <= width, "$.raster.maxWidthDots")
        }
        r.bandHeightDots?.let { b ->
            number(b.min, limits.maxRasterBandHeightDots, "$.raster.bandHeightDots.min")
            number(b.preferred, limits.maxRasterBandHeightDots, "$.raster.bandHeightDots.preferred")
            number(b.max, limits.maxRasterBandHeightDots, "$.raster.bandHeightDots.max")
            dependency(b.min <= b.preferred && b.preferred <= b.max, "$.raster.bandHeightDots.preferred")
            // Int dimensions widened before rounding/multiplication; the product fits Long.
            val w = r.maxWidthDots
            if (w != null && w > 0 && b.max > 0 && ((w.toLong() + 7) / 8) * b.max > limits.maxRasterBandBytes)
                error("PRF120", "$.raster.bandHeightDots.max", "Raster band exceeds trusted byte budget")
        }
    }
    private fun qr(q: NativeQrCapability, dialectId: String) {
        strategies(q.supported, q.strategies, q.preferredStrategy, StrategyPurpose.QR, dialectId, "$.nativeQr")
        dependency(if (q.supported) q.models.isNotEmpty() else q.models.isEmpty(), "$.nativeQr.models")
        dependency(if (q.supported) q.moduleSize != null else q.moduleSize == null, "$.nativeQr.moduleSize")
        dependency(if (q.supported) q.errorCorrection.isNotEmpty() else q.errorCorrection.isEmpty(), "$.nativeQr.errorCorrection")
        entries(q.models, "$.nativeQr.models") { value, path ->
            if (identifier(value, path) && !registry.knowsQrModel(value)) error("PRF106", path, "Unregistered QR model")
        }
        q.moduleSize?.let {
            number(it.min, limits.maxQrModuleSize, "$.nativeQr.moduleSize.min")
            number(it.max, limits.maxQrModuleSize, "$.nativeQr.moduleSize.max")
            dependency(it.min <= it.max, "$.nativeQr.moduleSize.max")
        }
        entries(q.errorCorrection, "$.nativeQr.errorCorrection") { value, path ->
            if (value !in listOf("L", "M", "Q", "H")) error("PRF106", path, "Unsupported v1 error correction")
        }
    }
    private fun barcode(b: NativeBarcodeCapability, dialectId: String) {
        strategies(b.supported, b.strategies, b.preferredStrategy, StrategyPurpose.BARCODE, dialectId, "$.nativeBarcode")
        dependency(if (b.supported) b.symbologies.isNotEmpty() else b.symbologies.isEmpty(), "$.nativeBarcode.symbologies")
        entries(b.symbologies, "$.nativeBarcode.symbologies") { value, path ->
            if (identifier(value, path) && !registry.knowsBarcodeSymbology(value)) error("PRF106", path, "Unregistered barcode symbology")
        }
    }
    private fun cutter(c: CutterCapability, dialectId: String) {
        dependency(if (c.supported) c.modes.isNotEmpty() else c.modes.isEmpty(), "$.cutter.modes")
        dependency(if (c.supported) c.strategy != null else c.strategy == null, "$.cutter.strategy")
        entries(c.modes, "$.cutter.modes") { value, path ->
            if (value != "full" && value != "partial") error("PRF106", path, "Unsupported v1 cut mode")
        }
        c.strategy?.let { strategy(it, StrategyPurpose.CUT, dialectId, "$.cutter.strategy") }
    }
}
