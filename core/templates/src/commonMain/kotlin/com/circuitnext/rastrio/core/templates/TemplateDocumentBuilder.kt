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

package com.circuitnext.rastrio.core.templates

import com.circuitnext.rastrio.core.document.*

sealed interface TemplateState
data class TodoItem(val text: String = "", val checked: Boolean = false)
data class TodoState(val title: String = "", val items: List<TodoItem> = emptyList()) : TemplateState
data class ShoppingItem(val description: String = "", val quantity: String = "", val unit: String = "", val checked: Boolean = false)
data class ShoppingListState(val title: String = "", val items: List<ShoppingItem> = emptyList(), val date: String = "") : TemplateState
data class QuickNoteState(val title: String = "", val body: String = "") : TemplateState
data class MemoState(
    val subject: String = "", val recipient: String = "", val body: String = "", val signature: String = "", val date: String = "",
) : TemplateState
data class QrMessageState(
    val payload: String = "", val caption: String = "",
    val errorCorrection: QrErrorCorrection = QrErrorCorrection.AUTO, val requestedSize: Length? = null,
) : TemplateState

/** Trusted operational policy; callers may lower ceilings, never raise them. */
data class TemplateResourcePolicy(
    val maxFieldBytes: Int = 64 * 1024,
    val maxTotalBytes: Int = 8 * 1024 * 1024,
    val maxItems: Int = 1_000,
    val maxLines: Int = 10_000,
) {
    init {
        require(maxFieldBytes in 1..64 * 1024)
        require(maxTotalBytes in 1..8 * 1024 * 1024)
        require(maxItems in 1..1_000)
        require(maxLines in 1..10_000)
    }
}

data class TemplateDiagnostic(val code: String, val field: String, val message: String)
data class TemplateBuildResult(val document: ThermalDocument?, val diagnostics: List<TemplateDiagnostic>)

/** Direct semantic conversion: no parser, printer geometry, network or QR generation. */
object TemplateDocumentBuilder {
    fun build(state: TemplateState, policy: TemplateResourcePolicy = TemplateResourcePolicy()): TemplateBuildResult {
        val validation = Validation(policy)
        when (state) {
            is TodoState -> {
                validation.field("title", state.title)
                validation.items(state.items.size)
                if (validation.diagnostic == null) state.items.forEachIndexed { index, item -> validation.field("items[$index].text", item.text, required = true) }
            }
            is ShoppingListState -> {
                validation.field("title", state.title)
                validation.field("date", state.date)
                validation.items(state.items.size)
                if (validation.diagnostic == null) state.items.forEachIndexed { index, item ->
                    validation.field("items[$index].description", item.description, required = true)
                    validation.field("items[$index].quantity", item.quantity)
                    validation.field("items[$index].unit", item.unit)
                }
            }
            is QuickNoteState -> { validation.field("title", state.title); validation.field("body", state.body, required = true) }
            is MemoState -> {
                validation.field("subject", state.subject); validation.field("recipient", state.recipient)
                validation.field("date", state.date)
                validation.field("body", state.body, required = true); validation.field("signature", state.signature)
            }
            is QrMessageState -> {
                validation.field("payload", state.payload, required = true, opaque = true)
                validation.field("caption", state.caption)
                state.requestedSize?.let {
                    if (!it.value.isFinite() || it.value <= 0 || it.value > 1_000 || it.unit != LengthUnit.MM)
                        validation.fail("TPL102", "requestedSize", "Size must be positive and at most 1000 mm")
                }
            }
        }
        validation.diagnostic?.let { return TemplateBuildResult(null, listOf(it)) }
        val blocks = mutableListOf<DocumentBlock>()
        fun title(text: String) { if (text.isNotBlank()) blocks += Heading(1, Alignment.LEFT, inline(text)) }
        when (state) {
            is TodoState -> {
                title(state.title)
                blocks += Checklist(state.items.map { ChecklistItem(it.checked, listOf(paragraph(it.text))) })
            }
            is ShoppingListState -> {
                if (state.title.isNotBlank()) blocks += Heading(1, Alignment.CENTER, inline(state.title))
                if (state.date.isNotBlank()) blocks += Paragraph(Alignment.CENTER, listOf(Text("Date: ")) + inline(state.date))
                blocks += Separator
                blocks += Table(
                    listOf(TableColumn(Alignment.LEFT, 1), TableColumn(Alignment.LEFT, 7), TableColumn(Alignment.RIGHT, 2)),
                    emptyList(), state.items.map {
                        listOf(TableCell(listOf(InlineCode(if (it.checked) "⊠" else "□"))), TableCell(inline(it.description)),
                            TableCell(inline(listOf(it.quantity, it.unit).filter { part -> part.isNotBlank() }.joinToString(" "))))
                    },
                )
            }
            is QuickNoteState -> { title(state.title); blocks += paragraph(state.body) }
            is MemoState -> {
                title(state.subject)
                if (state.recipient.isNotBlank()) blocks += Paragraph(Alignment.LEFT, listOf(Text("To: ")) + inline(state.recipient))
                if (state.date.isNotBlank()) blocks += Paragraph(Alignment.LEFT, listOf(Text("Date: ")) + inline(state.date))
                if (state.subject.isNotBlank() || state.recipient.isNotBlank() || state.date.isNotBlank()) blocks += Separator
                blocks += paragraph(state.body)
                if (state.signature.isNotBlank()) blocks += Paragraph(Alignment.RIGHT, listOf(LineBreak, LineBreak) + inline(state.signature))
            }
            is QrMessageState -> {
                blocks += QrCode(state.payload, Alignment.CENTER, state.errorCorrection, state.requestedSize)
                if (state.caption.isNotBlank()) blocks += Paragraph(Alignment.CENTER, inline(state.caption))
            }
        }
        val documentTitle = when (state) {
            is TodoState -> state.title; is ShoppingListState -> state.title
            is QuickNoteState -> state.title; is MemoState -> state.subject; is QrMessageState -> ""
        }.takeIf { it.isNotBlank() }
        return TemplateBuildResult(ThermalDocument(metadata = DocumentMetadata(documentTitle), blocks = blocks.toList()), emptyList())
    }

    private fun paragraph(text: String) = Paragraph(Alignment.LEFT, inline(text))

    /** CRLF, CR and LF represent author line breaks; opaque QR payloads bypass this. */
    private fun inline(text: String): List<InlineContent> {
        val result = mutableListOf<InlineContent>()
        var start = 0
        var index = 0
        while (index < text.length) {
            if (text[index] == '\r' || text[index] == '\n') {
                if (index > start) result += Text(text.substring(start, index))
                result += LineBreak
                if (text[index] == '\r' && index + 1 < text.length && text[index + 1] == '\n') index++
                start = index + 1
            }
            index++
        }
        if (start < text.length) result += Text(text.substring(start))
        return result.toList()
    }

    private class Validation(val policy: TemplateResourcePolicy) {
        var diagnostic: TemplateDiagnostic? = null
        var totalBytes = 0L
        var lines = 0
        // Conservative JSON envelope/node reservation includes title's metadata duplication.
        var serializedBytes = 4_096L

        fun fail(code: String, field: String, message: String) {
            if (diagnostic == null) diagnostic = TemplateDiagnostic(code, field, message)
        }

        fun items(count: Int) {
            if (diagnostic != null) return
            if (count == 0) fail("TPL100", "items", "Add at least one item")
            else if (count > policy.maxItems) fail("TPL120", "items", "Item count exceeds policy")
            else serializedBytes += count * 512L
        }

        fun field(name: String, text: String, required: Boolean = false, opaque: Boolean = false) {
            if (diagnostic != null) return
            // Every UTF-16 unit needs at least one UTF-8 byte; reject before scans/allocation.
            if (text.length > policy.maxFieldBytes) { fail("TPL120", name, "Field exceeds policy"); return }
            var bytes = 0L
            var jsonBytes = 0L
            var fieldLines = if (opaque) 0 else 1
            var index = 0
            while (index < text.length) {
                val c = text[index]
                val size = when {
                    c.isHighSurrogate() -> {
                        if (index + 1 >= text.length || !text[index + 1].isLowSurrogate()) {
                            fail("TPL101", name, "Field contains malformed Unicode"); return
                        }
                        index++; 4
                    }
                    c.isLowSurrogate() -> { fail("TPL101", name, "Field contains malformed Unicode"); return }
                    c.code < 0x80 -> 1
                    c.code < 0x800 -> 2
                    else -> 3
                }
                bytes += size
                jsonBytes += when { c.code < 0x20 -> 6; c == '"' || c == '\\' -> 2; else -> size }
                if (!opaque && (c == '\r' || c == '\n' && (index == 0 || text[index - 1] != '\r'))) fieldLines++
                index++
            }
            totalBytes += bytes
            lines += fieldLines
            serializedBytes += jsonBytes * 2 + fieldLines * 128L
            when {
                bytes > policy.maxFieldBytes || totalBytes > policy.maxTotalBytes -> fail("TPL120", name, "Text exceeds policy")
                lines > policy.maxLines || serializedBytes > 16L * 1024 * 1024 -> fail("TPL120", name, "Document exceeds policy")
                required && (if (opaque) text.isEmpty() else text.isBlank()) -> fail("TPL100", name, "Enter required content")
            }
        }
    }
}
