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

import com.circuitnext.rastrio.core.document.Orientation
import com.circuitnext.rastrio.core.layout.LayoutDiagnostic
import com.circuitnext.rastrio.core.layout.LogicalBlock
import com.circuitnext.rastrio.core.layout.LogicalDocument
import com.circuitnext.rastrio.core.text.SnapshotList

/**
 * One assembled logical canvas, in millimetres from the top-left (y increases down).
 * Blocks and diagnostics share the finalized immutable layout snapshots, including nested
 * lines, runs, measurements, markers, tables and unresolved image/QR placeholders.
 * Renderers paint supplied geometry; they must not measure, wrap, align or resolve assets again.
 * Viewport scaling, density, scrolling and lazy painting belong to Presentation.
 * This model carries no physical print strategy or promise of printer glyph fidelity.
 */
data class LogicalPreview(
    val widthMm: Double,
    val heightMm: Double,
    val orientation: Orientation,
    val blocks: SnapshotList<LogicalBlock>,
    val diagnostics: SnapshotList<LayoutDiagnostic>,
)

/** Constant-time projection: no layout, text service, content resolution or raster allocation. */
fun LogicalDocument.toLogicalPreview(): LogicalPreview = LogicalPreview(
    widthMm = widthMm,
    heightMm = heightMm,
    orientation = constraints.orientation,
    blocks = blocks,
    diagnostics = diagnostics,
)
