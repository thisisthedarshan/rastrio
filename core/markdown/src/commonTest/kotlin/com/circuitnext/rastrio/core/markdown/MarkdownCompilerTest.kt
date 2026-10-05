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

package com.circuitnext.rastrio.core.markdown

import com.circuitnext.rastrio.core.document.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MarkdownCompilerTest {
    private fun p(text: String) = Paragraph(Alignment.LEFT, listOf(Text(text)))

    @Test fun semanticBlocks() {
        val source = "# Title\n\nHello **bold** and *em* and ~~old~~ and `code`.\n\n---\n\n> Quote\n\n3. Third\n4. Fourth\n\n- [ ] Open\n- [x] Done\n"
        val expected = ThermalDocument(blocks = listOf(
            Heading(1, Alignment.LEFT, listOf(Text("Title"))),
            Paragraph(Alignment.LEFT, listOf(Text("Hello "), Strong(listOf(Text("bold"))), Text(" and "),
                Emphasis(listOf(Text("em"))), Text(" and "), Strike(listOf(Text("old"))), Text(" and "), InlineCode("code"), Text("."))),
            Separator, Quote(listOf(p("Quote"))),
            OrderedList(3, listOf(ListItem(listOf(p("Third"))), ListItem(listOf(p("Fourth"))))),
            Checklist(listOf(ChecklistItem(false, listOf(p("Open"))), ChecklistItem(true, listOf(p("Done"))))),
        ))
        val result = MarkdownCompiler.compile(source)
        assertEquals(expected, result.document)
        assertEquals(emptyList(), result.diagnostics)
        assertEquals(result, MarkdownCompiler.compile(source))
    }

    @Test fun rawHtmlIsLiteralAndImagesRemainUnresolved() {
        val result = MarkdownCompiler.compile("<script>alert(1)</script>\n\n![Logo](https://example.invalid/logo.png)")
        assertEquals(ThermalDocument(blocks = listOf(p("<script>alert(1)</script>"),
            Image(ExternalAssetReference("https://example.invalid/logo.png"), Alignment.LEFT, AutoSizing, "Logo"))), result.document)
        assertEquals(listOf("MD101", "MD201"), result.diagnostics.map { it.code })
    }

    @Test fun sourceLimitRejectsWithoutPartialDocument() {
        val result = MarkdownCompiler.compile("12345", MarkdownResourcePolicy(maxSourceBytes = 4))
        assertNull(result.document)
        assertEquals(listOf("MD120"), result.diagnostics.map { it.code })
    }
}
