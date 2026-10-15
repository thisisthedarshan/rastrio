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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import com.circuitnext.rastrio.core.document.Alignment
import com.circuitnext.rastrio.core.raster.DitherAlgorithm
import com.circuitnext.rastrio.presentation.*
import com.circuitnext.rastrio.printing.ImagePlatform

/** Saves only bounded user adjustments; source pixels and prepared output stay outside saved state. */
internal val ImagePrintEditsSaver = listSaver<ImagePrintEdits, String>(
    save = { edits -> with(edits) {
        listOf(cropX, cropY, cropWidth, cropHeight, widthDots, brightness.toString(), contrastPercent.toString(),
            gammaHundredths.toString(), algorithm.name, threshold.toString(), orientation?.toString().orEmpty(), alignment.name)
    } },
    restore = { values -> runCatching {
        require(values.size == 12 && values.all { it.length <= 20 } && values.take(5).all { it.length <= 12 })
        val edits = ImagePrintEdits(values[0], values[1], values[2], values[3], values[4],
            brightness = values[5].toInt(), contrastPercent = values[6].toInt(), gammaHundredths = values[7].toInt(),
            algorithm = DitherAlgorithm.valueOf(values[8]), threshold = values[9].toInt(),
            orientation = values[10].takeIf { it.isNotEmpty() }?.toInt(), alignment = Alignment.valueOf(values[11]))
        require(edits.brightness in -255..255 && edits.contrastPercent in 0..400 && edits.gammaHundredths in 10..400 &&
            edits.threshold in 0..255 && (edits.orientation == null || edits.orientation in 1..8))
        edits
    }.getOrNull() },
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ImagePrintScreen(state: ImagePrintState, platform: ImagePlatform?, status: String,
    busy: Boolean, onEdit: (ImagePrintEdits) -> Unit, onPreview: () -> Unit,
    onBack: () -> Unit, preparing: Boolean = false) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp).widthIn(max = 960.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Image Print", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("Back to Home") }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = platform != null && !busy, onClick = { platform?.importImage() }) { Text("Choose image") }
            Text(if (platform == null) "Image selection is available on Android" else status,
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val keyboardHeight = with(LocalDensity.current) { WindowInsets.ime.getBottom(this).toDp() }
            if (compactPrintLayout(maxWidth, maxHeight, keyboardHeight)) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ImagePhysicalPreview(state, preparing, Modifier.weight(0.48f).fillMaxHeight())
                    ImagePrintAdjustments(state, onEdit, busy, Modifier.weight(0.52f).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ImagePhysicalPreview(state, preparing, Modifier.weight(0.48f).fillMaxWidth())
                    HorizontalDivider()
                    ImagePrintAdjustments(state, onEdit, busy, Modifier.weight(0.52f).fillMaxWidth())
                }
            }
        }
        Button(enabled = state.input != null && !busy, onClick = onPreview,
            modifier = Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally)) { Text("Continue to Print") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImagePhysicalPreview(state: ImagePrintState, preparing: Boolean, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val metadataHeight = maxHeight * 0.5f
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val result = state.result
            var zoom by remember { mutableStateOf(1f) }
            Column(Modifier.fillMaxWidth().heightIn(max = metadataHeight).verticalScroll(rememberScrollState())) {
                Text("Physical preview", style = MaterialTheme.typography.titleMedium)
                Text("Image target: ${state.profile.display.name}", style = MaterialTheme.typography.bodySmall)
                if (preparing) Text("Updating preview…", modifier = Modifier.testTag("image-updating"))
                if (result is PhysicalPreviewResult.Ready) {
                    Text("Image prepared: ${result.preview.widthDots} dots wide · ${result.preview.heightDots} dots high",
                        style = MaterialTheme.typography.bodySmall)
                    FlowRow {
                        TextButton(onClick = { zoom = 1f }) { Text("Fit to width") }
                        TextButton(enabled = zoom > 1f, onClick = { zoom = (zoom / 1.25f).coerceAtLeast(1f) }) { Text("Zoom out") }
                        TextButton(enabled = zoom < 3f, onClick = { zoom = (zoom * 1.25f).coerceAtMost(3f) }) { Text("Zoom in") }
                    }
                }
            }
            if (result is PhysicalPreviewResult.Ready) {
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                    PhysicalPreviewCanvas(result.preview, minOf(1f, maxWidth.value / result.preview.widthDots) * zoom,
                        Modifier.fillMaxSize(), showDiagnostics = false)
                }
            } else Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                if (state.editDiagnostic != null) Text(state.editDiagnostic, color = LocalAuthoringColors.current.error)
                when (result) {
                    is PhysicalPreviewResult.PreparationFailed -> for (diagnostic in result.diagnostics)
                        Text(diagnostic.message, color = LocalAuthoringColors.current.error)
                    is PhysicalPreviewResult.PreviewFailed -> for (diagnostic in result.diagnostics)
                        Text(diagnostic.message, color = LocalAuthoringColors.current.error)
                    else -> if (!preparing && state.editDiagnostic == null)
                        Text(if (state.input == null) "Choose an image to preview" else "Waiting for updated preview")
                }
            }
        }
    }
}

@Composable
private fun ImagePrintAdjustments(state: ImagePrintState, onEdit: (ImagePrintEdits) -> Unit,
    busy: Boolean, modifier: Modifier) {
        LazyColumn(modifier.testTag("image-print-list")) {
            item {
                val input = state.input
                if (input != null) {
                    val orientation = state.edits.orientation ?: input.orientation
                    Text("Oriented image: ${if (orientation >= 5) input.image.height else input.image.width} × " +
                        "${if (orientation >= 5) input.image.width else input.image.height} pixels",
                        style = MaterialTheme.typography.bodySmall)
                }
                Text("Aspect ratio is preserved within the reserved square image area. Unused height remains paper feed.",
                    style = MaterialTheme.typography.bodySmall)
                ImageAdjustmentControls(state, onEdit, !busy)
            }
        }
}

/** Both editing and confirmation use the same retained edits and Core preparation path. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ImageAdjustmentControls(state: ImagePrintState, onEdit: (ImagePrintEdits) -> Unit, enabled: Boolean = true) {
    val edits = state.edits
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Image adjustments", style = MaterialTheme.typography.titleMedium)
        TextButton(enabled = enabled, onClick = { onEdit(ImagePrintEdits()) }) { Text("Reset image adjustments") }
        Text("Dithering: ${edits.algorithm.imageLabel()}")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (algorithm in DitherAlgorithm.entries) FilterChip(selected = edits.algorithm == algorithm,
                enabled = enabled, onClick = { onEdit(edits.copy(algorithm = algorithm)) },
                label = { Text(algorithm.imageLabel()) })
        }
        ImageSlider("Brightness", edits.brightness, "${edits.brightness}", -255f..255f, enabled) {
            onEdit(edits.copy(brightness = it))
        }
        ImageSlider("Contrast", edits.contrastPercent, "${edits.contrastPercent}%", 0f..400f, enabled) {
            onEdit(edits.copy(contrastPercent = it))
        }
        ImageSlider("Gamma", edits.gammaHundredths, "${edits.gammaHundredths / 100.0}", 10f..400f, enabled) {
            onEdit(edits.copy(gammaHundredths = it))
        }
        if (edits.algorithm == DitherAlgorithm.THRESHOLD) ImageSlider("Threshold", edits.threshold,
            "${edits.threshold}", 0f..255f, enabled) { onEdit(edits.copy(threshold = it)) }
        var orientationMenu by remember { mutableStateOf(false) }
        val orientations = listOf("Original orientation", "Normal", "Mirror horizontally", "Rotate 180°", "Mirror vertically",
            "Transpose", "Rotate 90° clockwise", "Transverse", "Rotate 90° counterclockwise")
        Box {
            OutlinedButton(enabled = enabled, onClick = { orientationMenu = true }) {
                Text("Orientation: ${orientations[(edits.orientation ?: 0).coerceIn(0, 8)]}")
            }
            DropdownMenu(expanded = orientationMenu, onDismissRequest = { orientationMenu = false }) {
                orientations.forEachIndexed { index, label -> DropdownMenuItem(text = { Text(label) }, onClick = {
                    orientationMenu = false
                    onEdit(edits.copy(orientation = index.takeIf { it != 0 }))
                }) }
            }
        }
        Text("Crop in oriented source pixels", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ImageNumber("Crop x", edits.cropX, enabled) { onEdit(edits.copy(cropX = it)) }
            ImageNumber("Crop y", edits.cropY, enabled) { onEdit(edits.copy(cropY = it)) }
            ImageNumber("Crop width", edits.cropWidth, enabled) { onEdit(edits.copy(cropWidth = it)) }
            ImageNumber("Crop height", edits.cropHeight, enabled) { onEdit(edits.copy(cropHeight = it)) }
            ImageNumber("Print width (dots)", edits.widthDots, enabled) { onEdit(edits.copy(widthDots = it)) }
        }
        Text("Blank crop dimensions use the full source; blank print width uses the target width.",
            style = MaterialTheme.typography.bodySmall)
        Text("Alignment")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (alignment in Alignment.entries) FilterChip(selected = edits.alignment == alignment, enabled = enabled,
                onClick = { onEdit(edits.copy(alignment = alignment)) }, label = { Text(alignment.name.lowercase()) })
        }
    }
}

@Composable
private fun ImageSlider(label: String, value: Int, display: String, range: ClosedFloatingPointRange<Float>,
    enabled: Boolean, onValue: (Int) -> Unit) {
    Text("$label: $display")
    Slider(value.toFloat(), { onValue(it.toInt()) }, enabled = enabled, valueRange = range,
        modifier = Modifier.semantics { contentDescription = label; stateDescription = display })
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
