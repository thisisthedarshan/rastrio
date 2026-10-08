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

package com.circuitnext.rastrio.core.preview

import com.circuitnext.rastrio.core.printer.*
import com.circuitnext.rastrio.core.text.SnapshotList

/** Preview-only ceilings; tile requests never allocate a full receipt bitmap or raster cache. */
data class PhysicalPreviewResourcePolicy(
    val preferredTileHeightDots: Int = 1024,
    val maxTileAreaDots: Int = 2_000_000,
    val maxPrimitivesPerTile: Int = 10_000,
    val maxDiagnostics: Int = 1_000,
) {
    init {
        require(preferredTileHeightDots in 1..1024 && maxTileAreaDots in 1..2_000_000)
        require(maxPrimitivesPerTile > 0 && maxDiagnostics > 0)
    }
}

data class PrintPreviewDiagnostic(val code: String, val message: String, val sourceBlockIndex: Int? = null)
enum class PhysicalPreviewAccuracy { EXACT_GEOMETRY_REPRESENTATIVE_GLYPHS, EXACT_PREPARED_DOTS, EXACT_PAPER_ADVANCE }
enum class PreparedGraphicKind { SEPARATOR, MANUAL_CUT_GUIDE }

/** Original prepared bounds stay authoritative; clippedBounds only limits viewport painting. */
sealed interface PhysicalPreviewPrimitive {
    val operation: PreparedOperation
    val clippedBounds: DotBounds
    val bounds: DotBounds get() = operation.bounds
    val accuracy: PhysicalPreviewAccuracy
}
data class NativeTextPrimitive(
    override val operation: NativeText,
    override val clippedBounds: DotBounds,
) : PhysicalPreviewPrimitive {
    override val accuracy = PhysicalPreviewAccuracy.EXACT_GEOMETRY_REPRESENTATIVE_GLYPHS
}
data class MonochromePrimitive(
    override val operation: PreparedOperation,
    val graphic: PreparedMonochrome,
    val kind: PreparedGraphicKind,
    override val clippedBounds: DotBounds,
) : PhysicalPreviewPrimitive {
    override val accuracy = PhysicalPreviewAccuracy.EXACT_PREPARED_DOTS
}
data class FeedPrimitive(
    override val operation: Feed,
    override val clippedBounds: DotBounds,
) : PhysicalPreviewPrimitive {
    override val accuracy = PhysicalPreviewAccuracy.EXACT_PAPER_ADVANCE
}

/** Cutter operation semantics only: never printed content and never extra paper height. */
data class AutomaticCutAnnotation(val operation: AutomaticCut) {
    val yDots: Int get() = operation.bounds.y
    val mode: CutMode get() = operation.mode
}
data class PrintPreviewTile(
    val index: Int,
    val bounds: DotBounds,
    val primitives: SnapshotList<PhysicalPreviewPrimitive>,
    val annotations: SnapshotList<AutomaticCutAnnotation>,
)
sealed interface PrintPreviewTileResult {
    data class Success(val tile: PrintPreviewTile) : PrintPreviewTileResult
    data class Failure(val diagnostics: SnapshotList<PrintPreviewDiagnostic>) : PrintPreviewTileResult
}
sealed interface PrintPreviewResult {
    data class Success(val preview: PrintPreview) : PrintPreviewResult
    data class Failure(val diagnostics: SnapshotList<PrintPreviewDiagnostic>) : PrintPreviewResult
}

/** Inexpensive descriptor retaining the exact immutable physical plan, not a rendered scene. */
class PrintPreview internal constructor(
    private val print: PreparedPrint,
    val tileHeightDots: Int,
    val diagnostics: SnapshotList<PrintPreviewDiagnostic>,
    private val resources: PhysicalPreviewResourcePolicy,
) {
    val target: PreparedTarget get() = print.target
    val widthDots: Int get() = target.printableWidthDots
    val heightDots: Int get() = print.heightDots
    // A zero-height job can still carry a cut at y=0; its single tile has zero physical height.
    val tileCount: Int = if (heightDots == 0) 1 else (heightDots - 1) / tileHeightDots + 1

    /** Changes viewport render units only; retains the same finalized physical plan. */
    fun withTileAreaLimit(maxTileAreaDots: Int): PrintPreviewResult = print.toPrintPreview(
        resources.copy(maxTileAreaDots = minOf(resources.maxTileAreaDots, maxTileAreaDots)))

    fun tile(index: Int): PrintPreviewTileResult {
        fun failure(code: String, message: String) = PrintPreviewTileResult.Failure(
            SnapshotList(listOf(PrintPreviewDiagnostic(code, message))))
        if (index !in 0 until tileCount) return failure("PRV101", "Preview tile index is outside the document")
        val top = index.toLong() * tileHeightDots
        val bottom = minOf(top + tileHeightDots, heightDots.toLong()).toInt()
        val bounds = DotBounds(0, top.toInt(), widthDots, bottom - top.toInt())
        val primitives = mutableListOf<PhysicalPreviewPrimitive>()
        val annotations = mutableListOf<AutomaticCutAnnotation>()
        for (operation in print.operations) {
            val original = operation.bounds
            if (original.y > bottom) break
            if (operation is AutomaticCut) {
                if (original.y >= bounds.y && (original.y < bottom || index == tileCount - 1 && original.y == bottom)) {
                    if (primitives.size + annotations.size >= resources.maxPrimitivesPerTile)
                        return failure("PRV120", "Preview tile exceeds primitive resource policy")
                    annotations.add(AutomaticCutAnnotation(operation))
                }
                continue
            }
            if (original.bottom <= bounds.y || original.y >= bottom) continue
            if (primitives.size + annotations.size >= resources.maxPrimitivesPerTile)
                return failure("PRV120", "Preview tile exceeds primitive resource policy")
            val clippedTop = maxOf(original.y, bounds.y)
            val clippedBottom = minOf(original.bottom, bottom)
            val clipped = DotBounds(original.x, clippedTop, original.width, clippedBottom - clippedTop)
            val primitive = when (operation) {
                is NativeText -> NativeTextPrimitive(operation, clipped)
                is Separator -> MonochromePrimitive(operation, operation.graphic, PreparedGraphicKind.SEPARATOR, clipped)
                is ManualCutGuide -> MonochromePrimitive(operation, operation.graphic, PreparedGraphicKind.MANUAL_CUT_GUIDE, clipped)
                is Feed -> FeedPrimitive(operation, clipped)
                is AutomaticCut -> error("Cut annotations are handled separately")
            }
            primitives.add(primitive)
        }
        return PrintPreviewTileResult.Success(PrintPreviewTile(index, bounds, SnapshotList(primitives), SnapshotList(annotations)))
    }
}

/** No source/layout/profile lookup, text measurement, artifact regeneration or raster allocation. */
fun PreparedPrint.toPrintPreview(resources: PhysicalPreviewResourcePolicy = PhysicalPreviewResourcePolicy()): PrintPreviewResult {
    fun failure(code: String, message: String) = PrintPreviewResult.Failure(
        SnapshotList(listOf(PrintPreviewDiagnostic(code, message))))
    if (diagnostics.size > resources.maxDiagnostics) return failure("PRV120", "Preview diagnostics exceed resource policy")
    val height = minOf(resources.preferredTileHeightDots, resources.maxTileAreaDots / target.printableWidthDots)
    if (height == 0) return failure("PRV120", "Prepared width exceeds preview tile area policy")
    val projectedDiagnostics = SnapshotList(diagnostics.map { PrintPreviewDiagnostic(it.code, it.message, it.sourceBlockIndex) })
    return PrintPreviewResult.Success(PrintPreview(this, height, projectedDiagnostics, resources))
}
