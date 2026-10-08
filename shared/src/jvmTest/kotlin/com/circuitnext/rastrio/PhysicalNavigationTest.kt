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
import com.circuitnext.rastrio.presentation.createAuthoringSession
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class PhysicalNavigationTest {
    @Test fun physicalRouteSelectsSyntheticWidthsAndRetainsSourceForLogicalPreview() = runComposeUiTest {
        setContent { RastrIOApp() }
        onNodeWithText("Open Markdown").performClick()
        onNodeWithText("Printer preview").assertIsNotEnabled()
        onNodeWithTag("markdown-source").performTextInput("Receipt")
        onNodeWithText("Compile").performClick()
        onNodeWithText("Printer preview").performClick()
        onNodeWithText("Synthetic test profiles only.", substring = true).assertExists()
        onNodeWithText("Prepared width: 384 dots", substring = true).assertExists()
        onNodeWithText("Open physical preview").performScrollTo().performClick()
        onNodeWithTag("physical-canvas").assertContentDescriptionEquals("Physical preview, 384 prepared dots wide")
        onNodeWithText("Back to printer setup").performClick()
        onNodeWithText("Synthetic 576 dots").performScrollTo().performClick()
        onNodeWithText("Prepared width: 576 dots", substring = true).assertExists()
        onNodeWithText("Open physical preview").performScrollTo().performClick()
        onNodeWithTag("physical-canvas").assertContentDescriptionEquals("Physical preview, 576 prepared dots wide")
        onNodeWithText("Back to printer setup").performClick()
        onNodeWithText("Back to Markdown").performScrollTo().performClick()
        onNodeWithTag("markdown-source").assertTextContains("Receipt")
        onNodeWithText("Open logical preview").performClick()
        onNodeWithTag("logical-canvas").assertExists()
        onNodeWithText("Receipt").assertExists()
    }

    @Test fun editingSourceInvalidatesPhysicalAndLogicalRoutes() = runComposeUiTest {
        setContent { RastrIOApp() }
        onNodeWithText("Open Markdown").performClick()
        onNodeWithTag("markdown-source").performTextInput("Old")
        onNodeWithText("Compile").performClick()
        onNodeWithText("Printer preview").performClick()
        onNodeWithText("Back to Markdown").performClick()
        onNodeWithTag("markdown-source").performTextReplacement("New")
        onNodeWithText("Printer preview").assertIsNotEnabled()
        onNodeWithText("Open logical preview").assertIsNotEnabled()
        onNodeWithText("Compile").performClick()
        onNodeWithText("Printer preview").performClick()
        onNodeWithText("Open physical preview").performScrollTo().performClick()
        onNodeWithText("New").assertExists()
        onNodeWithText("Old").assertDoesNotExist()
    }

    @Test fun cp437PhysicalPreviewRemainsUsableWhenLogicalLayoutRejectsTheSource() = runComposeUiTest {
        val session = createAuthoringSession().also { it.edit("Café"); it.compile() }
        setContent { RastrIOApp(session) }
        onNodeWithText("Open Markdown").performClick()
        onNodeWithText("Layout failed").assertExists()
        onNodeWithText("Open logical preview").assertIsNotEnabled()
        onNodeWithText("Printer preview").assertIsEnabled().performClick()
        onNodeWithText("Open physical preview").performScrollTo().performClick()
        onNodeWithText("Café").assertExists()
        onNodeWithText("Back to printer setup").performClick()
        onNodeWithText("Back to Markdown").performScrollTo().performClick()
        onNodeWithTag("markdown-source").assertTextContains("Café")
    }

    @Test fun unsupportedPreparationShowsFailureAndRetainsSourceAcrossHome() = runComposeUiTest {
        val session = createAuthoringSession().also { it.edit("世界"); it.compile() }
        setContent { RastrIOApp(session) }
        onNodeWithText("Open Markdown").performClick()
        onNodeWithText("Printer preview").performClick()
        onNodeWithText("Physical preparation failed").assertExists()
        onNodeWithText("Open physical preview").assertIsNotEnabled()
        onNodeWithText("Back to Markdown").performClick()
        onNodeWithTag("markdown-source").assertTextContains("世界")
        onNodeWithText("Back to Home").performClick()
        onNodeWithText("Open Markdown").performClick()
        onNodeWithTag("markdown-source").assertTextContains("世界")
        onNodeWithText("Printer preview").assertIsEnabled()
    }

    @Test fun manualAndAutomaticCutChoicesDisplayTheirPreparedSemantics() = runComposeUiTest {
        val session = createAuthoringSession().also { it.edit("Receipt"); it.compile() }
        setContent { RastrIOApp(session) }
        onNodeWithText("Open Markdown").performClick()
        onNodeWithText("Printer preview").performClick()
        onNodeWithText("Automatic full").performScrollTo().performClick()
        onNodeWithText("Physical preparation failed").assertExists()
        onNodeWithText("Open physical preview").assertIsNotEnabled()
        onNodeWithText("Manual guide").performScrollTo().performClick()
        onNodeWithText("Open physical preview").performScrollTo().performClick()
        onNodeWithContentDescription("Manual cut guide, printed content").assertExists()
        onNodeWithText("Back to printer setup").performClick()
        onNodeWithText("Synthetic 576 dots").performScrollTo().performClick()
        onNodeWithText("Automatic partial").performScrollTo().performClick()
        onNodeWithText("Open physical preview").performScrollTo().performClick()
        onNodeWithContentDescription("Automatic partial cut, non-printing annotation").assertExists()
        onNodeWithContentDescription("Manual cut guide, printed content").assertDoesNotExist()
    }
}
