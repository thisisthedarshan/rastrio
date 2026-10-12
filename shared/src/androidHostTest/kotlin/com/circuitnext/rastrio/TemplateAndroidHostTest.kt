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

import androidx.compose.runtime.saveable.SaverScope
import com.circuitnext.rastrio.core.templates.*
import com.circuitnext.rastrio.presentation.*
import kotlin.test.*

class TemplateAndroidHostTest {
    @Test fun saverUsesOnlyBoundedAndroidSaveableValuesAndRestoresDrafts() {
        val draft = TemplateForms(selected = TemplateKind.MEMO,
            todo = TodoState("Todo", listOf(TodoItem("done", true))),
            shopping = ShoppingListState(items = listOf(ShoppingItem("Milk", "2 L"))),
            memo = MemoState("Subject", "Team", "Body", "Signature"))
        val scope = object : SaverScope { override fun canBeSaved(value: Any): Boolean = value is String || value is List<*> }
        val saved = with(TemplateFormsSaver) { scope.save(draft) }
        assertNotNull(saved)
        assertEquals(draft, TemplateFormsSaver.restore(saved))
        assertNotNull(TemplateFormsSaver.restore(saved)?.build()?.document)
    }
}
