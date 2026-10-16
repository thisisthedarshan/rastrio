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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.circuitnext.rastrio.core.preview.*
import com.circuitnext.rastrio.core.printer.NativeText
import org.jetbrains.compose.resources.Font
import rastrio.shared.generated.resources.Res
import rastrio.shared.generated.resources.noto_sans_regular
import rastrio.shared.generated.resources.noto_sans_bold
import rastrio.shared.generated.resources.noto_sans_mono_regular
import rastrio.shared.generated.resources.noto_sans_mono_bold
import kotlin.math.roundToInt
import kotlin.math.ceil

// These CP437 scalars have no glyph in the pinned proportional faces.
internal fun physicalPreviewUsesMono(character: Char): Boolean =
    character in "⌐░▒▓│┤╡╢╖╕╣║╗╝╜╛┐└┴┬├─┼╞╟╚╔╩╦╠═╬╧╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀∞∩≡≥≤⌠⌡≈∙√■"

@Composable
internal fun PhysicalPreviewScreen(preview: PrintPreview, syntheticTarget: Boolean = true,
    printingControls: @Composable () -> Unit = {}, backLabel: String = "Back to printer setup", compact: Boolean = false, onBack: () -> Unit) {
    var zoom by remember(preview) { mutableStateOf(1f) }
    var details by remember(preview) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (compact) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Physical preview", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onBack) { Text(backLabel) }
        } else {
            WorkspaceHeader("Physical preview", backLabel, onBack)
        }
        Text(if (compact) { if (syntheticTarget) "Software preview" else "Selected printer target" }
            else "${if (syntheticTarget) "Synthetic test target" else "Selected printer target"} · ${preview.target.profileId}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.semantics {
                contentDescription = "Selected ${if (syntheticTarget) "synthetic " else ""}printer profile ${preview.target.profileId}"
            })
        if (compact) TextButton(onClick = { details = !details }) { Text(if (details) "Hide preview details" else "Preview details") }
        if (!compact || details) {
            Text("${preview.widthDots} dots wide · ${preview.target.horizontalDpi} × ${preview.target.verticalDpi} dpi", style = MaterialTheme.typography.bodySmall)
            Text("Exact raster dots; native glyphs are representative", style = MaterialTheme.typography.bodySmall)
            if (compact) preview.diagnostics.forEach { Text("${it.code}: ${it.message}", style = MaterialTheme.typography.bodySmall) }
        }
        if (compact && !details && preview.diagnostics.isNotEmpty()) Text(preview.diagnostics.first().message,
            style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Box(Modifier.heightIn(max = if (compact) 144.dp else 180.dp).verticalScroll(rememberScrollState())) { printingControls() }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(enabled = zoom > 0.5f, onClick = { zoom = (zoom / 1.25f).coerceAtLeast(0.5f) }) { Text("Zoom out") }
            TextButton(enabled = zoom < 3f, onClick = { zoom = (zoom * 1.25f).coerceAtMost(3f) }) { Text("Zoom in") }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val fit = if (compact) minOf(1f, maxWidth.value / preview.widthDots) else 1f
            PhysicalPreviewCanvas(preview, zoom * fit, Modifier.fillMaxSize(), showDiagnostics = !compact)
        }
    }
}

/** Viewport zoom/density change painting only; tiles retain their finalized dot coordinates. */
@Composable
internal fun PhysicalPreviewCanvas(preview: PrintPreview, zoom: Float = 1f, modifier: Modifier = Modifier, showDiagnostics: Boolean = true) {
    val font = FontFamily(Font(Res.font.noto_sans_regular, FontWeight.Normal), Font(Res.font.noto_sans_bold, FontWeight.Bold))
    val symbols = FontFamily(Font(Res.font.noto_sans_mono_regular, FontWeight.Normal), Font(Res.font.noto_sans_mono_bold, FontWeight.Bold))
    val density = LocalDensity.current
    val verticalZoom = zoom.toDouble() * preview.target.horizontalDpi / preview.target.verticalDpi
    val renderPreview = remember(preview, zoom, density.density) {
        val factorX = density.density.toDouble() * zoom
        val factorY = density.density.toDouble() * verticalZoom
        val pixelWidth = ceil(preview.widthDots * factorX)
        val maxRows = ((2_000_000.0 / pixelWidth - 1.0) / factorY).toInt().coerceAtLeast(0)
        val maxDotArea = (maxRows.toLong() * preview.widthDots).coerceIn(1, 2_000_000).toInt()
        preview.withTileAreaLimit(maxDotArea)
    }
    val paperWidth = (preview.widthDots * zoom).dp
    val annotationColor = MaterialTheme.colorScheme.primary
    Box(modifier.horizontalScroll(rememberScrollState()).testTag("physical-canvas")
        .semantics { contentDescription = "Physical preview, ${preview.widthDots} prepared dots wide" }) {
        LazyColumn(Modifier.width(paperWidth + 180.dp).fillMaxHeight().testTag("physical-tiles")) {
            items(if (showDiagnostics) preview.diagnostics.size else 0) { index ->
                val diagnostic = preview.diagnostics[index]
                Text("${diagnostic.code} · ${diagnostic.message}", color = LocalAuthoringColors.current.warning,
                    modifier = Modifier.padding(vertical = 4.dp).testTag("physical-diagnostic"))
            }
            if (renderPreview is PrintPreviewResult.Failure) item {
                Text("Physical preview failed · ${renderPreview.diagnostics.first().code}",
                    color = LocalAuthoringColors.current.error, modifier = Modifier.testTag("physical-tile-failure"))
            }
            val tiled = (renderPreview as? PrintPreviewResult.Success)?.preview
            items(tiled?.tileCount ?: 0) { index ->
                when (val result = remember(tiled, index) { tiled!!.tile(index) }) {
                    is PrintPreviewTileResult.Failure -> for (diagnostic in result.diagnostics) {
                        Text("Physical preview failed · ${diagnostic.code} · ${diagnostic.message}",
                            color = LocalAuthoringColors.current.error, modifier = Modifier.testTag("physical-tile-failure"))
                    }
                    is PrintPreviewTileResult.Success -> {
                        val tile = result.tile
                        // Round absolute boundaries once so fractional density cannot accumulate drift.
                        val factorX = density.density.toDouble() * zoom
                        val factorY = density.density.toDouble() * verticalZoom
                        val topPx = (tile.bounds.y.toDouble() * factorY).roundToInt()
                        val endPx = (tile.bounds.bottom.toDouble() * factorY).roundToInt()
                        val tileHeight = with(density) { (endPx - topPx).toDp() }
                        Box(Modifier.width(paperWidth + 180.dp).height(if (tile.bounds.height == 0) 24.dp else tileHeight)
                            .testTag("physical-tile-$index")) {
                            Box(Modifier.width(paperWidth).height(tileHeight).background(Color.White).clipToBounds().testTag("physical-paper-$index")) {
                                Canvas(Modifier.fillMaxSize()) {
                                    for (primitive in tile.primitives) when (primitive) {
                                        is MonochromePrimitive -> {
                                            val b = primitive.clippedBounds
                                            for (y in b.y until b.bottom) {
                                                var x = 0
                                                while (x < primitive.graphic.widthDots) {
                                                    if (!primitive.graphic.isBlack(x, y - primitive.bounds.y)) { x++; continue }
                                                    val start = x++
                                                    while (x < primitive.graphic.widthDots && primitive.graphic.isBlack(x, y - primitive.bounds.y)) x++
                                                    drawRect(Color.Black, Offset(((primitive.bounds.x + start) * factorX).toFloat(), (y * factorY - topPx).toFloat()),
                                                        Size(((x - start) * factorX).toFloat(), factorY.toFloat()))
                                                }
                                            }
                                        }
                                        is NativeTextPrimitive -> if (primitive.operation.style.underline) {
                                            val b = primitive.bounds
                                            drawRect(Color.Black, Offset((b.x * factorX).toFloat(), ((b.bottom - 1) * factorY - topPx).toFloat()), Size((b.width * factorX).toFloat(), factorY.toFloat()))
                                        }
                                        is FeedPrimitive -> Unit
                                    }
                                }
                                for (primitive in tile.primitives) when (primitive) {
                                    is NativeTextPrimitive -> NativeCells(primitive.operation, tile.bounds.y, zoom, verticalZoom, font, symbols)
                                    is MonochromePrimitive -> if (primitive.kind in listOf(PreparedGraphicKind.MANUAL_CUT_GUIDE, PreparedGraphicKind.NATIVE_QR, PreparedGraphicKind.RASTER_QR, PreparedGraphicKind.RASTER_TEXT)) {
                                        Box(Modifier.absoluteOffset { IntOffset((primitive.bounds.x * factorX).roundToInt(), (primitive.clippedBounds.y * factorY).roundToInt() - topPx) }
                                            .size((primitive.bounds.width * zoom).dp, (primitive.clippedBounds.height * verticalZoom).dp)
                                            .semantics { contentDescription = when (primitive.kind) {
                                                PreparedGraphicKind.NATIVE_QR -> "Native QR, representative firmware matrix, exact prepared geometry"
                                                PreparedGraphicKind.RASTER_QR -> "Raster QR, exact prepared dots"
                                                PreparedGraphicKind.RASTER_TEXT -> "Raster text, exact prepared dots"
                                                else -> "Manual cut guide, printed content"
                                            } })
                                    }
                                    is FeedPrimitive -> Box(Modifier.absoluteOffset(0.dp, ((primitive.clippedBounds.y - tile.bounds.y) * verticalZoom).dp)
                                        .size(paperWidth, (primitive.clippedBounds.height * verticalZoom).dp)
                                        .semantics { contentDescription = "Paper feed, ${primitive.operation.advanceDots} dots" })
                                }
                            }
                            for (annotation in tile.annotations) {
                                val y = ((annotation.yDots - tile.bounds.y) * verticalZoom).dp
                                Canvas(Modifier.width(paperWidth).height(if (tile.bounds.height == 0) 24.dp else tileHeight)) {
                                    val lineY = (annotation.yDots * factorY - topPx).toFloat().coerceIn(0f, size.height - 1f)
                                    drawLine(annotationColor, Offset(0f, lineY), Offset(size.width, lineY), 2f)
                                }
                                Text("Automatic ${annotation.mode.name.lowercase()} cut\nNot printed", color = annotationColor,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.absoluteOffset(paperWidth + 8.dp,
                                        y.coerceAtMost((if (tile.bounds.height == 0) 24.dp else tileHeight) - 24.dp).coerceAtLeast(0.dp))
                                        .width(164.dp).semantics { contentDescription = "Automatic ${annotation.mode.name.lowercase()} cut, non-printing annotation" })
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Noto ink is representative. Every cell origin/size comes exclusively from NativeText. */
@Composable
private fun NativeCells(run: NativeText, tileTop: Int, zoom: Float, verticalZoom: Double, font: FontFamily, symbols: FontFamily) {
    val density = LocalDensity.current
    val factorX = density.density.toDouble() * zoom
    val factorY = density.density.toDouble() * verticalZoom
    val originX = (run.bounds.x * factorX).roundToInt()
    val originY = (run.bounds.y * factorY).roundToInt()
    val widthPx = (run.bounds.right * factorX).roundToInt() - originX
    val heightPx = (run.bounds.bottom * factorY).roundToInt() - originY
    val fontSize = (run.cellHeightDots * verticalZoom / 1.362 / density.fontScale).sp
    Layout(content = {
        for (character in run.text) {
            Layout(content = {
                Text(character.toString(), color = Color.Black, maxLines = 1, softWrap = false, overflow = TextOverflow.Visible,
                    style = TextStyle(fontFamily = if (physicalPreviewUsesMono(character)) symbols else font, fontSize = fontSize, fontWeight = if (run.style.bold) FontWeight.Bold else FontWeight.Normal),
                    modifier = Modifier.clearAndSetSemantics {}.graphicsLayer {
                        scaleX = (run.style.widthScale.toDouble() / run.style.heightScale * zoom / verticalZoom).toFloat()
                        transformOrigin = TransformOrigin(0f, 0f)
                    })
            }, modifier = Modifier.clipToBounds()) { measurables, constraints ->
                val glyph = measurables.single().measure(Constraints())
                val baseline = (run.cellHeightDots * factorY * 1069f / 1362f).roundToInt()
                layout(constraints.maxWidth, constraints.maxHeight) { glyph.place(0, baseline - glyph[FirstBaseline]) }
            }
        }
    }, modifier = Modifier.absoluteOffset {
        IntOffset(originX, originY - (tileTop * factorY).roundToInt())
    }.wrapContentSize(Alignment.TopStart, unbounded = true)
        .requiredSize(with(density) { widthPx.toDp() }, with(density) { heightPx.toDp() })
        .semantics { text = AnnotatedString(run.text) }) { measurables, constraints ->
        val height = heightPx
        val cells = measurables.mapIndexed { index, measurable ->
            val left = ((run.bounds.x + index * run.cellWidthDots) * factorX).roundToInt() - originX
            val right = ((run.bounds.x + (index + 1) * run.cellWidthDots) * factorX).roundToInt() - originX
            measurable.measure(Constraints.fixed(right - left, height))
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            cells.forEachIndexed { index, cell -> cell.place(((run.bounds.x + index * run.cellWidthDots) * factorX).roundToInt() - originX, 0) }
        }
    }
}
