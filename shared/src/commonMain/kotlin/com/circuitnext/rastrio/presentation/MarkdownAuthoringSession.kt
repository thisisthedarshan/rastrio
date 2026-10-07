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

package com.circuitnext.rastrio.presentation

import com.circuitnext.rastrio.core.document.ThermalDocument
import com.circuitnext.rastrio.core.layout.LayoutConstraints
import com.circuitnext.rastrio.core.layout.LayoutDiagnostic
import com.circuitnext.rastrio.core.layout.LayoutResult
import com.circuitnext.rastrio.core.layout.LogicalDocument
import com.circuitnext.rastrio.core.layout.LogicalLayoutEngine
import com.circuitnext.rastrio.core.markdown.MarkdownCompiler
import com.circuitnext.rastrio.core.markdown.MarkdownDiagnostic
import com.circuitnext.rastrio.core.markdown.MarkdownResourcePolicy
import com.circuitnext.rastrio.core.preview.LogicalPreview
import com.circuitnext.rastrio.core.preview.toLogicalPreview
import com.circuitnext.rastrio.core.text.SnapshotList

/** A source and its current outcome are published together; editing invalidates the outcome. */
data class MarkdownAuthoringState(
    val source: String = "",
    val result: MarkdownAuthoringResult = MarkdownAuthoringResult.NotCompiled,
)

/** Stage-specific outcomes retain Core values and diagnostics without combining unrelated runs. */
sealed interface MarkdownAuthoringResult {
    data object NotCompiled : MarkdownAuthoringResult

    data class CompilationFailed(
        val diagnostics: SnapshotList<MarkdownDiagnostic>,
    ) : MarkdownAuthoringResult

    data class LayoutFailed(
        val document: ThermalDocument,
        val markdownDiagnostics: SnapshotList<MarkdownDiagnostic>,
        val diagnostics: SnapshotList<LayoutDiagnostic>,
    ) : MarkdownAuthoringResult

    data class Ready(
        val document: ThermalDocument,
        val markdownDiagnostics: SnapshotList<MarkdownDiagnostic>,
        val logicalDocument: LogicalDocument,
        val preview: LogicalPreview,
    ) : MarkdownAuthoringResult
}

/**
 * Synchronous authoring workflow owned by one caller. Retain this session across screen changes
 * and read its state after each event; no UI, navigation, lifecycle or observable-state dependency.
 * Logical constraints and the text-backed Core layout engine are explicit caller inputs.
 * Core diagnostics retain their stage-specific meaning, including normalized-source offsets.
 */
class MarkdownAuthoringSession(
    private val constraints: LayoutConstraints,
    private val layoutEngine: LogicalLayoutEngine,
    private val markdownPolicy: MarkdownResourcePolicy = MarkdownResourcePolicy(),
) {
    var state: MarkdownAuthoringState = MarkdownAuthoringState()
        private set

    /** An unchanged source keeps its valid result; changes require another explicit compile. */
    fun edit(source: String) {
        if (source != state.source) state = MarkdownAuthoringState(source)
    }

    fun compile() {
        val input = state
        val compilation = MarkdownCompiler.compile(input.source, markdownPolicy)
        val diagnostics = SnapshotList(compilation.diagnostics)
        val document = compilation.document
        val result = if (document == null) {
            MarkdownAuthoringResult.CompilationFailed(diagnostics)
        } else {
            when (val layout = layoutEngine.layout(document, constraints)) {
                is LayoutResult.Failure -> MarkdownAuthoringResult.LayoutFailed(document, diagnostics, layout.diagnostics)
                is LayoutResult.Success -> MarkdownAuthoringResult.Ready(
                    document, diagnostics, layout.document, layout.document.toLogicalPreview(),
                )
            }
        }
        // A Core adapter may re-enter editing; never publish output for a replaced source snapshot.
        if (state === input) state = input.copy(result = result)
    }
}
