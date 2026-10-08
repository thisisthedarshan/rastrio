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

/** Portable v1 byte boundary. Successful reads always pass the existing semantic validator. */
class TcfgCodec(
    registry: TrustedProfileRegistry,
    private val validationLimits: PrinterProfileValidationLimits,
    private val resourcePolicy: TcfgResourcePolicy = TcfgResourcePolicy(),
) {
    private val validator = PrinterProfileValidator(registry, validationLimits)

    fun decode(bytes: ByteArray): ProfileValidationResult<ValidatedPrinterProfile> = result {
        if (bytes.size > resourcePolicy.maxInputBytes) tcfgFail("PRF138", "$", "Trusted input byte limit exceeded")
        val source = try { bytes.decodeToString(throwOnInvalidSequence = true) }
            catch (_: CharacterCodingException) { tcfgFail("PRF130", "$", "Invalid UTF-8") }
        if (source.startsWith('\uFEFF')) tcfgFail("PRF130", "$", "UTF-8 BOM is not accepted")
        val root = TcfgJsonParser(source, resourcePolicy, validationLimits.maxCollectionEntries,
            minOf(resourcePolicy.maxStringBytes, validationLimits.maxStringBytes)).parse()
        validator.validate(decodeProfile(root))
    }

    /** Emits compact stable JSON, preserving domain collection order except ascending text scales. */
    fun encode(value: ValidatedPrinterProfile): ProfileValidationResult<ByteArray> = result {
        // Sorting allocates a copy: bound it even for values accepted under another trusted policy.
        val styles = value.profile.nativeText.styles
        for (scales in listOf(styles.widthScales, styles.heightScales)) {
            if (scales.size > validationLimits.maxCollectionEntries ||
                scales.size.toLong() * 2 + 1 > resourcePolicy.maxInputBytes)
                tcfgFail("PRF138", "$.nativeText.styles", "Trusted canonical sorting limit exceeded")
        }
        val writer = TcfgJsonWriter(resourcePolicy.maxInputBytes)
        writer.writeProfile(value.profile)
        val bytes = writer.toString().encodeToByteArray(throwOnInvalidSequence = true)
        if (bytes.size > resourcePolicy.maxInputBytes) tcfgFail("PRF138", "$", "Trusted output byte limit exceeded")
        ProfileValidationResult.Success(bytes)
    }

    private fun <T> result(block: () -> ProfileValidationResult<T>): ProfileValidationResult<T> =
        try { block() } catch (failure: TcfgFailure) { ProfileValidationResult.Failure(profileListOf(failure.diagnostic)) }

    private fun decodeProfile(value: TcfgJson): PrinterProfile {
        val o = fields(value, "$", "format", "schemaVersion", "profileId", "profileRevision", "display", "geometry",
            "nativeText", "raster", "nativeQr", "nativeBarcode", "cutter", "statusQuery", "protocol", "quirks",
            optional = setOf("printerBuffer"))
        if (o.string("format") != "rastrio-tcfg") tcfgFail("PRF137", "$.format", "Unsupported format")
        if (o.int("schemaVersion") != 1) tcfgFail("PRF137", "$.schemaVersion", "Unsupported schema version")
        val display = o.obj("display", "name", optional = setOf("manufacturer", "model"))
        val geometry = o.obj("geometry", "horizontalDpi", "verticalDpi", "printableWidthDots")
        val native = o.obj("nativeText", "supported", "fonts", "styles", "codePages")
        val styles = native.obj("styles", "bold", "underline", "widthScales", "heightScales")
        val fonts = native.list("fonts") { v, path ->
            val f = fields(v, path, "id", "cellWidthDots", "cellHeightDots", optional = setOf("lineAdvanceDots", "selector"))
            NativeFontCapability(f.string("id"), f.int("cellWidthDots"), f.int("cellHeightDots"), f.optionalInt("lineAdvanceDots"), f.selector())
        }
        val pages = native.list("codePages") { v, path ->
            val p = fields(v, path, "id", "repertoire", optional = setOf("selector"))
            CodePageCapability(p.string("id"), p.string("repertoire"), p.selector())
        }
        val raster = o.obj("raster", "supported", "strategies", optional = setOf("preferredStrategy", "maxWidthDots", "bandHeightDots"))
        val band = raster.optionalObj("bandHeightDots", "min", "preferred", "max")?.let { RasterBandHeight(it.int("min"), it.int("preferred"), it.int("max")) }
        val qr = o.obj("nativeQr", "supported", "strategies", optional = setOf("preferredStrategy", "models", "moduleSize", "errorCorrection"))
        val module = qr.optionalObj("moduleSize", "min", "max")?.let { QrModuleSize(it.int("min"), it.int("max")) }
        val barcode = o.obj("nativeBarcode", "supported", "strategies", optional = setOf("preferredStrategy", "symbologies"))
        val cutter = o.obj("cutter", "supported", "modes", optional = setOf("strategy"))
        val buffer = o.optionalObj("printerBuffer", optional = setOf("recommendedMaxBurstBytes", "recommendedPauseAfterBurstMs"))
        val status = o.obj("statusQuery", "supported", "strategies", optional = setOf("preferredStrategy"))
        val protocol = o.obj("protocol", "family", "dialectId", "initializationStrategy")
        if (protocol.string("family") != "escpos") tcfgFail("PRF137", "$.protocol.family", "Unsupported v1 protocol family")
        // Conditional presence is schema structure. Capability consistency remains Phase 5A's responsibility.
        if (raster.bool("supported")) raster.requireFields("preferredStrategy", "maxWidthDots", "bandHeightDots")
        if (qr.bool("supported")) qr.requireFields("preferredStrategy", "models", "moduleSize", "errorCorrection")
        if (barcode.bool("supported")) barcode.requireFields("preferredStrategy", "symbologies")
        if (cutter.bool("supported")) cutter.requireFields("strategy")
        if (status.bool("supported")) status.requireFields("preferredStrategy")
        return PrinterProfile(
            profileId = o.string("profileId"), profileRevision = o.int("profileRevision"),
            display = ProfileDisplay(display.string("name"), display.optionalString("manufacturer"), display.optionalString("model")),
            geometry = PrinterGeometry(geometry.int("horizontalDpi"), geometry.int("verticalDpi"), geometry.int("printableWidthDots")),
            nativeText = NativeTextCapability(native.bool("supported"), fonts,
                NativeStyleCapability(styles.bool("bold"), styles.bool("underline"), styles.ints("widthScales"), styles.ints("heightScales")), pages),
            raster = RasterCapability(raster.bool("supported"), raster.strings("strategies"), raster.optionalString("preferredStrategy"), raster.optionalInt("maxWidthDots"), band),
            nativeQr = NativeQrCapability(qr.bool("supported"), qr.strings("strategies"), qr.optionalString("preferredStrategy"),
                qr.optionalStrings("models"), module, qr.optionalStrings("errorCorrection")),
            nativeBarcode = NativeBarcodeCapability(barcode.bool("supported"), barcode.strings("strategies"), barcode.optionalString("preferredStrategy"), barcode.optionalStrings("symbologies")),
            cutter = CutterCapability(cutter.bool("supported"), cutter.strings("modes"), cutter.optionalString("strategy")),
            printerBuffer = buffer?.let { PrinterBufferGuidance(it.optionalInt("recommendedMaxBurstBytes"), it.optionalInt("recommendedPauseAfterBurstMs")) },
            statusQuery = StatusQueryCapability(status.bool("supported"), status.strings("strategies"), status.optionalString("preferredStrategy")),
            protocol = EscPosProtocolProfile(protocol.string("dialectId"), protocol.string("initializationStrategy")),
            quirks = o.strings("quirks"),
        )
    }
}

private fun fields(value: TcfgJson, path: String, vararg required: String, optional: Set<String> = emptySet()): TcfgFields {
    val map = (value as? TcfgJson.Obj)?.fields ?: tcfgFail("PRF135", path, "Expected object")
    if (map.keys.any { it !in required && it !in optional }) tcfgFail("PRF133", path, "Unknown v1 property")
    val result = TcfgFields(map, path)
    result.requireFields(*required)
    return result
}

/** All path segments passed here are schema-owned constants, never attacker-supplied property names. */
private class TcfgFields(private val map: Map<String, TcfgJson>, private val path: String) {
    fun requireFields(vararg names: String) {
        for (name in names) if (name !in map) tcfgFail("PRF134", "$path.$name", "Missing required property")
    }
    fun string(name: String): String = stringValue(map.getValue(name), "$path.$name")
    fun optionalString(name: String): String? = map[name]?.let { stringValue(it, "$path.$name") }
    fun int(name: String): Int = integerValue(map.getValue(name), "$path.$name")
    fun optionalInt(name: String): Int? = map[name]?.let { integerValue(it, "$path.$name") }
    fun bool(name: String): Boolean = (map[name] as? TcfgJson.Bool)?.value ?: tcfgFail("PRF135", "$path.$name", "Expected boolean")
    fun obj(name: String, vararg required: String, optional: Set<String> = emptySet()) = fields(map.getValue(name), "$path.$name", *required, optional = optional)
    fun optionalObj(name: String, vararg required: String, optional: Set<String> = emptySet()) = map[name]?.let { fields(it, "$path.$name", *required, optional = optional) }
    fun selector(): RegisteredSelector? = optionalObj("selector", "strategy", "parameter")?.let { RegisteredSelector(it.string("strategy"), it.int("parameter")) }
    fun <T> list(name: String, read: (TcfgJson, String) -> T): ProfileList<T> {
        val values = (map[name] as? TcfgJson.Arr)?.values ?: tcfgFail("PRF135", "$path.$name", "Expected array")
        return ProfileList(values.mapIndexed { i, v -> read(v, "$path.$name[$i]") })
    }
    fun strings(name: String): ProfileList<String> = list(name, ::stringValue)
    fun ints(name: String): ProfileList<Int> = list(name, ::integerValue)
    fun optionalStrings(name: String): ProfileList<String> = if (name in map) strings(name) else profileListOf()
}
private fun stringValue(value: TcfgJson, path: String): String = (value as? TcfgJson.Str)?.value ?: tcfgFail("PRF135", path, "Expected string")
private fun integerValue(value: TcfgJson, path: String): Int {
    val raw = (value as? TcfgJson.Num)?.raw ?: tcfgFail("PRF135", path, "Expected integer token")
    if (raw.any { it == '.' || it == 'e' || it == 'E' }) tcfgFail("PRF136", path, "Expected integer representation")
    return raw.toIntOrNull() ?: tcfgFail("PRF136", path, "Integer outside domain range")
}

private fun TcfgJsonWriter.writeProfile(p: PrinterProfile) = obj {
    field("format", "rastrio-tcfg"); field("schemaVersion", 1)
    field("profileId", p.profileId); field("profileRevision", p.profileRevision)
    field("display") { obj { field("name", p.display.name); p.display.manufacturer?.let { field("manufacturer", it) }; p.display.model?.let { field("model", it) } } }
    field("geometry") { obj { field("horizontalDpi", p.geometry.horizontalDpi); field("verticalDpi", p.geometry.verticalDpi); field("printableWidthDots", p.geometry.printableWidthDots) } }
    field("nativeText") { obj {
        val n = p.nativeText
        field("supported", n.supported)
        field("fonts") { arr(n.fonts) { f -> obj {
            field("id", f.id); field("cellWidthDots", f.cellWidthDots); field("cellHeightDots", f.cellHeightDots)
            f.lineAdvanceDots?.let { field("lineAdvanceDots", it) }; f.selector?.let { writeSelector(it) }
        } } }
        field("styles") { obj {
            field("bold", n.styles.bold); field("underline", n.styles.underline)
            field("widthScales") { arr(n.styles.widthScales.sorted()) { integer(it) } }
            field("heightScales") { arr(n.styles.heightScales.sorted()) { integer(it) } }
        } }
        field("codePages") { arr(n.codePages) { c -> obj { field("id", c.id); field("repertoire", c.repertoire); c.selector?.let { writeSelector(it) } } } }
    } }
    field("raster") { obj {
        val r = p.raster
        writeStrategies(r.supported, r.strategies, r.preferredStrategy)
        r.maxWidthDots?.let { field("maxWidthDots", it) }
        r.bandHeightDots?.let { b -> field("bandHeightDots") { obj { field("min", b.min); field("preferred", b.preferred); field("max", b.max) } } }
    } }
    field("nativeQr") { obj {
        val q = p.nativeQr
        writeStrategies(q.supported, q.strategies, q.preferredStrategy)
        if (q.models.isNotEmpty()) field("models") { arr(q.models) { string(it) } }
        q.moduleSize?.let { m -> field("moduleSize") { obj { field("min", m.min); field("max", m.max) } } }
        if (q.errorCorrection.isNotEmpty()) field("errorCorrection") { arr(q.errorCorrection) { string(it) } }
    } }
    field("nativeBarcode") { obj {
        val b = p.nativeBarcode
        writeStrategies(b.supported, b.strategies, b.preferredStrategy)
        if (b.symbologies.isNotEmpty()) field("symbologies") { arr(b.symbologies) { string(it) } }
    } }
    field("cutter") { obj { field("supported", p.cutter.supported); field("modes") { arr(p.cutter.modes) { string(it) } }; p.cutter.strategy?.let { field("strategy", it) } } }
    p.printerBuffer?.let { b -> field("printerBuffer") { obj {
        b.recommendedMaxBurstBytes?.let { field("recommendedMaxBurstBytes", it) }
        b.recommendedPauseAfterBurstMs?.let { field("recommendedPauseAfterBurstMs", it) }
    } } }
    field("statusQuery") { obj { writeStrategies(p.statusQuery.supported, p.statusQuery.strategies, p.statusQuery.preferredStrategy) } }
    field("protocol") { obj { when (val protocol = p.protocol) {
        is EscPosProtocolProfile -> { field("family", protocol.family); field("dialectId", protocol.dialectId); field("initializationStrategy", protocol.initializationStrategy) }
    } } }
    field("quirks") { arr(p.quirks) { string(it) } }
}
private fun TcfgJsonWriter.writeSelector(selector: RegisteredSelector) {
    field("selector") { obj { field("strategy", selector.strategy); field("parameter", selector.parameter) } }
}
private fun TcfgJsonWriter.writeStrategies(supported: Boolean, strategies: List<String>, preferred: String?) {
    field("supported", supported); field("strategies") { arr(strategies) { string(it) } }
    preferred?.let { field("preferredStrategy", it) }
}
