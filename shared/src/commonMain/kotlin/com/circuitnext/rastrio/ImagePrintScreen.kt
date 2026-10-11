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

package com.circuitnext.rastrio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import com.circuitnext.rastrio.core.profile.PrinterProfile
import com.circuitnext.rastrio.core.profile.SyntheticReferenceProfiles
import com.circuitnext.rastrio.core.raster.DitherAlgorithm
import com.circuitnext.rastrio.presentation.*
import com.circuitnext.rastrio.printing.ImagePlatform

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ImagePrintScreen(state: ImagePrintState, platform: ImagePlatform?, status: String,
    importedProfile: PrinterProfile?, busy: Boolean, onEdit: (ImagePrintEdits) -> Unit,
    onProfile: (PrinterProfile) -> Unit, onPrepare: () -> Unit, onPreview: () -> Unit,
    onBack: () -> Unit, printingControls: @Composable () -> Unit, preparing: Boolean = false) {
    val edits = state.edits
    LazyColumn(Modifier.fillMaxSize().padding(16.dp).widthIn(max = 960.dp).testTag("image-print-list"),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Image Print", style = MaterialTheme.typography.headlineMedium)
            AuthoringButton("Back to Home", onClick = onBack)
            AuthoringButton("Choose image", platform != null && !busy, onClick = { platform?.importImage() })
            Text(if (platform == null) "Image selection is available on Android" else status)
            val input = state.input
            if (input != null) Text("Oriented image: ${if (input.orientation >= 5) input.image.height else input.image.width} × " +
                "${if (input.orientation >= 5) input.image.width else input.image.height} pixels")
        }
        item {
            Text("Image target: ${state.profile.display.name}")
            Text("Aspect ratio is preserved within the reserved square image area. Unused height remains paper feed.",
                style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AuthoringButton("Simulate 384 dots", !busy, onClick = { onProfile(SyntheticReferenceProfiles.narrow) })
                AuthoringButton("Simulate 576 dots", !busy, onClick = { onProfile(SyntheticReferenceProfiles.wide80mm) })
                if (importedProfile != null) AuthoringButton("Use imported image profile", !busy,
                    onClick = { onProfile(importedProfile) })
            }
        }
        item {
            Text("Crop in oriented source pixels", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ImageNumber("Crop x", edits.cropX, !busy) { onEdit(edits.copy(cropX = it)) }
                ImageNumber("Crop y", edits.cropY, !busy) { onEdit(edits.copy(cropY = it)) }
                ImageNumber("Crop width", edits.cropWidth, !busy) { onEdit(edits.copy(cropWidth = it)) }
                ImageNumber("Crop height", edits.cropHeight, !busy) { onEdit(edits.copy(cropHeight = it)) }
                ImageNumber("Print width (dots)", edits.widthDots, !busy) { onEdit(edits.copy(widthDots = it)) }
            }
            Text("Blank crop dimensions use the full source; blank print width uses the target width.",
                style = MaterialTheme.typography.bodySmall)
        }
        item {
            Text("Brightness: ${edits.brightness}")
            Slider(edits.brightness.toFloat(), { onEdit(edits.copy(brightness = it.toInt())) }, enabled = !busy,
                valueRange = -255f..255f, modifier = Modifier.semantics {
                    contentDescription = "Brightness"; stateDescription = edits.brightness.toString()
                })
            Text("Contrast: ${edits.contrastPercent}%")
            Slider(edits.contrastPercent.toFloat(), { onEdit(edits.copy(contrastPercent = it.toInt())) }, enabled = !busy,
                valueRange = 0f..400f, modifier = Modifier.semantics {
                    contentDescription = "Contrast"; stateDescription = "${edits.contrastPercent}%"
                })
            Text("Gamma: ${edits.gammaHundredths / 100.0}")
            Slider(edits.gammaHundredths.toFloat(), { onEdit(edits.copy(gammaHundredths = it.toInt())) }, enabled = !busy,
                valueRange = 10f..400f, modifier = Modifier.semantics {
                    contentDescription = "Gamma"; stateDescription = (edits.gammaHundredths / 100.0).toString()
                })
        }
        item {
            Text("Dithering: ${edits.algorithm.imageLabel()}")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (algorithm in DitherAlgorithm.entries) AuthoringButton(algorithm.imageLabel(), !busy,
                    onClick = { onEdit(edits.copy(algorithm = algorithm)) })
            }
            AuthoringButton("Prepare image", state.input != null && !busy && !preparing, onPrepare)
            AuthoringButton("Open image physical preview", state.result is PhysicalPreviewResult.Ready && !busy && !preparing, onPreview)
            if (preparing) Text("Preparing image pixels and physical preview")
            if (state.editDiagnostic != null) Text(state.editDiagnostic, color = LocalAuthoringColors.current.error)
            when (val result = state.result) {
                is PhysicalPreviewResult.Ready -> Text("Image prepared: ${result.preview.widthDots} dots wide · ${result.preview.heightDots} dots high")
                is PhysicalPreviewResult.PreparationFailed -> for (diagnostic in result.diagnostics)
                    Text("${diagnostic.code} · ${diagnostic.message}", color = LocalAuthoringColors.current.error)
                is PhysicalPreviewResult.PreviewFailed -> for (diagnostic in result.diagnostics)
                    Text("${diagnostic.code} · ${diagnostic.message}", color = LocalAuthoringColors.current.error)
                PhysicalPreviewResult.NotPrepared -> Text("Prepare the image after changing its source or settings")
            }
        }
        item { printingControls() }
    }
}

@Composable
private fun ImageNumber(label: String, value: String, enabled: Boolean, onValue: (String) -> Unit) {
    OutlinedTextField(value, { onValue(it.take(12)) }, label = { Text(label) }, enabled = enabled,
        singleLine = true, modifier = Modifier.width(180.dp))
}

private fun DitherAlgorithm.imageLabel(): String = when (this) {
    DitherAlgorithm.THRESHOLD -> "Threshold"
    DitherAlgorithm.BAYER -> "Bayer"
    DitherAlgorithm.ATKINSON -> "Atkinson"
    DitherAlgorithm.FLOYD_STEINBERG -> "Floyd–Steinberg"
}
