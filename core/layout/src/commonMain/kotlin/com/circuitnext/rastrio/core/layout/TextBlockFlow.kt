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
private data class MeasuredSpan(val text: String, val typography: ResolvedTypography, val measurement: TextMeasurement,
    val sourceStartOffset: Int)
private data class FlowAtom(val span: MeasuredSpan?, val clusterIndex: Int = 0,
    val breakKind: BreakKind? = null, val tabColumnMm: Double? = null, val sourceStartOffset: Int = 0) {
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

internal data class ResolvedMarkerText(val text: String, val typography: ResolvedTypography,
    val measurement: TextMeasurement, val flowId: Int)

/** One shared paragraph, heading and code flow; finalized run measurements include tab advances. */
internal class TextBlockFlow(private val measurer: TextMeasurer, private val constraints: LayoutConstraints,
    private val charge: (Int, Int) -> Unit, private val diagnostics: MutableList<LayoutDiagnostic>,
    private val typographyResolver: TextFlowTypographyResolver? = null) {
    private var nextFlowId = 1
    private var measuredCodeUnits = 0L
    private val policy get() = constraints.resources
    fun measureMarker(text: String, typography: ResolvedTypography, index: Int): ResolvedMarkerText {
        val flowId = nextFlowId++
        val resolved = typographyResolver?.resolve(flowId, 0, text, typography)
            ?: SnapshotList(listOf(ResolvedTextSpan(text, typography)))
        if (resolved.size != 1 || resolved.single().text != text)
            abort("LAY103", "Ordered marker typography must preserve one complete text run", index)
        val effective = resolved.single().typography
        return ResolvedMarkerText(text, effective, measureUnresolved(text, effective, index), flowId)
    }
    fun measureUnresolved(text: String, typography: ResolvedTypography, index: Int): TextMeasurement {
        charge(1, index)
        return measure(text, typography, index).also { charge(it.clusters.size, index) }
    }
    fun layout(block: DocumentBlock, index: Int, originX: Double, width: Double, initialY: Double,
        cellContent: Boolean = false): Pair<LogicalTextBlock, Double> {
        val flowId = nextFlowId++
        var sourceOffset = 0
        var pendingStartOffset = 0
        var y = initialY
        if (block is CodeBlock) y = LogicalGeometry.add(y, LogicalGeometry.scale(constraints.typography.body.metrics.lineHeightMm, TextLayoutPolicyV1.codeBeforeLineFactor))
        val columns = mutableMapOf<Pair<ResolvedTypography, Boolean>, Double>()
        charge(1, index)
        val base = when (block) { is Heading -> constraints.typography.heading(block.level); is CodeBlock -> constraints.typography.code; else -> constraints.typography.body }
        val alignment = when (block) { is Paragraph -> block.alignment; is Heading -> block.alignment; is CodeBlock -> Alignment.LEFT; else -> error("Preflight failed") }
        val atoms = mutableListOf<FlowAtom>()
        var pendingTypography = base
        val pendingText = StringBuilder()
        fun graphemeBoundaries(text: String): SnapshotList<Int> = try {
            UnicodeSegmentation.graphemeBoundaries(text)
        } catch (_: IllegalArgumentException) {
            abort("LAY103", "Typography resolver input exceeds Unicode limits or is malformed", index)
        }
        fun resolve(text: String, typography: ResolvedTypography, offset: Int): SnapshotList<ResolvedTextSpan> {
            val resolver = typographyResolver ?: return SnapshotList(listOf(ResolvedTextSpan(text, typography)))
            val spans = resolver.resolve(flowId, offset, text, typography)
            if (spans.isEmpty() || spans.size > text.length.coerceAtLeast(1))
                abort("LAY103", "Typography resolver returned an invalid partition", index)
            charge(spans.size, index)
            val boundaries = graphemeBoundaries(text)
            var position = 0
            for (span in spans) {
                if (span.text.isEmpty() || span.text.length > text.length - position ||
                    !text.regionMatches(position, span.text, 0, span.text.length))
                    abort("LAY103", "Typography resolver must preserve source text", index)
                position += span.text.length
                if (boundaries.binarySearch(position) < 0)
                    abort("LAY103", "Typography resolver split a grapheme cluster", index)
            }
            if (position != text.length) abort("LAY103", "Typography resolver must preserve source text", index)
            if (spans.size == 1) return spans
            val merged = mutableListOf<ResolvedTextSpan>()
            var first = 0
            while (first < spans.size) {
                var next = first + 1
                while (next < spans.size && spans[next].typography == spans[first].typography) next++
                val mergedText = if (next == first + 1) spans[first].text else buildString {
                    for (part in first until next) append(spans[part].text)
                }
                merged.add(ResolvedTextSpan(mergedText, spans[first].typography))
                first = next
            }
            return SnapshotList(merged)
        }
        fun flush() {
            if (pendingText.isEmpty()) return
            charge(1, index) // reserve the measured span before its string/service allocations
            val text = pendingText.toString()
            pendingText.clear()
            var offset = pendingStartOffset
            for (resolved in resolve(text, pendingTypography, pendingStartOffset)) {
                val measured = measure(resolved.text, resolved.typography, index)
                charge(measured.clusters.size, index)
                val span = MeasuredSpan(resolved.text, resolved.typography, measured, offset)
                offset += resolved.text.length
                var breakIndex = 0
                for ((clusterIndex, cluster) in measured.clusters.withIndex()) {
                    val opportunity = measured.breakOpportunities.getOrNull(breakIndex)
                    val kind = if (opportunity?.offset == cluster.endOffset) { breakIndex++; opportunity.kind } else null
                    atoms.add(FlowAtom(span, clusterIndex, kind))
                }
            }
        }
        fun appendPending(text: String, from: Int, to: Int) {
            if (pendingText.isEmpty()) pendingStartOffset = sourceOffset
            pendingText.append(text, from, to)
            sourceOffset += to - from
        }
        fun appendText(text: String, resolved: ResolvedTypography, startOffset: Int = 0, endOffset: Int = text.length, isCode: Boolean = block is CodeBlock) {
            if (startOffset == endOffset) return
            if (resolved != pendingTypography) {
                if (typographyResolver != null && pendingText.isNotEmpty()) {
                    val previous = pendingText.toString()
                    val previousBoundaries = graphemeBoundaries(previous)
                    val nextText = text.substring(startOffset, endOffset)
                    val nextBoundaries = graphemeBoundaries(nextText)
                    val tail = previous.substring(previousBoundaries[previousBoundaries.size - 2])
                    val head = nextText.substring(0, nextBoundaries[1])
                    if (graphemeBoundaries(tail + head).binarySearch(tail.length) < 0)
                        abort("LAY103", "Semantic styles split a grapheme cluster", index)
                }
                flush()
                pendingTypography = resolved
            }
            var from = startOffset
            for (offset in startOffset until endOffset) if (text[offset] == '\t') {
                appendPending(text, from, offset)
                flush()
                charge(2, index) // tab span and cluster/atom before retaining either
                val tabTypography = resolve("\t", resolved, sourceOffset).single().typography
                val column = columns.getOrPut(tabTypography to isCode) {
                    if (isCode && tabTypography.fixedCell != null)
                        LogicalGeometry.normalize(tabTypography.fixedCell!!.advanceMm)
                    else {
                        charge(1, index)
                        val space = measure(" ", tabTypography, index)
                        charge(space.clusters.size, index)
                        LogicalGeometry.normalize(space.advanceMm)
                    }
                }
                if (column == 0.0) abort("LAY122", "Tab column cannot make coordinate progress", index)
                val measurement = TextMeasurement(1, 0.0, tabTypography.metrics,
                    SnapshotList(listOf(MeasuredCluster(0, 1, 0.0))), SnapshotList(emptyList()))
                atoms.add(FlowAtom(MeasuredSpan("\t", tabTypography, measurement, sourceOffset), 0, BreakKind.ALLOWED, column))
                sourceOffset++
                from = offset + 1
            }
            appendPending(text, from, endOffset)
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
                    atoms.add(FlowAtom(null, sourceStartOffset = sourceOffset))
                    if (character == '\r' && block.text.getOrNull(offset + 1) == '\n') offset++
                    sourceOffset++ // CRLF and semantic breaks each occupy one normalized source unit
                    from = offset + 1
                }
                offset++
            }
            appendText(block.text, base, from)
        } else walkInline(content(block, index), policy, index) { node, style ->
            if (node == LineBreak) {
                flush()
                charge(1, index) // semantic break markers are retained even before line generation
                atoms.add(FlowAtom(null, sourceStartOffset = sourceOffset))
                sourceOffset++
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
            charge(1, index)
            var spans: MutableList<MeasuredSpan>
            var slices: MutableList<Pair<Int, Int>>
            var tabColumns: MutableList<Double?>
            var measurements: List<TextMeasurement>
            var corrections = 0
            while (true) {
                spans = mutableListOf()
                slices = mutableListOf()
                tabColumns = mutableListOf()
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
                    spans.add(MeasuredSpan("", base, TextMeasurement(0, 0.0, base.metrics, SnapshotList(emptyList()), SnapshotList(emptyList())), atoms.getOrNull(start)?.sourceStartOffset ?: sourceOffset))
                    tabColumns.add(null)
                    slices.add(0 to 0)
                }
                advance = 0.0
                measurements = spans.mapIndexed { i, span ->
                    val measurement = if (span.text == "\t") {
                        val tabWidth = tabAdvance(advance, tabColumns[i]!!)
                        TextMeasurement(1, tabWidth, span.measurement.metrics,
                            SnapshotList(listOf(MeasuredCluster(0, 1, tabWidth))), SnapshotList(emptyList()))
                    } else {
                        val (from, to) = slices[i]
                        if (from == 0 && to == span.measurement.clusters.size || span.measurement.canSliceWithoutReshaping)
                            slice(span.measurement, from, to)
                        else {
                            charge(1, index)
                            val offset = span.measurement.clusters[from].startOffset
                            val limit = span.measurement.clusters[to - 1].endOffset
                            measure(span.text.substring(offset, limit), span.typography, index).also {
                                charge(it.clusters.size, index)
                            }
                        }
                    }
                    advance = LogicalGeometry.add(advance, measurement.advanceMm)
                    measurement
                }
                if (LogicalGeometry.fits(advance, width)) { overflow = false; break }
                // Only contract: shaping need not have monotone widths as more text is added.
                var prefix = 0.0
                var fittingEnd = start
                var fittingLegalEnd = start
                var firstSafeEnd = start
                var atomStart = start
                for (i in spans.indices) {
                    val span = spans[i]
                    val (from, to) = slices[i]
                    val sourceOffsetInSpan = if (from == to) 0 else span.measurement.clusters[from].startOffset
                    var original = from
                    var breakIndex = 0
                    for (cluster in measurements[i].clusters) {
                        prefix = LogicalGeometry.add(prefix, cluster.advanceMm)
                        val boundary = sourceOffsetInSpan + cluster.endOffset
                        while (original < to && span.measurement.clusters[original].endOffset < boundary) original++
                        if (original == to || span.measurement.clusters[original].endOffset != boundary) continue
                        val safeEnd = atomStart + original - from + 1
                        if (firstSafeEnd == start) firstSafeEnd = safeEnd
                        while (breakIndex < measurements[i].breakOpportunities.size &&
                            measurements[i].breakOpportunities[breakIndex].offset < cluster.endOffset) breakIndex++
                        if (LogicalGeometry.fits(prefix, width)) {
                            fittingEnd = safeEnd
                            if (measurements[i].breakOpportunities.getOrNull(breakIndex)?.offset == cluster.endOffset)
                                fittingLegalEnd = safeEnd
                        }
                    }
                    atomStart += to - from
                }
                val shorter = when {
                    fittingLegalEnd > start -> fittingLegalEnd
                    fittingEnd > start -> fittingEnd
                    else -> firstSafeEnd
                }
                if (shorter == end) { overflow = true; break } // one indivisible oversized shaping cluster
                if (shorter <= start || shorter > end || ++corrections > 8)
                    abort("LAY123", "Contextual line measurement did not converge", index)
                end = shorter
            }
            // A semantic break immediately after the finalized line belongs to that line.
            explicit = end < atoms.size && atoms[end].span == null
            if (overflow && !overflowReported) {
                charge(1, index)
                diagnostics.add(LayoutDiagnostic("LAY101", "One or more clusters exceed available width", index))
                overflowReported = true
            }
            var baselineOffset = 0.0
            var belowBaseline = 0.0
            for (measurement in measurements) {
                val offset = LogicalGeometry.normalize(measurement.metrics.baselineOffsetMm)
                val height = LogicalGeometry.normalize(measurement.metrics.lineHeightMm)
                if (height == 0.0) abort("LAY122", "Layout cannot make coordinate progress", index)
                if (LogicalGeometry.fits(baselineOffset, offset)) baselineOffset = offset
                val below = LogicalGeometry.subtract(height, offset)
                if (LogicalGeometry.fits(belowBaseline, below)) belowBaseline = below
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
                    LogicalBounds(runX, runY, LogicalGeometry.normalize(measured.advanceMm), LogicalGeometry.normalize(measured.metrics.lineHeightMm)), span.typography, measured, flowId, span.sourceStartOffset + from)
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
        if (cellContent) return output to y
        val factor = when (block) { is Heading -> TextLayoutPolicyV1.headingAfterLineFactor; is CodeBlock -> TextLayoutPolicyV1.codeAfterLineFactor; else -> TextLayoutPolicyV1.paragraphAfterLineFactor }
        val nextY = LogicalGeometry.add(y, LogicalGeometry.scale(constraints.typography.body.metrics.lineHeightMm, factor))
        if (LogicalGeometry.fits(nextY, y)) abort("LAY122", "Layout cannot make coordinate progress", index)
        y = nextY
        return output to y
    }
    private fun measure(text: String, typography: ResolvedTypography, index: Int): TextMeasurement {
        val maximumWork = policy.maxTextCodeUnits.toLong() * 8
        if (text.length.toLong() > maximumWork - measuredCodeUnits)
            abort("LAY120", "Contextual measurement work exceeds layout policy", index)
        measuredCodeUnits += text.length
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
        return TextMeasurement(end - offset, advance, source.metrics, SnapshotList(clusters), SnapshotList(breaks),
            canSliceWithoutReshaping = source.canSliceWithoutReshaping)
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
