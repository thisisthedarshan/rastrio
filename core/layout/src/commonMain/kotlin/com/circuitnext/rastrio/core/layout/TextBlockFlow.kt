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

internal class LayoutAbort(val diagnostics: SnapshotList<LayoutDiagnostic>) : RuntimeException()
internal fun abort(code: String, message: String, block: Int? = null): Nothing =
    throw LayoutAbort(SnapshotList(listOf(LayoutDiagnostic(code, message, block))))

/** Preserve service diagnostics, reserving their bounded conversion before copying. */
internal fun failMeasurement(diagnostics: SnapshotList<TextDiagnostic>, index: Int, charge: (Int, Int) -> Unit): Nothing {
    charge(diagnostics.size, index)
    throw LayoutAbort(SnapshotList(diagnostics.map { LayoutDiagnostic(it.code, it.message, index) }))
}

private data class FlowInlineFrame(val iterator: Iterator<InlineContent>, val style: TextStyle)
private data class MeasuredSpan(val text: String, val typography: ResolvedTypography, val measurement: TextMeasurement)
private data class FlowAtom(val span: MeasuredSpan?, val clusterIndex: Int = 0,
    val breakKind: BreakKind? = null, val tabColumnMm: Double? = null) {
    fun advanceAt(position: Double): Double = tabColumnMm?.let { tabAdvance(position, it) }
        ?: span?.measurement?.clusters?.get(clusterIndex)?.advanceMm ?: 0.0
}

/** Stop selection is layout geometry; all arithmetic uses the shared logical grid. */
private fun tabAdvance(position: Double, column: Double): Double {
    val stop = LogicalGeometry.ticks(LogicalGeometry.scale(column, 4.0))
    require(stop > 0)
    val current = LogicalGeometry.ticks(position)
    return LogicalGeometry.millimetres(stop - current % stop)
}

/** One shared paragraph, heading and code flow; finalized run measurements include tab advances. */
internal class TextBlockFlow(private val measurer: TextMeasurer, private val constraints: LayoutConstraints,
    private val charge: (Int, Int) -> Unit, private val diagnostics: MutableList<LayoutDiagnostic>) {
    private val policy get() = constraints.resources
    fun layout(block: DocumentBlock, index: Int, originX: Double, width: Double, initialY: Double): Pair<LogicalTextBlock, Double> {
        var y = initialY
        if (block is CodeBlock) y = LogicalGeometry.add(y, LogicalGeometry.scale(constraints.typography.body.metrics.lineHeightMm, TextLayoutPolicyV1.codeBeforeLineFactor))
        val columns = mutableMapOf<Pair<ResolvedTypography, Boolean>, Double>()
        charge(1, index)
        val base = when (block) { is Heading -> constraints.typography.heading(block.level); is CodeBlock -> constraints.typography.code; else -> constraints.typography.body }
        val alignment = when (block) { is Paragraph -> block.alignment; is Heading -> block.alignment; is CodeBlock -> Alignment.LEFT; else -> error("Preflight failed") }
        val atoms = mutableListOf<FlowAtom>()
        var pendingTypography = base
        val pendingText = StringBuilder()
        fun flush() {
            if (pendingText.isEmpty()) return
            charge(1, index) // reserve the measured span before its string/service allocations
            val text = pendingText.toString()
            pendingText.clear()
            val measured = measure(text, pendingTypography, index)
            charge(measured.clusters.size, index)
            val span = MeasuredSpan(text, pendingTypography, measured)
            var breakIndex = 0
            for ((clusterIndex, cluster) in measured.clusters.withIndex()) {
                val opportunity = measured.breakOpportunities.getOrNull(breakIndex)
                val kind = if (opportunity?.offset == cluster.endOffset) { breakIndex++; opportunity.kind } else null
                atoms.add(FlowAtom(span, clusterIndex, kind))
            }
        }
        fun appendText(text: String, resolved: ResolvedTypography, startOffset: Int = 0, endOffset: Int = text.length, isCode: Boolean = block is CodeBlock) {
            if (resolved != pendingTypography) { flush(); pendingTypography = resolved }
            var from = startOffset
            for (offset in startOffset until endOffset) if (text[offset] == '\t') {
                pendingText.append(text, from, offset)
                flush()
                charge(2, index) // tab span and cluster/atom before retaining either
                val column = columns.getOrPut(resolved to isCode) {
                    if (isCode && resolved.fixedCell != null)
                        LogicalGeometry.normalize(resolved.fixedCell!!.advanceMm)
                    else {
                        charge(1, index)
                        val space = measure(" ", resolved, index)
                        charge(space.clusters.size, index)
                        LogicalGeometry.normalize(space.advanceMm)
                    }
                }
                if (column == 0.0) abort("LAY122", "Tab column cannot make coordinate progress", index)
                val measurement = TextMeasurement(1, 0.0, resolved.metrics,
                    SnapshotList(listOf(MeasuredCluster(0, 1, 0.0))), SnapshotList(emptyList()))
                atoms.add(FlowAtom(MeasuredSpan("\t", resolved, measurement), 0, BreakKind.ALLOWED, column))
                from = offset + 1
            }
            pendingText.append(text, from, endOffset)
        }
        if (block is CodeBlock) {
            var from = 0
            var offset = 0
            while (offset < block.text.length) {
                val character = block.text[offset]
                if (character == '\r' || character == '\n') {
                    appendText(block.text, base, from, offset)
                    flush()
                    charge(1, index)
                    atoms.add(FlowAtom(null))
                    if (character == '\r' && block.text.getOrNull(offset + 1) == '\n') offset++
                    from = offset + 1
                }
                offset++
            }
            appendText(block.text, base, from)
        } else walkInline(content(block, index), policy, index) { node, style ->
            if (node == LineBreak) {
                flush()
                charge(1, index) // semantic break markers are retained even before line generation
                atoms.add(FlowAtom(null))
            } else {
                val resolved = combine(if (node is InlineCode) constraints.typography.code else base, style)
                appendText(when (node) { is Text -> node.text; is InlineCode -> node.text; else -> error("Not a leaf") }, resolved, isCode = node is InlineCode)
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
                if (!LogicalGeometry.fits(atom.advanceAt(advance), LogicalGeometry.subtract(width, advance))) {
                    if (end == start) {
                        end++ // retain the oversized cluster intact
                        overflow = true
                    } else if (legalEnd > start) end = legalEnd
                    break
                }
                advance = LogicalGeometry.add(advance, atom.advanceAt(advance))
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
            val tabColumns = mutableListOf<Double?>()
            var cursor = start
            while (cursor < end) {
                val span = atoms[cursor].span!!
                val first = atoms[cursor].clusterIndex
                var next = cursor + 1
                while (next < end && atoms[next].span === span) next++
                charge(1, index)
                spans.add(span)
                tabColumns.add(atoms[cursor].tabColumnMm)
                slices.add(first to atoms[next - 1].clusterIndex + 1)
                cursor = next
            }
            if (spans.isEmpty()) {
                charge(1, index)
                spans.add(MeasuredSpan("", base, TextMeasurement(0, 0.0, base.metrics, SnapshotList(emptyList()), SnapshotList(emptyList()))))
                tabColumns.add(null)
                slices.add(0 to 0)
            }
            var baselineOffset = 0.0
            var belowBaseline = 0.0
            advance = 0.0
            val measurements = spans.mapIndexed { i, span ->
                val measurement = if (span.text == "\t") {
                    val tabWidth = tabAdvance(advance, tabColumns[i]!!)
                    TextMeasurement(1, tabWidth, span.measurement.metrics,
                        SnapshotList(listOf(MeasuredCluster(0, 1, tabWidth))), SnapshotList(emptyList()))
                } else slice(span.measurement, slices[i].first, slices[i].second)
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
            val x = LogicalGeometry.add(originX, when (alignment) { Alignment.LEFT -> 0.0; Alignment.CENTER -> LogicalGeometry.scale(remaining, 0.5); Alignment.RIGHT -> remaining })
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
        val output = LogicalTextBlock(index, LogicalBounds(originX, blockY, width, LogicalGeometry.subtract(y, blockY)), SnapshotList(lines),
            if (block is CodeBlock) LogicalTextKind.CODE else LogicalTextKind.TEXT)
        val factor = when (block) { is Heading -> TextLayoutPolicyV1.headingAfterLineFactor; is CodeBlock -> TextLayoutPolicyV1.codeAfterLineFactor; else -> TextLayoutPolicyV1.paragraphAfterLineFactor }
        val nextY = LogicalGeometry.add(y, LogicalGeometry.scale(constraints.typography.body.metrics.lineHeightMm, factor))
        if (LogicalGeometry.fits(nextY, y)) abort("LAY122", "Layout cannot make coordinate progress", index)
        y = nextY
        return output to y
    }
    private fun measure(text: String, typography: ResolvedTypography, index: Int): TextMeasurement {
        val result = measurer.measure(TextMeasureRequest(text, typography))
        val measured = when (result) {
            is TextMeasureResult.Success -> result.measurement
            is TextMeasureResult.Failure -> failMeasurement(result.diagnostics, index, charge)
        }
        if (measured.textLength != text.length || measured.metrics != typography.metrics)
            abort("LAY103", "Measurement does not match resolved typography or input", index)
        return measured
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

    /** Iterator frames bound stack and sibling allocations even for hostile nested trees. */
internal fun walkInline(content: List<InlineContent>, policy: LayoutResourcePolicy, index: Int,
        onNode: () -> Unit = {}, leaf: (InlineContent, TextStyle) -> Unit) {
        val stack = mutableListOf(FlowInlineFrame(content.iterator(), TextStyle()))
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
                stack.add(FlowInlineFrame(children.iterator(), style))
            }
        }
    }
