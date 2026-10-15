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

/** Evidence is local application state; it is never added to the portable .tcfg schema. */
enum class ProfileEvidence { UNKNOWN, USER_ENTERED, INFERRED, HARDWARE_OBSERVED, VERIFIED }
enum class ProfileDraftFieldKind { TEXT, NUMBER, BOOLEAN, LIST }
data class ProfileVerification(val area: String, val procedure: String, val observation: String, val deviceLocator: String, val snapshot: String)

data class ProfileDraftField(val path: String, val label: String, val kind: ProfileDraftFieldKind)

object ProfileDraftFields {
    private fun text(path: String, label: String) = ProfileDraftField(path, label, ProfileDraftFieldKind.TEXT)
    private fun number(path: String, label: String) = ProfileDraftField(path, label, ProfileDraftFieldKind.NUMBER)
    private fun bool(path: String, label: String) = ProfileDraftField(path, label, ProfileDraftFieldKind.BOOLEAN)
    private fun list(path: String, label: String) = ProfileDraftField(path, label, ProfileDraftFieldKind.LIST)
    val basic = listOf(text("profileId", "Profile ID"), number("profileRevision", "Revision"),
        text("display.name", "Name"), text("display.manufacturer", "Manufacturer"), text("display.model", "Model"),
        number("geometry.horizontalDpi", "Effective horizontal DPI"), number("geometry.verticalDpi", "Effective vertical DPI"),
        number("geometry.printableWidthDots", "Reliable printable width in dots"))
    val advanced = listOf(
        text("protocol.dialectId", "Registered ESC/POS dialect"), text("protocol.initializationStrategy", "Initialization strategy"),
        bool("nativeText.supported", "Native text supported"), number("nativeText.fontCount", "Native font count (0–16)"),
        number("nativeText.codePageCount", "Code page count (0–16)"), bool("nativeText.styles.bold", "Native bold"),
        bool("nativeText.styles.underline", "Native underline"), list("nativeText.styles.widthScales", "Native width scales"),
        list("nativeText.styles.heightScales", "Native height scales"), bool("raster.supported", "Raster supported"),
        list("raster.strategies", "Raster strategies"), text("raster.preferredStrategy", "Preferred raster strategy"),
        number("raster.maxWidthDots", "Maximum raster width"), number("raster.bandHeightDots.min", "Minimum raster band height"),
        number("raster.bandHeightDots.preferred", "Preferred raster band height"), number("raster.bandHeightDots.max", "Maximum raster band height"),
        bool("nativeQr.supported", "Native QR supported"), list("nativeQr.strategies", "Native QR strategies"),
        text("nativeQr.preferredStrategy", "Preferred native QR strategy"), list("nativeQr.models", "Native QR models"),
        number("nativeQr.moduleSize.min", "Minimum QR module size"), number("nativeQr.moduleSize.max", "Maximum QR module size"),
        list("nativeQr.errorCorrection", "QR error corrections"), bool("nativeBarcode.supported", "Native barcode supported"),
        list("nativeBarcode.strategies", "Barcode strategies"), text("nativeBarcode.preferredStrategy", "Preferred barcode strategy"),
        list("nativeBarcode.symbologies", "Barcode symbologies"), bool("cutter.supported", "Automatic cutter supported"),
        list("cutter.modes", "Cutter modes"), text("cutter.strategy", "Cutter strategy"),
        bool("statusQuery.supported", "Status queries supported"), list("statusQuery.strategies", "Status strategies"),
        text("statusQuery.preferredStrategy", "Preferred status strategy"), number("printerBuffer.recommendedMaxBurstBytes", "Printer buffer burst bytes (optional)"),
        number("printerBuffer.recommendedPauseAfterBurstMs", "Printer buffer pause ms (optional)"), list("quirks", "Registered quirks"))
    fun fontFields(index: Int) = listOf(text("nativeText.fonts[$index].id", "Font ID"),
        number("nativeText.fonts[$index].cellWidthDots", "Font cell width"), number("nativeText.fonts[$index].cellHeightDots", "Font cell height"),
        number("nativeText.fonts[$index].lineAdvanceDots", "Font line advance (optional)"),
        text("nativeText.fonts[$index].selector.strategy", "Font selector strategy (optional)"),
        number("nativeText.fonts[$index].selector.parameter", "Font selector parameter"))
    fun codePageFields(index: Int) = listOf(text("nativeText.codePages[$index].id", "Code page ID"),
        text("nativeText.codePages[$index].repertoire", "Registered repertoire"),
        text("nativeText.codePages[$index].selector.strategy", "Code page selector strategy (optional)"),
        number("nativeText.codePages[$index].selector.parameter", "Code page selector parameter"))
    val evidenceAreas = listOf("geometry", "protocol", "nativeText", "raster", "nativeQr", "nativeBarcode", "cutter", "statusQuery", "printerBuffer")
    val worksheet = listOf(number("worksheet.paperWidthMm", "Paper width (mm, reported)"),
        number("worksheet.printableWidthMm", "Printable width (mm, reported)"),
        text("worksheet.charset", "Default charset (reported)"), text("worksheet.nativeFont", "Native font (reported)"))
    val all = basic + worksheet + advanced + (0 until 16).flatMap { fontFields(it) + codePageFields(it) }
    internal val paths = all.map { it.path }.toSet()
}

/** An incomplete worksheet never becomes a trusted Core profile merely by being saved. */
data class ProfileDraft(
    val draftId: String,
    val fields: Map<String, String>,
    val evidence: Map<String, ProfileEvidence> = emptyMap(),
    val evidenceNotes: String = "",
    val syntheticSource: Boolean = false,
    val suggestions: List<ProfileSuggestion> = emptyList(),
    val verifications: List<ProfileVerification> = emptyList(),
) {
    operator fun get(path: String): String = fields[path].orEmpty()
    fun withField(path: String, value: String): ProfileDraft {
        val area = path.substringBefore('.')
        return copy(fields = fields + (path to value), evidence = if (this[path] != value && area in ProfileDraftFields.evidenceAreas)
            evidence + (area to ProfileEvidence.UNKNOWN) else evidence,
            verifications = if (this[path] != value) verifications.filterNot { it.area == area || path == "quirks" } else verifications,
            suggestions = if (this[path] != value) suggestions.filterNot { it.path == path } else suggestions)
    }
    fun withEvidence(area: String, value: ProfileEvidence): ProfileDraft = copy(evidence = evidence + (area to value))

    /** Inspection proposes values; acceptance is explicit even when the existing field is blank. */
    fun withSuggestions(values: List<ProfileSuggestion>): ProfileDraft {
        require(values.size <= 32 && values.all { it.path in ProfileDraftFields.paths })
        return copy(suggestions = values.toList())
    }
    fun acceptSuggestion(value: ProfileSuggestion): ProfileDraft {
        require(value in suggestions)
        val updated = withField(value.path, value.value)
        return updated.copy(suggestions = (updated.suggestions + value).distinct().take(32))
    }
    private fun snapshot(area: String) = fields.entries.filter { it.key.substringBefore('.') == area || it.key == "quirks" }
        .sortedBy { it.key }.joinToString("") { "${it.key.length}:${it.key}${it.value.length}:${it.value}" }
    fun recordVerification(area: String, procedure: String, observation: String, deviceLocator: String): ProfileDraft {
        require(area in ProfileDraftFields.evidenceAreas && procedure.isNotBlank() && procedure.length <= 256 &&
            observation.isNotBlank() && observation.length <= 1024 && deviceLocator.isNotBlank() && deviceLocator.length <= 1024)
        return copy(evidence = evidence + (area to ProfileEvidence.VERIFIED),
            verifications = verifications.filterNot { it.area == area } + ProfileVerification(area, procedure, observation, deviceLocator, snapshot(area)))
    }
    fun validate(deviceLocator: String? = null): ProfileValidationResult<ValidatedPrinterProfile> = validate(requireEvidence = true, deviceLocator)
    /** Only Printer Lab may consume this candidate; it cannot activate or export an ordinary profile. */
    fun validateCandidate(): ProfileValidationResult<ValidatedPrinterProfile> = validate(requireEvidence = false, deviceLocator = null)

    private fun validate(requireEvidence: Boolean, deviceLocator: String?): ProfileValidationResult<ValidatedPrinterProfile> {
        val errors = mutableListOf<ProfileDiagnostic>()
        fun error(path: String, message: String) { errors += ProfileDiagnostic("PRF151", "$." + path, message) }
        if (!bounded()) return failure("Draft exceeds local storage limits or contains unknown fields")
        if (syntheticSource) return failure("A synthetic fixture cannot become a physical printer profile by duplication")
        fun integer(path: String, optional: Boolean = false): Int? {
            val value = this[path]
            if (optional && value.isEmpty()) return null
            return value.toIntOrNull().also { if (it == null) error(path, if (value.isEmpty()) "This value has not been established. Save this draft or use Printer Lab calibration." else "Enter a whole number or leave this value unknown.") }
        }
        fun required(path: String) = integer(path) ?: 0
        fun enabled(path: String): Boolean {
            if (this[path] !in listOf("", "true", "false")) error(path, "Choose true or false")
            return this[path] == "true"
        }
        fun strings(path: String): ProfileList<String> = ProfileList(this[path].split(',').map { it.trim() }.filter { it.isNotEmpty() })
        fun integers(path: String): ProfileList<Int> = ProfileList(strings(path).map {
            it.toIntOrNull() ?: run { error(path, "Enter comma-separated integers"); 0 }
        })
        fun optional(path: String) = this[path].ifEmpty { null }
        fun selector(path: String): RegisteredSelector? {
            val strategy = optional("$path.strategy")
            if (strategy == null && this["$path.parameter"].isNotEmpty()) error("$path.parameter", "A selector parameter requires its registered strategy")
            return strategy?.let { RegisteredSelector(it, required("$path.parameter")) }
        }
        fun count(path: String): Int {
            val value = if (this[path].isEmpty()) 0 else required(path)
            if (value !in 0..16) error(path, "Choose between 0 and 16 entries")
            return value.coerceIn(0, 16)
        }
        val native = enabled("nativeText.supported")
        val raster = enabled("raster.supported")
        val qr = enabled("nativeQr.supported")
        val barcode = enabled("nativeBarcode.supported")
        val cutter = enabled("cutter.supported")
        val status = enabled("statusQuery.supported")
        val candidate = PrinterProfile(
            profileId = this["profileId"], profileRevision = required("profileRevision"),
            display = ProfileDisplay(this["display.name"], optional("display.manufacturer"), optional("display.model")),
            geometry = PrinterGeometry(required("geometry.horizontalDpi"), required("geometry.verticalDpi"), required("geometry.printableWidthDots")),
            nativeText = NativeTextCapability(native, ProfileList((0 until count("nativeText.fontCount")).map { i ->
                val p = "nativeText.fonts[$i]"
                NativeFontCapability(this["$p.id"], required("$p.cellWidthDots"), required("$p.cellHeightDots"), integer("$p.lineAdvanceDots", true), selector("$p.selector"))
            }), NativeStyleCapability(enabled("nativeText.styles.bold"), enabled("nativeText.styles.underline"),
                integers("nativeText.styles.widthScales"), integers("nativeText.styles.heightScales")),
                ProfileList((0 until count("nativeText.codePageCount")).map { i ->
                    val p = "nativeText.codePages[$i]"
                    CodePageCapability(this["$p.id"], this["$p.repertoire"], selector("$p.selector"))
                })),
            raster = RasterCapability(raster, strings("raster.strategies"), optional("raster.preferredStrategy"), integer("raster.maxWidthDots", true),
                if ((listOf("min", "preferred", "max").any { this["raster.bandHeightDots.$it"].isNotEmpty() }))
                    RasterBandHeight(required("raster.bandHeightDots.min"), required("raster.bandHeightDots.preferred"), required("raster.bandHeightDots.max")) else null),
            nativeQr = NativeQrCapability(qr, strings("nativeQr.strategies"), optional("nativeQr.preferredStrategy"), strings("nativeQr.models"),
                if (this["nativeQr.moduleSize.min"].isNotEmpty() || this["nativeQr.moduleSize.max"].isNotEmpty())
                    QrModuleSize(required("nativeQr.moduleSize.min"), required("nativeQr.moduleSize.max")) else null, strings("nativeQr.errorCorrection")),
            nativeBarcode = NativeBarcodeCapability(barcode, strings("nativeBarcode.strategies"), optional("nativeBarcode.preferredStrategy"), strings("nativeBarcode.symbologies")),
            cutter = CutterCapability(cutter, strings("cutter.modes"), optional("cutter.strategy")),
            statusQuery = StatusQueryCapability(status, strings("statusQuery.strategies"), optional("statusQuery.preferredStrategy")),
            protocol = EscPosProtocolProfile(this["protocol.dialectId"], this["protocol.initializationStrategy"]),
            printerBuffer = if (this["printerBuffer.recommendedMaxBurstBytes"].isNotEmpty() || this["printerBuffer.recommendedPauseAfterBurstMs"].isNotEmpty())
                PrinterBufferGuidance(integer("printerBuffer.recommendedMaxBurstBytes", true), integer("printerBuffer.recommendedPauseAfterBurstMs", true)) else null,
            quirks = strings("quirks"),
        )
        // Missing/invalid worksheet numbers must not be converted into Core zero-value cascades.
        if (errors.isNotEmpty()) return ProfileValidationResult.Failure(ProfileList(errors.distinctBy { it.path }))
        val requiredEvidence = listOf("geometry", "protocol") + listOf("nativeText" to native, "raster" to raster,
            "nativeQr" to qr, "nativeBarcode" to barcode, "cutter" to cutter, "statusQuery" to status,
            "printerBuffer" to (candidate.printerBuffer != null)).filter { it.second }.map { it.first }
        if (requireEvidence) {
            requiredEvidence.forEach { area ->
                val record = verifications.singleOrNull { it.area == area }
                if (evidence[area] != ProfileEvidence.VERIFIED || record == null || record.snapshot != snapshot(area) ||
                    (deviceLocator != null && record.deviceLocator != deviceLocator))
                    error(area, "Record a procedure, observed result and printer identity before activating this capability")
            }
            if (verifications.map { it.deviceLocator }.distinct().size > 1) error("evidenceNotes", "Verification records refer to different printers")
        }
        when (val result = PrinterProfileFiles.validate(candidate)) {
            is ProfileValidationResult.Failure -> errors += result.diagnostics
            is ProfileValidationResult.Success -> if (errors.isEmpty()) return result
        }
        return ProfileValidationResult.Failure(ProfileList(errors.distinctBy { it.code to it.path }))
    }

    private fun bounded(): Boolean = draftId.length in 1..128 && fields.size <= ProfileDraftFields.paths.size &&
        fields.all { it.key in ProfileDraftFields.paths && it.value.length <= 1024 } &&
        evidence.keys.all { it in ProfileDraftFields.evidenceAreas } && evidenceNotes.length <= 4096 &&
        fields.values.sumOf { it.length } <= 49152 && suggestions.size <= 32 &&
        suggestions.all { it.path in ProfileDraftFields.paths && it.value.length <= 1024 && it.sourceDescription.length in 1..512 && it.deviceLocator.length in 1..1024 } &&
        verifications.size <= ProfileDraftFields.evidenceAreas.size && verifications.map { it.area }.distinct().size == verifications.size &&
        verifications.all { it.area in ProfileDraftFields.evidenceAreas && it.procedure.isNotBlank() && it.procedure.length <= 256 &&
            it.observation.isNotBlank() && it.observation.length <= 1024 && it.deviceLocator.isNotBlank() && it.deviceLocator.length <= 1024 && it.snapshot.length <= 16384 }

    /** Private versioned length framing keeps unknown values without a second portable profile schema. */
    fun encodeLocal(): String {
        require(bounded()) { "Draft exceeds local storage limits" }
        val parts = listOf(draftId, evidenceNotes, syntheticSource.toString(), fields.size.toString()) +
            fields.entries.sortedBy { it.key }.flatMap { listOf(it.key, it.value) } + listOf(evidence.size.toString()) +
            evidence.entries.sortedBy { it.key }.flatMap { listOf(it.key, it.value.name) } +
            listOf(suggestions.size.toString()) + suggestions.flatMap { listOf(it.path, it.value, it.source.name, it.sourceDescription, it.deviceLocator) } +
            listOf(verifications.size.toString()) + verifications.flatMap { listOf(it.area, it.procedure, it.observation, it.deviceLocator, it.snapshot) }
        val encoded = "RastrIO-local-draft-2:" + parts.joinToString("") { "${it.length}:$it" }
        require(encoded.length <= 65536) { "Encoded draft exceeds local storage limit" }
        return encoded
    }

    companion object {
        fun fresh(id: String) = ProfileDraft(id, mapOf("profileId" to id, "profileRevision" to "1"))
        fun decodeLocal(source: String): ProfileDraft? {
            val version = when { source.startsWith("RastrIO-local-draft-1:") -> 1; source.startsWith("RastrIO-local-draft-2:") -> 2; else -> return null }
            if (source.length > 65536) return null
            var offset = "RastrIO-local-draft-1:".length
            fun part(): String? {
                val end = source.indexOf(':', offset)
                if (end < offset || end - offset > 5) return null
                val length = source.substring(offset, end).toIntOrNull() ?: return null
                if (length < 0 || length > 16384 || length > source.length - end - 1) return null
                offset = end + 1 + length
                return source.substring(end + 1, offset)
            }
            val id = part() ?: return null
            val notes = part() ?: return null
            val synthetic = when (part()) { "true" -> true; "false" -> false; else -> return null }
            val count = part()?.toIntOrNull() ?: return null
            if (count !in 0..ProfileDraftFields.paths.size) return null
            val fields = mutableMapOf<String, String>()
            repeat(count) {
                val key = part() ?: return null
                val value = part() ?: return null
                if (key in fields || key !in ProfileDraftFields.paths) return null
                fields[key] = value
            }
            val evidenceCount = part()?.toIntOrNull() ?: return null
            if (evidenceCount !in 0..ProfileDraftFields.evidenceAreas.size) return null
            val evidence = mutableMapOf<String, ProfileEvidence>()
            repeat(evidenceCount) {
                val key = part() ?: return null
                val value = part() ?: return null
                if (key in evidence || key !in ProfileDraftFields.evidenceAreas) return null
                evidence[key] = ProfileEvidence.entries.firstOrNull { it.name == value } ?: return null
            }
            val suggestions = mutableListOf<ProfileSuggestion>()
            val records = mutableListOf<ProfileVerification>()
            if (version == 2) {
                val suggestionCount = part()?.toIntOrNull() ?: return null
                if (suggestionCount !in 0..32) return null
                repeat(suggestionCount) {
                    val path = part() ?: return null; val value = part() ?: return null
                    val sourceName = part() ?: return null
                    val classification = InspectionEvidence.entries.firstOrNull { it.name == sourceName } ?: return null
                    val description = part() ?: return null; val identity = part() ?: return null
                    suggestions += runCatching { ProfileSuggestion(path, value, classification, description, identity) }.getOrNull() ?: return null
                }
                val recordCount = part()?.toIntOrNull() ?: return null
                if (recordCount !in 0..ProfileDraftFields.evidenceAreas.size) return null
                repeat(recordCount) {
                    records += ProfileVerification(part() ?: return null, part() ?: return null, part() ?: return null, part() ?: return null, part() ?: return null)
                }
            }
            if (offset != source.length) return null
            return ProfileDraft(id, fields, evidence, notes, synthetic, suggestions, records).takeIf { it.bounded() }
        }

        fun fromProfile(profile: PrinterProfile, newId: String = profile.profileId): ProfileDraft {
            val f = mutableMapOf<String, String>()
            fun put(path: String, value: Any?) { if (value != null) f[path] = value.toString() }
            fun list(path: String, values: List<*>) { put(path, values.joinToString(",")) }
            fun selector(path: String, value: RegisteredSelector?) { put("$path.strategy", value?.strategy); put("$path.parameter", value?.parameter) }
            put("profileId", newId); put("profileRevision", profile.profileRevision)
            put("display.name", profile.display.name); put("display.manufacturer", profile.display.manufacturer); put("display.model", profile.display.model)
            put("geometry.horizontalDpi", profile.geometry.horizontalDpi); put("geometry.verticalDpi", profile.geometry.verticalDpi); put("geometry.printableWidthDots", profile.geometry.printableWidthDots)
            val native = profile.nativeText
            put("nativeText.supported", native.supported); put("nativeText.fontCount", native.fonts.size); put("nativeText.codePageCount", native.codePages.size)
            native.fonts.forEachIndexed { i, font -> val p = "nativeText.fonts[$i]"
                put("$p.id", font.id); put("$p.cellWidthDots", font.cellWidthDots); put("$p.cellHeightDots", font.cellHeightDots); put("$p.lineAdvanceDots", font.lineAdvanceDots); selector("$p.selector", font.selector) }
            native.codePages.forEachIndexed { i, page -> val p = "nativeText.codePages[$i]"
                put("$p.id", page.id); put("$p.repertoire", page.repertoire); selector("$p.selector", page.selector) }
            put("nativeText.styles.bold", native.styles.bold); put("nativeText.styles.underline", native.styles.underline)
            list("nativeText.styles.widthScales", native.styles.widthScales); list("nativeText.styles.heightScales", native.styles.heightScales)
            val r = profile.raster
            put("raster.supported", r.supported); list("raster.strategies", r.strategies); put("raster.preferredStrategy", r.preferredStrategy); put("raster.maxWidthDots", r.maxWidthDots)
            put("raster.bandHeightDots.min", r.bandHeightDots?.min); put("raster.bandHeightDots.preferred", r.bandHeightDots?.preferred); put("raster.bandHeightDots.max", r.bandHeightDots?.max)
            val q = profile.nativeQr
            put("nativeQr.supported", q.supported); list("nativeQr.strategies", q.strategies); put("nativeQr.preferredStrategy", q.preferredStrategy)
            list("nativeQr.models", q.models); put("nativeQr.moduleSize.min", q.moduleSize?.min); put("nativeQr.moduleSize.max", q.moduleSize?.max); list("nativeQr.errorCorrection", q.errorCorrection)
            val b = profile.nativeBarcode
            put("nativeBarcode.supported", b.supported); list("nativeBarcode.strategies", b.strategies); put("nativeBarcode.preferredStrategy", b.preferredStrategy); list("nativeBarcode.symbologies", b.symbologies)
            put("cutter.supported", profile.cutter.supported); list("cutter.modes", profile.cutter.modes); put("cutter.strategy", profile.cutter.strategy)
            put("statusQuery.supported", profile.statusQuery.supported); list("statusQuery.strategies", profile.statusQuery.strategies); put("statusQuery.preferredStrategy", profile.statusQuery.preferredStrategy)
            put("printerBuffer.recommendedMaxBurstBytes", profile.printerBuffer?.recommendedMaxBurstBytes); put("printerBuffer.recommendedPauseAfterBurstMs", profile.printerBuffer?.recommendedPauseAfterBurstMs)
            val protocol = profile.protocol as EscPosProtocolProfile
            put("protocol.dialectId", protocol.dialectId); put("protocol.initializationStrategy", protocol.initializationStrategy); list("quirks", profile.quirks)
            return ProfileDraft(newId, f, ProfileDraftFields.evidenceAreas.associateWith { ProfileEvidence.USER_ENTERED }, syntheticSource = isSyntheticProfile(profile))
        }
        private fun failure(message: String) = ProfileValidationResult.Failure(profileListOf(ProfileDiagnostic("PRF151", "$", message)))
    }
}

interface ProfileDraftStorage {
    fun loadDrafts(): List<ProfileDraft>
    fun saveDrafts(drafts: List<ProfileDraft>): Boolean
}

/** Desktop/Web fallback remains session-local until their platform storage adapters are provided. */
class MemoryProfileDraftStorage : ProfileDraftStorage {
    private var encoded = emptyList<String>()
    override fun loadDrafts(): List<ProfileDraft> = encoded.mapNotNull(ProfileDraft::decodeLocal)
    override fun saveDrafts(drafts: List<ProfileDraft>): Boolean {
        if (drafts.size > 32 || drafts.map { it.draftId }.distinct().size != drafts.size) return false
        val values = drafts.map { runCatching { it.encodeLocal() }.getOrNull() ?: return false }
        encoded = values
        return true
    }
}
