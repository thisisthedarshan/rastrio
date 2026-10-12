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

package com.circuitnext.rastrio.core.printer

import com.circuitnext.rastrio.core.document.*
import com.circuitnext.rastrio.core.layout.*
import com.circuitnext.rastrio.core.profile.PrinterProfile
import com.circuitnext.rastrio.core.text.*

internal fun isRasterTypography(typography: ResolvedTypography) = !typography.identity.startsWith("native:")

internal data class PhysicalTextRow(val yMm: Double, val heightMm: Double,
    val runs: List<LogicalTextRun>, val hasGraphicMarker: Boolean)

/** Traversal only: all geometry remains owned by core-layout. */
internal fun physicalTextRows(blocks: List<LogicalBlock>): List<PhysicalTextRow> {
    val rows = mutableListOf<PhysicalTextRow>()
    val stack = mutableListOf(blocks.iterator())
    while (stack.isNotEmpty()) {
        if (!stack.last().hasNext()) { stack.removeAt(stack.lastIndex); continue }
        val children = when (val block = stack.last().next()) {
            is LogicalTextBlock -> { block.lines.forEach { rows.add(PhysicalTextRow(it.bounds.yMm, it.bounds.heightMm, it.runs, false)) }; null }
            is LogicalTableBlock -> { for (row in block.rows) for (cell in row.cells) cell.text.lines.forEach {
                rows.add(PhysicalTextRow(it.bounds.yMm, it.bounds.heightMm, it.runs, false)) }; null }
            is LogicalQuoteBlock -> block.blocks.iterator()
            is LogicalListBlock -> {
                for (item in block.items) {
                    val marker = item.marker
                    rows.add(PhysicalTextRow(marker.bounds.yMm, marker.bounds.heightMm,
                        if (marker is LogicalOrderedMarker) listOf(marker.run) else emptyList(), marker !is LogicalOrderedMarker))
                }
                sequence { for (item in block.items) yieldAll(item.blocks) }.iterator()
            }
            else -> null
        }
        if (children != null) stack.add(children)
    }
    return rows
}

/** Monotone source-range promotion, bounded re-layout through the existing logical engine. */
internal fun layoutWithTextStrategies(document: ThermalDocument, constraints: LayoutConstraints,
    profile: PrinterProfile, options: PrintOptions, nativeMeasurer: TextMeasurer,
    backend: RasterTextBackend?, resources: PreparationResourcePolicy, isCancelled: () -> Boolean): LogicalDocument {
    val promoted = mutableMapOf<Int, MutableList<IntRange>>()
    var promotionCount = 0
    fun native(text: String, style: TextStyle): Boolean = profile.nativeText.supported &&
        !style.emphasis && !style.strike && (style.weight != TextWeight.BOLD || profile.nativeText.styles.bold) &&
        (!(style.underline || options.nativeUnderline) || profile.nativeText.styles.underline) &&
        NativeTextRepertoires.assess(if (text == "\t") " " else text, profile.nativeText.codePages.firstOrNull { it.id == options.codePageId }?.repertoire
            ?: profile.nativeText.codePages.firstOrNull()?.repertoire ?: "", TextResourcePolicy()) is NativeCoverageResult.Representable
    fun typography(style: TextStyle, supplied: ResolvedTypography): ResolvedTypography {
        val renderer = backend ?: return supplied.copy(identity = "raster-unavailable")
        if (options.widthScale != options.heightScale) return supplied.copy(identity = "raster-scale-unavailable")
        return try { renderer.typography(style.copy(underline = style.underline || options.nativeUnderline),
            if (profile.nativeText.supported) constraints.typography.body.metrics.ascentMm + constraints.typography.body.metrics.descentMm else 3.0) }
        catch (_: TextBackendUnavailableException) { supplied.copy(identity = "raster-unavailable") }
    }
    val resolver = TextFlowTypographyResolver { flow, offset, text, supplied ->
        if (isCancelled()) fail("PRN130", "Preparation cancelled")
        if (backend == null && profile.nativeText.supported &&
            NativeTextRepertoires.assess(if (text == "\t") " " else text, profile.nativeText.codePages.first().repertoire) is NativeCoverageResult.Representable &&
            !native(text, supplied.style)) fail("PRN102", "Requested text style has no supported native strategy")
        val boundaries = UnicodeSegmentation.graphemeBoundaries(text)
        val output = mutableListOf<ResolvedTextSpan>()
        val ranges = promoted[flow].orEmpty()
        var rangeIndex = ranges.binarySearch { if (it.last < offset) -1 else if (it.first > offset) 1 else 0 }
        if (rangeIndex < 0) rangeIndex = -rangeIndex - 1
        var start = 0
        var raster: Boolean? = null
        for (index in 0 until boundaries.lastIndex) {
            val from = boundaries[index]
            val to = boundaries[index + 1]
            while (rangeIndex < ranges.size && ranges[rangeIndex].last < offset + from) rangeIndex++
            val forced = rangeIndex < ranges.size && ranges[rangeIndex].first < offset + to
            val nextRaster = forced || !native(text.substring(from, to), supplied.style)
            if (raster != null && raster != nextRaster) {
                output.add(ResolvedTextSpan(text.substring(start, from), if (raster) typography(supplied.style, supplied) else supplied))
                start = from
            }
            raster = nextRaster
        }
        if (text.isNotEmpty()) output.add(ResolvedTextSpan(text.substring(start), if (raster == true) typography(supplied.style, supplied) else supplied))
        else output.add(ResolvedTextSpan(text, supplied))
        SnapshotList(output)
    }
    val measurer = TextMeasurer { request ->
        if (isCancelled()) fail("PRN130", "Preparation cancelled")
        if (request.typography.identity == "raster-scale-unavailable")
            TextMeasureResult.Failure(SnapshotList(listOf(TextDiagnostic("PRN102", "Non-uniform text scaling has no controlled raster representation"))))
        else if (isRasterTypography(request.typography)) backend?.measure(request, isCancelled)
            ?: TextMeasureResult.Failure(SnapshotList(listOf(TextDiagnostic("TXT132", "Controlled raster text backend is unavailable"))))
        else nativeMeasurer.measure(request)
    }
    repeat(8) { iteration ->
        val logical = when (val result = FoundationLayoutEngine(measurer, resolver).layout(document, constraints)) {
            is LayoutResult.Success -> result.document
            is LayoutResult.Failure -> throw PreparationAbort(SnapshotList(result.diagnostics.map { PreparationDiagnostic(it.code, it.message, it.sourceBlockIndex) }))
        }
        val rows = physicalTextRows(logical.blocks).sortedBy { it.yMm }
        val intervals = mutableListOf<Pair<Double, Double>>()
        for (row in rows) if (row.hasGraphicMarker || row.runs.any { isRasterTypography(it.typography) }) {
            val end = row.yMm + row.heightMm
            val previous = intervals.lastOrNull()
            if (previous != null && row.yMm < previous.second) intervals[intervals.lastIndex] = previous.first to maxOf(previous.second, end)
            else intervals.add(row.yMm to end)
        }
        var changed = false
        var interval = 0
        for (row in rows) {
            if (isCancelled()) fail("PRN130", "Preparation cancelled")
            while (interval < intervals.size && intervals[interval].second <= row.yMm) interval++
            if (interval == intervals.size) break
            if (intervals[interval].first >= row.yMm + row.heightMm) continue
            for (run in row.runs) if (run.sourceFlowId > 0 && run.text.isNotEmpty() && !isRasterTypography(run.typography)) {
                val from = run.sourceStartOffset
                val to = from + run.text.length - 1
                val ranges = promoted.getOrPut(run.sourceFlowId) { mutableListOf() }
                if (ranges.none { it.first <= from && it.last >= to }) {
                    if (++promotionCount > resources.maxOperations) fail("PRN120", "Text strategy ranges exceed resource policy")
                    var first = from
                    var last = to
                    var index = 0
                    while (index < ranges.size) {
                        val range = ranges[index]
                        if (range.first <= last + 1 && range.last + 1 >= first) {
                            first = minOf(first, range.first); last = maxOf(last, range.last); ranges.removeAt(index)
                        } else index++
                    }
                    ranges.add(first..last)
                    ranges.sortBy { it.first }
                    changed = true
                }
            }
        }
        if (!changed) {
            if (logical.diagnostics.size + (if (iteration > 0) 1 else 0) > resources.maxDiagnostics) fail("PRN120", "Diagnostic count exceeds preparation policy")
            return if (iteration == 0) logical else logical.copy(diagnostics = SnapshotList(logical.diagnostics +
                LayoutDiagnostic("PRN202", "Raster text metrics required authoritative layout re-resolution")))
        }
    }
    fail("PRN143", "Text strategy layout did not converge within preparation policy")
}
