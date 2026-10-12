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

import com.circuitnext.rastrio.core.document.*
import com.circuitnext.rastrio.core.printer.*
import com.circuitnext.rastrio.core.profile.PrinterProfile
import com.circuitnext.rastrio.core.profile.SyntheticReferenceProfiles
import com.circuitnext.rastrio.core.preview.*
import com.circuitnext.rastrio.core.text.SnapshotList
import com.circuitnext.rastrio.core.text.RasterTextBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext

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
    private val rasterTextBackend: RasterTextBackend? = null,
    private val preparePrint: (ThermalDocument, PrinterProfile, PrintOptions, () -> Boolean) -> PreparationResult =
        { document, profile, options, isCancelled ->
            BasicPreparationEngine(rasterTextBackend = rasterTextBackend).prepare(document, profile, options, isCancelled = isCancelled) },
    private val previewResources: PhysicalPreviewResourcePolicy = PhysicalPreviewResourcePolicy(),
) {
    var state = PhysicalPreviewState()
        private set
    private var preparedDocument: ThermalDocument? = null
    private var resolvedImages: Map<AssetReference, ResolvedRasterImage> = emptyMap()

    fun invalidate() {
        preparedDocument = null
        resolvedImages = emptyMap()
        state = state.copy(result = PhysicalPreviewResult.NotPrepared)
    }

    /** Keep target choices without preparing content from another authoring workspace. */
    fun configure(profile: PrinterProfile, options: PrintOptions = state.options) {
        invalidate()
        state = state.copy(profile = profile, options = options)
    }

    /** Explicit binding to the current picked pixels; changing the source releases older bindings. */
    fun resolveImage(reference: AssetReference, image: ResolvedRasterImage): Boolean {
        if (resolvedImages.values.any { it.image !== image.image }) resolvedImages = emptyMap()
        if (reference !in resolvedImages && resolvedImages.size >= 1000) return false
        resolvedImages = resolvedImages + (reference to image)
        preparedDocument = null
        state = state.copy(result = PhysicalPreviewResult.NotPrepared)
        return true
    }

    /** Unchanged document, profile and options reuse the exact plan and its failure/success result. */
    fun prepare(
        authoring: MarkdownAuthoringResult,
        profile: PrinterProfile = state.profile,
        options: PrintOptions = state.options,
    ) {
        prepareDocument(authoring.semanticDocument(), profile, options)
    }

    fun prepare(document: ThermalDocument, profile: PrinterProfile = state.profile, options: PrintOptions = state.options) {
        prepareDocument(document, profile, options)
    }

    private fun prepareDocument(document: ThermalDocument?, profile: PrinterProfile, options: PrintOptions) {
        if (document != null && document == preparedDocument && profile == state.profile && options == state.options) return
        val input = PhysicalPreviewState(profile, options)
        state = input
        preparedDocument = null
        if (document == null) return
        val prepared = if (resolvedImages.isEmpty()) preparePrint(document, profile, options, { false })
            else BasicPreparationEngine(rasterTextBackend = rasterTextBackend).prepare(document, profile, options, resolvedImages = resolvedImages)
        val result = project(prepared)
        // A reentrant source/options change must not publish an obsolete preparation.
        if (state === input) {
            state = input.copy(result = result)
            preparedDocument = document
        }
    }

    suspend fun prepareAsync(authoring: MarkdownAuthoringResult, profile: PrinterProfile = state.profile,
        options: PrintOptions = state.options) {
        prepareDocumentAsync(authoring.semanticDocument(), profile, options)
    }

    suspend fun prepareAsync(document: ThermalDocument, profile: PrinterProfile = state.profile, options: PrintOptions = state.options) {
        prepareDocumentAsync(document, profile, options)
    }

    private suspend fun prepareDocumentAsync(document: ThermalDocument?, profile: PrinterProfile, options: PrintOptions) {
        if (document != null && document == preparedDocument && profile == state.profile && options == state.options) return
        val input = PhysicalPreviewState(profile, options)
        val images = resolvedImages.toMap()
        state = input
        preparedDocument = null
        if (document == null) return
        val job = currentCoroutineContext().job
        val result = withContext(Dispatchers.Default) {
            val prepared = if (images.isEmpty()) preparePrint(document, profile, options, { !job.isActive })
                else BasicPreparationEngine(rasterTextBackend = rasterTextBackend).prepare(document, profile, options, resolvedImages = images,
                    isCancelled = { !job.isActive })
            project(prepared)
        }
        if (state === input) {
            state = input.copy(result = result)
            preparedDocument = document
        }
    }

    private fun project(prepared: PreparationResult): PhysicalPreviewResult = when (prepared) {
            is PreparationResult.Failure -> PhysicalPreviewResult.PreparationFailed(prepared.diagnostics)
            is PreparationResult.Success -> when (val preview = prepared.print.toPrintPreview(previewResources)) {
                is PrintPreviewResult.Failure -> PhysicalPreviewResult.PreviewFailed(preview.diagnostics)
                is PrintPreviewResult.Success -> PhysicalPreviewResult.Ready(prepared.print, preview.preview)
            }
    }
}
