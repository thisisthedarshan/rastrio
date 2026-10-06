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

import com.circuitnext.rastrio.core.document.Alignment
import com.circuitnext.rastrio.core.document.Length
import com.circuitnext.rastrio.core.document.LengthUnit
import com.circuitnext.rastrio.core.document.Orientation
import com.circuitnext.rastrio.core.text.*

/** Trusted runtime policy. Bounds clusters/atoms, spans, blocks, lines, runs and diagnostics. */
data class LayoutResourcePolicy(
    val maxBlocks: Int = 100_000,
    val maxInlineNodes: Int = 500_000,
    val maxTextCodeUnits: Int = 8 * 1024 * 1024,
    // Provisional retained-cost ceiling; rationale and constrained-heap probe in RESOURCE_LIMITS.
    val maxItems: Int = 100_000,
    val maxWidthMm: Double = 1_000.0,
    val maxInlineDepth: Int = 64,
) {
    init {
        require(maxBlocks > 0 && maxInlineNodes > 0 && maxTextCodeUnits > 0 && maxItems > 0)
        require(maxInlineDepth in 1..64)
        require(maxWidthMm.isFinite() && maxWidthMm > 0.0 && maxWidthMm <= 1_000.0)
    }
}

/** Runtime inputs only. Width is explicit in both orientations; landscape does not swap axes. */
data class LayoutConstraints(
    val canvasWidth: Length,
    val orientation: Orientation,
    val typography: TypographyContext,
    val resources: LayoutResourcePolicy = LayoutResourcePolicy(),
) {
    init {
        require(canvasWidth.unit == LengthUnit.MM)
        require(canvasWidth.value.isFinite() && canvasWidth.value > 0.0 && canvasWidth.value <= resources.maxWidthMm)
        require(LogicalGeometry.normalize(canvasWidth.value) > 0.0)
    }
}

/** All coordinates are absolute millimetres from the canvas top-left, with y increasing down. */
data class LogicalBounds(val xMm: Double, val yMm: Double, val widthMm: Double, val heightMm: Double) {
    init {
        require(listOf(xMm, yMm, widthMm, heightMm).all { it.isFinite() && it >= 0.0 })
        LogicalGeometry.add(xMm, widthMm)
        LogicalGeometry.add(yMm, heightMm)
    }
}

data class LayoutDiagnostic(val code: String, val message: String, val sourceBlockIndex: Int? = null)

/** Retains the exact resolved constraints; consumers must not remeasure or realign text. */
data class LogicalDocument(
    val constraints: LayoutConstraints,
    val heightMm: Double,
    val blocks: SnapshotList<LogicalBlock>,
    val diagnostics: SnapshotList<LayoutDiagnostic> = SnapshotList(emptyList()),
) {
    val widthMm: Double get() = LogicalGeometry.normalize(constraints.canvasWidth.value)
    init { require(heightMm.isFinite() && heightMm >= 0.0) }
}

sealed interface LogicalBlock {
    val sourceBlockIndex: Int
    val bounds: LogicalBounds
}
data class LogicalTextBlock(
    override val sourceBlockIndex: Int,
    override val bounds: LogicalBounds,
    val lines: SnapshotList<LogicalTextLine>,
) : LogicalBlock {
    init { require(sourceBlockIndex >= 0) }
}

/** Geometry-only placeholder: content resolution and later printer preparation remain separate. */
enum class PlaceholderKind { IMAGE, QR }
data class LogicalPlaceholder(
    override val sourceBlockIndex: Int,
    override val bounds: LogicalBounds,
    val kind: PlaceholderKind,
    val alignment: Alignment,
) : LogicalBlock {
    init { require(sourceBlockIndex >= 0) }
}

/** Overflow is an engine policy/diagnostic; an intact oversized cluster remains representable. */
data class LogicalTextLine(
    val bounds: LogicalBounds,
    val availableWidthMm: Double,
    val advanceMm: Double,
    val baselineMm: Double,
    val alignment: Alignment,
    val runs: SnapshotList<LogicalTextRun>,
    val endsWithExplicitBreak: Boolean = false,
) {
    init {
        require(availableWidthMm.isFinite() && availableWidthMm > 0.0)
        require(advanceMm.isFinite() && advanceMm >= 0.0)
        require(baselineMm.isFinite() && baselineMm >= 0.0)
        require(LogicalGeometry.fits(bounds.yMm, baselineMm) &&
            LogicalGeometry.fits(baselineMm, LogicalGeometry.add(bounds.yMm, bounds.heightMm)))
    }
}
data class LogicalTextRun(
    val text: String,
    val bounds: LogicalBounds,
    val typography: ResolvedTypography,
    val measurement: TextMeasurement,
) {
    init { require(text.length == measurement.textLength) }
}

sealed interface LayoutResult {
    data class Success(val document: LogicalDocument) : LayoutResult
    data class Failure(val diagnostics: SnapshotList<LayoutDiagnostic>) : LayoutResult {
        init { require(diagnostics.isNotEmpty()) }
    }
}
