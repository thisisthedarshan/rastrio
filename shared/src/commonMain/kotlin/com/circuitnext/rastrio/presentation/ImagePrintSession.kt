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
import com.circuitnext.rastrio.core.preview.*
import com.circuitnext.rastrio.core.profile.PrinterProfile
import com.circuitnext.rastrio.core.profile.SyntheticReferenceProfiles
import com.circuitnext.rastrio.core.raster.*
import com.circuitnext.rastrio.printing.ImageInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext

/** Raw numeric edits retain invalid input and invalidate output before validation/preparation. */
data class ImagePrintEdits(
    val cropX: String = "0", val cropY: String = "0", val cropWidth: String = "", val cropHeight: String = "",
    val widthDots: String = "", val brightness: Int = 0, val contrastPercent: Int = 100,
    val gammaHundredths: Int = 100, val algorithm: DitherAlgorithm = DitherAlgorithm.THRESHOLD,
    val threshold: Int = 128, val orientation: Int? = null, val alignment: Alignment = Alignment.CENTER,
)

data class ImagePrintState(
    val input: ImageInput? = null,
    val edits: ImagePrintEdits = ImagePrintEdits(),
    val profile: PrinterProfile = SyntheticReferenceProfiles.narrow,
    val options: PrintOptions = PrintOptions(),
    val result: PhysicalPreviewResult = PhysicalPreviewResult.NotPrepared,
    val editDiagnostic: String? = null,
)

/** Portable application coordination only; finalized pixels are produced once by Core preparation. */
class ImagePrintSession {
    var state = ImagePrintState()
        private set

    fun select(input: ImageInput?) {
        if (input == state.input) return
        state = state.copy(input = input, edits = ImagePrintEdits(), result = PhysicalPreviewResult.NotPrepared, editDiagnostic = null)
    }

    fun edit(edits: ImagePrintEdits) {
        if (edits == state.edits) return
        state = state.copy(edits = edits, result = PhysicalPreviewResult.NotPrepared, editDiagnostic = null)
    }

    fun configure(profile: PrinterProfile = state.profile, options: PrintOptions = state.options) {
        if (profile == state.profile && options == state.options) return
        state = state.copy(profile = profile, options = options, result = PhysicalPreviewResult.NotPrepared, editDiagnostic = null)
    }

    fun invalidate() { state = state.copy(result = PhysicalPreviewResult.NotPrepared, editDiagnostic = null) }

    fun prepare() {
        if (state.result is PhysicalPreviewResult.Ready) return
        val inputState = state
        val result = prepareState(inputState)
        if (state === inputState) state = result
    }

    suspend fun prepareAsync() {
        if (state.result is PhysicalPreviewResult.Ready) return
        val inputState = state
        val job = currentCoroutineContext().job
        val result = withContext(Dispatchers.Default) { prepareState(inputState) { !job.isActive } }
        if (state === inputState) state = result
    }

    private fun prepareState(inputState: ImagePrintState, cancelled: () -> Boolean = { false }): ImagePrintState {
        val input = inputState.input ?: return inputState
        val e = inputState.edits
        val orientation = e.orientation ?: input.orientation
        val orientedWidth = if (orientation >= 5) input.image.height else input.image.width
        val orientedHeight = if (orientation >= 5) input.image.width else input.image.height
        val x = e.cropX.toIntOrNull()
        val y = e.cropY.toIntOrNull()
        val width = if (e.cropWidth.isBlank()) orientedWidth else e.cropWidth.toIntOrNull()
        val height = if (e.cropHeight.isBlank()) orientedHeight else e.cropHeight.toIntOrNull()
        val dots = if (e.widthDots.isBlank()) inputState.profile.geometry.printableWidthDots else e.widthDots.toIntOrNull()
        if (x == null || y == null || width == null || height == null || dots == null || x < 0 || y < 0 ||
            width <= 0 || height <= 0 || x.toLong() + width > orientedWidth || y.toLong() + height > orientedHeight ||
            dots !in 1..inputState.profile.geometry.printableWidthDots || e.brightness !in -255..255 ||
            e.contrastPercent !in 0..400 || e.gammaHundredths !in 10..400 || e.threshold !in 0..255 ||
            orientation !in 1..8) {
            return inputState.copy(result = PhysicalPreviewResult.NotPrepared,
                editDiagnostic = "Invalid crop, print width, or image adjustment")
        }
        val asset = EmbeddedAssetReference("selected-image")
        val document = ThermalDocument(blocks = listOf(Image(asset, e.alignment,
            RequestedWidthSizing(Length(dots * 25.4 / inputState.profile.geometry.horizontalDpi)))))
        val raster = ResolvedRasterImage(input.image, RasterOptions(width, height, orientation,
            ImageCrop(x, y, width, height), e.brightness, e.contrastPercent, e.gammaHundredths, e.algorithm, e.threshold))
        val prepared = BasicPreparationEngine().prepare(document, inputState.profile, inputState.options,
            resolvedImages = mapOf(asset to raster), isCancelled = cancelled)
        val result = when (prepared) {
            is PreparationResult.Failure -> PhysicalPreviewResult.PreparationFailed(prepared.diagnostics)
            is PreparationResult.Success -> when (val preview = prepared.print.toPrintPreview()) {
                is PrintPreviewResult.Failure -> PhysicalPreviewResult.PreviewFailed(preview.diagnostics)
                is PrintPreviewResult.Success -> PhysicalPreviewResult.Ready(prepared.print, preview.preview)
            }
        }
        return inputState.copy(result = result, editDiagnostic = null)
    }
}
