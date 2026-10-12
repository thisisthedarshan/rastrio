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

import com.circuitnext.rastrio.core.document.Length
import com.circuitnext.rastrio.core.document.QrErrorCorrection
import com.circuitnext.rastrio.core.templates.*

internal enum class TemplateKind(val label: String) {
    TODO("Todo"), SHOPPING("Shopping List"), NOTE("Quick Note"), MEMO("Memo"), QR("QR Message")
}

/** All drafts are retained when changing template or leaving its screen. */
internal data class TemplateForms(
    val selected: TemplateKind = TemplateKind.TODO,
    val todo: TodoState = TodoState(),
    val shopping: ShoppingListState = ShoppingListState(),
    val note: QuickNoteState = QuickNoteState(),
    val memo: MemoState = MemoState(),
    val qr: QrMessageState = QrMessageState(),
    val qrSizeMm: String = "",
) {
    fun build(): TemplateBuildResult {
        val state = when (selected) {
            TemplateKind.TODO -> todo
            TemplateKind.SHOPPING -> shopping
            TemplateKind.NOTE -> note
            TemplateKind.MEMO -> memo
            TemplateKind.QR -> {
                val size = if (qrSizeMm.isBlank()) null else qrSizeMm.toDoubleOrNull()?.let { Length(it) }
                if (qrSizeMm.isNotBlank() && size == null) return TemplateBuildResult(null,
                    listOf(TemplateDiagnostic("TPL102", "requestedSize", "Enter a positive size in millimetres")))
                qr.copy(requestedSize = size)
            }
        }
        return TemplateDocumentBuilder.build(state)
    }

    /** A bounded private saveable-state snapshot, not a portable document format. */
    fun snapshot(): List<String> = buildList {
        add("1"); add(selected.name)
        add(todo.title); add(todo.items.size.toString())
        todo.items.forEach { add(it.text); add(if (it.checked) "1" else "0") }
        add(shopping.title); add(shopping.items.size.toString())
        shopping.items.forEach { add(it.description); add(it.quantity) }
        add(note.title); add(note.body)
        add(memo.subject); add(memo.recipient); add(memo.body); add(memo.signature)
        add(qr.payload); add(qr.caption); add(qr.errorCorrection.name); add(qrSizeMm)
    }

    fun withinSaveLimit(): Boolean = todo.items.size <= MAX_FORM_ITEMS && shopping.items.size <= MAX_FORM_ITEMS &&
        snapshot().sumOf { it.length.toLong() } <= MAX_SAVED_CHARACTERS

    companion object {
        const val MAX_FORM_ITEMS = 128
        // Leave room in Android's saved-state transaction for navigation and other screens.
        const val MAX_SAVED_CHARACTERS = 32_768

        fun restore(values: List<String>): TemplateForms? {
            if (values.size > 2 * MAX_FORM_ITEMS * 2 + 20 ||
                values.sumOf { it.length.toLong() } > MAX_SAVED_CHARACTERS) return null
            var index = 0
            fun next(): String = values.getOrNull(index++) ?: throw IllegalArgumentException()
            fun count(): Int = next().toIntOrNull()?.takeIf { it in 0..MAX_FORM_ITEMS } ?: throw IllegalArgumentException()
            return try {
                require(next() == "1")
                val selected = TemplateKind.valueOf(next())
                val todoTitle = next()
                val todoItems = List(count()) {
                    val text = next()
                    val checked = next().also { require(it == "0" || it == "1") } == "1"
                    TodoItem(text, checked)
                }
                val shoppingTitle = next()
                val shoppingItems = List(count()) { ShoppingItem(next(), next()) }
                val note = QuickNoteState(next(), next())
                val memo = MemoState(next(), next(), next(), next())
                val qr = QrMessageState(next(), next(), QrErrorCorrection.valueOf(next()))
                val size = next()
                require(index == values.size)
                TemplateForms(selected, TodoState(todoTitle, todoItems), ShoppingListState(shoppingTitle, shoppingItems),
                    note, memo, qr, size)
            } catch (_: IllegalArgumentException) { null }
        }
    }
}

internal fun <T> List<T>.moveTemplateItem(index: Int, direction: Int): List<T> {
    val target = index + direction
    if (index !in indices || target !in indices) return this
    return toMutableList().apply { add(target, removeAt(index)) }.toList()
}
