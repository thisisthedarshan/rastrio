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

internal data class GoldenCase(val name: String, val source: String, val document: ThermalDocument, val diagnostics: List<MarkdownDiagnostic>)
internal object MarkdownGoldens {
    private fun p(text: String) = p(Text(text))
    private fun p(vararg content: InlineContent) = Paragraph(Alignment.LEFT, content.toList())
    private fun h(level: Int, text: String) = Heading(level, Alignment.LEFT, listOf(Text(text)))
    private fun item(text: String) = ListItem(listOf(p(text)))
    private fun cell(text: String) = TableCell(listOf(Text(text)))
    val cases = listOf(
        GoldenCase("paragraph", "First line\nsecond line.\n\nThird paragraph.\n", ThermalDocument(blocks = listOf(p("First line second line."), p("Third paragraph."))), emptyList()),
        GoldenCase("headings", "# One\n\n## Two\n\n### Three\n\n#### Four\n\n##### Five\n\n###### Six\n\nSetext one\n===\n\nSetext two\n---\n", ThermalDocument(blocks = listOf(h(1,"One"), h(2,"Two"), h(3,"Three"), h(4,"Four"), h(5,"Five"), h(6,"Six"), h(1,"Setext one"), h(2,"Setext two"))), emptyList()),
        GoldenCase("formatting", "**bold** *em* ~~gone~~ `code`\n", ThermalDocument(blocks = listOf(p(Strong(listOf(Text("bold"))), Text(" "), Emphasis(listOf(Text("em"))), Text(" "), Strike(listOf(Text("gone"))), Text(" "), InlineCode("code")))), emptyList()),
        GoldenCase("unordered-list", "- Apple\n- Banana\n", ThermalDocument(blocks = listOf(UnorderedList(listOf(item("Apple"), item("Banana"))))), emptyList()),
        GoldenCase("ordered-list", "3. Three\n4. Four\n", ThermalDocument(blocks = listOf(OrderedList(3, listOf(item("Three"), item("Four"))))), emptyList()),
        GoldenCase("nested-list", "- Parent\n  - Child\n    1. Grandchild\n", ThermalDocument(blocks = listOf(
            UnorderedList(listOf(ListItem(listOf(
                p("Parent"), UnorderedList(listOf(ListItem(listOf(
                    p("Child"), OrderedList(1, listOf(item("Grandchild")))
                ))))
            ))))
        )), emptyList()),
        GoldenCase("task-list", "- [ ] Open\n- [x] Done\n", ThermalDocument(blocks = listOf(Checklist(listOf(ChecklistItem(false, listOf(p("Open"))), ChecklistItem(true, listOf(p("Done"))))))), emptyList()),
        GoldenCase("blockquote", "> Quote\n>\n> > Nested\n", ThermalDocument(blocks = listOf(Quote(listOf(p("Quote"), Quote(listOf(p("Nested"))))))), emptyList()),
        GoldenCase("code", "```kotlin\nval x = 1\n```\n\n    indented\n\n` a  b `\n", ThermalDocument(blocks = listOf(CodeBlock("val x = 1\n", "kotlin"), CodeBlock("indented\n"), p(InlineCode("a  b")))), emptyList()),
        GoldenCase("links", "[Example](https://example.com) and <dev@example.com>\n\n[Reference][id]\n\n[id]: /guide \"Guide\"\n", ThermalDocument(blocks = listOf(p(Link("https://example.com", listOf(Text("Example"))), Text(" and "), Link("mailto:dev@example.com", listOf(Text("dev@example.com")))), p(Link("/guide", listOf(Text("Reference")))))), listOf(MarkdownDiagnostic("MD103", MarkdownSeverity.WARNING, "Link title is not represented by document v1", 54, 69))),
        GoldenCase("image", "![Logo](assets/logo.png)\n", ThermalDocument(blocks = listOf(Image(ExternalAssetReference("assets/logo.png"), Alignment.LEFT, AutoSizing, "Logo"))), listOf(MarkdownDiagnostic("MD201", MarkdownSeverity.WARNING, "Markdown image requires explicit asset resolution", 0, 24))),
        GoldenCase("external-image", "![Remote](https://example.invalid/logo.png)\n", ThermalDocument(blocks = listOf(Image(ExternalAssetReference("https://example.invalid/logo.png"), Alignment.LEFT, AutoSizing, "Remote"))), listOf(MarkdownDiagnostic("MD201", MarkdownSeverity.WARNING, "Markdown image requires explicit asset resolution", 0, 43))),
        GoldenCase("table", "| Item | Qty |\n| :--- | ---: |\n| Tea | 2 |\n", ThermalDocument(blocks = listOf(Table(listOf(TableColumn(Alignment.LEFT), TableColumn(Alignment.RIGHT)), listOf(cell("Item"), cell("Qty")), listOf(listOf(cell("Tea"), cell("2")))))), emptyList()),
        GoldenCase("unicode", "हिन्दी 日本語 مرحبا café 😀 &amp; &#x1F600;\n", ThermalDocument(blocks = listOf(p("हिन्दी 日本語 مرحبا café 😀 & 😀"))), emptyList()),
        GoldenCase("raw-html", "<script src=\"https://example.invalid/run.js\">alert(1)</script>\n", ThermalDocument(blocks = listOf(p("<script src=\"https://example.invalid/run.js\">alert(1)</script>"))), listOf(MarkdownDiagnostic("MD101", MarkdownSeverity.WARNING, "Unsupported raw HTML preserved as literal text", 0, 62))),
        GoldenCase("mixed-document", "# Pocket Notes\n\nA small **offline** tool for notes.\n\n## Install\n\n```sh\n./gradlew build\n```\n\n## Features\n\n- [x] Unicode\n- [ ] Printing\n\n> Keep your notes local.\n\n| Platform | Status |\n| :--- | :---: |\n| Android | Active |\n| Desktop | Dev |\n\n[Guide](docs/guide.md)\n\n![Logo](assets/logo.png)\n\n---\n\nLicensed under Apache-2.0.\n", ThermalDocument(blocks = listOf(h(1,"Pocket Notes"), p(Text("A small "), Strong(listOf(Text("offline"))), Text(" tool for notes.")), h(2,"Install"), CodeBlock("./gradlew build\n", "sh"), h(2,"Features"), Checklist(listOf(ChecklistItem(true,listOf(p("Unicode"))), ChecklistItem(false,listOf(p("Printing"))))), Quote(listOf(p("Keep your notes local."))), Table(listOf(TableColumn(Alignment.LEFT),TableColumn(Alignment.CENTER)),listOf(cell("Platform"),cell("Status")),listOf(listOf(cell("Android"),cell("Active")),listOf(cell("Desktop"),cell("Dev")))), p(Link("docs/guide.md",listOf(Text("Guide")))), Image(ExternalAssetReference("assets/logo.png"),Alignment.LEFT,AutoSizing,"Logo"), Separator, p("Licensed under Apache-2.0."))), listOf(MarkdownDiagnostic("MD201", MarkdownSeverity.WARNING, "Markdown image requires explicit asset resolution", 264, 288)))
    )
}

class MarkdownGoldenTest {
    private fun check(name: String) {
        val case = MarkdownGoldens.cases.single { it.name == name }
        val expected = MarkdownCompilation(case.document, case.diagnostics)
        assertEquals(expected, MarkdownCompiler.compile(case.source), name)
        assertEquals(expected, MarkdownCompiler.compile(case.source), "$name deterministic repeat")
    }
    @Test fun paragraph() = check("paragraph")
    @Test fun headings() = check("headings")
    @Test fun formatting() = check("formatting")
    @Test fun unordered_list() = check("unordered-list")
    @Test fun ordered_list() = check("ordered-list")
    @Test fun nested_list() = check("nested-list")
    @Test fun task_list() = check("task-list")
    @Test fun blockquote() = check("blockquote")
    @Test fun code() = check("code")
    @Test fun links() = check("links")
    @Test fun image() = check("image")
    @Test fun external_image() = check("external-image")
    @Test fun table() = check("table")
    @Test fun unicode() = check("unicode")
    @Test fun raw_html() = check("raw-html")
    @Test fun mixed_document() = check("mixed-document")
}
