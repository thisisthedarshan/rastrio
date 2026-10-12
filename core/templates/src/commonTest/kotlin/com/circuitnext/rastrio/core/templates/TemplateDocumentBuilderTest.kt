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
import kotlin.test.*

class TemplateDocumentBuilderTest {
    private fun build(state: TemplateState) = assertNotNull(TemplateDocumentBuilder.build(state).document)
    private fun failure(state: TemplateState, code: String, field: String, policy: TemplateResourcePolicy = TemplateResourcePolicy()) {
        val result = TemplateDocumentBuilder.build(state, policy)
        assertNull(result.document)
        assertEquals(code, result.diagnostics.single().code)
        assertEquals(field, result.diagnostics.single().field)
    }

    @Test fun emptyStatesReturnActionableDiagnostics() {
        failure(TodoState(), "TPL100", "items")
        failure(ShoppingListState(), "TPL100", "items")
        failure(QuickNoteState(), "TPL100", "body")
        failure(MemoState(), "TPL100", "body")
        failure(QrMessageState(), "TPL100", "payload")
    }

    @Test fun todoKeepsOrderAndCheckStateWithoutParsing() {
        val document = build(TodoState("Tasks", listOf(TodoItem("*literal*", true), TodoItem("कागज & <tag>"))))
        assertEquals(DocumentMetadata("Tasks"), document.metadata)
        assertEquals(Heading(1, Alignment.LEFT, listOf(Text("Tasks"))), document.blocks[0])
        assertEquals(Checklist(listOf(
            ChecklistItem(true, listOf(Paragraph(Alignment.LEFT, listOf(Text("*literal*"))))),
            ChecklistItem(false, listOf(Paragraph(Alignment.LEFT, listOf(Text("कागज & <tag>"))))),
        )), document.blocks[1])
    }

    @Test fun shoppingQuantitiesAreLiteralAndOptional() {
        val document = build(ShoppingListState(items = listOf(ShoppingItem("Milk", "2 L"), ShoppingItem("Bread"))))
        val list = document.blocks.single() as UnorderedList
        assertEquals(listOf(Text("2 L"), Text(" × "), Text("Milk")), (list.items[0].blocks.single() as Paragraph).content)
        assertEquals(listOf(Text("Bread")), (list.items[1].blocks.single() as Paragraph).content)
        assertNull(document.metadata.title)
    }

    @Test fun intentionalLineBreaksSurviveIncludingEmptyLinesAndTrailingBreak() {
        val content = (build(QuickNoteState(body = "a\r\n\r\nb\rc\n")).blocks.single() as Paragraph).content
        assertEquals(listOf(Text("a"), LineBreak, LineBreak, Text("b"), LineBreak, Text("c"), LineBreak), content)
    }

    @Test fun memoIncludesOnlyEnteredFields() {
        val full = build(MemoState("Subject", "Team", "Body", "Darshan"))
        assertEquals(4, full.blocks.size)
        assertEquals(listOf(Text("To: "), Text("Team")), (full.blocks[1] as Paragraph).content)
        assertEquals(listOf(Text("Darshan")), (full.blocks.last() as Paragraph).content)
        assertEquals(listOf(Paragraph(Alignment.LEFT, listOf(Text("Body")))), build(MemoState(body = "Body")).blocks)
    }

    @Test fun qrRemainsOpaqueSemanticDataWithRequestedPreferences() {
        val state = QrMessageState("https://invalid.example/\u0000\r\n नमस्ते", "Scan\nme", QrErrorCorrection.HIGH, Length(30.0))
        val document = build(state)
        assertEquals(QrCode(state.payload, Alignment.CENTER, QrErrorCorrection.HIGH, Length(30.0)), document.blocks.first())
        assertEquals(Paragraph(Alignment.CENTER, listOf(Text("Scan"), LineBreak, Text("me"))), document.blocks.last())
        assertNotNull(build(QrMessageState(" ")).blocks.single())
    }

    @Test fun editRemoveReorderAndToggleAreRepresentedByNewStates() {
        val state = TodoState(items = listOf(TodoItem("one"), TodoItem("two"), TodoItem("three")))
        val edited = state.copy(items = listOf(state.items[2].copy(checked = true), state.items[0].copy(text = "changed")))
        val items = (build(edited).blocks.single() as Checklist).items
        assertEquals(listOf(true, false), items.map { it.checked })
        assertEquals(listOf("three", "changed"), items.map { ((it.blocks.single() as Paragraph).content.single() as Text).text })
        assertEquals(3, state.items.size)
        val shopping = ShoppingListState(items = listOf(ShoppingItem("one"), ShoppingItem("two")))
        val changed = shopping.copy(items = listOf(shopping.items[1].copy(quantity = "4")))
        assertEquals(1, (build(changed).blocks.single() as UnorderedList).items.size)
    }

    @Test fun equivalentStatesBuildEqualRoundTrippableDocuments() {
        val states = listOf<TemplateState>(TodoState(items = listOf(TodoItem("a"))),
            ShoppingListState(items = listOf(ShoppingItem("नमस्ते", "2"))),
            QuickNoteState("Note", "* & < > \" \\ é\nnext"), MemoState(body = "memo"),
            QrMessageState("opaque\u0000text", errorCorrection = QrErrorCorrection.QUARTILE))
        states.forEach { state ->
            val document = build(state)
            assertEquals(document, build(state))
            assertEquals(document, TdArchive.load(TdArchive.save(document)).document)
        }
    }

    @Test fun blankRequiredItemFieldsFailWithoutDisclosingContent() {
        failure(TodoState(items = listOf(TodoItem(" "))), "TPL100", "items[0].text")
        failure(ShoppingListState(items = listOf(ShoppingItem())), "TPL100", "items[0].description")
        val result = TemplateDocumentBuilder.build(QuickNoteState(body = "private" + '\uD800'))
        assertFalse(result.diagnostics.single().message.contains("private"))
    }

    @Test fun malformedUnicodeIsRejectedInEveryTemplateWhilePairsSurvive() {
        listOf("\uD800", "\uDC00", "\uD800x").forEach { text ->
            failure(TodoState(items = listOf(TodoItem(text))), "TPL101", "items[0].text")
            failure(ShoppingListState(items = listOf(ShoppingItem(text))), "TPL101", "items[0].description")
            failure(QuickNoteState(body = text), "TPL101", "body")
            failure(MemoState(body = text), "TPL101", "body")
            failure(QrMessageState(text), "TPL101", "payload")
        }
        assertNotNull(build(QuickNoteState(body = "\uD83D\uDE00")))
    }

    @Test fun exactAndOneOverUtf8AndAggregateLimits() {
        val policy = TemplateResourcePolicy(maxFieldBytes = 4, maxTotalBytes = 4)
        assertNotNull(TemplateDocumentBuilder.build(QuickNoteState(body = "éé"), policy).document)
        failure(QuickNoteState(body = "ééa"), "TPL120", "body", policy)
        failure(QuickNoteState(title = "a", body = "éé"), "TPL120", "body", policy)
        assertNotNull(TemplateDocumentBuilder.build(QrMessageState("\uD83D\uDE00"), policy).document)
    }

    @Test fun everyTemplateEnforcesFieldLimits() {
        val policy = TemplateResourcePolicy(maxFieldBytes = 2)
        failure(TodoState(items = listOf(TodoItem("123"))), "TPL120", "items[0].text", policy)
        failure(ShoppingListState(items = listOf(ShoppingItem("a", "123"))), "TPL120", "items[0].quantity", policy)
        failure(QuickNoteState(body = "123"), "TPL120", "body", policy)
        failure(MemoState(body = "a", signature = "123"), "TPL120", "signature", policy)
        failure(QrMessageState("123"), "TPL120", "payload", policy)
    }

    @Test fun itemAndLineBudgetsStopBeforeOutputAmplification() {
        val policy = TemplateResourcePolicy(maxItems = 2, maxLines = 3)
        assertNotNull(TemplateDocumentBuilder.build(TodoState(items = List(2) { TodoItem("a") }), policy).document)
        failure(TodoState(items = List(3) { TodoItem("a") }), "TPL120", "items", policy)
        failure(ShoppingListState(items = List(3) { ShoppingItem("a") }), "TPL120", "items", policy)
        assertNotNull(TemplateDocumentBuilder.build(QuickNoteState(body = "a\nb"), policy).document)
        failure(QuickNoteState(body = "a\nb\nc"), "TPL120", "body", policy)
    }

    @Test fun invalidQrSizesAndPolicyEscalationAreRejected() {
        listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, 1000.01).forEach {
            failure(QrMessageState("a", requestedSize = Length(it)), "TPL102", "requestedSize")
        }
        assertNotNull(build(QrMessageState("a", requestedSize = Length(1000.0))))
        assertFailsWith<IllegalArgumentException> { TemplateResourcePolicy(maxItems = 1001) }
        assertFailsWith<IllegalArgumentException> { TemplateResourcePolicy(maxFieldBytes = 65537) }
        assertFailsWith<IllegalArgumentException> { TemplateResourcePolicy(maxLines = 0) }
    }

    @Test fun overLimitCollectionsAreNotTraversed() {
        val items = object : AbstractList<TodoItem>() {
            override val size: Int get() = Int.MAX_VALUE
            override fun get(index: Int): TodoItem = error("oversized input was traversed")
        }
        failure(TodoState(items = items), "TPL120", "items")
    }
}
