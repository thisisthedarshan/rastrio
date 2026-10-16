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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.math.pow
import kotlin.test.*
import org.jetbrains.skia.Image

@OptIn(ExperimentalTestApi::class)
class WorkspaceUiTest {
    @Test fun homeDestinationsStayReachableAtLargeTextOnSmallPhone() = runComposeUiTest {
        val opened = mutableListOf<String>()
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.6f)) {
                AuthoringTheme(dark = false) {
                    Box(Modifier.requiredSize(320.dp, 640.dp)) {
                        HomeScreen({ opened += "image" }, { opened += "templates" }, { opened += "printers" }) { opened += "markdown" }
                    }
                }
            }
        }
        for (label in listOf("Open Markdown", "Open Templates", "Open Image Print", "Open Printers")) {
            onNodeWithText(label).performScrollTo().assertIsDisplayed().performClick()
        }
        runOnIdle { assertEquals(listOf("markdown", "templates", "image", "printers"), opened) }
    }

    @Test fun themeForegroundsRemainReadableInBothAppearances() {
        for (dark in listOf(false, true)) runComposeUiTest {
            var pairs = emptyList<Pair<Color, Color>>()
            setContent {
                AuthoringTheme(dark = dark) {
                    val c = MaterialTheme.colorScheme
                    val extra = LocalAuthoringColors.current
                    SideEffect { pairs = listOf(c.onBackground to c.background, c.onSurface to c.surface,
                        c.onSurfaceVariant to c.surfaceVariant, c.onPrimary to c.primary,
                        c.onPrimaryContainer to c.primaryContainer, c.onSecondary to c.secondary,
                        c.onSecondaryContainer to c.secondaryContainer, c.onError to c.error,
                        c.onErrorContainer to c.errorContainer, extra.disabledText to extra.disabledSurface) }
                }
            }
            runOnIdle {
                fun luminance(c: Color): Double {
                    fun linear(v: Float) = if (v <= 0.04045f) v.toDouble() / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
                    return 0.2126 * linear(c.red) + 0.7152 * linear(c.green) + 0.0722 * linear(c.blue)
                }
                for ((foreground, background) in pairs) {
                    val a = luminance(foreground); val b = luminance(background)
                    assertTrue((maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05) >= 4.5, "Text contrast in dark=$dark: $foreground on $background")
                }
            }
        }
    }

    @Test fun formattingHandlesEmptyAndReversedUnicodeSelections() {
        val bold = formatMarkdown(TextFieldValue(""), MarkdownFormat.BOLD)
        assertEquals("**text**", bold.text)
        assertEquals(TextRange(0, 8), bold.selection)
        assertEquals("A **café** Z", formatMarkdown(TextFieldValue("A café Z", TextRange(6, 2)), MarkdownFormat.BOLD).text)
        assertEquals("one \n## two", formatMarkdown(TextFieldValue("one two", TextRange(4, 7)), MarkdownFormat.HEADING).text)
        assertEquals("- List item", formatMarkdown(TextFieldValue(""), MarkdownFormat.LIST).text)
    }

    @Test fun captureWorkspaceAppearance() {
        if (System.getenv("RASTRIO_UI_CAPTURE") != "1") return
        for (dark in listOf(false, true)) runComposeUiTest {
            setContent { AuthoringTheme(dark = dark) {
                Surface(Modifier.requiredSize(390.dp, 740.dp), color = MaterialTheme.colorScheme.background) { HomeScreen({}, {}, {}) {} }
            } }
            val data = assertNotNull(Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap()).encodeToData())
            File("/tmp/rastrio-workspace-${if (dark) "dark" else "light"}.png").writeBytes(data.bytes)
        }
    }
}
