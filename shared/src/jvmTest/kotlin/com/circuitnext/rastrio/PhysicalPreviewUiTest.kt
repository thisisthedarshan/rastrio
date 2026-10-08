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
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.circuitnext.rastrio.core.document.*
import com.circuitnext.rastrio.core.printer.*
import com.circuitnext.rastrio.core.preview.*
import com.circuitnext.rastrio.core.profile.PrinterGeometry
import com.circuitnext.rastrio.core.profile.PrinterProfile
import com.circuitnext.rastrio.core.profile.SyntheticReferenceProfiles
import kotlin.test.*
import kotlin.math.roundToInt

@OptIn(ExperimentalTestApi::class)
class PhysicalPreviewUiTest {
    private fun plan(text: String = "Receipt", profile: PrinterProfile = SyntheticReferenceProfiles.narrow,
        options: PrintOptions = PrintOptions()): PreparedPrint =
        assertIs<PreparationResult.Success>(BasicPreparationEngine().prepare(
            ThermalDocument(blocks = listOf(Paragraph(Alignment.LEFT, listOf(Text(text))))), profile, options)).print

    private fun preview(print: PreparedPrint, resources: PhysicalPreviewResourcePolicy = PhysicalPreviewResourcePolicy()) =
        assertIs<PrintPreviewResult.Success>(print.toPrintPreview(resources)).preview

    @Test fun zoomDensityAndFontScalePreservePreparedLinesAndGeometry() = runComposeUiTest {
        val print = plan("A".repeat(40), SyntheticReferenceProfiles.narrow.copy(geometry = PrinterGeometry(203, 406, 384)))
        val preview = preview(print)
        val runs = print.operations.filterIsInstance<NativeText>()
        val bounds = runs.map { it.bounds }
        val lines = runs.map { it.text }
        assertEquals(listOf("A".repeat(32), "A".repeat(8)), lines)
        var zoom by mutableStateOf(1f)
        var density by mutableStateOf(Density(1f, 1f))
        setContent { CompositionLocalProvider(LocalDensity provides density) {
            AuthoringTheme { Box(Modifier.requiredSize(360.dp, 360.dp)) {
                PhysicalPreviewCanvas(preview, zoom, Modifier.fillMaxSize())
            } }
        } }
        for ((scale, screenDensity, fontScale) in listOf(Triple(1f, 1f, 1f), Triple(0.5f, 1.5f, 2f), Triple(2f, 1f, 0.8f))) {
            runOnIdle { zoom = scale; density = Density(screenDensity, fontScale) }
            for ((index, line) in lines.withIndex()) {
                val node = onNodeWithText(line).fetchSemanticsNode()
                assertEquals(bounds[index].width * scale * screenDensity, node.size.width.toFloat(), 1f)
                assertEquals(bounds[index].height * scale * screenDensity * 203f / 406f, node.size.height.toFloat(), 1f)
            }
            assertEquals(bounds, runs.map { it.bounds })
            assertEquals(lines, print.operations.filterIsInstance<NativeText>().map { it.text })
            val projected = assertIs<PrintPreviewTileResult.Success>(preview.tile(0)).tile.primitives.filterIsInstance<NativeTextPrimitive>()
            for (index in runs.indices) assertSame(runs[index], projected[index].operation)
        }
    }

    @Test fun manualGuideAndFallbackWarningRemainPrintedAndAccessible() = runComposeUiTest {
        val print = plan(options = PrintOptions(cutIntent = CutIntent.AUTOMATIC_OR_MANUAL, finalFeedDots = 12))
        setContent { AuthoringTheme { PhysicalPreviewScreen(preview(print)) {} } }
        onNodeWithText("Receipt").assertExists()
        onNodeWithContentDescription("Manual cut guide, printed content").assertExists()
        onNodeWithText("PRN201", substring = true).assertIsDisplayed()
        onAllNodesWithContentDescription("Paper feed, 12 dots").assertCountEquals(
            print.operations.filterIsInstance<Feed>().count { it.advanceDots == 12 })
        onNodeWithContentDescription("Automatic full cut, non-printing annotation").assertDoesNotExist()
        onNodeWithContentDescription("Selected synthetic printer profile ${print.target.profileId}").assertExists()
    }

    @Test fun automaticCutIsAnAnnotationWithNoPrintedGuide() = runComposeUiTest {
        val print = plan(profile = SyntheticReferenceProfiles.wide80mm,
            options = PrintOptions(cutIntent = CutIntent.AUTOMATIC, cutMode = CutMode.PARTIAL))
        setContent { AuthoringTheme { PhysicalPreviewCanvas(preview(print), modifier = Modifier.fillMaxSize()) } }
        onNodeWithContentDescription("Automatic partial cut, non-printing annotation").assertExists()
        onNodeWithText("Not printed", substring = true).assertExists()
        onNodeWithContentDescription("Manual cut guide, printed content").assertDoesNotExist()
        assertTrue(print.operations.none { it is ManualCutGuide })
        assertEquals(print.heightDots, print.operations.filterIsInstance<AutomaticCut>().single().bounds.y)
    }

    @Test fun tileResourceFailureIsVisibleWithoutSuccessfulContent() = runComposeUiTest {
        val print = plan("A".repeat(40))
        val preview = preview(print, PhysicalPreviewResourcePolicy(maxPrimitivesPerTile = 1))
        assertIs<PrintPreviewTileResult.Failure>(preview.tile(0))
        setContent { AuthoringTheme { PhysicalPreviewCanvas(preview, modifier = Modifier.fillMaxSize()) } }
        onNodeWithTag("physical-tile-failure").assertIsDisplayed()
        onNodeWithText("Physical preview failed · PRV120", substring = true).assertExists()
        onNodeWithText("A".repeat(32)).assertDoesNotExist()
    }

    @Test fun distantRowsUseTheSameAbsolutePixelRoundingForEveryTileSize() = runComposeUiTest {
        val profile = SyntheticReferenceProfiles.narrow.copy(
            geometry = PrinterGeometry(203, 203, 256),
            raster = SyntheticReferenceProfiles.narrow.raster.copy(maxWidthDots = 256),
        )
        val input = ThermalDocument(blocks = (1..550).map { Paragraph(Alignment.LEFT, listOf(Text("Line $it"))) })
        val print = assertIs<PreparationResult.Success>(BasicPreparationEngine().prepare(input, profile)).print
        val run = print.operations.filterIsInstance<NativeText>().first { it.text == "Line 532" }
        assertEquals(19116, run.bounds.y)
        val scale = 2.44140625f
        val factor = 3.0 * scale
        var displayed by mutableStateOf(preview(print, PhysicalPreviewResourcePolicy(preferredTileHeightDots = 97)))
        setContent { CompositionLocalProvider(LocalDensity provides Density(3f, 1f)) {
            AuthoringTheme { Box(Modifier.requiredSize(300.dp, 300.dp)) {
                PhysicalPreviewCanvas(displayed, scale, Modifier.fillMaxSize())
            } }
        } }
        for (tileHeight in listOf(97, 103)) {
            runOnIdle { displayed = preview(print, PhysicalPreviewResourcePolicy(preferredTileHeightDots = tileHeight)) }
            val index = run.bounds.y / tileHeight
            onNodeWithTag("physical-tiles").performScrollToIndex(index)
            val tile = onNodeWithTag("physical-tile-$index").fetchSemanticsNode()
            val node = onNodeWithText(run.text).fetchSemanticsNode()
            val top = (index * tileHeight * factor).roundToInt()
            assertEquals(((run.bounds.y * factor).roundToInt() - top).toFloat(), node.positionInRoot.y - tile.positionInRoot.y, 0.1f)
            assertEquals((run.bounds.bottom * factor).roundToInt() - (run.bounds.y * factor).roundToInt(), node.size.height)
        }
    }

    @Test fun supplementaryBoxDrawingAndMathGlyphsPaintAtBothWeights() = runComposeUiTest {
        val characters = "┌─┐∞√"
        val normal = plan(characters)
        val boldInput = ThermalDocument(blocks = listOf(Paragraph(Alignment.LEFT, listOf(Strong(listOf(Text(characters)))))))
        val bold = assertIs<PreparationResult.Success>(BasicPreparationEngine().prepare(boldInput, SyntheticReferenceProfiles.narrow)).print
        var displayed by mutableStateOf(preview(normal))
        setContent { AuthoringTheme { Box(Modifier.requiredSize(600.dp, 200.dp)) {
            PhysicalPreviewCanvas(displayed, modifier = Modifier.fillMaxSize())
        } } }
        onNodeWithText(characters).assertExists()
        val regularPixels = onRoot().captureToImage().toPixelMap()
        runOnIdle { displayed = preview(bold) }
        onNodeWithText(characters).assertExists()
        val boldPixels = onRoot().captureToImage().toPixelMap()
        val run = normal.operations.filterIsInstance<NativeText>().single()
        for (index in characters.indices) for (pixels in listOf(regularPixels, boldPixels)) {
            assertTrue((0 until run.cellHeightDots).any { y ->
                (index * run.cellWidthDots until (index + 1) * run.cellWidthDots).any { x ->
                    pixels[x, y] != androidx.compose.ui.graphics.Color.White
                }
            }, "Supplementary glyph ${characters[index]} must paint inside its prepared cell")
        }
        assertTrue((0 until run.cellHeightDots).any { y -> (0 until run.bounds.width).any { x -> regularPixels[x, y] != boldPixels[x, y] } })
    }

    @Test fun shortTilesPreserveGlyphPixelsAtPreparedCoordinates() = runComposeUiTest {
        val print = plan("Receipt")
        var displayed by mutableStateOf(preview(print))
        setContent { AuthoringTheme { Box(Modifier.requiredSize(600.dp, 200.dp)) {
            PhysicalPreviewCanvas(displayed, modifier = Modifier.fillMaxSize())
        } } }
        val original = onRoot().captureToImage().toPixelMap()
        runOnIdle { displayed = preview(print, PhysicalPreviewResourcePolicy(preferredTileHeightDots = 16)) }
        val tiled = onRoot().captureToImage().toPixelMap()
        for (y in 0 until print.heightDots) for (x in 0 until print.target.printableWidthDots) {
            assertEquals(original[x, y], tiled[x, y], "Retiling moved prepared glyph ink at ($x,$y)")
        }
    }

    @Test fun longOutputComposesOnlyVisibleTilesAndScrollsToFinalPreparedText() = runComposeUiTest {
        val input = ThermalDocument(blocks = (1..2000).map { Paragraph(Alignment.LEFT, listOf(Text("Line $it"))) })
        val print = assertIs<PreparationResult.Success>(BasicPreparationEngine().prepare(input, SyntheticReferenceProfiles.narrow)).print
        val preview = preview(print)
        assertTrue(preview.tileCount > 40)
        setContent { AuthoringTheme { Box(Modifier.requiredSize(600.dp, 400.dp)) {
            PhysicalPreviewCanvas(preview, modifier = Modifier.fillMaxSize())
        } } }
        onNodeWithText("Line 1").assertExists()
        onNodeWithText("Line 2000").assertDoesNotExist()
        onNodeWithTag("physical-tiles").performScrollToIndex(preview.diagnostics.size + preview.tileCount - 1)
        onNodeWithText("Line 2000").assertExists()
        onNodeWithText("Line 1").assertDoesNotExist()
    }
}
