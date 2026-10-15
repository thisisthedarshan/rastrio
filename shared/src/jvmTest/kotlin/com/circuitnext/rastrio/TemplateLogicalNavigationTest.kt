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
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class TemplateLogicalNavigationTest {
    @Test fun quickNoteLogicalPreviewReturnsToItsRetainedDraft() = runComposeUiTest {
        setContent { RastrIOApp() }
        onNodeWithText("Open Templates").performClick()
        onNodeWithText("Quick Note").performClick()
        onNodeWithTag("template-Title").performTextInput("Note title")
        onNodeWithTag("template-Note").performTextInput("First note line\nSecond note line")
        onNodeWithText("Open logical preview").assertDoesNotExist()
        onNodeWithText("Preview", substring = false).performScrollTo().performClick()
        waitUntil { onAllNodesWithTag("physical-canvas").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Open logical preview").performScrollTo().performClick()
        onNodeWithText("Logical preview").assertExists()
        onNodeWithTag("logical-canvas").assertContentDescriptionEquals("Assembled logical canvas, 120.0 mm wide")
        onNodeWithText("Note title").assertExists()
        onNodeWithText("First note line").assertExists()
        onNodeWithText("Second note line").assertExists()
        onNodeWithText("Back to preview").performClick()
        onNodeWithTag("physical-canvas").assertExists()
        onNodeWithText("Back to templates").performClick()
        onNodeWithTag("template-Title").assertTextContains("Note title")
        onNodeWithTag("template-Note").assertTextContains("First note line\nSecond note line")
        onNodeWithText("Open logical preview").assertDoesNotExist()
        onNodeWithText("Preview", substring = false).performScrollTo().assertIsEnabled()
    }
}
