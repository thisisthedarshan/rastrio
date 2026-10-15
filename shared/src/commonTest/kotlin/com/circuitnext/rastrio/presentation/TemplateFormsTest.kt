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

package com.circuitnext.rastrio.presentation

import com.circuitnext.rastrio.core.document.*
import com.circuitnext.rastrio.core.templates.*
import kotlin.test.*

class TemplateFormsTest {
    @Test fun restorationKeepsEveryDraftAndQrPreferencesIncludingInvalidEdits() {
        val forms = TemplateForms(TemplateKind.QR,
            TodoState("Tasks", listOf(TodoItem("checked", true), TodoItem("open"))),
            ShoppingListState("Food", listOf(ShoppingItem("Milk", "2 L"))),
            QuickNoteState("Note", "é\nनमस्ते"), MemoState("Subject", "Team", "Body", "Name"),
            QrMessageState("https://example.invalid/\u0000", "Caption", QrErrorCorrection.HIGH), "invalid")
        val restored = assertNotNull(TemplateForms.restore(forms.snapshot()))
        assertEquals(forms, restored)
        assertNull(restored.build().document)
        assertEquals("requestedSize", restored.build().diagnostics.single().field)
        for (kind in TemplateKind.entries) {
            val selected = restored.copy(selected = kind, qrSizeMm = "30")
            assertNotNull(selected.build().document)
            assertEquals(selected, TemplateForms.restore(selected.snapshot()))
        }
    }

    @Test fun datedShoppingAndMemoDraftsRoundTripAndOldSnapshotMigrates() {
        val forms = TemplateForms(shopping = ShoppingListState("Food", listOf(ShoppingItem("Milk", "2", "L", true)), "Today"),
            memo = MemoState(body = "Memo", date = "Tomorrow"))
        assertEquals(forms, TemplateForms.restore(forms.snapshot()))
        assertNull(TemplateForms.restore(forms.snapshot().toMutableList().apply { this[lastIndex] = "invalid-check" }))
        val legacy = listOf("1", "SHOPPING", "", "0", "Food", "1", "Milk", "2 L", "", "", "", "", "Memo", "Name", "", "", "AUTO", "")
        val restored = assertNotNull(TemplateForms.restore(legacy))
        assertEquals("2 L", restored.shopping.items.single().quantity)
        assertEquals("", restored.shopping.date)
        assertEquals("", restored.shopping.items.single().unit)
        assertFalse(restored.shopping.items.single().checked)
        assertEquals("", restored.memo.date)
        assertEquals(restored, TemplateForms.restore(restored.snapshot()))
        assertFalse(forms.copy(shopping = forms.shopping.copy(date = "x".repeat(32768))).withinSaveLimit())
    }

    @Test fun reorderedItemsPreserveChecksAndQuantityWithoutMutatingOriginal() {
        val todo = listOf(TodoItem("one", true), TodoItem("two"), TodoItem("three"))
        assertEquals(listOf(todo[1], todo[0], todo[2]), todo.moveTemplateItem(1, -1))
        assertEquals(listOf(todo[0], todo[2], todo[1]), todo.moveTemplateItem(1, 1))
        assertSame(todo, todo.moveTemplateItem(0, -1))
        assertEquals(true, todo[0].checked)
        val shopping = listOf(ShoppingItem("a", "4"), ShoppingItem("b", "2 L"))
        assertEquals("4", shopping.moveTemplateItem(0, 1).last().quantity)
    }

    @Test fun savedStateRejectsCorruptionAndBudgetsBeforeAllocation() {
        val snapshot = TemplateForms().snapshot()
        assertNull(TemplateForms.restore(emptyList()))
        assertNull(TemplateForms.restore(snapshot + "extra"))
        assertNull(TemplateForms.restore(snapshot.toMutableList().apply { this[0] = "999" }))
        assertNull(TemplateForms.restore(snapshot.toMutableList().apply { this[3] = Int.MAX_VALUE.toString() }))
        assertNull(TemplateForms.restore(listOf("x".repeat(TemplateForms.MAX_SAVED_CHARACTERS + 1))))
        assertFalse(TemplateForms(note = QuickNoteState(body = "a".repeat(TemplateForms.MAX_SAVED_CHARACTERS))).withinSaveLimit())
        assertFalse(TemplateForms(todo = TodoState(items = List(129) { TodoItem() })).withinSaveLimit())
    }

    @Test fun semanticPreviewValidationNeverReusesAnotherDraft() {
        val forms = TemplateForms(note = QuickNoteState(body = "Private note"), qr = QrMessageState("opaque\r\n"))
        assertNull(forms.build().document)
        val note = assertNotNull(forms.copy(selected = TemplateKind.NOTE).build().document)
        assertIs<Paragraph>(note.blocks.single())
        val qr = assertNotNull(forms.copy(selected = TemplateKind.QR, qrSizeMm = "25").build().document)
        assertEquals(QrCode("opaque\r\n", Alignment.CENTER, QrErrorCorrection.AUTO, Length(25.0)), qr.blocks.single())
        assertNull(forms.copy(selected = TemplateKind.QR, qrSizeMm = "NaN").build().document)
    }
}
