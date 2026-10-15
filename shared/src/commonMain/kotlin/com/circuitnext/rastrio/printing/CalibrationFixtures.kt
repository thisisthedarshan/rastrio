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

import com.circuitnext.rastrio.core.document.*
import com.circuitnext.rastrio.core.escpos.*
import com.circuitnext.rastrio.core.printer.*
import com.circuitnext.rastrio.core.profile.*
import com.circuitnext.rastrio.core.raster.*
import com.circuitnext.rastrio.core.text.SnapshotList

/** Stable procedures generate semantic inputs, never raw printer programs or automatic writes. */
enum class CalibrationProcedure(val title: String, val hardwareTestId: String, val instructions: String) {
    GEOMETRY("Printable width and effective DPI", "HW-037",
        "Use explicit candidate geometry, or select and acknowledge an isolated experimental starter. Measure both axes separately using the known dot intervals; repeat measurements and inspect edge clipping. Millimetre width alone cannot establish DPI."),
    NATIVE_FONT("Native font and line advance", "HW-001/002/004",
        "Select an explicit candidate font and page. Inspect prepared native text and captured selector bytes; measure cell dimensions and line advance on paper. Raster fallback cannot establish native font behavior."),
    CP437("CP437 repertoire and selector", "HW-036",
        "Enter the independently established CP437 selector. Compare each printed glyph with an independent reference and captured bytes. A self-test CP437 label does not establish the selector."),
    RASTER("Raster compatibility", "HW-007",
        "Inspect finalized packing, odd-width edge pixels, black/white bands and gradient. Record the tested raster strategy, width and band limit; never infer all raster limits from one successful sample."),
    RASTER_QR("Raster QR compatibility", "HW-018/019",
        "Scan the opaque test payload from paper with an independent scanner. Inspect all quiet-zone edges and record measured size; preview or byte transmission does not establish scanning success."),
    NATIVE_QR("Native QR host command compatibility", "HW-017",
        "Experimental candidate only: explicitly enter the registered native strategy and parameters, inspect captured bytes and require a native prepared operation. Firmware self-test QR does not prove host command support."),
    BUFFER_CHUNK("Printer buffer and transport chunks", "HW-029",
        "Print one bounded sample at an explicitly selected platform write-chunk policy. Record chunk size, pacing and partial-output state. Printer buffering and transport chunks are separate; never automatically repeat after ambiguous output."),
}

data class CalibrationRequest(
    val procedure: CalibrationProcedure,
    val widthDots: Int,
    val heightDots: Int = 64,
    val nativeFontId: String? = null,
    val codePageId: String? = null,
    val qrPayload: String = "RastrIO",
    val bufferLines: Int = 8,
)

/** Candidate remains isolated from ordinary profile activation and PrinterInstance assignment. */
data class CalibrationFixture(
    val procedure: CalibrationProcedure,
    val profile: PrinterProfile,
    val document: ThermalDocument,
    val options: PrintOptions,
    val resolvedImages: Map<AssetReference, ResolvedRasterImage>,
) {
    val encodingPolicy: EncodingResourcePolicy get() = EncodingResourcePolicy(maxChunkBytes = 4096, maxEncodedBytes = 65536)
    val resources: PreparationResourcePolicy get() = PreparationResourcePolicy(maxOperations = 256, maxDiagnostics = 32,
        maxTextCodeUnits = 4096, maxHeightDots = 2048, maxGraphicBandBytes = 8192,
        maxPreparedGraphicBytes = 49152, maxResidentGraphicBytes = 49152)

    /** Production preparation and encoder preflight, with no device/session access. */
    fun prepare(isCancelled: () -> Boolean = { false }): PreparationResult {
        val result = BasicPreparationEngine(resources = resources).prepare(document, profile, options,
            resolvedImages = resolvedImages, isCancelled = isCancelled)
        if (result !is PreparationResult.Success) return result
        val operations = result.print.operations
        if (procedure in listOf(CalibrationProcedure.NATIVE_FONT, CalibrationProcedure.CP437) &&
            (operations.none { it is NativeText } || operations.any { it is PreparedRasterGraphic }))
            return failure("Native character/font calibration must use native text throughout")
        if (procedure == CalibrationProcedure.NATIVE_QR && operations.none { it is NativeQr })
            return failure("Native QR calibration must retain a native QR prepared operation")
        val encoded = EscPosEncoder(encodingPolicy).validate(result.print)
        if (encoded !is EncodingResult.Success) return failure("Calibration encoding preflight failed: " + when (encoded) {
            is EncodingResult.Failure -> "${encoded.diagnostic.code}: ${encoded.diagnostic.message}"
            else -> "cancelled"
        })
        return result
    }
    private fun failure(message: String) = PreparationResult.Failure(SnapshotList(listOf(PreparationDiagnostic("PRN152", message))))
}

object CalibrationFixtures {
    /** Hypotheses for an acknowledged registered command trial, never ordinary profile defaults. */
    fun experimentalStarter(draft: ProfileDraft, commandCompatibilityAcknowledged: Boolean): ProfileDraft? {
        if (!commandCompatibilityAcknowledged || draft.syntheticSource) return null
        val values = mapOf(
            "geometry.horizontalDpi" to "203", "geometry.verticalDpi" to "203", "geometry.printableWidthDots" to "384",
            "protocol.dialectId" to "escpos.generic", "protocol.initializationStrategy" to "escpos.init.standard",
            "raster.supported" to "true", "raster.strategies" to "escpos.raster.gs-v-0", "raster.preferredStrategy" to "escpos.raster.gs-v-0",
            "raster.maxWidthDots" to "384", "raster.bandHeightDots.min" to "1", "raster.bandHeightDots.preferred" to "64", "raster.bandHeightDots.max" to "64",
            "nativeText.supported" to "false", "nativeText.fontCount" to "0", "nativeText.codePageCount" to "0",
            "nativeText.styles.bold" to "false", "nativeText.styles.underline" to "false",
            "nativeText.styles.widthScales" to "", "nativeText.styles.heightScales" to "",
            "nativeQr.supported" to "false", "nativeBarcode.supported" to "false", "cutter.supported" to "false", "statusQuery.supported" to "false",
            "printerBuffer.recommendedMaxBurstBytes" to "", "printerBuffer.recommendedPauseAfterBurstMs" to "", "quirks" to "",
        )
        val isolated = ProfileDraft.fresh(draft.draftId).withField("profileId", draft["profileId"])
            .withField("profileRevision", draft["profileRevision"]).withField("display.name", "Experimental raster trial")
        val candidate = values.entries.fold(isolated) { current, (path, value) -> current.withField(path, value) }
            .copy(evidence = emptyMap(), verifications = emptyList())
        return candidate.takeIf { it.validateCandidate() is ProfileValidationResult.Success }
    }

    fun create(draft: ProfileDraft, request: CalibrationRequest): ProfileValidationResult<CalibrationFixture> {
        val profile = when (val result = draft.validateCandidate()) {
            is ProfileValidationResult.Failure -> return result
            is ProfileValidationResult.Success -> result.value.profile
        }
        fun invalid(path: String, message: String) = ProfileValidationResult.Failure(profileListOf(ProfileDiagnostic("PRF152", path, message)))
        if (request.widthDots !in 1..1024 || request.widthDots > profile.geometry.printableWidthDots)
            return invalid("$.calibration.widthDots", "Enter 1–1024 dots within the explicit candidate printable width")
        if (request.heightDots !in 1..64) return invalid("$.calibration.heightDots", "Calibration image height must be 1–64 dots")
        if (request.bufferLines !in 1..16) return invalid("$.calibration.bufferLines", "Choose 1–16 lines; repeat prints are always manual")
        if (request.qrPayload.length > 128 || request.qrPayload.encodeToByteArray().size > 256 || request.qrPayload.isBlank() ||
            request.qrPayload.any { it.code < 32 || it.code == 127 })
            return invalid("$.calibration.qrPayload", "Use a nonempty printable test payload of at most 256 UTF-8 bytes")
        val native = request.procedure in listOf(CalibrationProcedure.NATIVE_FONT, CalibrationProcedure.CP437, CalibrationProcedure.BUFFER_CHUNK)
        if (native && (!profile.nativeText.supported || request.nativeFontId == null || request.codePageId == null))
            return invalid("$.calibration.nativeText", "Select an explicit candidate native font and code page")
        if (request.procedure == CalibrationProcedure.CP437 &&
            profile.nativeText.codePages.none { it.id == request.codePageId && it.repertoire == "cp437" })
            return invalid("$.calibration.codePageId", "CP437 calibration requires a declared CP437 repertoire and explicit selector")
        val raster = request.procedure in listOf(CalibrationProcedure.GEOMETRY, CalibrationProcedure.RASTER, CalibrationProcedure.RASTER_QR)
        if (raster && !profile.raster.supported) return invalid("$.calibration.raster", "Declare an explicit candidate raster strategy before generating this procedure")
        if (request.procedure == CalibrationProcedure.NATIVE_QR && !profile.nativeQr.supported)
            return invalid("$.calibration.nativeQr", "This isolated experiment requires an explicit native QR candidate; ordinary support remains disabled without verified evidence")
        val pattern = request.procedure in listOf(CalibrationProcedure.GEOMETRY, CalibrationProcedure.RASTER)
        if (pattern && request.widthDots > (profile.raster.maxWidthDots ?: 0))
            return invalid("$.calibration.widthDots", "Known-dot patterns must fit the candidate raster width without scaling")
        val images = mutableMapOf<AssetReference, ResolvedRasterImage>()
        fun paragraph(value: String) = Paragraph(Alignment.LEFT, listOf(Text(value)))
        val blocks: List<DocumentBlock> = when (request.procedure) {
            CalibrationProcedure.GEOMETRY, CalibrationProcedure.RASTER -> {
                val reference = EmbeddedAssetReference("rastrio-calibration-pattern")
                val width = request.widthDots
                val height = request.heightDots
                val horizontal = profile.geometry.horizontalDpi.toLong()
                val vertical = profile.geometry.verticalDpi.toLong()
                // Oversampling cancels the normal image aspect conversion while preserving exact dot intervals.
                val scaleX = maxOf(2L, (2L * vertical + horizontal - 1) / horizontal)
                val sourceWidth = width * scaleX
                val sourceHeight = (height * scaleX * horizontal + vertical / 2) / vertical
                if (sourceWidth !in 1L..32768L || sourceHeight !in 1L..32768L || sourceWidth * sourceHeight > 1_048_576)
                    return invalid("$.calibration.sourcePixels", "Candidate axes exceed the 1,048,576-pixel calibration source limit; reduce the pattern dimensions")
                val sourceColumns = sourceWidth.toInt()
                val sourceRows = sourceHeight.toInt()
                val pixels = IntArray((sourceWidth * sourceHeight).toInt()) { index ->
                    val x = index % sourceColumns * width / sourceColumns
                    val y = index / sourceColumns * height / sourceRows
                    val black = if (request.procedure == CalibrationProcedure.GEOMETRY)
                        x == 0 || x == width - 1 || y == 0 || y == height - 1 || (x % 8 == 0 && y < 8) || (y % 8 == 0 && x < 8)
                    else y < 8 || (y in 16..23 && x % 2 == 0) || (y >= 32 && x % 8 < 4)
                    if (black) 0xff000000.toInt() else 0xffffffff.toInt()
                }
                images[reference] = ResolvedRasterImage(DecodedImage(sourceWidth.toInt(), sourceHeight.toInt(), pixels),
                    RasterOptions(width, height, algorithm = DitherAlgorithm.THRESHOLD))
                listOf(Image(reference, Alignment.LEFT, RequestedWidthSizing(Length(width * 25.4 / profile.geometry.horizontalDpi))))
            }
            CalibrationProcedure.NATIVE_FONT -> listOf(paragraph("0123456789 ABCDEFGHIJKLMNOP"), paragraph("MMMMWWWW iiiillll"), paragraph("Line advance 0123456789"))
            CalibrationProcedure.CP437 -> listOf("CP437 0123456789", "ÇüéâäàåçêëèïîìÄÅ", "┌─┐│└┘", "αΓπΣσµτΦΘΩδ∞").map(::paragraph)
            CalibrationProcedure.RASTER_QR, CalibrationProcedure.NATIVE_QR -> listOf(QrCode(request.qrPayload,
                Alignment.LEFT, QrErrorCorrection.MEDIUM, Length(request.widthDots * 25.4 / profile.geometry.horizontalDpi)))
            CalibrationProcedure.BUFFER_CHUNK -> (1..request.bufferLines).map { paragraph("Chunk sample $it 0123456789 ABCDEF") }
        }
        return ProfileValidationResult.Success(CalibrationFixture(request.procedure, profile,
            ThermalDocument(metadata = DocumentMetadata(request.procedure.hardwareTestId), blocks = blocks),
            PrintOptions(nativeFontId = if (native) request.nativeFontId else null,
                codePageId = if (native) request.codePageId else null, cutIntent = CutIntent.NONE, finalFeedDots = 0,
                qrStrategy = if (request.procedure == CalibrationProcedure.NATIVE_QR) QrStrategy.NATIVE else QrStrategy.RASTER), images))
    }
}
