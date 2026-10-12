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
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import com.circuitnext.rastrio.core.document.*
import com.circuitnext.rastrio.presentation.*
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class TemplateUiTest {
    @Test fun todoCrudChecksReorderAndPreviewUseSemanticContent() = runComposeUiTest {
        var document: ThermalDocument? = null
        setContent {
            var forms by rememberTemplateFormsState()
            AuthoringTheme { TemplateScreen(forms, { forms = it }, { document = it }, {}) }
        }
        onNodeWithText("Add item").performClick()
        onNodeWithTag("template-Item 1").performTextInput("First")
        onNodeWithContentDescription("Item 1 completed").performClick()
        onNodeWithText("Add item").performScrollTo().performClick()
        onNodeWithTag("template-Item 2").performScrollTo().performTextInput("Second")
        onNodeWithText("Move item 2 up").performScrollTo().performClick()
        onNodeWithText("Preview template").performScrollTo().performClick()
        runOnIdle {
            val items = (assertNotNull(document).blocks.single() as Checklist).items
            assertEquals(listOf(false, true), items.map { it.checked })
            assertEquals("Second", ((items.first().blocks.single() as Paragraph).content.single() as Text).text)
        }
        onNodeWithText("Remove item 1").performScrollTo().performClick()
        onNodeWithText("Preview template").performScrollTo().performClick()
        runOnIdle { assertEquals(1, (assertNotNull(document).blocks.single() as Checklist).items.size) }
    }

    @Test fun shoppingMemoQrAndNoteKeepTheirDraftsAndShowValidation() = runComposeUiTest {
        var document: ThermalDocument? = null
        setContent {
            var forms by rememberTemplateFormsState()
            AuthoringTheme { TemplateScreen(forms, { forms = it }, { document = it }, {}) }
        }
        onNodeWithText("Shopping List").performClick()
        onNodeWithText("Add item").performClick()
        onNodeWithTag("template-Item 1").performTextInput("Milk")
        onNodeWithTag("template-Quantity 1").performScrollTo().performTextInput("2 L")
        onNodeWithText("Preview template").performScrollTo().performClick()
        runOnIdle { assertIs<UnorderedList>(assertNotNull(document).blocks.single()) }
        onNodeWithTag("template-list").performScrollToIndex(0)
        onAllNodesWithText("Memo")[0].performClick()
        onNodeWithTag("template-Subject").performTextInput("Meeting")
        onNodeWithTag("template-Memo body").performTextInput("Discuss roadmap")
        onNodeWithText("Preview template").performScrollTo().performClick()
        runOnIdle { assertEquals("Meeting", assertNotNull(document).metadata.title) }
        onNodeWithTag("template-list").performScrollToIndex(0)
        onAllNodesWithText("QR Message")[0].performClick()
        onNodeWithText("Preview template").performScrollTo().performClick()
        onNodeWithText("payload: Enter required content").assertExists()
        onNodeWithTag("template-QR payload").performScrollTo().performTextInput("https://example.invalid")
        onNodeWithText("Preview template").performScrollTo().performClick()
        runOnIdle { assertIs<QrCode>(assertNotNull(document).blocks.single()) }
        onNodeWithTag("template-list").performScrollToIndex(0)
        onAllNodesWithText("Quick Note")[0].performClick()
        onNodeWithTag("template-Note").performTextInput("Quick thought")
        onNodeWithTag("template-list").performScrollToIndex(0)
        onAllNodesWithText("Shopping List")[0].performClick()
        onNodeWithTag("template-Item 1").assertTextContains("Milk")
        onNodeWithTag("template-Quantity 1").assertTextContains("2 L")
        onNodeWithTag("template-list").performScrollToIndex(0)
        onAllNodesWithText("Quick Note")[0].performClick()
        onNodeWithTag("template-Note").assertTextContains("Quick thought")
    }
    @Test fun lifecycleSaveAndRecreateRestoresFormAndSelectedTemplate() = runComposeUiTest {
        var registry by mutableStateOf(SaveableStateRegistry(null) { true })
        var visible by mutableStateOf(true)
        var saved: Map<String, List<Any?>> = emptyMap()
        setContent {
            CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                if (visible) {
                    var forms by rememberTemplateFormsState()
                    AuthoringTheme { TemplateScreen(forms, { forms = it }, {}, {}) }
                }
            }
        }
        onNodeWithText("Quick Note").performClick()
        onNodeWithTag("template-Title").performTextInput("Retained title")
        onNodeWithTag("template-Note").performTextInput("Retained body")
        runOnIdle { saved = registry.performSave(); visible = false }
        waitForIdle()
        runOnIdle { registry = SaveableStateRegistry(saved) { true }; visible = true }
        onNodeWithTag("template-Title").assertTextContains("Retained title")
        onNodeWithTag("template-Note").assertTextContains("Retained body")
    }

}
