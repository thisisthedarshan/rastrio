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
import org.intellij.markdown.MarkdownParsingException
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getParentOfType
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.html.entities.Entities
import org.intellij.markdown.lexer.Compat.codePointToString
import org.intellij.markdown.lexer.TokenInfo
import org.intellij.markdown.parser.CancellationToken
import org.intellij.markdown.parser.MarkdownParser
import org.intellij.markdown.parser.sequentialparsers.SequentialParser
import org.intellij.markdown.parser.sequentialparsers.SequentialParserManager
import org.intellij.markdown.parser.sequentialparsers.TokensCache

/** Trusted runtime policy, never read from Markdown. Parser work is counted deterministically. */
data class MarkdownResourcePolicy(
    val maxSourceBytes: Long = 8L * 1024 * 1024,
    val maxAstNodes: Int = 250_000,
    val maxNesting: Int = 128,
    val maxParserOperations: Long = 50_000_000,
    val document: TdResourcePolicy = TdResourcePolicy(),
    val maxSourceLines: Int = 100_000,
)

enum class MarkdownSeverity { WARNING, ERROR }
/** Offsets refer to UTF-16 positions in newline-normalized source; end is exclusive. */
data class MarkdownDiagnostic(
    val code: String,
    val severity: MarkdownSeverity,
    val message: String,
    val startOffset: Int? = null,
    val endOffset: Int? = null,
)
data class MarkdownCompilation(val document: ThermalDocument?, val diagnostics: List<MarkdownDiagnostic>)

object MarkdownCompiler {
    fun compile(source: String, policy: MarkdownResourcePolicy = MarkdownResourcePolicy()): MarkdownCompilation {
        require(policy.maxSourceBytes >= 0 && policy.maxAstNodes > 0 && policy.maxNesting in 1..128 && policy.maxParserOperations > 0 && policy.maxSourceLines > 0)
        return try {
            checkSource(source, policy)
            val text = source.replace("\r\n", "\n").replace('\r', '\n')
            var operations = 0L
            fun consumeWork(amount: Long = 1) {
                if (amount > policy.maxParserOperations - operations) fail("MD121", "Markdown parser work exceeds policy")
                operations += amount
            }
            val root = MarkdownParser(boundedFlavour(::consumeWork), true, CancellationToken { consumeWork() })
                .buildMarkdownTreeFromString(BoundedSource(text, 0, text.length, ::consumeWork))
            checkAst(root, policy)
            val compiler = Compiler(text, root, policy.document)
            val document = ThermalDocument(blocks = compiler.blocks(root.children))
            checkDocument(document, policy.document)
            MarkdownCompilation(document, compiler.diagnostics.toList())
        } catch (error: CompilationFailure) {
            MarkdownCompilation(null, listOf(error.diagnostic))
        } catch (_: MarkdownParsingException) {
            MarkdownCompilation(null, listOf(MarkdownDiagnostic("MD100", MarkdownSeverity.ERROR, "Markdown parser rejected input")))
        }
    }
}

/** Meter cached-token scans too: upstream inline parsers can scan without reading source. */
private fun boundedFlavour(consumeWork: (Long) -> Unit): GFMFlavourDescriptor {
    val original = GFMFlavourDescriptor().sequentialParserManager
    return object : GFMFlavourDescriptor() {
        override val sequentialParserManager = object : SequentialParserManager() {
            override fun getParserSequence(): List<SequentialParser> = original.getParserSequence().map { parser ->
                object : SequentialParser {
                    override fun parse(tokens: TokensCache, rangesToGlue: List<IntRange>) =
                        parser.parse(object : TokensCache() {
                            override val cachedTokens = metered(tokens.cachedTokens, consumeWork)
                            override val filteredTokens = metered(tokens.filteredTokens, consumeWork)
                            override val originalText = tokens.originalText
                            override val originalTextRange = tokens.originalTextRange
                        }, rangesToGlue)
                }
            }
        }
    }
}

private fun metered(tokens: List<TokenInfo>, consumeWork: (Long) -> Unit): List<TokenInfo> =
    object : AbstractList<TokenInfo>() {
        override val size: Int get() = tokens.size
        override fun get(index: Int): TokenInfo {
            consumeWork(1)
            return tokens[index]
        }
    }

/** Bounds lexer reads as well as parser cancellation checkpoints, including source slices. */
private class BoundedSource(
    private val source: String,
    private val start: Int,
    override val length: Int,
    private val consumeWork: (Long) -> Unit,
) : CharSequence {
    override fun get(index: Int): Char {
        require(index in 0 until length)
        consumeWork(1)
        return source[start + index]
    }
    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        require(startIndex in 0..endIndex && endIndex <= length)
        consumeWork(1)
        return BoundedSource(source, start + startIndex, endIndex - startIndex, consumeWork)
    }
    override fun toString(): String {
        consumeWork(length.toLong())
        return source.substring(start, start + length)
    }
}

private class CompilationFailure(val diagnostic: MarkdownDiagnostic) : RuntimeException(diagnostic.message)
private fun fail(code: String, message: String): Nothing = throw CompilationFailure(MarkdownDiagnostic(code, MarkdownSeverity.ERROR, message))

/** Counts UTF-8 without allocating a second document-sized byte array. */
private fun utf8Size(text: String): Long {
    var bytes = 0L
    var i = 0
    while (i < text.length) {
        val c = text[i++]
        bytes += when {
            c.isHighSurrogate() -> {
                if (i == text.length || !text[i++].isLowSurrogate()) fail("MD100", "Invalid Unicode input")
                4
            }
            c.isLowSurrogate() -> fail("MD100", "Invalid Unicode input")
            c.code < 128 -> 1
            c.code < 2048 -> 2
            else -> 3
        }
    }
    return bytes
}
private fun checkSource(text: String, policy: MarkdownResourcePolicy) {
    if (text.length.toLong() > policy.maxSourceBytes || utf8Size(text) > policy.maxSourceBytes) fail("MD120", "Markdown source exceeds policy")
    var lines = 1L
    var previous = '\u0000'
    for (character in text) {
        if (character == '\r' || character == '\n' && previous != '\r') {
            // Check before increment, including the final empty line after a terminator.
            if (lines >= policy.maxSourceLines.toLong()) fail("MD121", "Markdown line count exceeds policy")
            lines++
        }
        previous = character
    }
}
private fun checkAst(root: ASTNode, policy: MarkdownResourcePolicy) {
    val stack = ArrayDeque<Pair<ASTNode, Int>>()
    stack.add(root to 0)
    var nodes = 0
    while (stack.isNotEmpty()) {
        val (node, depth) = stack.removeLast()
        if (++nodes > policy.maxAstNodes || depth > policy.maxNesting) fail("MD121", "Markdown AST complexity exceeds policy")
        node.children.forEach { stack.add(it to depth + 1) }
    }
}

private val ASTNode.kind: String get() = type.toString().removePrefix("Markdown:")

private class Compiler(private val source: String, root: ASTNode, private val documentPolicy: TdResourcePolicy) {
    val diagnostics = mutableListOf<MarkdownDiagnostic>()
    private val definitions = mutableMapOf<String, String>()
    private val definitionTitles = mutableSetOf<String>()
    private fun raw(node: ASTNode) = source.substring(node.startOffset, node.endOffset)
    private fun warning(node: ASTNode, code: String, message: String) {
        diagnostics.add(MarkdownDiagnostic(code, MarkdownSeverity.WARNING, message, node.startOffset, node.endOffset))
    }
    private fun label(text: String) = text.trim().replace(Regex("\\s+"), " ").lowercase()

    init {
        val stack = ArrayDeque<ASTNode>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            if (node.kind == "LINK_DEFINITION") {
                val key = node.children.firstOrNull { it.kind == "LINK_LABEL" }
                val value = node.children.firstOrNull { it.kind == "LINK_DESTINATION" }
                if (key != null && value != null) {
                    val normalized = label(raw(key))
                    if (normalized !in definitions) {
                        definitions[normalized] = destination(raw(value))
                        if (node.children.any { it.kind == "LINK_TITLE" }) definitionTitles.add(normalized)
                    }
                }
            }
            node.children.asReversed().forEach(stack::addLast)
        }
    }

    fun blocks(nodes: List<ASTNode>): List<DocumentBlock> {
        return nodes.flatMap { node ->
            val type = node.kind
            when {
                type == "PARAGRAPH" -> paragraph(node)
                type.startsWith("ATX_") || type.startsWith("SETEXT_") -> {
                    val level = type.substringAfter('_').toInt()
                    val content = node.children.firstOrNull { it.kind in listOf("ATX_CONTENT", "SETEXT_CONTENT") }
                    listOf(Heading(level, Alignment.LEFT, inline(trimWhitespace(content?.children.orEmpty()))))
                }
                type == "UNORDERED_LIST" || type == "ORDERED_LIST" -> list(node)
                type == "BLOCK_QUOTE" && node.children.isNotEmpty() -> listOf(Quote(blocks(node.children).ifEmpty { listOf(Paragraph(Alignment.LEFT, emptyList())) }))
                type == "CODE_FENCE" || type == "CODE_BLOCK" -> listOf(code(node))
                type == "HORIZONTAL_RULE" -> listOf(Separator)
                type == "HTML_BLOCK" -> {
                    warning(node, "MD101", "Unsupported raw HTML preserved as literal text")
                    listOf(Paragraph(Alignment.LEFT, listOf(Text(raw(node)))))
                }
                type == "TABLE" -> listOf(table(node))
                type in listOf("WHITE_SPACE", "EOL", "LIST_BULLET", "LIST_NUMBER", "CHECK_BOX", "LINK_DEFINITION", "BLOCK_QUOTE") || raw(node).isBlank() -> emptyList()
                else -> {
                    warning(node, "MD102", "Unsupported Markdown preserved as literal text")
                    listOf(Paragraph(Alignment.LEFT, listOf(Text(raw(node)))))
                }
            }
        }
    }

    private fun paragraph(node: ASTNode): List<DocumentBlock> {
        val result = mutableListOf<DocumentBlock>()
        val pending = mutableListOf<ASTNode>()
        fun flush() {
            if (pending.isNotEmpty()) {
                result.add(Paragraph(Alignment.LEFT, inline(trimWhitespace(pending))))
                pending.clear()
            }
        }
        for (child in node.children) {
            if (child.kind == "IMAGE") {
                val link = child.children.firstOrNull { it.kind.endsWith("LINK") }
                val info = link?.let(::link)
                if (info == null) pending.add(child) else {
                    flush()
                    warning(child, "MD201", "Markdown image requires explicit asset resolution")
                    result.add(Image(ExternalAssetReference(info.first), Alignment.LEFT, AutoSizing, plain(inline(info.second))))
                }
            } else pending.add(child)
        }
        flush()
        return result.ifEmpty { listOf(Paragraph(Alignment.LEFT, emptyList())) }
    }

    private fun list(node: ASTNode): List<DocumentBlock> {
        val items = node.children.filter { it.kind == "LIST_ITEM" }
        val ordered = node.kind == "ORDERED_LIST"
        val result = mutableListOf<DocumentBlock>()
        val normal = mutableListOf<ListItem>()
        val checks = mutableListOf<ChecklistItem>()
        val start = if (ordered) raw(node.children.firstOrNull { it.kind == "LIST_ITEM" } ?: node)
            .trimStart().takeWhile { it.isDigit() }.toLongOrNull() ?: 1L else 1L
        var consumed = 0L
        fun flushNormal() {
            if (normal.isNotEmpty()) {
                result.add(if (ordered) OrderedList(start + consumed, normal.toList()) else UnorderedList(normal.toList()))
                consumed += normal.size
                normal.clear()
            }
        }
        fun flushChecks() {
            if (checks.isNotEmpty()) { result.add(Checklist(checks.toList())); consumed += checks.size; checks.clear() }
        }
        for (item in items) {
            val checkbox = item.children.firstOrNull { it.kind == "CHECK_BOX" }
            val content = blocks(item.children).ifEmpty { listOf(Paragraph(Alignment.LEFT, emptyList())) }
            if (checkbox != null) {
                flushNormal()
                if (ordered) warning(checkbox, "MD103", "Ordered task numbering cannot be represented by checklist semantics")
                checks.add(ChecklistItem(raw(checkbox).contains('x', true), content))
            } else { flushChecks(); normal.add(ListItem(content)) }
        }
        flushNormal(); flushChecks()
        return result
    }

    private fun code(node: ASTNode): CodeBlock {
        val fenced = node.kind == "CODE_FENCE"
        val content = StringBuilder()
        val indent = if (fenced) raw(node).takeWhile { it == ' ' }.length.coerceAtMost(3) else 4
        var active = !fenced
        for (child in node.children) {
            val type = child.kind
            if (active && type in listOf("CODE_FENCE_CONTENT", "CODE_LINE", "EOL")) {
                content.append(if (type == "EOL") raw(child) else stripIndent(raw(child), indent))
            }
            if (!active && type == "EOL") active = true
        }
        var text = content.toString()
        if (text.isNotEmpty() && !text.endsWith('\n')) text += "\n"
        val language = node.children.firstOrNull { it.kind == "FENCE_LANG" }?.let { decode(raw(it).trim().substringBefore(' ')) }
        return CodeBlock(text, language)
    }

    private fun stripIndent(text: String, indent: Int): String {
        var index = 0
        var columns = 0
        while (index < text.length && columns < indent) {
            when (text[index]) { ' ' -> columns++; '\t' -> columns += 4 - columns % 4; else -> break }
            index++
        }
        return " ".repeat((columns - indent).coerceAtLeast(0)) + text.substring(index)
    }

    private fun table(node: ASTNode): Table {
        val rowNodes = node.children.filter { it.kind == "ROW" }
        val headerNode = node.children.first { it.kind == "HEADER" }
        val separators = node.children.first { it.kind == "TABLE_SEPARATOR" }
        val alignments = raw(separators).trim().trim('|').split('|').map {
            val cell = it.trim()
            when { cell.startsWith(':') && cell.endsWith(':') -> Alignment.CENTER; cell.endsWith(':') -> Alignment.RIGHT; else -> Alignment.LEFT }
        }
        fun cells(row: ASTNode) = row.children.filter { it.kind == "CELL" }.map { TableCell(inline(trimWhitespace(it.children))) }
        val columnCount = headerNode.children.count { it.kind == "CELL" }
        if (columnCount > documentPolicy.maxTableColumns || rowNodes.size > documentPolicy.maxTableRows ||
            (rowNodes.size.toLong() + 1) * columnCount > documentPolicy.maxTableCells) fail("MD121", "Document table exceeds policy")
        val header = cells(headerNode)
        val columns = header.indices.map { TableColumn(alignments.getOrElse(it) { Alignment.LEFT }) }
        val rows = rowNodes.map { row ->
            val cells = cells(row)
            if (row.children.any { it.kind == "TABLE_SEPARATOR" && raw(it).any { char -> char != '|' && !char.isWhitespace() } }) {
                warning(row, "MD103", "Extra GFM table cells are ignored by GFM semantics")
            }
            List(columns.size) { cells.getOrElse(it) { TableCell(emptyList()) } }
        }
        return Table(columns, header, rows)
    }

    private fun inline(nodes: List<ASTNode>, allowAutolinks: Boolean = true): List<InlineContent> {
        val result = mutableListOf<InlineContent>()
        val pending = StringBuilder()
        fun flushText() {
            if (pending.isNotEmpty()) { result.add(Text(pending.toString())); pending.clear() }
        }
        fun append(content: InlineContent) {
            if (content is Text) pending.append(content.text) else { flushText(); result.add(content) }
        }
        for ((index, node) in nodes.withIndex()) {
            val type = node.kind
            // GFM lexer exposes angle-delimited email as three sibling tokens.
            if (type == "<" && nodes.getOrNull(index + 1)?.kind == "EMAIL_AUTOLINK" && nodes.getOrNull(index + 2)?.kind == ">") continue
            if (type == ">" && nodes.getOrNull(index - 1)?.kind == "EMAIL_AUTOLINK" && nodes.getOrNull(index - 2)?.kind == "<") continue
            when (type) {
                "EMPH", "STRONG", "STRIKETHROUGH" -> {
                    val delimiter = if (type == "STRIKETHROUGH") "~" else "EMPH"
                    val children = node.children.dropWhile { it.kind == delimiter && it.children.isEmpty() }.dropLastWhile { it.kind == delimiter && it.children.isEmpty() }
                    val content = inline(children, allowAutolinks)
                    append(when (type) { "EMPH" -> Emphasis(content); "STRONG" -> Strong(content); else -> Strike(content) })
                }
                "CODE_SPAN" -> {
                    val raw = raw(node)
                    val count = raw.takeWhile { it == '`' }.length
                    var code = raw.substring(count, raw.length - count).replace('\n', ' ')
                    if (code.startsWith(' ') && code.endsWith(' ') && code.any { it != ' ' }) code = code.substring(1, code.length - 1)
                    if (node.getParentOfType(GFMTokenTypes.CELL) != null) code = code.replace("\\|", "|")
                    append(InlineCode(code))
                }
                "INLINE_LINK", "FULL_REFERENCE_LINK", "SHORT_REFERENCE_LINK" -> {
                    val info = link(node)
                    if (info == null) append(Text(decode(raw(node)))) else append(Link(info.first, inline(info.second, allowAutolinks = false)))
                }
                "AUTOLINK", "GFM_AUTOLINK", "EMAIL_AUTOLINK" -> {
                    if (!allowAutolinks) { append(Text(decode(raw(node)))); continue }
                    val text = raw(node).removeSurrounding("<", ">")
                    val destination = when { text.startsWith("www.") -> "http://$text"; '@' in text && ':' !in text -> "mailto:$text"; else -> text }
                    append(Link(decode(destination), listOf(Text(decode(text)))))
                }
                "BR" -> append(LineBreak)
                "EOL" -> if (pending.isNotEmpty() || result.lastOrNull() != LineBreak) append(Text(" "))
                "HTML_TAG" -> { warning(node, "MD101", "Unsupported raw HTML preserved as literal text"); append(Text(raw(node))) }
                "IMAGE", "INLINE_MATH", "BLOCK_MATH", "ALERT_TITLE" -> {
                    warning(node, "MD102", "Unsupported inline construct preserved as literal text")
                    append(Text(raw(node)))
                }
                "BLOCK_QUOTE" -> if (node.getParentOfType(GFMTokenTypes.CELL) != null) append(Text(raw(node).trimStart()))
                "CHECK_BOX" -> Unit
                else -> append(Text(decode(raw(node))))
            }
        }
        flushText()
        return result
    }

    private fun link(node: ASTNode): Pair<String, List<ASTNode>>? {
        val labelNode = node.children.firstOrNull { it.kind == "LINK_TEXT" }
        val reference = node.children.firstOrNull { it.kind == "LINK_LABEL" }
        val destination = node.children.firstOrNull { it.kind == "LINK_DESTINATION" }
        val text = labelNode ?: reference ?: return null
        val children = text.children.drop(1).dropLast(1)
        if (node.kind == "INLINE_LINK") {
            if (node.children.any { it.kind == "LINK_TITLE" }) warning(node, "MD103", "Link title is not represented by document v1")
            return destination(destination?.let(::raw) ?: "") to children
        }
        val key = label(raw(reference ?: text))
        if (key in definitionTitles) warning(node, "MD103", "Link title is not represented by document v1")
        return definitions[key]?.let { it to children }
    }
    private fun destination(text: String) = decode(text.removeSurrounding("<", ">"))
    private fun trimWhitespace(nodes: List<ASTNode>) = nodes.dropWhile { it.kind == "WHITE_SPACE" }.dropLastWhile { it.kind in listOf("WHITE_SPACE", "EOL") }
    private fun plain(nodes: List<InlineContent>): String = nodes.joinToString("") {
        when (it) { is Text -> it.text; is InlineCode -> it.text; is Strong -> plain(it.children); is Emphasis -> plain(it.children); is Strike -> plain(it.children); is Link -> plain(it.children); LineBreak -> "\n" }
    }
}

private val escapes = Regex("""\\([!"#$%&'()*+,\-./:;<=>?@\[\\\]\^_`{|}~])|&(?:#[0-9]{1,8}|#[xX][0-9a-fA-F]{1,8}|[A-Za-z][A-Za-z0-9]{1,31});""")
private fun decode(text: String): String = escapes.replace(text) { match ->
    if (match.groups[1] != null) match.groupValues[1] else {
        val entity = match.value
        val code = when {
            entity.startsWith("&#x", true) -> entity.substring(3, entity.length - 1).toIntOrNull(16)
            entity.startsWith("&#") -> entity.substring(2, entity.length - 1).toIntOrNull()
            else -> Entities.map[entity]
        }
        if (code == null) entity else codePointToString(if (code == 0 || code > 0x10ffff || code in 0xd800..0xdfff) 0xfffd else code)
    }
}

private fun checkDocument(document: ThermalDocument, policy: TdResourcePolicy) {
    val blocks = ArrayDeque<Pair<DocumentBlock, Int>>()
    val inlines = ArrayDeque<Pair<InlineContent, Int>>()
    var blockCount = 0
    var inlineCount = 0
    fun text(value: String) { if (utf8Size(value) > policy.maxStringBytes) fail("MD121", "Document string exceeds policy") }
    fun addInline(nodes: List<InlineContent>, depth: Int) { nodes.forEach { inlines.add(it to depth) } }
    document.blocks.forEach { blocks.add(it to 0) }
    while (blocks.isNotEmpty()) {
        val (block, depth) = blocks.removeLast()
        if (++blockCount > policy.maxBlocks || depth > policy.maxNesting) fail("MD121", "Document block complexity exceeds policy")
        fun children(nodes: List<DocumentBlock>) { nodes.forEach { blocks.add(it to depth + 1) } }
        when (block) {
            is Paragraph -> addInline(block.content, depth + 1)
            is Heading -> addInline(block.content, depth + 1)
            is UnorderedList -> block.items.forEach { children(it.blocks) }
            is OrderedList -> block.items.forEach { children(it.blocks) }
            is Checklist -> block.items.forEach { children(it.blocks) }
            is Quote -> children(block.blocks)
            is CodeBlock -> { text(block.text); block.language?.let(::text) }
            is Image -> { text((block.asset as ExternalAssetReference).uri); block.altText?.let(::text) }
            is Table -> {
                if (block.columns.size > policy.maxTableColumns || block.rows.size > policy.maxTableRows || (block.rows.size.toLong() + 1) * block.columns.size > policy.maxTableCells) fail("MD121", "Document table exceeds policy")
                block.header.forEach { addInline(it.content, depth + 1) }
                block.rows.forEach { row -> row.forEach { addInline(it.content, depth + 1) } }
            }
            Separator -> Unit
            is QrCode -> error("Markdown does not produce QR blocks")
        }
    }
    while (inlines.isNotEmpty()) {
        val (node, depth) = inlines.removeLast()
        if (++inlineCount > policy.maxInlineNodes || depth > policy.maxNesting) fail("MD121", "Document inline complexity exceeds policy")
        when (node) {
            is Text -> text(node.text)
            is InlineCode -> text(node.text)
            is Strong -> addInline(node.children, depth + 1)
            is Emphasis -> addInline(node.children, depth + 1)
            is Strike -> addInline(node.children, depth + 1)
            is Link -> { text(node.destination); addInline(node.children, depth + 1) }
            LineBreak -> Unit
        }
    }
}
