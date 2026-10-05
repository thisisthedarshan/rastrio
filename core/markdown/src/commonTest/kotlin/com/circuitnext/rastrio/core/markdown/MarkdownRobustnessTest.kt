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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MarkdownRobustnessTest {
    private fun p(vararg content: InlineContent) = Paragraph(Alignment.LEFT, content.toList())
    private fun rejects(source: String, policy: MarkdownResourcePolicy, code: String = "MD121") {
        val result = MarkdownCompiler.compile(source, policy)
        assertNull(result.document)
        assertEquals(listOf(code), result.diagnostics.map { it.code })
        assertEquals(MarkdownSeverity.ERROR, result.diagnostics.single().severity)
        assertEquals(result, MarkdownCompiler.compile(source, policy))
    }

    @Test fun sourceUtf8BoundaryAndInvalidUnicode() {
        val policy = MarkdownResourcePolicy(maxSourceBytes = 4)
        assertNotNull(MarkdownCompiler.compile("😀", policy).document)
        rejects("😀a", policy, "MD120")
        assertNotNull(MarkdownCompiler.compile("", policy.copy(maxSourceBytes = 0)).document)
        rejects("x", policy.copy(maxSourceBytes = 0), "MD120")
        rejects("\uD800", policy, "MD100")
        rejects("\uDC00", policy, "MD100")
    }

    @Test fun astNodeAndNestingBoundaries() {
        // MARKDOWN_FILE -> PARAGRAPH -> TEXT: three nodes, depth two.
        val policy = MarkdownResourcePolicy(maxAstNodes = 3, maxNesting = 2)
        assertNotNull(MarkdownCompiler.compile("a", policy).document)
        rejects("a", policy.copy(maxAstNodes = 2))
        rejects("a", policy.copy(maxNesting = 1))
        rejects("> ".repeat(200) + "deep", MarkdownResourcePolicy())
        rejects("- parent\n" + "  - child\n".repeat(100), policy)
    }

    @Test fun parserWorkBoundaryIsDeterministic() {
        val source = "**work**"
        var low = 1L
        var high = 100_000L
        while (low < high) {
            val middle = (low + high) / 2
            if (MarkdownCompiler.compile(source, MarkdownResourcePolicy(maxParserOperations = middle)).document == null) low = middle + 1 else high = middle
        }
        assertNotNull(MarkdownCompiler.compile(source, MarkdownResourcePolicy(maxParserOperations = low)).document)
        rejects(source, MarkdownResourcePolicy(maxParserOperations = low - 1))
        rejects("[".repeat(20_000) + "x" + "]".repeat(20_000), MarkdownResourcePolicy(maxParserOperations = 10_000))
    }

    @Test fun canonicalDocumentLimits() {
        val policy = MarkdownResourcePolicy(document = TdResourcePolicy(maxBlocks = 1, maxInlineNodes = 1, maxStringBytes = 4, maxNesting = 2))
        assertNotNull(MarkdownCompiler.compile("abcd", policy).document)
        rejects("abcde", policy)
        rejects("a\n\nb", policy)
        rejects("a *b*", policy)
        assertNotNull(MarkdownCompiler.compile("**a**", policy.copy(document = policy.document.copy(maxInlineNodes = 2))).document)
        rejects("***a***", policy.copy(document = policy.document.copy(maxInlineNodes = 10)))
    }

    @Test fun tableBoundariesIncludeHeaderAndPaddedCells() {
        val source = "| a | b |\n| - | - |\n| c |\n"
        val td = TdResourcePolicy(maxTableColumns = 2, maxTableRows = 1, maxTableCells = 4)
        val policy = MarkdownResourcePolicy(document = td)
        assertNotNull(MarkdownCompiler.compile(source, policy).document)
        rejects(source, policy.copy(document = td.copy(maxTableColumns = 1)))
        rejects(source, policy.copy(document = td.copy(maxTableRows = 0)))
        rejects(source, policy.copy(document = td.copy(maxTableCells = 3)))
        val longTable = "| a | b |\n| - | - |\n" + "| x | y |\n".repeat(20_001)
        rejects(longTable, MarkdownResourcePolicy())
    }

    @Test fun longTokensAndPathologicalSyntaxNeverLoseContentSilently() {
        val token = "a".repeat(65_536)
        assertEquals(ThermalDocument(blocks = listOf(p(Text(token)))), MarkdownCompiler.compile(token).document)
        rejects(token + "a", MarkdownResourcePolicy())
        val sources = listOf("*".repeat(10_000), "[a](".repeat(1000), "`".repeat(10_000),
            "[x]: /a\n".repeat(2000) + "\n[x]", "***a***", "[broken](", "~~~\nunterminated")
        for (source in sources) {
            val result = MarkdownCompiler.compile(source)
            assertTrue(result.document != null || result.diagnostics.any { it.severity == MarkdownSeverity.ERROR })
            assertEquals(result, MarkdownCompiler.compile(source))
        }
    }

    @Test fun breaksEscapesNestedFormattingAndCodeNormalization() {
        val source = "one  \ntwo\\\nthree\nfour &lt; \\* \n\n***nested*** ` a\nb `"
        assertEquals(ThermalDocument(blocks = listOf(
            p(Text("one"), LineBreak, Text("two"), LineBreak, Text("three four < *")),
            p(Emphasis(listOf(Strong(listOf(Text("nested"))))), Text(" "), InlineCode("a b")),
        )), MarkdownCompiler.compile(source).document)
    }

    @Test fun mixedTasksAndInlineImageKeepOrder() {
        val source = "- plain\n- [x] done\n- final\n\nbefore ![a](a.png) after"
        val result = MarkdownCompiler.compile(source)
        assertEquals(ThermalDocument(blocks = listOf(
            UnorderedList(listOf(ListItem(listOf(p(Text("plain")))))),
            Checklist(listOf(ChecklistItem(true, listOf(p(Text("done")))))),
            UnorderedList(listOf(ListItem(listOf(p(Text("final")))))),
            p(Text("before")), Image(ExternalAssetReference("a.png"), Alignment.LEFT, AutoSizing, "a"), p(Text("after")),
        )), result.document)
        assertEquals(listOf("MD201"), result.diagnostics.map { it.code })
    }

    @Test fun inlineHtmlUnknownExtensionsAndUnresolvedReferencesAreLiteral() {
        val result = MarkdownCompiler.compile("a <b onclick=\"run()\">bold</b> &amp; [missing][id] \$x\$")
        assertEquals(ThermalDocument(blocks = listOf(p(Text("a <b onclick=\"run()\">bold</b> & [missing][id] \$x\$")))), result.document)
        assertEquals(listOf("MD101", "MD101", "MD102"), result.diagnostics.map { it.code })
    }

    @Test fun referenceImagesAndFirstDefinitionWins() {
        val result = MarkdownCompiler.compile("![Café][logo]\n\n[logo]: assets/first.png\n[logo]: assets/second.png")
        assertEquals(ThermalDocument(blocks = listOf(Image(ExternalAssetReference("assets/first.png"), Alignment.LEFT, AutoSizing, "Café"))), result.document)
        assertEquals(listOf("MD201"), result.diagnostics.map { it.code })
    }

    @Test fun largeCodeManyReferencesImagesAndNestedListsAreBounded() {
        val policy = MarkdownResourcePolicy()
        rejects("```\n" + "code".repeat(20_000) + "\n```", policy)
        rejects("![a](a.png)\n\n".repeat(1000), policy.copy(maxAstNodes = 1000))
        rejects("[a](a) ".repeat(10_000), policy.copy(maxAstNodes = 1000))
        val nested = (0..150).joinToString("\n") { "  ".repeat(it) + "- nested" }
        rejects(nested, policy)
        rejects("> ".repeat(150) + "[a](".repeat(100) + "*".repeat(10_000), policy.copy(maxParserOperations = 1000))
    }

    @Test fun everyGoldenDocumentRoundTripsThroughEstablishedTdContract() {
        MarkdownGoldens.cases.forEach { case ->
            val document = checkNotNull(MarkdownCompiler.compile(case.source).document)
            assertEquals(document, TdArchive.load(TdArchive.save(document)).document, case.name)
        }
    }

    @Test fun emptyAtxHeadingsHaveEmptySemanticContent() {
        assertEquals(ThermalDocument(blocks = listOf(Heading(1, Alignment.LEFT, emptyList()), Heading(2, Alignment.LEFT, emptyList()))), MarkdownCompiler.compile("#\n\n##\n").document)
    }

    @Test fun codeInsideContainersAndIndentedFences() {
        assertEquals(ThermalDocument(blocks = listOf(CodeBlock("a\nb\n"))), MarkdownCompiler.compile("  ```\n  a\n b\n  ```\n").document)
        assertEquals(ThermalDocument(blocks = listOf(Quote(listOf(CodeBlock("a\n"))))), MarkdownCompiler.compile("> ```\n> a\n> ```\n").document)
        assertEquals(ThermalDocument(blocks = listOf(UnorderedList(listOf(ListItem(listOf(p(Text("item")), CodeBlock("a\n"))))))), MarkdownCompiler.compile("- item\n\n      a\n").document)
    }

    @Test fun seededMalformedCorpusProducesDiagnosticsOrValidPortableDocuments() {
        val random = kotlin.random.Random(20261005)
        val alphabet = "abc *_[!]()<>|`~#:-&;\n\t\\"
        repeat(256) {
            val source = buildString { repeat(random.nextInt(1, 256)) { append(alphabet[random.nextInt(alphabet.length)]) } }
            val result = MarkdownCompiler.compile(source)
            assertEquals(result, MarkdownCompiler.compile(source))
            if (result.document != null) assertEquals(result.document, TdArchive.load(TdArchive.save(result.document)).document)
            else assertTrue(result.diagnostics.any { it.severity == MarkdownSeverity.ERROR })
        }
    }

    @Test fun v1InlineImageAndOrderedTaskLimitationsHaveExplicitDiagnostics() {
        val image = MarkdownCompiler.compile("**![a](a.png)**")
        assertEquals(ThermalDocument(blocks = listOf(p(Strong(listOf(Text("![a](a.png)")))))), image.document)
        assertEquals(listOf("MD102"), image.diagnostics.map { it.code })
        val tasks = MarkdownCompiler.compile("3. [x] Done\n4. Plain")
        assertEquals(ThermalDocument(blocks = listOf(Checklist(listOf(ChecklistItem(true, listOf(p(Text("Done")))))), OrderedList(4, listOf(ListItem(listOf(p(Text("Plain")))))))), tasks.document)
        assertEquals(listOf("MD103"), tasks.diagnostics.map { it.code })
        val table = MarkdownCompiler.compile("| a | b |\n| - | - |\n| x | y | z |")
        assertEquals(ThermalDocument(blocks = listOf(Table(listOf(TableColumn(Alignment.LEFT), TableColumn(Alignment.LEFT)), listOf(TableCell(listOf(Text("a"))), TableCell(listOf(Text("b")))), listOf(listOf(TableCell(listOf(Text("x"))), TableCell(listOf(Text("y")))))))), table.document)
        assertEquals(listOf("MD103"), table.diagnostics.map { it.code })
    }

    @Test fun explicitLinkLabelsDoNotCreateNestedAutomaticLinks() {
        val source = "[www.example.com](https://other.example) and www.example.com"
        assertEquals(ThermalDocument(blocks = listOf(p(Link("https://other.example", listOf(Text("www.example.com"))), Text(" and "), Link("http://www.example.com", listOf(Text("www.example.com")))))), MarkdownCompiler.compile(source).document)
    }

    @Test fun tableCodePipesAndLeadingQuoteCharactersAreSemanticText() {
        val source = "| a | b |\n| - | - |\n| > value | `a\\|b` |\n\n`a\\|b`"
        assertEquals(ThermalDocument(blocks = listOf(
            Table(listOf(TableColumn(Alignment.LEFT), TableColumn(Alignment.LEFT)), listOf(TableCell(listOf(Text("a"))), TableCell(listOf(Text("b")))), listOf(listOf(TableCell(listOf(Text("> value"))), TableCell(listOf(InlineCode("a|b")))))),
            p(InlineCode("a\\|b")),
        )), MarkdownCompiler.compile(source).document)
    }

    @Test fun newlineNormalizationAndDiagnosticPrivacy() {
        val source = "private <b>value</b>"
        val result = MarkdownCompiler.compile(source)
        assertTrue(result.diagnostics.none { "private" in it.message || "value" in it.message })
        assertEquals(MarkdownCompiler.compile("a\n\nb"), MarkdownCompiler.compile("a\r\n\r\nb"))
    }
}
