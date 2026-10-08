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

package com.circuitnext.rastrio.core.text

/** Repertoire coverage alone does not establish printer font, style or strategy reliability. */
sealed interface NativeCoverageResult {
    /** Exact unsigned character bytes, never printer command or control bytes. */
    data class Representable(val characterBytes: SnapshotList<Int>) : NativeCoverageResult {
        init { require(characterBytes.all { it in 0x20..0x7e || it in 0x80..0xff }) }
    }
    data class Unsupported(val diagnostics: SnapshotList<TextDiagnostic>) : NativeCoverageResult {
        init { require(diagnostics.isNotEmpty()) }
    }
    data class Unknown(val diagnostics: SnapshotList<TextDiagnostic>) : NativeCoverageResult {
        init { require(diagnostics.isNotEmpty()) }
    }
}

/** Controlled portable mapping; no host charset, normalization, substitution or hidden I/O. */
object NativeTextRepertoires {
    // CP437's low-byte pictographs and DEL glyph are deliberately excluded: they are controls
    // in printer streams. U+00A0 retains its exact 0xff mapping and never becomes a breakable space.
    private const val cp437High =
        "ÇüéâäàåçêëèïîìÄÅ" +
        "ÉæÆôöòûùÿÖÜ¢£¥₧ƒ" +
        "áíóúñÑªº¿⌐¬½¼¡«»" +
        "░▒▓│┤╡╢╖╕╣║╗╝╜╛┐" +
        "└┴┬├─┼╞╟╚╔╩╦╠═╬╧" +
        "╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀" +
        "αßΓπΣσµτΦΘΩδ∞φε∩" +
        "≡±≥≤⌠⌡÷≈°∙·√ⁿ²■\u00a0"

    fun assess(
        text: String,
        repertoire: String,
        resources: TextResourcePolicy = TextResourcePolicy(),
    ): NativeCoverageResult {
        if (text.length > resources.maxCodeUnits) return NativeCoverageResult.Unsupported(
            SnapshotList(listOf(TextDiagnostic("TXT120", "Native text exceeds resource policy"))))
        if (repertoire != "cp437") return NativeCoverageResult.Unknown(
            SnapshotList(listOf(TextDiagnostic("TXT102", "Native repertoire mapping is unavailable"))))
        val bytes = ArrayList<Int>(text.length)
        for (character in text) {
            val byte = if (character.code in 0x20..0x7e) character.code else {
                val index = cp437High.indexOf(character)
                if (index < 0) return NativeCoverageResult.Unsupported(
                    SnapshotList(listOf(TextDiagnostic("TXT100", "Text is outside the safe native repertoire"))))
                0x80 + index
            }
            bytes.add(byte)
        }
        return NativeCoverageResult.Representable(SnapshotList(bytes))
    }
}

/**
 * Fixed-cell measurement for a registered simple repertoire using caller-supplied printer metrics.
 * Unsupported sequences fail as a whole; this is not a Unicode shaping or raster backend.
 */
class RegisteredFixedCellMeasurer(
    private val repertoire: String = "cp437",
    private val resources: TextResourcePolicy = TextResourcePolicy(),
) : TextMeasurer {
    override fun measure(request: TextMeasureRequest): TextMeasureResult {
        val coverage = NativeTextRepertoires.assess(request.text, repertoire, resources)
        when (coverage) {
            is NativeCoverageResult.Unsupported -> return TextMeasureResult.Failure(coverage.diagnostics)
            is NativeCoverageResult.Unknown -> return TextMeasureResult.Failure(coverage.diagnostics)
            is NativeCoverageResult.Representable -> Unit
        }
        fun failure(code: String, message: String) = TextMeasureResult.Failure(
            SnapshotList(listOf(TextDiagnostic(code, message))))
        val cell = request.typography.fixedCell ?: return failure("TXT101", "Fixed-cell geometry is unavailable")
        val ticks = try { LogicalGeometry.ticks(cell.advanceMm) } catch (_: IllegalArgumentException) {
            return failure("TXT120", "Text advance exceeds coordinate range")
        }
        if (request.text.isNotEmpty() && ticks > LogicalGeometry.maxTicks / request.text.length) {
            return failure("TXT120", "Text advance exceeds coordinate range")
        }
        val cellAdvance = LogicalGeometry.millimetres(ticks)
        val clusters = ArrayList<MeasuredCluster>(request.text.length)
        val breaks = mutableListOf<BreakOpportunity>()
        request.text.forEachIndexed { index, character ->
            clusters.add(MeasuredCluster(index, index + 1, cellAdvance))
            if (character == ' ') breaks.add(BreakOpportunity(index + 1, BreakKind.ALLOWED))
        }
        return TextMeasureResult.Success(TextMeasurement(
            request.text.length, LogicalGeometry.millimetres(ticks * request.text.length),
            request.typography.metrics, SnapshotList(clusters), SnapshotList(breaks)))
    }
}
