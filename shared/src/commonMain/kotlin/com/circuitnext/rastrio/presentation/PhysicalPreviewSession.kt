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
import com.circuitnext.rastrio.core.printer.*
import com.circuitnext.rastrio.core.profile.PrinterProfile
import com.circuitnext.rastrio.core.profile.SyntheticReferenceProfiles
import com.circuitnext.rastrio.core.preview.*
import com.circuitnext.rastrio.core.text.SnapshotList

/** A semantic document remains usable even when the ASCII logical authoring backend rejects it. */
fun MarkdownAuthoringResult.semanticDocument(): ThermalDocument? = when (this) {
    is MarkdownAuthoringResult.Ready -> document
    is MarkdownAuthoringResult.LayoutFailed -> document
    MarkdownAuthoringResult.NotCompiled, is MarkdownAuthoringResult.CompilationFailed -> null
}

sealed interface PhysicalPreviewResult {
    data object NotPrepared : PhysicalPreviewResult
    data class PreparationFailed(val diagnostics: SnapshotList<PreparationDiagnostic>) : PhysicalPreviewResult
    data class PreviewFailed(val diagnostics: SnapshotList<PrintPreviewDiagnostic>) : PhysicalPreviewResult
    data class Ready(val print: PreparedPrint, val preview: PrintPreview) : PhysicalPreviewResult
}
data class PhysicalPreviewState(
    val profile: PrinterProfile = SyntheticReferenceProfiles.narrow,
    val options: PrintOptions = PrintOptions(),
    val result: PhysicalPreviewResult = PhysicalPreviewResult.NotPrepared,
)

/** Coordinates Core only; each output change replaces the plan, with no physical algorithms here. */
class PhysicalPreviewSession(
    private val preparePrint: (ThermalDocument, PrinterProfile, PrintOptions) -> PreparationResult =
        { document, profile, options -> BasicPreparationEngine().prepare(document, profile, options) },
    private val previewResources: PhysicalPreviewResourcePolicy = PhysicalPreviewResourcePolicy(),
) {
    var state = PhysicalPreviewState()
        private set
    private var preparedDocument: ThermalDocument? = null

    fun invalidate() {
        preparedDocument = null
        state = state.copy(result = PhysicalPreviewResult.NotPrepared)
    }

    /** Unchanged document, profile and options reuse the exact plan and its failure/success result. */
    fun prepare(
        authoring: MarkdownAuthoringResult,
        profile: PrinterProfile = state.profile,
        options: PrintOptions = state.options,
    ) {
        val document = authoring.semanticDocument()
        if (document != null && document == preparedDocument && profile == state.profile && options == state.options) return
        val input = PhysicalPreviewState(profile, options)
        state = input
        preparedDocument = null
        if (document == null) return
        val result = when (val prepared = preparePrint(document, profile, options)) {
            is PreparationResult.Failure -> PhysicalPreviewResult.PreparationFailed(prepared.diagnostics)
            is PreparationResult.Success -> when (val preview = prepared.print.toPrintPreview(previewResources)) {
                is PrintPreviewResult.Failure -> PhysicalPreviewResult.PreviewFailed(preview.diagnostics)
                is PrintPreviewResult.Success -> PhysicalPreviewResult.Ready(prepared.print, preview.preview)
            }
        }
        // A reentrant source/options change must not publish an obsolete preparation.
        if (state === input) {
            state = input.copy(result = result)
            preparedDocument = document
        }
    }
}
