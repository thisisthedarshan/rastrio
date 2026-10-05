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

/** UTF-16 offsets; a cluster is indivisible for emergency breaking. End is exclusive. */
data class MeasuredCluster(val startOffset: Int, val endOffset: Int, val advanceMm: Double) {
    init {
        require(startOffset >= 0 && endOffset > startOffset)
        require(advanceMm.isFinite() && advanceMm >= 0.0)
    }
}
enum class BreakKind { ALLOWED, MANDATORY }
/** UTF-16 boundary after a cluster; layout chooses whether to use an allowed break. */
data class BreakOpportunity(val offset: Int, val kind: BreakKind) {
    init { require(offset > 0) }
}
data class TextDiagnostic(val code: String, val message: String)
data class TextMeasureRequest(val text: String, val typography: ResolvedTypography)

/**
 * Complete measurement of one supplied run. Implementations must partition text into grapheme-safe
 * clusters, provide stable metrics/coverage, and fail when required shaping cannot be supplied.
 * Cluster advances include shaping effects when the backend supports shaping. This is not a glyph
 * painting plan; a later shaping backend may add portable glyph data without exposing native handles.
 */
data class TextMeasurement(
    val textLength: Int,
    val advanceMm: Double,
    val metrics: TypographyMetrics,
    val clusters: SnapshotList<MeasuredCluster>,
    val breakOpportunities: SnapshotList<BreakOpportunity>,
) {
    init {
        require(textLength >= 0 && advanceMm.isFinite() && advanceMm >= 0.0)
        var end = 0
        var totalAdvance = 0L
        for (cluster in clusters) {
            require(cluster.startOffset == end && cluster.endOffset <= textLength)
            end = cluster.endOffset
            val advance = LogicalGeometry.ticks(cluster.advanceMm)
            require(totalAdvance <= LogicalGeometry.maxTicks - advance)
            totalAdvance += advance
        }
        require(end == textLength && totalAdvance == LogicalGeometry.ticks(advanceMm))
        // Validate in linear time without allocating a second boundary collection.
        var clusterIndex = 0
        var previous = 0
        for (opportunity in breakOpportunities) {
            require(opportunity.offset > previous && opportunity.offset <= textLength)
            while (clusterIndex < clusters.size && clusters[clusterIndex].endOffset < opportunity.offset) clusterIndex++
            require(clusterIndex < clusters.size && clusters[clusterIndex].endOffset == opportunity.offset)
            previous = opportunity.offset
        }
    }
}
sealed interface TextMeasureResult {
    data class Success(val measurement: TextMeasurement) : TextMeasureResult
    data class Failure(val diagnostics: SnapshotList<TextDiagnostic>) : TextMeasureResult {
        init { require(diagnostics.isNotEmpty()) }
    }
}

/** Injected, deterministic for identical requests and controlled backend/resources. No hidden I/O. */
fun interface TextMeasurer {
    fun measure(request: TextMeasureRequest): TextMeasureResult
}

/** Trusted application policy; initial per-run bound matches the portable string ceiling. */
data class TextResourcePolicy(val maxCodeUnits: Int = 64 * 1024) {
    init { require(maxCodeUnits > 0) }
}

/**
 * Phase 3A baseline only: printable ASCII U+0020..U+007E, one explicitly supplied cell per character.
 * It is not Unicode segmentation, a font-coverage database or a shaping engine. Tabs, control codes,
 * line breaks and every non-ASCII sequence fail without partial/fabricated geometry.
 */
class AsciiFixedCellMeasurer(private val resources: TextResourcePolicy = TextResourcePolicy()) : TextMeasurer {
    override fun measure(request: TextMeasureRequest): TextMeasureResult {
        fun failure(code: String, message: String) = TextMeasureResult.Failure(SnapshotList(listOf(TextDiagnostic(code, message))))
        if (request.text.length > resources.maxCodeUnits) return failure("TXT120", "Text measurement exceeds resource policy")
        val cell = request.typography.fixedCell ?: return failure("TXT101", "Fixed-cell geometry is unavailable")
        if (request.text.any { it.code !in 0x20..0x7e }) return failure("TXT100", "Text requires a different measurement backend")
        val cellTicks = try { LogicalGeometry.ticks(cell.advanceMm) } catch (_: IllegalArgumentException) {
            return failure("TXT120", "Text advance exceeds coordinate range")
        }
        if (request.text.isNotEmpty() && cellTicks > LogicalGeometry.maxTicks / request.text.length) {
            return failure("TXT120", "Text advance exceeds coordinate range")
        }
        val advance = LogicalGeometry.millimetres(cellTicks * request.text.length)
        val cellAdvance = LogicalGeometry.millimetres(cellTicks)
        val clusters = ArrayList<MeasuredCluster>(request.text.length)
        val breaks = mutableListOf<BreakOpportunity>()
        request.text.forEachIndexed { index, character ->
            clusters.add(MeasuredCluster(index, index + 1, cellAdvance))
            if (character == ' ') breaks.add(BreakOpportunity(index + 1, BreakKind.ALLOWED))
        }
        return TextMeasureResult.Success(TextMeasurement(request.text.length, advance, request.typography.metrics,
            SnapshotList(clusters), SnapshotList(breaks)))
    }
}
