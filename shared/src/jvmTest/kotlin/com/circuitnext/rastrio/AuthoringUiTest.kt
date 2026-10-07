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

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.*
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.runtime.*
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.input.key.Key
import org.jetbrains.skia.Image
import java.io.File
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import com.circuitnext.rastrio.core.document.Orientation
import com.circuitnext.rastrio.presentation.*
import com.circuitnext.rastrio.core.preview.toLogicalPreview


@OptIn(ExperimentalTestApi::class)
class AuthoringUiTest {
    @Test fun homeOpensEditor() = runComposeUiTest {
        setContent { RastrioApp() }
        onNodeWithText("Open Markdown").performClick()
        onNodeWithTag("markdown-source").assertExists()
        onNodeWithText("Compile").assertIsEnabled()
        onNodeWithText("Open logical preview").assertIsNotEnabled()
    }

    @Test fun currentSourceCompilesAndSurvivesPreviewAndBack() = runComposeUiTest {
        setContent { RastrioApp() }
        onNodeWithText("Open Markdown").performClick()
        onNodeWithTag("markdown-source").performTextInput("# Receipt\nHello")
        onNodeWithText("Compile").performClick()
        onNodeWithText("Ready for logical preview").assertExists()
        onNodeWithText("Open logical preview").performClick()
        onNodeWithText("Logical preview").assertExists()
        onNodeWithText("Receipt").assertExists()
        onNodeWithText("Hello").assertExists()
        onNodeWithText("Back to Markdown").performClick()
        onNodeWithTag("markdown-source").assertTextContains("# Receipt\nHello")
        onNodeWithText("Open logical preview").assertIsEnabled()
        onNodeWithTag("markdown-source").performTextReplacement("Changed")
        onNodeWithText("Open logical preview").assertIsNotEnabled()
        onNodeWithText("Compile to update the preview").assertExists()
    }

    @Test fun warningSeverityAndRangeStayWithSource() = runComposeUiTest {
        setContent { RastrioApp() }
        onNodeWithText("Open Markdown").performClick()
        onNodeWithTag("markdown-source").performTextInput("<b>literal</b>")
        onNodeWithText("Compile").performClick()
        onNodeWithText("Warning · Markdown · MD101 · normalized UTF-16 range 0–3").assertExists()
        onNodeWithText("Open logical preview").performClick()
        onNodeWithText("Back to Markdown").performClick()
        onNodeWithText("Warning · Markdown · MD101 · normalized UTF-16 range 0–3").assertExists()
        onNodeWithTag("markdown-source").performTextReplacement("Plain")
        onAllNodesWithText("Warning · Markdown · MD101", substring = true).assertCountEquals(0)
    }

    @Test fun homeRoundTripRetainsSourceResultAndDiagnostics() = runComposeUiTest {
        setContent { RastrioApp() }
        onNodeWithText("Open Markdown").performClick()
        onNodeWithTag("markdown-source").performTextInput("<b>retained</b>")
        onNodeWithText("Compile").performClick()
        onNodeWithText("Back to Home").performClick()
        onNodeWithText("Open Markdown").performClick()
        onNodeWithTag("markdown-source").assertTextContains("<b>retained</b>")
        onNodeWithText("Open logical preview").assertIsEnabled()
        onNodeWithText("Warning · Markdown · MD101 · normalized UTF-16 range 0–3").assertExists()
        onNodeWithText("Open logical preview").performClick()
        onNodeWithText("<b>retained</b>").assertExists()
    }

    @Test fun unsupportedTextShowsLayoutErrorAndDisablesPreview() = runComposeUiTest {
        setContent { RastrioApp() }
        onNodeWithText("Open Markdown").performClick()
        onNodeWithTag("markdown-source").performTextInput("世界")
        onNodeWithText("Compile").performClick()
        onNodeWithText("Layout failed").assertExists()
        onNodeWithText("Error · Layout · TXT100", substring = true).assertExists()
        onNodeWithText("Open logical preview").assertIsNotEnabled()
    }

    @Test fun wideCanvasRemainsAssembledAndHorizontallyInspectable() = runComposeUiTest {
        val session = createAuthoringSession(240.0, Orientation.LANDSCAPE)
        setContent { Box(Modifier.requiredSize(360.dp, 720.dp)) { RastrioApp(session) } }
        onNodeWithText("Open Markdown").performClick()
        onNodeWithTag("markdown-source").performTextInput("| Left | Right |\n| --- | --- |\n| A | B |")
        onNodeWithText("Compile").performClick()
        onNodeWithText("Open logical preview").performClick()
        val canvas = onNodeWithTag("logical-canvas")
        canvas.assertContentDescriptionEquals("Assembled logical canvas, 240.0 mm wide")
        canvas.performSemanticsAction(SemanticsActions.ScrollBy) { it(400f, 0f) }
        assertTrue(canvas.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange].value() > 0f)
        onAllNodesWithTag("logical-canvas").assertCountEquals(1)
    }

    @Test fun emptyDocumentOffersAUsablePreview() = runComposeUiTest {
        setContent { RastrioApp() }
        onNodeWithText("Open Markdown").performClick()
        onNodeWithText("Compile").performClick()
        onNodeWithText("Open logical preview").performClick()
        onNodeWithTag("logical-canvas").assertExists()
        onNodeWithText("Back to Markdown").performClick()
        onNodeWithTag("markdown-source").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
    }

    @Test fun longDocumentComposesOnlyVisibleBands() = runComposeUiTest {
        val session = createAuthoringSession()
        session.edit((1..2000).joinToString("\n\n") { "Line $it" })
        session.compile()
        val ready = assertIs<MarkdownAuthoringResult.Ready>(session.state.result)
        setContent { AuthoringTheme { LogicalPreviewCanvas(ready.preview, Modifier.fillMaxSize()) } }
        onNodeWithText("Line 1").assertExists()
        onNodeWithText("Line 2000").assertDoesNotExist()
        // A tall document retains geometry references; it is never one composed receipt bitmap.
        val bands = PreviewBands(ready.preview).count
        assertTrue(bands > 100)
        onNode(hasScrollToIndexAction()).performScrollToIndex(bands - 1)
        onNodeWithText("Line 2000").assertIsDisplayed()
        onNodeWithText("Line 1").assertDoesNotExist()
    }

    @Test fun compilationFailureIsPersistentAndDistinctFromLayoutFailure() = runComposeUiTest {
        val session = createAuthoringSession()
        session.edit("\uD800")
        setContent { RastrioApp(session) }
        onNodeWithText("Open Markdown").performClick()
        onNodeWithText("Compile").performClick()
        onNodeWithText("Markdown compilation failed").assertExists()
        onNodeWithText("Error · Markdown · MD100", substring = true).assertExists()
        onNodeWithText("Open logical preview").assertIsNotEnabled()
    }

    @Test fun resolvedCodeLineCrossingBandHasOneAccessibleTextNode() = runComposeUiTest {
        val session = createAuthoringSession()
        session.edit("```\n" + (1..25).joinToString("\n") { "CODE$it" } + "\n```")
        session.compile()
        val ready = assertIs<MarkdownAuthoringResult.Ready>(session.state.result)
        setContent { AuthoringTheme { LogicalPreviewCanvas(ready.preview, Modifier.fillMaxSize()) } }
        for (line in 1..25) onNodeWithText("CODE$line").assertExists()
    }

    @Test fun keyboardActivationOpensAuthoring() = runComposeUiTest {
        setContent { RastrioApp() }
        onNodeWithText("Open Markdown").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        onNodeWithText("Open Markdown").assertIsFocused()
        onNodeWithText("Open Markdown").performKeyInput { keyDown(Key.Enter); keyUp(Key.Enter) }
        onNodeWithTag("markdown-source").assertExists()
    }

    @Test fun sharedContentRendersInDarkDocumentWorkspace() = runComposeUiTest {
        val session = createAuthoringSession()
        session.edit("# Shopping\n\n> A note\n\n- Parent\n  - Child\n\n- [ ] Open\n- [x] Done\n\n---\n\n| Item | Count |\n| --- | --- |\n| Apples | 3 |\n\n![Photo](https://example.invalid/photo.png)")
        session.compile()
        val ready = assertIs<MarkdownAuthoringResult.Ready>(session.state.result)
        var canvasColor = androidx.compose.ui.graphics.Color.Unspecified
        setContent { AuthoringTheme(dark = true) {
            canvasColor = MaterialTheme.colorScheme.surface
            Surface(color = MaterialTheme.colorScheme.background) { PreviewScreen(ready.preview) {} }
        } }
        onNodeWithText("Shopping").assertExists()
        onNodeWithText("A note").assertExists()
        onNodeWithText("Parent").assertExists()
        onNodeWithText("Child").assertExists()
        onNodeWithContentDescription("Checked list item").assertExists()
        onNodeWithContentDescription("Unchecked list item").assertExists()
        onNodeWithText("Apples").assertExists()
        val table = ready.preview.blocks.filterIsInstance<com.circuitnext.rastrio.core.layout.LogicalTableBlock>().single()
        val countRun = table.rows.first().cells[1].text.lines.first().runs.first()
        assertEquals(2, table.columns.size)
        assertEquals(table.rows.first().cells[1].contentBounds.xMm, countRun.bounds.xMm)
        assertEquals(16f + (countRun.bounds.xMm * 4).toFloat(),
            onNodeWithText("Count").fetchSemanticsNode().boundsInRoot.left, 1f)
        onNodeWithText("Image placeholder").assertExists()
        val glyphBounds = onNodeWithText("Count").fetchSemanticsNode().boundsInRoot
        val painted = onRoot().captureToImage().toPixelMap()
        assertTrue((glyphBounds.top.toInt() until glyphBounds.bottom.toInt()).any { y ->
            (glyphBounds.left.toInt() until glyphBounds.right.toInt()).any { x -> painted[x, y] != canvasColor }
        }, "Resolved table glyph must paint inside its supplied bounds")
        if (System.getenv("RASTRIO_UI_CAPTURE") == "1") {
            val png = Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap()).encodeToData()
            File("/tmp/rastrio-4c-dark-preview.png").writeBytes(assertNotNull(png).bytes)
        }
    }

    @Test fun shortFinalBandDoesNotShiftItsClippedGlyphFragment() = runComposeUiTest {
        val session = createAuthoringSession()
        session.compile()
        val constraints = assertIs<MarkdownAuthoringResult.Ready>(session.state.result).logicalDocument.constraints
        // The Core code block has no trailing newline; Markdown fences preserve a final empty line.
        val layout = com.circuitnext.rastrio.core.layout.FoundationLayoutEngine(
            com.circuitnext.rastrio.core.text.AsciiFixedCellMeasurer()).layout(
            com.circuitnext.rastrio.core.document.ThermalDocument(blocks = listOf(
                com.circuitnext.rastrio.core.document.CodeBlock((1..16).joinToString("\n") { "CODEg$it" }))), constraints)
        val document = assertIs<com.circuitnext.rastrio.core.layout.LayoutResult.Success>(layout).document
        val original = document.toLogicalPreview()
        var preview by mutableStateOf(original)
        setContent { AuthoringTheme { LogicalPreviewCanvas(preview, Modifier.fillMaxSize()) } }
        val short = onRoot().captureToImage().toPixelMap()
        // Add empty canvas below the SAME Core objects. Existing glyph pixels must not move.
        runOnIdle { preview = original.copy(heightMm = 128.0) }
        val tall = onRoot().captureToImage().toPixelMap()
        for (y in 0 until 268) for (x in 0 until 480) {
            assertEquals(tall[x, y], short[x, y], "Canvas-only height changed paint at ($x,$y)")
        }
    }
}
