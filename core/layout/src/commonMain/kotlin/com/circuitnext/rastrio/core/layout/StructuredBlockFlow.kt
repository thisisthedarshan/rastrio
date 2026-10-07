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

private data class VisitFrame(val blocks: Iterator<DocumentBlock>, val depth: Int, val source: Int?, var position: Int = 0)

/** No recursive walk and no sibling-wide flattened copies, even before service work. */
internal fun preflightDocument(document: ThermalDocument, policy: LayoutResourcePolicy) {
    if (document.schemaVersion != 1) abort("LAY102", "Unsupported document schema version")
    var blockCount = 0L
    var itemCount = 0L
    var inlineCount = 0L
    var textCount = 0L
    fun countText(text: String, index: Int) {
        textCount = boundedCount(textCount, text.length.toLong(), policy.maxTextCodeUnits.toLong(), index)
    }
    fun countInline(content: List<InlineContent>, index: Int) {
        walkInline(content, policy, index, onNode = {
            if (++inlineCount > policy.maxInlineNodes) abort("LAY120", "Inline count exceeds layout policy", index)
        }) { node, _ ->
            when (node) { is Text -> countText(node.text, index); is InlineCode -> countText(node.text, index); else -> Unit }
        }
    }
    val stack = mutableListOf(VisitFrame(document.blocks.iterator(), 1, null))
    while (stack.isNotEmpty()) {
        val frame = stack.last()
        if (!frame.blocks.hasNext()) { stack.removeAt(stack.lastIndex); continue }
        val index = frame.source ?: frame.position
        frame.position++
        if (++blockCount > policy.maxBlocks) abort("LAY120", "Block count exceeds layout policy", index)
        val block = frame.blocks.next()
        val children: Iterator<DocumentBlock>? = when (block) {
            is Paragraph, is Heading -> {
                if (block is Heading && block.level !in 1..6) abort("LAY104", "Invalid heading level", index)
                val content = if (block is Paragraph) block.content else (block as Heading).content
                countInline(content, index)
                null
            }
            is Table -> {
                val shape = tableShape(block, policy, index)
                // Structural geometry plus the minimum block/line/run cost of every cell.
                itemCount = boundedCount(itemCount, shape.geometryCost + shape.cells * 3,
                    policy.maxItems.toLong(), index)
                for (cell in block.header) countInline(cell.content, index)
                for (row in block.rows) {
                    for (cell in row) countInline(cell.content, index)
                }
                null
            }
            is Image -> {
                itemCount = boundedCount(itemCount, 1, policy.maxItems.toLong(), index)
                when (val asset = block.asset) {
                    is EmbeddedAssetReference -> countText(asset.assetId, index)
                    is ExternalAssetReference -> countText(asset.uri, index)
                }
                block.altText?.let { countText(it, index) }
                (block.sizing as? RequestedWidthSizing)?.let { placeholderLength(it.width, policy, index) }
                null
            }
            is QrCode -> {
                itemCount = boundedCount(itemCount, 1, policy.maxItems.toLong(), index)
                countText(block.payload, index)
                block.requestedSize?.let { placeholderLength(it, policy, index) }
                null
            }
            is CodeBlock -> { countText(block.text, index); null }
            Separator -> null
            is Quote -> {
                if (block.blocks.isEmpty()) abort("LAY105", "Quote has no blocks", index)
                block.blocks.iterator()
            }
            is UnorderedList, is OrderedList, is Checklist -> {
                val count = when (block) { is UnorderedList -> block.items.size; is OrderedList -> block.items.size; else -> (block as Checklist).items.size }
                itemCount = boundedCount(itemCount, count.toLong(), policy.maxItems.toLong(), index)
                if (block is OrderedList) {
                    if (block.start < 0 || count > 0 && block.start > Long.MAX_VALUE - (count - 1).toLong())
                        abort("LAY106", "Ordered numbering is invalid or exceeds range", index)
                }
                sequence {
                    when (block) {
                        is UnorderedList -> for (item in block.items) {
                            if (item.blocks.isEmpty()) abort("LAY105", "List item has no blocks", index)
                            yieldAll(item.blocks)
                        }
                        is OrderedList -> for (item in block.items) {
                            if (item.blocks.isEmpty()) abort("LAY105", "List item has no blocks", index)
                            yieldAll(item.blocks)
                        }
                        is Checklist -> for (item in block.items) {
                            if (item.blocks.isEmpty()) abort("LAY105", "Checklist item has no blocks", index)
                            yieldAll(item.blocks)
                        }
                    }
                }.iterator()
            }
        }
        if (children != null && children.hasNext()) {
            if (frame.depth >= policy.maxBlockDepth) abort("LAY120", "Block nesting exceeds layout policy", index)
            stack.add(VisitFrame(children, frame.depth + 1, index))
        }
    }
}

private sealed interface FlowFrame
private enum class FrameKind { ROOT, QUOTE, ITEM }
private class BlockFrame(
    val iterator: Iterator<DocumentBlock>, val source: Int?, val x: Double, val width: Double,
    val listDepth: Int, val startY: Double, val kind: FrameKind,
    val destination: BlockFrame? = null, val itemDestination: ListFrame? = null,
    val marker: LogicalListMarker? = null, val containerX: Double = x, val containerWidth: Double = width,
    val minimumHeight: Double = 0.0,
) : FlowFrame {
    val output = mutableListOf<LogicalBlock>()
    var position = 0
}
private class ListFrame(
    val block: DocumentBlock, val source: Int, val destination: BlockFrame,
    val markerX: Double, val contentX: Double, val contentWidth: Double, val columnWidth: Double,
    val listDepth: Int, val startY: Double, val measured: List<TextMeasurement>,
) : FlowFrame {
    val items = mutableListOf<LogicalListItem>()
    var position = 0
    val count: Int get() = when (block) { is OrderedList -> block.items.size; is UnorderedList -> block.items.size; else -> (block as Checklist).items.size }
}

internal class StructuredBlockFlow(private val measurer: TextMeasurer, private val constraints: LayoutConstraints) {
    private val diagnostics = mutableListOf<LayoutDiagnostic>()
    private var items = 0L
    private var y = 0.0
    private var source: Int? = null
    private val body get() = constraints.typography.body
    private val bodyHeight get() = LogicalGeometry.normalize(body.metrics.lineHeightMm)
    private fun em(index: Int): Double {
        val em = LogicalGeometry.add(body.metrics.ascentMm, body.metrics.descentMm)
        if (em == 0.0) abort("LAY122", "Body em cannot make coordinate progress", index)
        return em
    }
    private fun charge(count: Int, index: Int) {
        if (count.toLong() > constraints.resources.maxItems.toLong() - items)
            abort("LAY120", "Geometry item count exceeds layout policy", index)
        items += count
    }
    private fun measure(text: String, typography: ResolvedTypography, index: Int): TextMeasurement {
        charge(1, index)
        val measured = when (val result = measurer.measure(TextMeasureRequest(text, typography))) {
            is TextMeasureResult.Success -> result.measurement
            is TextMeasureResult.Failure -> failMeasurement(result.diagnostics, index, ::charge)
        }
        if (measured.textLength != text.length || measured.metrics != typography.metrics)
            abort("LAY103", "Measurement does not match resolved typography or input", index)
        charge(measured.clusters.size, index)
        return measured
    }
    private fun remaining(width: Double, indent: Double, index: Int): Double {
        if (LogicalGeometry.fits(width, indent)) abort("LAY107", "Indentation and markers exhaust available width", index)
        return LogicalGeometry.subtract(width, indent)
    }
    fun layout(document: ThermalDocument): LayoutResult {
        val width = LogicalGeometry.normalize(constraints.canvasWidth.value)
        val root = BlockFrame(document.blocks.iterator(), null, 0.0, width, 0, 0.0, FrameKind.ROOT)
        val stack = mutableListOf<FlowFrame>(root)
        val textFlow = TextBlockFlow(measurer, constraints, ::charge, diagnostics)
        val tableFlow = TableBlockFlow(constraints, textFlow, ::charge)
        val placeholderFlow = PlaceholderBlockFlow(constraints, ::charge)
        try {
            while (stack.isNotEmpty()) {
                when (val frame = stack.last()) {
                    is BlockFrame -> {
                        if (!frame.iterator.hasNext()) {
                            stack.removeAt(stack.lastIndex)
                            when (frame.kind) {
                                FrameKind.ROOT -> Unit
                                FrameKind.QUOTE -> frame.destination!!.output.add(LogicalQuoteBlock(frame.source!!,
                                    LogicalBounds(frame.containerX, frame.startY, frame.containerWidth, LogicalGeometry.subtract(y, frame.startY)),
                                    frame.x, SnapshotList(frame.output)))
                                FrameKind.ITEM -> {
                                    val minimumBottom = LogicalGeometry.add(frame.startY, frame.minimumHeight)
                                    if (LogicalGeometry.fits(y, minimumBottom)) y = minimumBottom
                                    frame.itemDestination!!.items.add(LogicalListItem(
                                        LogicalBounds(frame.containerX, frame.startY, frame.containerWidth, LogicalGeometry.subtract(y, frame.startY)),
                                        frame.marker!!, frame.x, SnapshotList(frame.output)))
                                }
                            }
                            continue
                        }
                        val index = frame.source ?: frame.position
                        frame.position++
                        source = index
                        when (val block = frame.iterator.next()) {
                            is Paragraph, is Heading, is CodeBlock -> {
                                val (output, nextY) = textFlow.layout(block, index, frame.x, frame.width, y)
                                frame.output.add(output)
                                y = nextY
                            }
                            is Table -> {
                                val (output, nextY) = tableFlow.layout(block, index, frame.x, frame.width, y)
                                frame.output.add(output)
                                y = nextY
                            }
                            is Image, is QrCode -> {
                                val (output, nextY) = placeholderFlow.layout(block, index, frame.x, frame.width, y)
                                frame.output.add(output)
                                y = nextY
                            }
                            is Quote -> {
                                charge(2, index) // container and its bounded iterator frame
                                val indent = LogicalGeometry.scale(em(index), TextLayoutPolicyV1.quoteIndentEm)
                                val available = remaining(frame.width, indent, index)
                                stack.add(BlockFrame(block.blocks.iterator(), index, LogicalGeometry.add(frame.x, indent),
                                    available, frame.listDepth, y, FrameKind.QUOTE, destination = frame,
                                    containerX = frame.x, containerWidth = frame.width))
                            }
                            Separator -> {
                                charge(1, index)
                                val gap = LogicalGeometry.scale(bodyHeight, TextLayoutPolicyV1.separatorSpacingLineFactor)
                                val thickness = LogicalGeometry.scale(em(index), TextLayoutPolicyV1.separatorThicknessEm)
                                if (thickness == 0.0) abort("LAY122", "Separator cannot make coordinate progress", index)
                                y = LogicalGeometry.add(y, gap)
                                frame.output.add(LogicalSeparator(index, LogicalBounds(frame.x, y, frame.width, thickness), gap, gap))
                                y = LogicalGeometry.add(LogicalGeometry.add(y, thickness), gap)
                            }
                            is UnorderedList, is OrderedList, is Checklist -> {
                                charge(2, index) // logical container and list frame
                                val count = when (block) { is UnorderedList -> block.items.size; is OrderedList -> block.items.size; else -> (block as Checklist).items.size }
                                if (count == 0) {
                                    frame.output.add(LogicalListBlock(index, LogicalBounds(frame.x, y, frame.width, 0.0),
                                        kind(block), 0.0, frame.x, SnapshotList(emptyList())))
                                    continue
                                }
                                val indent = LogicalGeometry.scale(em(index), if (frame.listDepth == 0)
                                    TextLayoutPolicyV1.outerListIndentEm else TextLayoutPolicyV1.nestedListIndentEm)
                                var column = when (block) { is Checklist -> LogicalGeometry.scale(em(index), TextLayoutPolicyV1.checklistSideEm)
                                    is UnorderedList -> LogicalGeometry.scale(em(index), TextLayoutPolicyV1.unorderedSideEm); else -> 0.0 }
                                val measured = mutableListOf<TextMeasurement>()
                                if (block is OrderedList) for (position in 0 until count) {
                                    charge(1, index) // retained marker slot before marker string/service allocations
                                    val marker = measure("${block.start + position.toLong()}.", constraints.typography.listMarker, index)
                                    column = maxOf(column, LogicalGeometry.normalize(marker.advanceMm))
                                    measured.add(marker)
                                }
                                val gap = LogicalGeometry.normalize(measure(" ", body, index).advanceMm)
                                val offset = LogicalGeometry.add(LogicalGeometry.add(indent, column), gap)
                                val available = remaining(frame.width, offset, index)
                                stack.add(ListFrame(block, index, frame, LogicalGeometry.add(frame.x, indent),
                                    LogicalGeometry.add(frame.x, offset), available, column, frame.listDepth + 1, y, measured))
                            }
                        }
                    }
                    is ListFrame -> {
                        source = frame.source
                        if (frame.position == frame.count) {
                            stack.removeAt(stack.lastIndex)
                            frame.destination.output.add(LogicalListBlock(frame.source,
                                LogicalBounds(frame.destination.x, frame.startY, frame.destination.width, LogicalGeometry.subtract(y, frame.startY)),
                                kind(frame.block), frame.columnWidth, frame.contentX, SnapshotList(frame.items)))
                            continue
                        }
                        val position = frame.position++
                        val index = frame.source
                        charge(3, index) // item, marker and iterator frame before any retained children
                        val marker: LogicalListMarker = when (val block = frame.block) {
                            is OrderedList -> {
                                val measured = frame.measured[position]
                                val advance = LogicalGeometry.normalize(measured.advanceMm)
                                val markerX = LogicalGeometry.add(frame.markerX, LogicalGeometry.subtract(frame.columnWidth, advance))
                                val typography = constraints.typography.listMarker
                                val markerHeight = LogicalGeometry.normalize(typography.metrics.lineHeightMm)
                                if (markerHeight == 0.0) abort("LAY122", "Marker cannot make coordinate progress", index)
                                LogicalOrderedMarker(block.start + position.toLong(),
                                    LogicalTextRun("${block.start + position.toLong()}.", LogicalBounds(markerX, y, advance,
                                        markerHeight), typography, measured),
                                    LogicalGeometry.add(y, typography.metrics.baselineOffsetMm))
                            }
                            is UnorderedList -> LogicalUnorderedMarker(graphicBounds(frame.markerX, frame.columnWidth))
                            is Checklist -> {
                                val bounds = graphicBounds(frame.markerX, frame.columnWidth)
                                val stroke = LogicalGeometry.scale(em(index), TextLayoutPolicyV1.checklistStrokeEm)
                                if (stroke == 0.0) abort("LAY122", "Checklist stroke cannot make coordinate progress", index)
                                val inset = LogicalGeometry.scale(stroke, 0.5)
                                val points = if (block.items[position].checked) {
                                    charge(3, index)
                                    TextLayoutPolicyV1.checklistPoints.map { (x, y) ->
                                        LogicalPoint(LogicalGeometry.add(bounds.xMm, LogicalGeometry.scale(bounds.widthMm, x)),
                                            LogicalGeometry.add(bounds.yMm, LogicalGeometry.scale(bounds.heightMm, y)))
                                    }
                                } else emptyList()
                                LogicalChecklistMarker(bounds, block.items[position].checked, stroke,
                                    LogicalBounds(LogicalGeometry.add(bounds.xMm, inset), LogicalGeometry.add(bounds.yMm, inset),
                                        LogicalGeometry.subtract(bounds.widthMm, stroke), LogicalGeometry.subtract(bounds.heightMm, stroke)), SnapshotList(points))
                            }
                            else -> error("Not a list")
                        }
                        val children = when (val block = frame.block) { is OrderedList -> block.items[position].blocks
                            is UnorderedList -> block.items[position].blocks; else -> (block as Checklist).items[position].blocks }
                        val height = maxOf(bodyHeight, LogicalGeometry.add(LogicalGeometry.subtract(marker.bounds.yMm, y), marker.bounds.heightMm))
                        stack.add(BlockFrame(children.iterator(), index, frame.contentX, frame.contentWidth, frame.listDepth, y, FrameKind.ITEM,
                            itemDestination = frame, marker = marker, containerX = frame.markerX,
                            containerWidth = LogicalGeometry.subtract(LogicalGeometry.add(frame.destination.x, frame.destination.width), frame.markerX), minimumHeight = height))
                    }
                }
            }
        } catch (_: IllegalArgumentException) {
            abort("LAY122", "Layout exceeds coordinate range", source)
        }
        return LayoutResult.Success(LogicalDocument(constraints, y, SnapshotList(root.output), SnapshotList(diagnostics)))
    }
    private fun graphicBounds(x: Double, side: Double): LogicalBounds {
        if (side == 0.0) abort("LAY122", "Marker cannot make coordinate progress", source)
        return LogicalBounds(x, LogicalGeometry.add(y, LogicalGeometry.scale(LogicalGeometry.subtract(bodyHeight, side), 0.5)), side, side)
    }
    private fun kind(block: DocumentBlock): LogicalListKind = when (block) {
        is OrderedList -> LogicalListKind.ORDERED; is UnorderedList -> LogicalListKind.UNORDERED; else -> LogicalListKind.CHECKLIST
    }
}
