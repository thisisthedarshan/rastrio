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
import com.circuitnext.rastrio.core.templates.*
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class TemplateUiTest {
    @Test fun galleryOpensDedicatedEditorAndKeepsTechnicalControlsOut() = runComposeUiTest {
        var document: ThermalDocument? = null
        setContent {
            var forms by rememberTemplateFormsState()
            var editor by remember { mutableStateOf(false) }
            AuthoringTheme {
                if (editor) TemplateScreen(forms, { forms = it }, { document = it }, { editor = false })
                else TemplateGallery({ kind -> forms = forms.copy(selected = kind); editor = true }, {})
            }
        }
        onNodeWithText("Shopping List").performClick()
        onNodeWithText("Todo").assertDoesNotExist()
        onNodeWithText("Technical controls").assertDoesNotExist()
        onNodeWithText("Print now").assertDoesNotExist()
        onNodeWithText("Add item").performClick()
        onNodeWithTag("template-Item").performTextInput("Milk for breakfast and baking")
        onNodeWithTag("template-Quantity").performTextInput("2")
        onNodeWithText("Unit").performClick()
        onNodeWithText("L").performClick()
        onNodeWithText("Save item").performClick()
        onNodeWithText("2 L").assertExists()
        onNodeWithText("Preview").performScrollTo().performClick()
        runOnIdle {
            val table = assertIs<Table>(assertNotNull(document).blocks.last())
            assertEquals(listOf(Text("2 L")), table.rows.single().last().content)
        }
        onNodeWithTag("template-list").performScrollToIndex(0)
        onNodeWithContentDescription("Back to Templates").performClick()
        onNodeWithText("Quick Note").performClick()
        onNodeWithTag("template-Note").performTextInput("Private note")
        onNodeWithTag("template-list").performScrollToIndex(0)
        onNodeWithContentDescription("Back to Templates").performClick()
        onNodeWithText("Shopping List").performClick()
        onNodeWithText("Milk for breakfast and baking").assertExists()
    }

    @Test fun todoSheetActionsKeepOrderChecksAndPreviewHasNoImplicitPrint() = runComposeUiTest {
        var document: ThermalDocument? = null
        var previews = 0
        setContent {
            var forms by rememberTemplateFormsState()
            AuthoringTheme { TemplateScreen(forms, { forms = it }, { document = it; previews++ }, {}) }
        }
        onNodeWithText("Add item").performClick()
        onNodeWithTag("template-Item").performTextInput("First")
        onNodeWithText("Save item").performClick()
        onNodeWithContentDescription("Item 1 completed").performClick()
        onNodeWithText("Add item").performClick()
        onNodeWithTag("template-Item").performTextInput("Second")
        onNodeWithText("Save item").performClick()
        onNodeWithContentDescription("Item 2 actions").performClick()
        onNodeWithText("Move up").performClick()
        runOnIdle { assertEquals(0, previews) }
        onNodeWithText("Preview").performScrollTo().performClick()
        runOnIdle { assertEquals(listOf(false, true), (assertNotNull(document).blocks.single() as Checklist).items.map { it.checked }) }
    }

    @Test fun shoppingSheetRetainsCustomUnitsAndChecks() = runComposeUiTest {
        var draft = TemplateForms(selected = TemplateKind.SHOPPING,
            shopping = ShoppingListState(items = listOf(ShoppingItem("Cable", "3", "coils", true))))
        setContent {
            var forms by remember { mutableStateOf(draft) }
            AuthoringTheme { TemplateScreen(forms, { forms = it; draft = it }, {}, {}) }
        }
        onNodeWithContentDescription("Item 1 actions").performClick()
        onNodeWithText("Edit").performClick()
        onNodeWithTag("template-Custom unit").assertTextContains("coils")
        onNodeWithTag("template-Custom unit").performTextReplacement("reels")
        onNodeWithText("Save item").performClick()
        runOnIdle {
            assertEquals("reels", draft.shopping.items.single().unit)
            assertTrue(draft.shopping.items.single().checked)
        }
        onNodeWithText("3 reels").assertExists()
    }

    @Test fun qrOptionsAreCollapsedAndPreviewUsesAuthoritativeCallback() = runComposeUiTest {
        var physical = 0
        var generic = 0
        setContent {
            var forms by remember { mutableStateOf(TemplateForms(selected = TemplateKind.QR)) }
            AuthoringTheme { TemplateScreen(forms, { forms = it }, { generic++ }, {}, onQrPreview = { physical++ }) }
        }
        onNodeWithTag("template-Requested size (mm, optional)").assertDoesNotExist()
        onNodeWithText("Preview").performScrollTo().performClick()
        onNodeWithText("payload: Enter required content").assertExists()
        onNodeWithTag("template-QR payload").performScrollTo().performTextInput("https://example.invalid")
        onNodeWithText("Preview").performScrollTo().performClick()
        runOnIdle { assertEquals(1, physical); assertEquals(0, generic) }
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
        // Selected template is part of the bounded saveable draft.
        onNodeWithTag("template-Title").performTextInput("Retained title")
        runOnIdle { saved = registry.performSave(); visible = false }
        waitForIdle()
        runOnIdle { registry = SaveableStateRegistry(saved) { true }; visible = true }
        onNodeWithTag("template-Title").assertTextContains("Retained title")
    }
}
