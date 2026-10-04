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

package com.circuitnext.rastrio.core.document

internal data class Manifest(
    val assets: List<AssetMetadata>,
    val source: SourceMetadata?,
)
data class SourceMetadata(val path: String, val mediaType: String, val byteSize: Long, val sha256: String? = null)

internal object TdJson {
    fun encodeDocument(document: ThermalDocument, policy: TdResourcePolicy): ByteArray {
        val writer = JsonWriter(policy.maxDocumentBytes)
        writer.obj {
            field("schemaVersion", 1)
            field("metadata") { obj { document.metadata.title?.let { field("title", it) } } }
            field("layout") {
                obj {
                    field("orientation", document.layout.orientation.wire())
                    document.layout.maxWidth?.let { length -> field("maxWidth") { writeLength(length) } }
                }
            }
            field("blocks") { arr(document.blocks) { writeBlock(it as DocumentBlock) } }
        }
        return writer.toString().encodeToByteArray()
    }

    fun decodeDocument(bytes: ByteArray, policy: TdResourcePolicy): ThermalDocument {
        val root = StrictJson(decodeUtf8(bytes), policy).parse()
        val fields = objectOf(root, "document", setOf("schemaVersion", "metadata", "layout", "blocks"))
        val version = fields.getValue("schemaVersion").asLong("schemaVersion")
        if (version != 1L) throw TdException.Invalid("Unsupported document schema version")
        val metadataFields = objectOf(fields.getValue("metadata"), "metadata", emptySet(), setOf("title"))
        val metadata = DocumentMetadata(metadataFields["title"]?.asString("title"))
        val layoutFields = objectOf(fields.getValue("layout"), "layout", setOf("orientation"), setOf("maxWidth"))
        val layout = DocumentLayout(
            orientation = parseEnum(layoutFields.getValue("orientation").asString("orientation"), mapOf("portrait" to Orientation.PORTRAIT, "landscape" to Orientation.LANDSCAPE), "orientation"),
            maxWidth = layoutFields["maxWidth"]?.let { readLength(it, policy) },
        )
        val counter = NodeCounter(policy)
        val blocks = fields.getValue("blocks").asArray("blocks").also { if (it.size > policy.maxBlocks) throw TdException.Limit("Block count exceeds policy", "TD125") }.map { readBlock(it, policy, counter, 0) }
        val document = ThermalDocument(1, metadata, layout, blocks)
        validateDocument(document, policy)
        return document
    }

    fun encodeManifest(manifest: Manifest, policy: TdResourcePolicy): ByteArray {
        val writer = JsonWriter(policy.maxManifestBytes)
        writer.obj {
            field("format", "rastrio-td")
            field("containerVersion", 1)
            field("documentSchemaVersion", 1)
            field("encoding", "json")
            field("assets") {
                arr(manifest.assets.sortedBy { it.path }) {
                    val asset = it as AssetMetadata
                    obj {
                        field("id", asset.id); field("path", asset.path); field("mediaType", asset.mediaType); field("byteSize", asset.byteSize)
                        asset.sha256?.let { hash -> field("sha256", hash) }
                    }
                }
            }
            manifest.source?.let { source -> field("source") {
                obj { field("path", source.path); field("mediaType", source.mediaType); field("byteSize", source.byteSize); source.sha256?.let { field("sha256", it) } }
            } }
        }
        return writer.toString().encodeToByteArray()
    }

    fun decodeManifest(bytes: ByteArray, policy: TdResourcePolicy): Manifest {
        val root = StrictJson(decodeUtf8(bytes), policy).parse()
        val fields = objectOf(root, "manifest", setOf("format", "containerVersion", "documentSchemaVersion", "encoding", "assets"), setOf("source"))
        if (fields.getValue("format").asString("format") != "rastrio-td") throw TdException.Invalid("Invalid .td format")
        if (fields.getValue("containerVersion").asLong("containerVersion") != 1L) throw TdException.Invalid("Unsupported container version")
        if (fields.getValue("documentSchemaVersion").asLong("documentSchemaVersion") != 1L) throw TdException.Invalid("Unsupported document schema version")
        if (fields.getValue("encoding").asString("encoding") != "json") throw TdException.Invalid("Unsupported .td encoding")
        val assets = fields.getValue("assets").asArray("assets").also { if (it.size > policy.maxAssets) throw TdException.Limit("Asset count exceeds policy", "TD125") }.map { value ->
            val asset = objectOf(value, "asset descriptor", setOf("id", "path", "mediaType", "byteSize"), setOf("sha256"))
            AssetMetadata(
                id = asset.getValue("id").asString("asset id"), path = asset.getValue("path").asString("asset path"),
                mediaType = asset.getValue("mediaType").asString("asset mediaType"), byteSize = asset.getValue("byteSize").asLong("asset byteSize"),
                sha256 = asset["sha256"]?.asString("asset sha256"),
            )
        }
        val source = fields["source"]?.let { value ->
            val item = objectOf(value, "source descriptor", setOf("path", "mediaType", "byteSize"), setOf("sha256"))
            SourceMetadata(item.getValue("path").asString("source path"), item.getValue("mediaType").asString("source mediaType"), item.getValue("byteSize").asLong("source byteSize"), item["sha256"]?.asString("source sha256"))
        }
        validateManifest(Manifest(assets, source), policy)
        return Manifest(assets, source)
    }

    private fun JsonWriter.writeLength(length: Length) = obj { field("value", length.value); field("unit", "mm") }
    private fun JsonWriter.writeInline(inline: InlineContent) {
        obj {
            when (inline) {
                is Text -> { field("type", "text"); field("text", inline.text) }
                is Strong -> { field("type", "strong"); field("children") { arr(inline.children) { writeInline(it as InlineContent) } } }
                is Emphasis -> { field("type", "emphasis"); field("children") { arr(inline.children) { writeInline(it as InlineContent) } } }
                is Strike -> { field("type", "strike"); field("children") { arr(inline.children) { writeInline(it as InlineContent) } } }
                is InlineCode -> { field("type", "inlineCode"); field("text", inline.text) }
                is Link -> { field("type", "link"); field("destination", inline.destination); field("children") { arr(inline.children) { writeInline(it as InlineContent) } } }
                LineBreak -> field("type", "lineBreak")
            }
        }
    }
    private fun JsonWriter.writeBlock(block: DocumentBlock) {
        obj {
            when (block) {
                is Paragraph -> { field("type", "paragraph"); field("alignment", block.alignment.wire()); field("content") { arr(block.content) { writeInline(it as InlineContent) } } }
                is Heading -> { field("type", "heading"); field("level", block.level); field("alignment", block.alignment.wire()); field("content") { arr(block.content) { writeInline(it as InlineContent) } } }
                is UnorderedList -> { field("type", "unorderedList"); field("items") { writeItems(block.items) } }
                is OrderedList -> { field("type", "orderedList"); field("start", block.start); field("items") { writeItems(block.items) } }
                is Checklist -> { field("type", "checklist"); field("items") { arr(block.items) { item -> val value = item as ChecklistItem; obj { field("checked", value.checked); field("blocks") { arr(value.blocks) { writeBlock(it as DocumentBlock) } } } } } }
                is Quote -> { field("type", "quote"); field("blocks") { arr(block.blocks) { writeBlock(it as DocumentBlock) } } }
                is CodeBlock -> { field("type", "codeBlock"); field("text", block.text); block.language?.let { field("language", it) } }
                Separator -> field("type", "separator")
                is Image -> {
                    field("type", "image")
                    field("asset") { obj { when (val asset = block.asset) { is EmbeddedAssetReference -> { field("kind", "embedded"); field("assetId", asset.assetId) }; is ExternalAssetReference -> { field("kind", "external"); field("uri", asset.uri) } } } }
                    field("alignment", block.alignment.wire())
                    field("sizing") { obj { when (val sizing = block.sizing) { AutoSizing -> field("mode", "auto"); FitWidthSizing -> field("mode", "fitWidth"); is RequestedWidthSizing -> { field("mode", "width"); field("width") { writeLength(sizing.width) } } } } }
                    block.altText?.let { field("altText", it) }
                }
                is Table -> {
                    field("type", "table")
                    field("columns") { arr(block.columns) { column -> obj { field("alignment", (column as TableColumn).alignment.wire()) } } }
                    field("header") { arr(block.header) { writeCell(it as TableCell) } }
                    field("rows") { arr(block.rows) { row -> arr(row as List<TableCell>) { writeCell(it as TableCell) } } }
                }
                is QrCode -> { field("type", "qrCode"); field("payload", block.payload); field("alignment", block.alignment.wire()); field("errorCorrection", block.errorCorrection.wire()); block.requestedSize?.let { field("requestedSize") { writeLength(it) } } }
            }
        }
    }
    private fun JsonWriter.writeItems(items: List<ListItem>) = arr(items) { item -> obj { field("blocks") { arr((item as ListItem).blocks) { writeBlock(it as DocumentBlock) } } } }
    private fun JsonWriter.writeCell(cell: TableCell) = obj { field("content") { arr(cell.content) { writeInline(it as InlineContent) } }; cell.alignment?.let { field("alignment", it.wire()) } }

    private fun readLength(value: JsonValue, policy: TdResourcePolicy): Length {
        val fields = objectOf(value, "length", setOf("value", "unit"))
        val unit = fields.getValue("unit").asString("unit")
        if (unit != "mm") throw TdException.Invalid("Unsupported length unit")
        val number = fields.getValue("value").asDouble("length value")
        if (number <= 0.0 || number > policy.maxLogicalWidthMm) throw TdException.Invalid("Length is outside document policy")
        return Length(number)
    }

    private fun readInline(value: JsonValue, policy: TdResourcePolicy, counter: NodeCounter, depth: Int): InlineContent {
        counter.inline(depth)
        val raw = value.asObject("inline content")
        val type = raw["type"]?.asString("inline type") ?: throw TdException.Invalid("Missing inline type")
        return when (type) {
            "text" -> { val f = objectOf(value, "text inline", setOf("type", "text")); Text(f.getValue("text").asString("text")) }
            "inlineCode" -> { val f = objectOf(value, "inlineCode", setOf("type", "text")); InlineCode(f.getValue("text").asString("text")) }
            "lineBreak" -> { objectOf(value, "lineBreak", setOf("type")); LineBreak }
            "strong", "emphasis", "strike" -> {
                val f = objectOf(value, type, setOf("type", "children")); val children = f.getValue("children").asArray("children").map { readInline(it, policy, counter, depth + 1) }
                when (type) { "strong" -> Strong(children); "emphasis" -> Emphasis(children); else -> Strike(children) }
            }
            "link" -> { val f = objectOf(value, "link", setOf("type", "destination", "children")); Link(f.getValue("destination").asString("destination"), f.getValue("children").asArray("children").map { readInline(it, policy, counter, depth + 1) }) }
            else -> throw TdException.Invalid("Unknown inline discriminator")
        }
    }

    private fun readBlock(value: JsonValue, policy: TdResourcePolicy, counter: NodeCounter, depth: Int): DocumentBlock {
        counter.block(depth)
        val raw = value.asObject("document block")
        val type = raw["type"]?.asString("block type") ?: throw TdException.Invalid("Missing block type")
        fun alignment(fields: Map<String, JsonValue>) = parseEnum(fields.getValue("alignment").asString("alignment"), mapOf("left" to Alignment.LEFT, "center" to Alignment.CENTER, "right" to Alignment.RIGHT), "alignment")
        fun inlineList(fields: Map<String, JsonValue>, name: String = "content") = fields.getValue(name).asArray(name).map { readInline(it, policy, counter, depth + 1) }
        fun blockList(fields: Map<String, JsonValue>, name: String = "blocks"): List<DocumentBlock> = fields.getValue(name).asArray(name).map { readBlock(it, policy, counter, depth + 1) }.also { if (it.isEmpty()) throw TdException.Invalid("$name must not be empty") }
        return when (type) {
            "paragraph" -> { val f = objectOf(value, type, setOf("type", "alignment", "content")); Paragraph(alignment(f), inlineList(f)) }
            "heading" -> { val f = objectOf(value, type, setOf("type", "level", "alignment", "content")); val level = f.getValue("level").asLong("level"); if (level !in 1..6) throw TdException.Invalid("Invalid heading level"); Heading(level.toInt(), alignment(f), inlineList(f)) }
            "unorderedList", "orderedList" -> {
                val required = if (type == "orderedList") setOf("type", "start", "items") else setOf("type", "items")
                val f = objectOf(value, type, required)
                val items = f.getValue("items").asArray("items").map { item -> val itemFields = objectOf(item, "list item", setOf("blocks")); ListItem(blockList(itemFields)) }
                if (type == "unorderedList") UnorderedList(items) else { val start = f.getValue("start").asLong("start"); if (start < 0) throw TdException.Invalid("Invalid ordered-list start"); OrderedList(start, items) }
            }
            "checklist" -> { val f = objectOf(value, type, setOf("type", "items")); Checklist(f.getValue("items").asArray("items").map { item -> val g = objectOf(item, "checklist item", setOf("checked", "blocks")); ChecklistItem(g.getValue("checked").asBoolean("checked"), blockList(g)) }) }
            "quote" -> { val f = objectOf(value, type, setOf("type", "blocks")); Quote(blockList(f)) }
            "codeBlock" -> { val f = objectOf(value, type, setOf("type", "text"), setOf("language")); CodeBlock(f.getValue("text").asString("text"), f["language"]?.asString("language")) }
            "separator" -> { objectOf(value, type, setOf("type")); Separator }
            "image" -> {
                val f = objectOf(value, type, setOf("type", "asset", "alignment", "sizing"), setOf("altText"))
                val assetFields = f.getValue("asset").asObject("asset reference")
                val kind = assetFields["kind"]?.asString("asset kind") ?: throw TdException.Invalid("Missing asset kind")
                val asset = when (kind) {
                    "embedded" -> { val a = objectOf(f.getValue("asset"), "embedded asset reference", setOf("kind", "assetId")); EmbeddedAssetReference(a.getValue("assetId").asString("assetId")) }
                    "external" -> { val a = objectOf(f.getValue("asset"), "external asset reference", setOf("kind", "uri")); ExternalAssetReference(a.getValue("uri").asString("uri")) }
                    else -> throw TdException.Invalid("Unknown asset reference kind", "TD101")
                }
                val sizingFields = f.getValue("sizing").asObject("sizing")
                val mode = sizingFields["mode"]?.asString("sizing mode") ?: throw TdException.Invalid("Missing sizing mode")
                val sizing = when (mode) {
                    "auto" -> { objectOf(f.getValue("sizing"), "auto sizing", setOf("mode")); AutoSizing }
                    "fitWidth" -> { objectOf(f.getValue("sizing"), "fitWidth sizing", setOf("mode")); FitWidthSizing }
                    "width" -> { val s = objectOf(f.getValue("sizing"), "width sizing", setOf("mode", "width")); RequestedWidthSizing(readLength(s.getValue("width"), policy)) }
                    else -> throw TdException.Invalid("Unknown image sizing mode")
                }
                Image(asset, alignment(f), sizing, f["altText"]?.asString("altText"))
            }
            "table" -> {
                val f = objectOf(value, type, setOf("type", "columns", "header", "rows"))
                val columns = f.getValue("columns").asArray("columns").map { c -> val cf = objectOf(c, "table column", setOf("alignment")); TableColumn(parseEnum(cf.getValue("alignment").asString("alignment"), mapOf("left" to Alignment.LEFT, "center" to Alignment.CENTER, "right" to Alignment.RIGHT), "alignment")) }
                if (columns.isEmpty()) throw TdException.Invalid("Table must have columns")
                if (columns.size > policy.maxTableColumns) throw TdException.Limit("Table column count exceeds policy", "TD125")
                fun cell(v: JsonValue): TableCell { val cf = objectOf(v, "table cell", setOf("content"), setOf("alignment")); return TableCell(cf.getValue("content").asArray("content").map { readInline(it, policy, counter, depth + 1) }, cf["alignment"]?.let { parseEnum(it.asString("alignment"), mapOf("left" to Alignment.LEFT, "center" to Alignment.CENTER, "right" to Alignment.RIGHT), "alignment") }) }
                val header = f.getValue("header").asArray("header").map(::cell)
                val rows = f.getValue("rows").asArray("rows").also { if (it.size > policy.maxTableRows) throw TdException.Limit("Table row count exceeds policy", "TD125") }.map { it.asArray("row").map(::cell) }
                if (rows.sumOf { it.size.toLong() } + header.size > policy.maxTableCells) throw TdException.Limit("Table cell count exceeds policy", "TD125")
                Table(columns, header, rows)
            }
            "qrCode" -> { val f = objectOf(value, type, setOf("type", "payload", "alignment", "errorCorrection"), setOf("requestedSize")); QrCode(f.getValue("payload").asString("payload"), alignment(f), parseEnum(f.getValue("errorCorrection").asString("errorCorrection"), mapOf("auto" to QrErrorCorrection.AUTO, "low" to QrErrorCorrection.LOW, "medium" to QrErrorCorrection.MEDIUM, "quartile" to QrErrorCorrection.QUARTILE, "high" to QrErrorCorrection.HIGH), "error correction"), f["requestedSize"]?.let { readLength(it, policy) }) }
            else -> throw TdException.Invalid("Unknown block discriminator")
        }
    }

    fun validateDocument(document: ThermalDocument, policy: TdResourcePolicy) {
        if (document.schemaVersion != 1) throw TdException.Invalid("Unsupported document schema version")
        document.metadata.title?.validateText(policy)
        document.layout.maxWidth?.let { validateLength(it, policy) }
        var blocks = 0
        var inline = 0
        fun visitInline(nodes: List<InlineContent>, depth: Int) {
            if (depth > policy.maxNesting) throw TdException.Limit("Document nesting exceeds policy", "TD125")
            nodes.forEach { node ->
                inline++
                if (inline > policy.maxInlineNodes) throw TdException.Limit("Inline node count exceeds policy", "TD125")
                when (node) { is Text -> node.text.validateText(policy); is InlineCode -> node.text.validateText(policy); is Strong -> visitInline(node.children, depth + 1); is Emphasis -> visitInline(node.children, depth + 1); is Strike -> visitInline(node.children, depth + 1); is Link -> { node.destination.validateText(policy); visitInline(node.children, depth + 1) }; LineBreak -> Unit }
            }
        }
        fun visitBlock(block: DocumentBlock, depth: Int) {
            if (depth > policy.maxNesting) throw TdException.Limit("Document nesting exceeds policy", "TD125")
            blocks++
            if (blocks > policy.maxBlocks) throw TdException.Limit("Block count exceeds policy", "TD125")
            when (block) {
                is Paragraph -> visitInline(block.content, depth + 1)
                is Heading -> { if (block.level !in 1..6) throw TdException.Invalid("Invalid heading level"); visitInline(block.content, depth + 1) }
                is UnorderedList -> block.items.forEach { item -> if (item.blocks.isEmpty()) throw TdException.Invalid("List item has no blocks"); item.blocks.forEach { visitBlock(it, depth + 1) } }
                is OrderedList -> { if (block.start < 0) throw TdException.Invalid("Invalid ordered-list start"); block.items.forEach { if (it.blocks.isEmpty()) throw TdException.Invalid("List item has no blocks"); it.blocks.forEach { child -> visitBlock(child, depth + 1) } } }
                is Checklist -> block.items.forEach { if (it.blocks.isEmpty()) throw TdException.Invalid("Checklist item has no blocks"); it.blocks.forEach { child -> visitBlock(child, depth + 1) } }
                is Quote -> { if (block.blocks.isEmpty()) throw TdException.Invalid("Quote has no blocks"); block.blocks.forEach { visitBlock(it, depth + 1) } }
                is CodeBlock -> { block.text.validateText(policy); block.language?.validateText(policy) }
                Separator -> Unit
                is Image -> { block.altText?.validateText(policy); when (block.asset) { is EmbeddedAssetReference -> (block.asset as EmbeddedAssetReference).assetId.validateText(policy); is ExternalAssetReference -> (block.asset as ExternalAssetReference).uri.validateText(policy) }; if (block.sizing is RequestedWidthSizing) validateLength((block.sizing as RequestedWidthSizing).width, policy) }
                is Table -> {
                    if (block.columns.isEmpty() || block.header.size != block.columns.size || block.rows.any { it.size != block.columns.size }) throw TdException.Invalid("Invalid table dimensions")
                    if (block.columns.size > policy.maxTableColumns || block.rows.size > policy.maxTableRows || (block.rows.sumOf { it.size.toLong() } + block.header.size) > policy.maxTableCells) throw TdException.Limit("Table exceeds policy", "TD125")
                    (block.header + block.rows.flatten()).forEach { visitInline(it.content, depth + 1) }
                }
                is QrCode -> { block.payload.validateText(policy); block.requestedSize?.let { validateLength(it, policy) } }
            }
        }
        document.blocks.forEach { visitBlock(it, 0) }
    }
    private fun validateLength(length: Length, policy: TdResourcePolicy) { if (!length.value.isFinite() || length.value <= 0 || length.value > policy.maxLogicalWidthMm || length.unit != LengthUnit.MM) throw TdException.Invalid("Invalid length") }
    private fun String.validateText(policy: TdResourcePolicy) { if (!hasValidUnicode()) throw TdException.Invalid("Invalid Unicode string"); if (encodeToByteArray().size > policy.maxStringBytes) throw TdException.Limit("Document string exceeds policy", "TD125") }
    fun validateManifest(manifest: Manifest, policy: TdResourcePolicy) {
        val ids = mutableSetOf<String>(); val paths = mutableSetOf<String>()
        manifest.assets.forEach { asset ->
            if (!asset.id.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) || !ids.add(asset.id)) throw TdException.Invalid("Invalid or duplicate asset id")
            validateArchivePath(asset.path, policy)
            if (!asset.path.startsWith("assets/") || !paths.add(asset.path)) throw TdException.Invalid("Invalid or duplicate asset path")
            validateMediaType(asset.mediaType)
            if (asset.byteSize < 0) throw TdException.Invalid("Invalid asset byte size")
            if (asset.byteSize > policy.maxAssetBytes) throw TdException.Limit("Asset byte size exceeds policy", "TD123")
            asset.sha256?.let(::validateHash)
        }
        manifest.source?.let { source ->
            validateArchivePath(source.path, policy)
            if (!source.path.startsWith("source/")) throw TdException.Invalid("Invalid source path")
            validateMediaType(source.mediaType)
            if (source.byteSize < 0) throw TdException.Invalid("Invalid source byte size")
            if (source.byteSize > policy.maxSourceBytes) throw TdException.Limit("Source byte size exceeds policy", "TD123")
            source.sha256?.let(::validateHash)
        }
    }
    private fun validateHash(hash: String) { if (!hash.matches(Regex("[0-9a-f]{64}"))) throw TdException.Invalid("Invalid SHA-256") }
    private fun validateMediaType(mediaType: String) { if (!mediaType.matches(Regex("[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+"))) throw TdException.Invalid("Invalid media type") }
    fun validateArchivePath(path: String, policy: TdResourcePolicy): String {
        if (path.isEmpty() || !path.hasValidUnicode() || path.startsWith('/') || path.startsWith('\\') || '\\' in path || '\u0000' in path || path.contains(':')) throw TdException.Invalid("Invalid archive path")
        val segments = path.split('/')
        if (segments.any { it.isEmpty() || it == "." || it == ".." }) throw TdException.Invalid("Invalid archive path")
        if (segments.size > policy.maxPathSegments || path.encodeToByteArray().size > policy.maxPathBytes) throw TdException.Limit("Archive path exceeds policy", "TD126")
        return segments.joinToString("/")
    }
    private fun decodeUtf8(bytes: ByteArray): String {
        val decoded = try { bytes.decodeToString(throwOnInvalidSequence = true) } catch (_: Exception) { throw TdException.Invalid("Malformed UTF-8 JSON") }
        if (decoded.startsWith('\uFEFF')) throw TdException.Invalid("UTF-8 BOM is not accepted")
        return decoded
    }
    private fun <T> parseEnum(value: String, choices: Map<String, T>, label: String): T = choices[value] ?: throw TdException.Invalid("Unsupported $label")
    private fun Orientation.wire() = if (this == Orientation.PORTRAIT) "portrait" else "landscape"
    private fun Alignment.wire() = when (this) { Alignment.LEFT -> "left"; Alignment.CENTER -> "center"; Alignment.RIGHT -> "right" }
    private fun QrErrorCorrection.wire() = when (this) { QrErrorCorrection.AUTO -> "auto"; QrErrorCorrection.LOW -> "low"; QrErrorCorrection.MEDIUM -> "medium"; QrErrorCorrection.QUARTILE -> "quartile"; QrErrorCorrection.HIGH -> "high" }
    private class NodeCounter(private val policy: TdResourcePolicy) {
        private var blocks = 0
        private var inlines = 0
        fun block(depth: Int) { if (depth > policy.maxNesting) throw TdException.Limit("Document nesting exceeds policy", "TD125"); blocks++; if (blocks > policy.maxBlocks) throw TdException.Limit("Block count exceeds policy", "TD125") }
        fun inline(depth: Int) { if (depth > policy.maxNesting) throw TdException.Limit("Document nesting exceeds policy", "TD125"); inlines++; if (inlines > policy.maxInlineNodes) throw TdException.Limit("Inline node count exceeds policy", "TD125") }
    }
}
