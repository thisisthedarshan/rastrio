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

package com.circuitnext.rastrio.core.layout

import com.circuitnext.rastrio.core.document.*
import com.circuitnext.rastrio.core.text.*

interface LogicalLayoutEngine {
    fun layout(document: ThermalDocument, constraints: LayoutConstraints): LayoutResult
}

/** Centrally versioned subset of TEXT_RENDERING_SPEC Section 17.3. */
object TextLayoutPolicyV1 {
    const val paragraphAfterLineFactor: Double = 0.5
    // Provisional Phase 3B heading spacing: the same body-based gap, without a new scale.
    const val headingAfterLineFactor: Double = 0.5
}

private class LayoutAbort(val diagnostics: SnapshotList<LayoutDiagnostic>) : RuntimeException()
private fun abort(code: String, message: String, block: Int? = null): Nothing =
    throw LayoutAbort(SnapshotList(listOf(LayoutDiagnostic(code, message, block))))

private data class InlineFrame(val iterator: Iterator<InlineContent>, val style: TextStyle)
private data class MeasuredSpan(val text: String, val typography: ResolvedTypography, val measurement: TextMeasurement)
/** A null span is a semantic LineBreak; other atoms refer to indivisible measured clusters. */
private data class FlowAtom(val span: MeasuredSpan?, val clusterIndex: Int = 0, val breakKind: BreakKind? = null) {
    val advanceMm: Double get() = span?.measurement?.clusters?.get(clusterIndex)?.advanceMm ?: 0.0
}

/**
 * Phase 3B paragraph/heading flow. Measures each contiguous resolved span once; wraps using
 * supplied opportunities, then measured cluster boundaries. Whitespace is preserved verbatim.
 * No platform measurement, printer strategy or hidden I/O participates in these decisions.
 */
class FoundationLayoutEngine(private val measurer: TextMeasurer) : LogicalLayoutEngine {
    override fun layout(document: ThermalDocument, constraints: LayoutConstraints): LayoutResult {
        var sourceIndex: Int? = null
        return try {
            if (document.schemaVersion != 1) abort("LAY102", "Unsupported document schema version")
            val policy = constraints.resources
            if (document.blocks.size > policy.maxBlocks) abort("LAY120", "Block count exceeds layout policy")
            var inlineCount = 0L
            var textCount = 0L
            // Complete preflight before measurement or flattening, including nested styles.
            for ((index, block) in document.blocks.withIndex()) {
                sourceIndex = index
                if (block is Heading && block.level !in 1..6) abort("LAY104", "Invalid heading level", index)
                walk(content(block, index), policy, index, onNode = {
                    inlineCount++
                    if (inlineCount > policy.maxInlineNodes) abort("LAY120", "Inline count exceeds layout policy", index)
                }) { node, _ ->
                    textCount += when (node) { is Text -> node.text.length.toLong(); is InlineCode -> node.text.length.toLong(); else -> 0L }
                    if (textCount > policy.maxTextCodeUnits) abort("LAY120", "Text count exceeds layout policy", index)
                }
            }

            var items = 0L
            fun charge(count: Int, index: Int) {
                if (count.toLong() > policy.maxItems.toLong() - items) abort("LAY120", "Geometry item count exceeds layout policy", index)
                items += count
            }
            val width = LogicalGeometry.normalize(constraints.canvasWidth.value)
            val blocks = mutableListOf<LogicalBlock>()
            val diagnostics = mutableListOf<LayoutDiagnostic>()
            var y = 0.0
            for ((index, block) in document.blocks.withIndex()) {
                sourceIndex = index
                charge(1, index)
                val base = if (block is Heading) constraints.typography.heading(block.level) else constraints.typography.body
                val alignment = when (block) { is Paragraph -> block.alignment; is Heading -> block.alignment; else -> error("Preflight failed") }
                val atoms = mutableListOf<FlowAtom>()
                var pendingTypography = base
                val pendingText = StringBuilder()
                fun flush() {
                    if (pendingText.isEmpty()) return
                    charge(1, index) // reserve the measured span before its string/service allocations
                    val text = pendingText.toString()
                    pendingText.clear()
                    val measured = when (val result = measurer.measure(TextMeasureRequest(text, pendingTypography))) {
                        is TextMeasureResult.Failure -> throw LayoutAbort(SnapshotList(result.diagnostics.map {
                            LayoutDiagnostic(it.code, it.message, index)
                        }))
                        is TextMeasureResult.Success -> result.measurement
                    }
                    if (measured.textLength != text.length || measured.metrics != pendingTypography.metrics) {
                        abort("LAY103", "Measurement does not match resolved typography or input", index)
                    }
                    charge(measured.clusters.size, index)
                    val span = MeasuredSpan(text, pendingTypography, measured)
                    var breakIndex = 0
                    for ((clusterIndex, cluster) in measured.clusters.withIndex()) {
                        val opportunity = measured.breakOpportunities.getOrNull(breakIndex)
                        val kind = if (opportunity?.offset == cluster.endOffset) { breakIndex++; opportunity.kind } else null
                        atoms.add(FlowAtom(span, clusterIndex, kind))
                    }
                }
                walk(content(block, index), policy, index) { node, style ->
                    if (node == LineBreak) {
                        flush()
                        charge(1, index) // semantic break markers are retained even before line generation
                        atoms.add(FlowAtom(null))
                    } else {
                        val resolved = combine(if (node is InlineCode) constraints.typography.code else base, style)
                        if (resolved != pendingTypography) { flush(); pendingTypography = resolved }
                        pendingText.append(when (node) { is Text -> node.text; is InlineCode -> node.text; else -> error("Not a leaf") })
                    }
                }
                flush()
                val blockY = y
                val lines = mutableListOf<LogicalTextLine>()
                var start = 0
                var overflowReported = false
                // A trailing semantic break leaves a final empty line; backend breaks do not.
                while (start < atoms.size || lines.isEmpty() || atoms.lastOrNull()?.span == null && start == atoms.size) {
                    var end = start
                    var advance = 0.0
                    var legalEnd = -1
                    var explicit = false
                    var overflow = false
                    while (end < atoms.size) {
                        val atom = atoms[end]
                        if (atom.span == null) { explicit = true; break }
                        if (!LogicalGeometry.fits(atom.advanceMm, LogicalGeometry.subtract(width, advance))) {
                            if (end == start) {
                                end++ // retain the oversized cluster intact
                                overflow = true
                            } else if (legalEnd > start) end = legalEnd
                            break
                        }
                        advance = LogicalGeometry.add(advance, atom.advanceMm)
                        end++
                        if (atom.breakKind != null) legalEnd = end
                        if (atom.breakKind == BreakKind.MANDATORY) break
                    }
                    // A semantic break immediately after the selected line belongs to that line.
                    if (end < atoms.size && atoms[end].span == null) explicit = true
                    charge(1, index)
                    if (overflow && !overflowReported) {
                        charge(1, index)
                        diagnostics.add(LayoutDiagnostic("LAY101", "One or more clusters exceed available width", index))
                        overflowReported = true
                    }
                    val spans = mutableListOf<MeasuredSpan>()
                    val slices = mutableListOf<Pair<Int, Int>>()
                    var cursor = start
                    while (cursor < end) {
                        val span = atoms[cursor].span!!
                        val first = atoms[cursor].clusterIndex
                        var next = cursor + 1
                        while (next < end && atoms[next].span === span) next++
                        charge(1, index)
                        spans.add(span)
                        slices.add(first to atoms[next - 1].clusterIndex + 1)
                        cursor = next
                    }
                    if (spans.isEmpty()) {
                        charge(1, index)
                        spans.add(MeasuredSpan("", base, TextMeasurement(0, 0.0, base.metrics, SnapshotList(emptyList()), SnapshotList(emptyList()))))
                        slices.add(0 to 0)
                    }
                    var baselineOffset = 0.0
                    var belowBaseline = 0.0
                    advance = 0.0
                    val measurements = spans.mapIndexed { i, span ->
                        val measurement = slice(span.measurement, slices[i].first, slices[i].second)
                        val offset = LogicalGeometry.normalize(measurement.metrics.baselineOffsetMm)
                        val height = LogicalGeometry.normalize(measurement.metrics.lineHeightMm)
                        if (height == 0.0) abort("LAY122", "Layout cannot make coordinate progress", index)
                        if (LogicalGeometry.fits(baselineOffset, offset)) baselineOffset = offset
                        val below = LogicalGeometry.subtract(height, offset)
                        if (LogicalGeometry.fits(belowBaseline, below)) belowBaseline = below
                        advance = LogicalGeometry.add(advance, measurement.advanceMm)
                        measurement
                    }
                    val height = LogicalGeometry.add(baselineOffset, belowBaseline)
                    val baseline = LogicalGeometry.add(y, baselineOffset)
                    val remaining = if (LogicalGeometry.fits(advance, width)) LogicalGeometry.subtract(width, advance) else 0.0
                    val x = when (alignment) { Alignment.LEFT -> 0.0; Alignment.CENTER -> LogicalGeometry.scale(remaining, 0.5); Alignment.RIGHT -> remaining }
                    var runX = x
                    val runs = spans.mapIndexed { i, span ->
                        val measured = measurements[i]
                        val from = if (slices[i].first == slices[i].second) 0 else span.measurement.clusters[slices[i].first].startOffset
                        val to = if (slices[i].first == slices[i].second) 0 else span.measurement.clusters[slices[i].second - 1].endOffset
                        val runY = LogicalGeometry.subtract(baseline, measured.metrics.baselineOffsetMm)
                        val run = LogicalTextRun(span.text.substring(from, to),
                            LogicalBounds(runX, runY, LogicalGeometry.normalize(measured.advanceMm), LogicalGeometry.normalize(measured.metrics.lineHeightMm)), span.typography, measured)
                        runX = LogicalGeometry.add(runX, measured.advanceMm)
                        run
                    }
                    lines.add(LogicalTextLine(LogicalBounds(x, y, advance, height), width, advance, baseline, alignment, SnapshotList(runs), explicit))
                    val bottom = LogicalGeometry.add(y, height)
                    if (LogicalGeometry.fits(bottom, y)) abort("LAY122", "Layout cannot make coordinate progress", index)
                    y = bottom
                    start = end + if (explicit) 1 else 0
                    if (start >= atoms.size && !explicit) break
                }
                blocks.add(LogicalTextBlock(index, LogicalBounds(0.0, blockY, width, LogicalGeometry.subtract(y, blockY)), SnapshotList(lines)))
                val factor = if (block is Heading) TextLayoutPolicyV1.headingAfterLineFactor else TextLayoutPolicyV1.paragraphAfterLineFactor
                val nextY = LogicalGeometry.add(y, LogicalGeometry.scale(constraints.typography.body.metrics.lineHeightMm, factor))
                if (LogicalGeometry.fits(nextY, y)) abort("LAY122", "Layout cannot make coordinate progress", index)
                y = nextY
            }
            LayoutResult.Success(LogicalDocument(constraints, y, SnapshotList(blocks), SnapshotList(diagnostics)))
        } catch (failure: LayoutAbort) {
            LayoutResult.Failure(failure.diagnostics)
        } catch (_: IllegalArgumentException) {
            LayoutResult.Failure(SnapshotList(listOf(LayoutDiagnostic("LAY122", "Layout exceeds coordinate range", sourceIndex))))
        }
    }

    private fun content(block: DocumentBlock, index: Int): List<InlineContent> = when (block) {
        is Paragraph -> block.content
        is Heading -> block.content
        else -> abort("LAY100", "Block layout is unavailable in the paragraph flow engine", index)
    }

    private fun combine(base: ResolvedTypography, semantic: TextStyle) = base.copy(style = base.style.copy(
        weight = if (semantic.weight == TextWeight.BOLD) TextWeight.BOLD else base.style.weight,
        emphasis = base.style.emphasis || semantic.emphasis,
        strike = base.style.strike || semantic.strike,
    ))

    /** Iterator frames bound stack and sibling allocations even for hostile nested trees. */
    private fun walk(content: List<InlineContent>, policy: LayoutResourcePolicy, index: Int,
        onNode: () -> Unit = {}, leaf: (InlineContent, TextStyle) -> Unit) {
        val stack = mutableListOf(InlineFrame(content.iterator(), TextStyle()))
        while (stack.isNotEmpty()) {
            val frame = stack.last()
            if (!frame.iterator.hasNext()) { stack.removeAt(stack.lastIndex); continue }
            onNode()
            val node = frame.iterator.next()
            val children = when (node) { is Strong -> node.children; is Emphasis -> node.children; is Strike -> node.children; is Link -> node.children; else -> null }
            if (children == null) leaf(node, frame.style) else {
                if (stack.size >= policy.maxInlineDepth) abort("LAY120", "Inline nesting exceeds layout policy", index)
                val style = when (node) {
                    is Strong -> frame.style.copy(weight = TextWeight.BOLD)
                    is Emphasis -> frame.style.copy(emphasis = true)
                    is Strike -> frame.style.copy(strike = true)
                    else -> frame.style
                }
                stack.add(InlineFrame(children.iterator(), style))
            }
        }
    }

    /** Slice finalized measurements without remeasurement, retaining local UTF-16 boundaries. */
    private fun slice(source: TextMeasurement, from: Int, to: Int): TextMeasurement {
        if (from == 0 && to == source.clusters.size) return source
        val offset = source.clusters[from].startOffset
        val end = source.clusters[to - 1].endOffset
        var advance = 0.0
        val clusters = (from until to).map { i ->
            val cluster = source.clusters[i]
            advance = LogicalGeometry.add(advance, cluster.advanceMm)
            MeasuredCluster(cluster.startOffset - offset, cluster.endOffset - offset, LogicalGeometry.normalize(cluster.advanceMm))
        }
        // Lower-bound search avoids rescanning all break opportunities for every narrow line.
        val result = source.breakOpportunities.binarySearch { it.offset.compareTo(offset) }
        var low = if (result >= 0) result + 1 else -result - 1
        val breaks = mutableListOf<BreakOpportunity>()
        while (low < source.breakOpportunities.size && source.breakOpportunities[low].offset <= end) {
            val opportunity = source.breakOpportunities[low++]
            breaks.add(opportunity.copy(offset = opportunity.offset - offset))
        }
        return TextMeasurement(end - offset, advance, source.metrics, SnapshotList(clusters), SnapshotList(breaks))
    }
}
