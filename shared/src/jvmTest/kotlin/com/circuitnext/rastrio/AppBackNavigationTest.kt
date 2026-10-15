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

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class AppBackNavigationTest {
    @Test fun systemBackReturnsThroughPagesAndIsDisabledAtHome() = runComposeUiTest {
        var enabled = false
        var back: () -> Unit = {}
        setContent { RastrIOApp(backHandler = { active, action -> SideEffect { enabled = active; back = action } }) }
        runOnIdle { assertFalse(enabled) }
        onNodeWithText("Open Printers").performClick()
        runOnIdle { assertTrue(enabled) }
        onNodeWithText("Create Printer Profile").performScrollTo().performClick()
        onNodeWithTag("profile-editor-list").performScrollToNode(hasText("Calibrate draft in Printer Lab"))
        onNodeWithText("Calibrate draft in Printer Lab").performScrollTo().performClick()
        onNodeWithText("Printer Lab").assertExists()
        runOnIdle { back() }
        onNodeWithText("Create Printer Profile").assertExists()
        runOnIdle { back() }
        onNodeWithText("Printers and profiles").assertExists()
        runOnIdle { back() }
        onNodeWithText("Open Markdown").assertExists()
        runOnIdle { assertFalse(enabled) }
    }

    @Test fun templateBackReturnsToGalleryThenHome() = runComposeUiTest {
        var back: () -> Unit = {}
        setContent { RastrIOApp(backHandler = { _, action -> SideEffect { back = action } }) }
        onNodeWithText("Open Templates").performClick()
        onNodeWithText("Shopping List").performClick()
        runOnIdle { back() }
        onNodeWithText("Quick Note").assertExists()
        runOnIdle { back() }
        onNodeWithText("Open Markdown").assertExists()
    }
}
