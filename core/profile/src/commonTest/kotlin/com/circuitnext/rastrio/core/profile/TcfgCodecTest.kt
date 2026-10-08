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

class TcfgCodecTest {
    private val limits = PrinterProfileValidationLimits(600, 1024, 256, 8, 256, 32768, 16, 65536, 1000, 16)
    private var lookups = 0
    private val registry = object : TrustedProfileRegistry {
        override fun dialect(id: String): DialectDefinition? {
            lookups++
            return when (id) {
                "test.dialect" -> DialectDefinition("default-font", "test.repertoire", true)
                "test.other" -> DialectDefinition()
                else -> null
            }
        }
        override fun strategy(id: String): StrategyDefinition? {
            lookups++
            val purpose = when (id) {
                "test.init" -> StrategyPurpose.INITIALIZATION
                "test.font" -> StrategyPurpose.FONT_SELECTION
                "test.page" -> StrategyPurpose.CODE_PAGE_SELECTION
                "test.raster" -> StrategyPurpose.RASTER
                "test.qr" -> StrategyPurpose.QR
                "test.barcode" -> StrategyPurpose.BARCODE
                "test.cut" -> StrategyPurpose.CUT
                "test.status" -> StrategyPurpose.STATUS_QUERY
                else -> return null
            }
            return StrategyDefinition(purpose, profileListOf("test.dialect"),
                if (purpose in listOf(StrategyPurpose.FONT_SELECTION, StrategyPurpose.CODE_PAGE_SELECTION)) 0..2 else null, true)
        }
        override fun knowsRepertoire(id: String) = id == "test.repertoire"
        override fun knowsQrModel(id: String) = id == "model2"
        override fun knowsBarcodeSymbology(id: String) = id == "code128"
        override fun quirk(id: String) = if (id == "test.quirk") QuirkDefinition(profileListOf("test.dialect")) else null
    }
    private fun codec(policy: TcfgResourcePolicy = TcfgResourcePolicy(), semantic: PrinterProfileValidationLimits = limits) =
        TcfgCodec(registry, semantic, policy)
    private val json = """{
      "format":"rastrio-tcfg","schemaVersion":1,"profileId":"test.profile","profileRevision":1,
      "display":{"name":"Printer 印刷 😀","manufacturer":"Test","model":"Portable"},
      "geometry":{"horizontalDpi":203,"verticalDpi":300,"printableWidthDots":384},
      "nativeText":{"supported":true,"fonts":[{"id":"default-font","cellWidthDots":12,"cellHeightDots":24}],
        "styles":{"bold":true,"underline":false,"widthScales":[1,2],"heightScales":[1,2]},
        "codePages":[{"id":"page","repertoire":"test.repertoire"}]},
      "raster":{"supported":true,"strategies":["test.raster"],"preferredStrategy":"test.raster","maxWidthDots":384,
        "bandHeightDots":{"min":1,"preferred":64,"max":128}},
      "nativeQr":{"supported":true,"strategies":["test.qr"],"preferredStrategy":"test.qr","models":["model2"],
        "moduleSize":{"min":1,"max":8},"errorCorrection":["L","M","Q","H"]},
      "nativeBarcode":{"supported":true,"strategies":["test.barcode"],"preferredStrategy":"test.barcode","symbologies":["code128"]},
      "cutter":{"supported":true,"modes":["full","partial"],"strategy":"test.cut"},
      "printerBuffer":{"recommendedMaxBurstBytes":4096,"recommendedPauseAfterBurstMs":0},
      "statusQuery":{"supported":true,"strategies":["test.status"],"preferredStrategy":"test.status"},
      "protocol":{"family":"escpos","dialectId":"test.dialect","initializationStrategy":"test.init"},"quirks":["test.quirk"]
    }""".trimIndent()
    private fun read(text: String = json, c: TcfgCodec = codec()) = read(text.encodeToByteArray(), c)
    private fun read(bytes: ByteArray, c: TcfgCodec = codec()) =
        assertIs<ProfileValidationResult.Success<ValidatedPrinterProfile>>(c.decode(bytes)).value
    private fun write(p: ValidatedPrinterProfile, c: TcfgCodec = codec()) =
        assertIs<ProfileValidationResult.Success<ByteArray>>(c.encode(p)).value
    private fun reject(text: String, code: String, path: String? = null, c: TcfgCodec = codec()) = reject(text.encodeToByteArray(), code, path, c)
    private fun reject(bytes: ByteArray, code: String, path: String? = null, c: TcfgCodec = codec()) {
        val result = assertIs<ProfileValidationResult.Failure>(c.decode(bytes))
        assertTrue(result.diagnostics.any { it.code == code && (path == null || it.path == path) }, result.toString())
        assertEquals(result, c.decode(bytes))
    }

    @Test fun completeProfileRoundTripsCanonically() {
        val p = read()
        assertEquals(300, p.profile.geometry.verticalDpi)
        val bytes = write(p)
        assertEquals(p.profile, read(bytes).profile)
        assertTrue(bytes.contentEquals(write(p)))
        assertTrue(bytes.contentEquals(write(read(bytes))))
        val text = bytes.decodeToString(throwOnInvalidSequence = true)
        assertFalse(text.startsWith('\uFEFF'))
        assertTrue(text.indexOf("\"display\"") < text.indexOf("\"geometry\""))
        assertTrue(text.indexOf("\"cutter\"") < text.indexOf("\"printerBuffer\""))
        assertTrue(text.contains("印刷 😀"))
    }

    @Test fun nativeRasterOptionalAndSelectorVariants() {
        val full = read().profile
        val candidates = listOf(
            full.copy(raster = RasterCapability(false), nativeQr = NativeQrCapability(false), nativeBarcode = NativeBarcodeCapability(false),
                cutter = CutterCapability(false), printerBuffer = null, statusQuery = StatusQueryCapability(false), quirks = profileListOf()),
            full.copy(nativeText = NativeTextCapability(false)),
            full.copy(printerBuffer = PrinterBufferGuidance()),
            full.copy(nativeText = full.nativeText.copy(fonts = profileListOf(full.nativeText.fonts[0].copy(
                lineAdvanceDots = 24, selector = RegisteredSelector("test.font", 2))),
                codePages = profileListOf(full.nativeText.codePages[0].copy(selector = RegisteredSelector("test.page", 1))))),
        )
        for (candidate in candidates) {
            val validated = assertIs<ProfileValidationResult.Success<ValidatedPrinterProfile>>(PrinterProfileValidator(registry, limits).validate(candidate)).value
            assertEquals(candidate, read(write(validated)).profile)
        }
        val text = write(read(write(assertIs<ProfileValidationResult.Success<ValidatedPrinterProfile>>(
            PrinterProfileValidator(registry, limits).validate(candidates[0])).value))).decodeToString()
        assertFalse(text.contains("printerBuffer"))
        assertTrue(text.contains("\"raster\":{\"supported\":false,\"strategies\":[]}"))
    }

    @Test fun scaleSortingPreservesOtherCollectionOrder() {
        val input = json.replace("[1,2]", "[2,1]").replace("[\"full\",\"partial\"]", "[\"partial\",\"full\"]")
        val p = read(input)
        val canonical = read(write(p)).profile
        assertEquals(listOf(1,2), canonical.nativeText.styles.widthScales)
        assertEquals(listOf("partial", "full"), canonical.cutter.modes)
        assertTrue(write(read(write(p))).contentEquals(write(p)))
    }

    @Test fun strictSyntaxMatrix() {
        val invalid = listOf("", " \t\r\n", "{", "[", "{\"x\":[}", "/*comment*/$json", "$json//comment", "{'x':1}", "{x:1}",
            json.dropLast(1) + ",}", json.replace("[1,2]", "[1,2,]"), "$json true",
            json.replace("203", "NaN"), json.replace("203", "Infinity"), json.replace("203", "-Infinity"),
            json.replace("203", "+203"), json.replace("203", "0203"), json.replace("203", "1e"),
            json.replace("203", "1."), json.replace("203", "-"), json.replace("Printer", "\\x"),
            json.replace("Printer", "\\uGGGG"), json.replace("Printer", "\\u12"), json.replace("Printer", "a\n"),
            json.replace("Printer", "\\uD800"), json.replace("Printer", "\\uDC00"))
        for (value in invalid) reject(value, "PRF131")
    }

    @Test fun duplicatesAreRejectedBeforeValueParsingOrOverwrite() {
        reject(json.replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":2"), "PRF132")
        reject(json.replace("\"horizontalDpi\":203", "\"horizontalDpi\":203,\"horizontalDpi\":garbage"), "PRF132")
        reject(json.replace("\"name\":", "\"na\\u006de\":\"first\",\"name\":"), "PRF132")
    }

    @Test fun utf8AndBomPolicy() {
        for (bytes in listOf(byteArrayOf(0x80.toByte()), byteArrayOf(0xC0.toByte(), 0xAF.toByte()),
            byteArrayOf(0xE2.toByte(), 0x28, 0xA1.toByte()), byteArrayOf(0xF0.toByte(), 0x9F.toByte()),
            byteArrayOf(0xED.toByte(), 0xA0.toByte(), 0x80.toByte()), byteArrayOf(0xF4.toByte(), 0x90.toByte(), 0x80.toByte(), 0x80.toByte())))
            reject(bytes, "PRF130")
        reject("\uFEFF$json", "PRF130")
        reject("$json\uFEFF", "PRF131")
        read(json.replace("Printer 印刷 😀", "ASCII"))
        read(json.replace("Printer", "\\uD83D\\uDE00"))
        read(json.replace("Printer", "in-string \uFEFF"))
    }

    @Test fun schemaTypesVersionAndFamily() {
        for ((from, to, code, path) in listOf(
            listOf("rastrio-tcfg", "other", "PRF137", "$.format"),
            listOf("\"schemaVersion\":1", "\"schemaVersion\":2", "PRF137", "$.schemaVersion"),
            listOf("\"schemaVersion\":1,", "", "PRF134", "$.schemaVersion"),
            listOf("\"format\":\"rastrio-tcfg\",", "", "PRF134", "$.format"),
            listOf("\"name\":\"Printer 印刷 😀\"", "\"name\":null", "PRF135", "$.display.name"),
            listOf("\"model\":\"Portable\"", "\"model\":null", "PRF135", "$.display.model"),
            listOf("\"family\":\"escpos\"", "\"family\":\"future-protocol\"", "PRF137", "$.protocol.family"),
            listOf("\"horizontalDpi\":203", "\"horizontalDpi\":\"203\"", "PRF135", "$.geometry.horizontalDpi"),
            listOf("\"horizontalDpi\":203", "\"horizontalDpi\":true", "PRF135", "$.geometry.horizontalDpi"),
            listOf("\"fonts\":[", "\"fonts\":{\"bad\":[", "PRF131", "$"))) {
            val before = lookups
            reject(json.replace(from, to), code, if (code == "PRF131") null else path)
            assertEquals(before, lookups)
        }
        reject("[]", "PRF135", "$")
        reject(json.replace("\"display\":{", "\"display\":[{"), "PRF131")
    }

    @Test fun integerTokensAreExactAndBounded() {
        for (token in listOf("203.0", "2.03e2", "203e0", "2147483648", "-2147483649"))
            reject(json.replace("203", token), "PRF136", "$.geometry.horizontalDpi")
        reject(json.replace("203", "9".repeat(65)), "PRF138")
        reject(json.replace("203", "-2147483648"), "PRF103", "$.geometry.horizontalDpi")
    }

    @Test fun unknownFieldsAtEveryObjectLevelAndSafeDiagnostics() {
        val markers = listOf("\"format\":", "\"name\":", "\"horizontalDpi\":", "\"supported\":true,\"fonts\":",
            "\"id\":\"default-font\"", "\"bold\":", "\"id\":\"page\"", "\"supported\":true,\"strategies\":[\"test.raster\"]",
            "\"min\":1,\"preferred\":", "\"supported\":true,\"strategies\":[\"test.qr\"]", "\"min\":1,\"max\":8",
            "\"supported\":true,\"strategies\":[\"test.barcode\"]", "\"supported\":true,\"modes\":",
            "\"recommendedMaxBurstBytes\":", "\"supported\":true,\"strategies\":[\"test.status\"]", "\"family\":")
        for (marker in markers) reject(json.replace(marker, "\"secret-key\":\"secret-value\",$marker"), "PRF133")
        val selectors = json.replace("\"cellHeightDots\":24", "\"cellHeightDots\":24,\"selector\":{\"strategy\":\"test.font\",\"parameter\":0,\"script\":\"secret\"}")
        reject(selectors, "PRF133", "$.nativeText.fonts[0].selector")
        reject(json.replace("\"repertoire\":\"test.repertoire\"", "\"repertoire\":\"test.repertoire\",\"selector\":{\"strategy\":\"test.page\",\"parameter\":0,\"script\":\"secret\"}"), "PRF133")
        val errors = assertIs<ProfileValidationResult.Failure>(codec().decode(json.replace("\"format\":", "\"secret-key\":\"secret-value\",\"format\":").encodeToByteArray()))
        assertFalse(errors.toString().contains("secret"))
    }

    @Test fun executableInstanceAndInheritanceFieldsFailClosed() {
        for (key in listOf("rawCommands", "initializeHex", "commandTemplate", "script", "className", "remoteStrategyUrl", "transport",
            "bluetoothAddress", "usbDevice", "writeChunkSize", "writeTimeout", "instanceId", "lastUsed", "overrides", "extends", "include")) {
            reject(json.replace("\"format\":", "\"$key\":\"private\",\"format\":"), "PRF133", "$")
            reject(json.replace("\"family\":", "\"$key\":\"private\",\"family\":"), "PRF133", "$.protocol")
        }
    }

    @Test fun parsingBudgetsApplyBeforeRegistryAndDuringParsing() {
        val before = lookups
        reject(json, "PRF138", c = codec(TcfgResourcePolicy(maxInputBytes = 16)))
        reject("[[[]]]", "PRF138", c = codec(TcfgResourcePolicy(maxJsonDepth = 2)))
        reject("{\"a\":\"éé\"}", "PRF138", c = codec(TcfgResourcePolicy(maxStringBytes = 3)))
        reject("{\"éé\":0}", "PRF138", c = codec(TcfgResourcePolicy(maxStringBytes = 3)))
        reject("[0,0,0]", "PRF138", c = codec(TcfgResourcePolicy(maxJsonTokens = 3)))
        reject("{\"a\":0,\"b\":0}", "PRF138", c = codec(TcfgResourcePolicy(maxJsonTokens = 4)))
        reject("{\"a\":0,\"b\":0}", "PRF138", c = codec(TcfgResourcePolicy(maxObjectProperties = 1)))
        reject(json.replace("[1,2]", "[1,2,3]"), "PRF138", c = codec(semantic = limits.copy(maxCollectionEntries = 2)))
        reject("[" + List(17) { "0" }.joinToString(",") + "]", "PRF138")
        assertEquals(before, lookups)
        reject(json.replace("Printer", "long"), "PRF138", c = codec(semantic = limits.copy(maxStringBytes = 2)))
    }

    @Test fun semanticValidationCannotBeBypassed() {
        for ((from, to, code, path) in listOf(
            listOf("\"horizontalDpi\":203", "\"horizontalDpi\":0", "PRF103", "$.geometry.horizontalDpi"),
            listOf("\"printableWidthDots\":384", "\"printableWidthDots\":0", "PRF103", "$.geometry.printableWidthDots"),
            listOf("\"cellWidthDots\":12", "\"cellWidthDots\":0", "PRF103", "$.nativeText.fonts[0].cellWidthDots"),
            listOf("test.init", "unknown", "PRF106", "$.protocol.initializationStrategy"),
            listOf("test.dialect", "test.other", "PRF107", "$.protocol.initializationStrategy"),
            listOf("\"preferred\":64", "\"preferred\":0", "PRF103", "$.raster.bandHeightDots.preferred"),
            listOf("\"min\":1,\"max\":8", "\"min\":9,\"max\":8", "PRF104", "$.nativeQr.moduleSize.max"),
            listOf("\"modes\":[\"full\",\"partial\"]", "\"modes\":[]", "PRF104", "$.cutter.modes"),
            listOf("\"recommendedMaxBurstBytes\":4096", "\"recommendedMaxBurstBytes\":0", "PRF103", "$.printerBuffer.recommendedMaxBurstBytes"),
            listOf("test.quirk", "unknown", "PRF106", "$.quirks[0]"),
            listOf("model2", "future", "PRF106", "$.nativeQr.models[0]"),
            listOf("\"L\",\"M\",\"Q\",\"H\"", "\"X\"", "PRF106", "$.nativeQr.errorCorrection[0]"),
            listOf("\"full\",\"partial\"", "\"future\"", "PRF106", "$.cutter.modes[0]")))
            reject(json.replace(from, to), code, path)
        val full = read().profile
        val invalid = full.copy(nativeText = NativeTextCapability(false), raster = RasterCapability(false))
        // Test-local writer construction is intentional: the production reader must still reject this candidate.
        reject(write(ValidatedPrinterProfile(invalid)).decodeToString(), "PRF104", "$")
    }

    @Test fun readerAcceptsReorderedPropertiesAndJsonWhitespace() {
        read(json.replace("\"horizontalDpi\":203,\"verticalDpi\":300", "\"verticalDpi\":300,\"horizontalDpi\":203"))
        read(" \r\n\t$json \n")
        read(json.replace("\"Printer 印刷 😀\"", "\"Printer \\/ \\\" \\\\ \\t \\r \\n\""))
    }

    @Test fun writerHonorsOutputBudget() {
        val result = assertIs<ProfileValidationResult.Failure>(codec(TcfgResourcePolicy(maxInputBytes = 32)).encode(read()))
        assertEquals("PRF138", result.diagnostics.single().code)
    }

    private fun jsonTree() = TcfgJsonParser(json, TcfgResourcePolicy(), 16, 65536).parse() as TcfgJson.Obj
    private fun wire(value: TcfgJson): String = when (value) {
        is TcfgJson.Obj -> value.fields.entries.joinToString(",", "{", "}") { (key, item) -> "\"$key\":" + wire(item) }
        is TcfgJson.Arr -> value.values.joinToString(",", "[", "]", transform = ::wire)
        is TcfgJson.Str -> TcfgJsonWriter(1024 * 1024).apply { string(value.value) }.toString()
        is TcfgJson.Num -> value.raw
        is TcfgJson.Bool -> value.value.toString()
        TcfgJson.Null -> "null"
    }
    private fun replaceAt(value: TcfgJson, segments: List<String>, replacement: (TcfgJson) -> TcfgJson): TcfgJson {
        if (segments.isEmpty()) return replacement(value)
        return when (value) {
            is TcfgJson.Obj -> TcfgJson.Obj(value.fields.mapValues { (key, v) ->
                if (key == segments[0]) replaceAt(v, segments.drop(1), replacement) else v
            })
            is TcfgJson.Arr -> TcfgJson.Arr(value.values.mapIndexed { i, v ->
                if (i.toString() == segments[0]) replaceAt(v, segments.drop(1), replacement) else v
            })
            else -> error("Invalid fixture mutation path")
        }
    }

    @Test fun allPresentPropertiesRejectNullAndWrongTypes() {
        val tree = jsonTree()
        fun walk(value: TcfgJson, segments: List<String>) {
            when (value) {
                is TcfgJson.Obj -> for ((key, v) in value.fields) {
                    val path = segments + key
                    reject(wire(replaceAt(tree, path) { TcfgJson.Null }), "PRF135")
                    val wrong = if (v is TcfgJson.Str) TcfgJson.Bool(false) else TcfgJson.Str("wrong")
                    reject(wire(replaceAt(tree, path) { wrong }), "PRF135")
                    walk(v, path)
                }
                is TcfgJson.Arr -> value.values.forEachIndexed { i, v -> walk(v, segments + i.toString()) }
                else -> Unit
            }
        }
        walk(tree, emptyList())
    }

    @Test fun missingRequiredPropertiesAtEveryObjectLevel() {
        val tree = jsonTree()
        val optional = setOf("display.manufacturer", "display.model", "printerBuffer", "printerBuffer.recommendedMaxBurstBytes",
            "printerBuffer.recommendedPauseAfterBurstMs")
        fun walk(value: TcfgJson, segments: List<String>) {
            when (value) {
                is TcfgJson.Obj -> for ((key, v) in value.fields) {
                    if ((segments + key).joinToString(".") !in optional) {
                        val mutated = replaceAt(tree, segments) { obj -> TcfgJson.Obj((obj as TcfgJson.Obj).fields - key) }
                        reject(wire(mutated), "PRF134")
                    }
                    walk(v, segments + key)
                }
                is TcfgJson.Arr -> value.values.forEachIndexed { i, v -> walk(v, segments + i.toString()) }
                else -> Unit
            }
        }
        walk(tree, emptyList())
    }

    @Test fun optionalSelectorAndLineAdvanceNullsReject() {
        for (key in listOf("lineAdvanceDots", "selector"))
            reject(json.replace("\"cellHeightDots\":24", "\"cellHeightDots\":24,\"$key\":null"), "PRF135")
        reject(json.replace("\"repertoire\":\"test.repertoire\"", "\"repertoire\":\"test.repertoire\",\"selector\":null"), "PRF135")
        for (field in listOf("strategy", "parameter")) {
            val selector = if (field == "strategy") "\"strategy\":null,\"parameter\":0" else "\"strategy\":\"test.font\",\"parameter\":null"
            reject(json.replace("\"cellHeightDots\":24", "\"cellHeightDots\":24,\"selector\":{$selector}"), "PRF135")
        }
    }

    @Test fun exactResourceBoundaries() {
        val bytes = json.encodeToByteArray()
        read(bytes, codec(TcfgResourcePolicy(maxInputBytes = bytes.size)))
        reject(bytes, "PRF138", c = codec(TcfgResourcePolicy(maxInputBytes = bytes.size - 1)))
        val value = "a".repeat(40)
        read(json.replace("Printer 印刷 😀", value), codec(TcfgResourcePolicy(maxStringBytes = 40)))
        reject(json.replace("Printer 印刷 😀", value + "a"), "PRF138", c = codec(TcfgResourcePolicy(maxStringBytes = 40)))
        reject("[".repeat(64) + "0" + "]".repeat(64), "PRF135")
        reject("[".repeat(65) + "0" + "]".repeat(65), "PRF138")
        reject(json.replace("Printer 印刷 😀", "😀".repeat(11)), "PRF138", c = codec(TcfgResourcePolicy(maxStringBytes = 40)))
        val p = read()
        val canonical = write(p)
        assertTrue(canonical.contentEquals(write(p, codec(TcfgResourcePolicy(maxInputBytes = canonical.size)))))
        assertIs<ProfileValidationResult.Failure>(codec(TcfgResourcePolicy(maxInputBytes = canonical.size - 1)).encode(p))
    }

    @Test fun writerBoundsCanonicalSortingAcrossValidationPolicies() {
        val result = assertIs<ProfileValidationResult.Failure>(codec(semantic = limits.copy(maxCollectionEntries = 1)).encode(read()))
        assertEquals("PRF138", result.diagnostics.single().code)
    }

    @Test fun unicodeEscapesRequireAsciiHexDigits() {
        for (digits in listOf("００４１", "٠٠٤١", "00ＡＦ", "00ａｆ"))
            reject(json.replace("Printer", "\\u$digits"), "PRF131")
        read(json.replace("Printer", "\\u0041"))
        read(json.replace("Printer", "\\u00aF"))
    }

}
