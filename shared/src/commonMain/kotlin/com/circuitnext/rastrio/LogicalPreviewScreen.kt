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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.circuitnext.rastrio.core.layout.*
import com.circuitnext.rastrio.core.preview.LogicalPreview
import com.circuitnext.rastrio.core.text.TextWeight
import org.jetbrains.compose.resources.Font
import rastrio.shared.generated.resources.Res
import rastrio.shared.generated.resources.jetbrains_mono_regular
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

// Viewport choices, never inputs to Core wrapping or geometry.
private const val DP_PER_MM = 4.0
private const val BAND_MM = 64.0

internal sealed interface PreviewElement {
    val bounds: LogicalBounds
    data class Text(val run: LogicalTextRun, val baselineMm: Double) : PreviewElement {
        override val bounds get() = run.bounds
    }
    data class Fill(override val bounds: LogicalBounds) : PreviewElement
    data class Check(val marker: LogicalChecklistMarker) : PreviewElement {
        override val bounds get() = marker.bounds
    }
    data class Placeholder(override val bounds: LogicalBounds, val label: String, val description: String = label) : PreviewElement
}

/** Index references only: even large tables are composed by visible bands rather than one table.
 * Each ordinary item has at most three band references. Tall placeholders are interval-filtered
 * separately, avoiding an allocation proportional to their height. No pixels or source are copied.
 */
internal class PreviewBands(preview: LogicalPreview) {
    val count = maxOf(1, ceil(preview.heightMm / BAND_MM).toInt())
    private val bands = mutableMapOf<Int, MutableList<PreviewElement>>()
    // ponytail: scan tall placeholders per visible band; use an interval index only if profiling requires it.
    private val tall = mutableListOf<PreviewElement>()
    private fun add(element: PreviewElement) {
        val start = floor(element.bounds.yMm / BAND_MM).toInt().coerceIn(0, count - 1)
        val end = floor((element.bounds.yMm + element.bounds.heightMm) / BAND_MM).toInt().coerceIn(start, count - 1)
        if (end - start > 2) tall.add(element)
        else for (band in start..end) bands.getOrPut(band) { mutableListOf() }.add(element)
    }
    private fun text(block: LogicalTextBlock) {
        for (line in block.lines) for (run in line.runs) if (run.text != "\t") add(PreviewElement.Text(run, line.baselineMm))
    }
    private fun blocks(values: List<LogicalBlock>) {
        for (block in values) when (block) {
            is LogicalTextBlock -> text(block)
            is LogicalQuoteBlock -> blocks(block.blocks)
            is LogicalTableBlock -> for (row in block.rows) for (cell in row.cells) text(cell.text)
            is LogicalListBlock -> for (item in block.items) {
                when (val marker = item.marker) {
                    is LogicalOrderedMarker -> add(PreviewElement.Text(marker.run, marker.baselineMm))
                    is LogicalUnorderedMarker -> add(PreviewElement.Fill(marker.bounds))
                    is LogicalChecklistMarker -> add(PreviewElement.Check(marker))
                }
                blocks(item.blocks)
            }
            is LogicalSeparator -> add(PreviewElement.Fill(block.bounds))
            is LogicalImagePlaceholder -> add(PreviewElement.Placeholder(block.bounds, "Image placeholder",
                "Image placeholder" + (block.altText?.let { ": $it" } ?: "")))
            is LogicalQrPlaceholder -> add(PreviewElement.Placeholder(block.bounds, "QR placeholder"))
            is LogicalPlaceholder -> add(PreviewElement.Placeholder(block.bounds, "${block.kind} placeholder"))
        }
    }
    init { blocks(preview.blocks) }
    fun elements(band: Int): List<PreviewElement> = (bands[band] ?: emptyList()) + tall.filter {
        it.bounds.yMm < (band + 1) * BAND_MM && it.bounds.yMm + it.bounds.heightMm > band * BAND_MM
    }
}

@Composable
internal fun PreviewScreen(preview: LogicalPreview, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Logical preview", style = MaterialTheme.typography.headlineMedium)
        AuthoringButton("Back to Markdown", onClick = onBack)
        Text("${preview.widthMm} × ${preview.heightMm} mm · ${preview.orientation.name.lowercase()}" +
            " · scroll to inspect", style = MaterialTheme.typography.bodySmall)
        LogicalPreviewCanvas(preview, Modifier.weight(1f).fillMaxWidth())
    }
}

@Composable
internal fun LogicalPreviewCanvas(preview: LogicalPreview, modifier: Modifier = Modifier) {
    val index = remember(preview) { PreviewBands(preview) }
    val font = FontFamily(Font(Res.font.jetbrains_mono_regular))
    val density = LocalDensity.current
    val ink = MaterialTheme.colorScheme.onSurface
    val width = (preview.widthMm * DP_PER_MM).dp
    Box(modifier.horizontalScroll(rememberScrollState()).testTag("logical-canvas")
        .semantics { contentDescription = "Assembled logical canvas, ${preview.widthMm} mm wide" }) {
        LazyColumn(Modifier.width(width).fillMaxHeight().background(MaterialTheme.colorScheme.surface)) {
            items(index.count) { band ->
                val top = band * BAND_MM
                val height = if (preview.heightMm == 0.0) 16.0 else minOf(BAND_MM, preview.heightMm - top)
                // Round absolute band boundaries once; fractional densities cannot accumulate drift.
                val topPx = (top * DP_PER_MM * density.density).roundToInt()
                val endPx = ((top + height) * DP_PER_MM * density.density).roundToInt()
                val bandHeight = with(density) { (endPx - topPx).toDp() }
                val elements = remember(index, band) { index.elements(band) }
                Box(Modifier.requiredSize(width, bandHeight).clipToBounds()) {
                    Canvas(Modifier.fillMaxSize()) {
                        val factor = (DP_PER_MM * density.density).toFloat()
                        fun origin(b: LogicalBounds) = Offset((b.xMm * factor).toFloat(), (b.yMm * factor - topPx).toFloat())
                        fun extent(b: LogicalBounds) = Size((b.widthMm * factor).toFloat(), (b.heightMm * factor).toFloat())
                        for (element in elements) when (element) {
                            is PreviewElement.Fill -> drawRect(ink, origin(element.bounds), extent(element.bounds))
                            is PreviewElement.Check -> {
                                val marker = element.marker
                                val stroke = (marker.strokeWidthMm * factor).toFloat()
                                drawRect(ink, origin(marker.outlineCenterlineBounds), extent(marker.outlineCenterlineBounds), style = Stroke(stroke))
                                if (marker.checked) {
                                    val path = Path()
                                    marker.checkPoints.forEachIndexed { i, point ->
                                        val x = (point.xMm * factor).toFloat(); val y = (point.yMm * factor - topPx).toFloat()
                                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                    }
                                    drawPath(path, ink, style = Stroke(stroke, cap = StrokeCap.Butt, join = StrokeJoin.Bevel))
                                }
                            }
                            is PreviewElement.Placeholder -> drawRect(ink, origin(element.bounds), extent(element.bounds), style = Stroke(density.density))
                            is PreviewElement.Text -> Unit
                        }
                    }
                    for (element in elements) when (element) {
                        is PreviewElement.Text -> ResolvedRun(element, top, font)
                        is PreviewElement.Placeholder -> Text(element.label, style = MaterialTheme.typography.labelSmall,
                            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                            color = ink,
                            modifier = Modifier.absoluteOffset {
                                IntOffset((element.bounds.xMm * DP_PER_MM * density.density).roundToInt(),
                                    (element.bounds.yMm * DP_PER_MM * density.density).roundToInt() - topPx)
                            }.wrapContentSize(androidx.compose.ui.Alignment.TopStart, unbounded = true)
                                .width((element.bounds.widthMm * DP_PER_MM).dp)
                                .heightIn(max = minOf((BAND_MM * DP_PER_MM).dp, (element.bounds.heightMm * DP_PER_MM).dp))
                                .then(if (floor(element.bounds.yMm / BAND_MM).toInt() == band)
                                    Modifier.semantics { contentDescription = element.description }
                                    else Modifier.clearAndSetSemantics {}))
                        is PreviewElement.Check -> Box(Modifier.absoluteOffset((element.bounds.xMm * DP_PER_MM).dp,
                            ((element.bounds.yMm - top) * DP_PER_MM).dp)
                            .size((element.bounds.widthMm * DP_PER_MM).dp, (element.bounds.heightMm * DP_PER_MM).dp)
                            .then(if (floor(element.bounds.yMm / BAND_MM).toInt() == band)
                                Modifier.semantics { contentDescription = if (element.marker.checked) "Checked list item" else "Unchecked list item" }
                                else Modifier))
                        is PreviewElement.Fill -> Unit
                    }
                }
            }
        }
    }
}

/** Painting alignment only: a glyph baseline is aligned to Core's already-final baseline.
 * Text never wraps, determines a band height, or moves a subsequent run/block.
 */
@Composable
private fun ResolvedRun(element: PreviewElement.Text, bandTop: Double, font: FontFamily) {
    val run = element.run
    val density = LocalDensity.current
    val style = run.typography.style
    val decorations = listOfNotNull(if (style.underline) TextDecoration.Underline else null,
        if (style.strike) TextDecoration.LineThrough else null)
    val fontSize = ((run.typography.metrics.ascentMm / 1.020 * DP_PER_MM) / density.fontScale).sp
    Layout(content = {
        Text(run.text, color = MaterialTheme.colorScheme.onSurface,
            softWrap = false, maxLines = 1, overflow = TextOverflow.Visible,
            style = TextStyle(fontFamily = font, fontSize = fontSize,
                fontWeight = if (style.weight == TextWeight.BOLD) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (style.emphasis) FontStyle.Italic else FontStyle.Normal,
                textDecoration = if (decorations.isEmpty()) null else TextDecoration.combine(decorations)))
    }, modifier = Modifier.absoluteOffset {
        val factor = DP_PER_MM * density.density
        IntOffset((run.bounds.xMm * factor).roundToInt(),
            (run.bounds.yMm * factor).roundToInt() - (bandTop * factor).roundToInt())
    }.wrapContentSize(androidx.compose.ui.Alignment.TopStart, unbounded = true).requiredSize(
            (run.bounds.widthMm * DP_PER_MM).dp, (run.bounds.heightMm * DP_PER_MM).dp)
        .then(if (floor(run.bounds.yMm / BAND_MM) * BAND_MM == bandTop) Modifier
            else Modifier.clearAndSetSemantics {})) { measurables, constraints ->
        val glyph = measurables.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val factor = DP_PER_MM * density.density
        val baseline = (element.baselineMm * factor).roundToInt() - (run.bounds.yMm * factor).roundToInt()
        layout(constraints.maxWidth, constraints.maxHeight) {
            glyph.place(0, baseline - glyph[FirstBaseline])
        }
    }
}
