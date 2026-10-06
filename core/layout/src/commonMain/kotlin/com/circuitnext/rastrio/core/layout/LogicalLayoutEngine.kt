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

/** Centrally versioned portable logical layout dimensions; all changes affect output. */
object TextLayoutPolicyV1 {
    const val paragraphAfterLineFactor: Double = 0.5
    const val headingAfterLineFactor: Double = 0.5
    const val codeBeforeLineFactor: Double = 0.5
    const val codeAfterLineFactor: Double = 0.5
    const val outerListIndentEm: Double = 1.0
    const val nestedListIndentEm: Double = 2.0
    const val unorderedSideEm: Double = 0.25
    const val checklistSideEm: Double = 1.0
    const val checklistStrokeEm: Double = 0.08
    internal val checklistPoints = SnapshotList(listOf(0.2 to 0.5, 0.4 to 0.7, 0.8 to 0.25))
    const val quoteIndentEm: Double = 1.0
    const val separatorThicknessEm: Double = 0.05
    const val separatorSpacingLineFactor: Double = 0.5
}

/** Shared text flow and bounded iterative structured-block flow; no hidden I/O or printer policy. */
class FoundationLayoutEngine(private val measurer: TextMeasurer) : LogicalLayoutEngine {
    override fun layout(document: ThermalDocument, constraints: LayoutConstraints): LayoutResult = try {
        preflightDocument(document, constraints.resources)
        StructuredBlockFlow(measurer, constraints).layout(document)
    } catch (failure: LayoutAbort) {
        LayoutResult.Failure(failure.diagnostics)
    } catch (_: IllegalArgumentException) {
        LayoutResult.Failure(SnapshotList(listOf(LayoutDiagnostic("LAY122", "Layout exceeds coordinate range"))))
    }
}
