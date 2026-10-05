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

/** Centrally versioned Phase 3A subset of TEXT_RENDERING_SPEC Section 17.3. */
object TextLayoutPolicyV1 {
    const val paragraphAfterLineFactor: Double = 0.5
}

/**
 * Empty documents and fitting single-line plain paragraphs only. Unsupported semantics fail
 * atomically; wrapping and comprehensive block/inline flow belong to subsequent Phase 3 work.
 * The injected measurer owns segmentation/measurement, never alignment or block positions.
 */
class FoundationLayoutEngine(private val measurer: TextMeasurer) : LogicalLayoutEngine {
    override fun layout(document: ThermalDocument, constraints: LayoutConstraints): LayoutResult {
        fun failure(code: String, message: String, block: Int? = null) =
            LayoutResult.Failure(SnapshotList(listOf(LayoutDiagnostic(code, message, block))))
        if (document.schemaVersion != 1) return failure("LAY102", "Unsupported document schema version")
        val policy = constraints.resources
        if (document.blocks.size > policy.maxBlocks) return failure("LAY120", "Block count exceeds layout policy")

        // Preflight the supported input before concatenation or measurement allocates output.
        var inlineCount = 0L
        var textCount = 0L
        for ((index, block) in document.blocks.withIndex()) {
            if (block !is Paragraph) return failure("LAY100", "Block layout is unavailable in the foundation engine", index)
            inlineCount += block.content.size.toLong()
            if (inlineCount > policy.maxInlineNodes) return failure("LAY120", "Inline count exceeds layout policy", index)
            for (inline in block.content) {
                if (inline !is Text) return failure("LAY100", "Inline layout is unavailable in the foundation engine", index)
                textCount += inline.text.length.toLong()
                if (textCount > policy.maxTextCodeUnits) return failure("LAY120", "Text count exceeds layout policy", index)
            }
        }

        val blocks = mutableListOf<LogicalBlock>()
        val width = LogicalGeometry.normalize(constraints.canvasWidth.value)
        val typography = constraints.typography.body
        var y = 0.0
        var items = 0L
        for ((index, block) in document.blocks.withIndex()) {
            block as Paragraph // guaranteed by preflight
            if (items + 3 > policy.maxItems) return failure("LAY120", "Geometry item count exceeds layout policy", index)
            val text = buildString { block.content.forEach { append((it as Text).text) } }
            val measured = when (val result = measurer.measure(TextMeasureRequest(text, typography))) {
                is TextMeasureResult.Failure -> return LayoutResult.Failure(SnapshotList(result.diagnostics.map {
                    LayoutDiagnostic(it.code, it.message, index)
                }))
                is TextMeasureResult.Success -> result.measurement
            }
            if (measured.textLength != text.length || measured.metrics != typography.metrics) {
                return failure("LAY103", "Measurement does not match resolved typography or input", index)
            }
            if (measured.breakOpportunities.any { it.kind == BreakKind.MANDATORY && it.offset < text.length }) {
                return failure("LAY100", "Paragraph requires explicit line flow", index)
            }
            items += 3L + measured.clusters.size
            if (items > policy.maxItems) return failure("LAY120", "Geometry item count exceeds layout policy", index)
            try {
                val advance = LogicalGeometry.normalize(measured.advanceMm)
                if (!LogicalGeometry.fits(advance, width)) return failure("LAY101", "Paragraph requires wrapping", index)
                val height = LogicalGeometry.normalize(measured.metrics.lineHeightMm)
                val baseline = LogicalGeometry.add(y, measured.metrics.baselineOffsetMm)
                val bottom = LogicalGeometry.add(y, height)
                val nextY = LogicalGeometry.add(bottom, LogicalGeometry.scale(height, TextLayoutPolicyV1.paragraphAfterLineFactor))
                if (bottom <= y || nextY <= bottom) return failure("LAY122", "Layout exceeds coordinate range", index)
                val remaining = LogicalGeometry.subtract(width, advance)
                val x = when (block.alignment) {
                    Alignment.LEFT -> 0.0
                    Alignment.CENTER -> LogicalGeometry.scale(remaining, 0.5)
                    Alignment.RIGHT -> remaining
                }
                val bounds = LogicalBounds(x, y, advance, height)
                val run = LogicalTextRun(text, bounds, typography, measured)
                val line = LogicalTextLine(bounds, width, advance, baseline, block.alignment, SnapshotList(listOf(run)))
                blocks.add(LogicalTextBlock(index, LogicalBounds(0.0, y, width, height), SnapshotList(listOf(line))))
                y = nextY
            } catch (_: IllegalArgumentException) {
                return failure("LAY122", "Layout exceeds coordinate range", index)
            }
        }
        return LayoutResult.Success(LogicalDocument(constraints, y, SnapshotList(blocks)))
    }
}
