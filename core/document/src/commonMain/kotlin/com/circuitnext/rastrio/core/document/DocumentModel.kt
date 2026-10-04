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

data class ThermalDocument(
    val schemaVersion: Int = 1,
    val metadata: DocumentMetadata = DocumentMetadata(),
    val layout: DocumentLayout = DocumentLayout(),
    val blocks: List<DocumentBlock> = emptyList(),
)

data class DocumentMetadata(val title: String? = null)

data class DocumentLayout(
    val orientation: Orientation = Orientation.PORTRAIT,
    val maxWidth: Length? = null,
)

enum class Orientation { PORTRAIT, LANDSCAPE }
enum class Alignment { LEFT, CENTER, RIGHT }
enum class LengthUnit { MM }
data class Length(val value: Double, val unit: LengthUnit = LengthUnit.MM)

sealed interface InlineContent
data class Text(val text: String) : InlineContent
data class Strong(val children: List<InlineContent>) : InlineContent
data class Emphasis(val children: List<InlineContent>) : InlineContent
data class Strike(val children: List<InlineContent>) : InlineContent
data class InlineCode(val text: String) : InlineContent
data class Link(val destination: String, val children: List<InlineContent>) : InlineContent
data object LineBreak : InlineContent

sealed interface DocumentBlock
data class Paragraph(val alignment: Alignment, val content: List<InlineContent>) : DocumentBlock
data class Heading(val level: Int, val alignment: Alignment, val content: List<InlineContent>) : DocumentBlock
data class ListItem(val blocks: List<DocumentBlock>)
data class UnorderedList(val items: List<ListItem>) : DocumentBlock
data class OrderedList(val start: Long, val items: List<ListItem>) : DocumentBlock
data class ChecklistItem(val checked: Boolean, val blocks: List<DocumentBlock>)
data class Checklist(val items: List<ChecklistItem>) : DocumentBlock
data class Quote(val blocks: List<DocumentBlock>) : DocumentBlock
data class CodeBlock(val text: String, val language: String? = null) : DocumentBlock
data object Separator : DocumentBlock

sealed interface AssetReference
data class EmbeddedAssetReference(val assetId: String) : AssetReference
data class ExternalAssetReference(val uri: String) : AssetReference
sealed interface ImageSizing
data object AutoSizing : ImageSizing
data object FitWidthSizing : ImageSizing
data class RequestedWidthSizing(val width: Length) : ImageSizing
data class Image(
    val asset: AssetReference,
    val alignment: Alignment,
    val sizing: ImageSizing,
    val altText: String? = null,
) : DocumentBlock

data class TableColumn(val alignment: Alignment)
data class TableCell(val content: List<InlineContent>, val alignment: Alignment? = null)
data class Table(
    val columns: List<TableColumn>,
    val header: List<TableCell>,
    val rows: List<List<TableCell>>,
) : DocumentBlock

enum class QrErrorCorrection { AUTO, LOW, MEDIUM, QUARTILE, HIGH }
data class QrCode(
    val payload: String,
    val alignment: Alignment,
    val errorCorrection: QrErrorCorrection,
    val requestedSize: Length? = null,
) : DocumentBlock

data class AssetMetadata(
    val id: String,
    val path: String,
    val mediaType: String,
    val byteSize: Long,
    val sha256: String? = null,
)

/** Resource limits are trusted application policy and are never loaded from a .td file. */
data class TdResourcePolicy(
    val maxArchiveBytes: Long = 64L * 1024 * 1024,
    val maxExpandedBytes: Long = 256L * 1024 * 1024,
    val maxResidentArchiveBytes: Long = 128L * 1024 * 1024,
    val maxEntries: Int = 1024,
    val maxEntryBytes: Long = 64L * 1024 * 1024,
    val maxManifestBytes: Int = 64 * 1024,
    val maxDocumentBytes: Int = 16 * 1024 * 1024,
    val maxAssetBytes: Long = 32L * 1024 * 1024,
    val maxAssets: Int = 512,
    val maxSourceBytes: Long = 8L * 1024 * 1024,
    val maxPathBytes: Int = 1024,
    val maxPathSegments: Int = 64,
    val maxJsonDepth: Int = 64,
    val maxJsonTokens: Int = 1_000_000,
    val maxStringBytes: Int = 64 * 1024,
    val maxBlocks: Int = 100_000,
    val maxInlineNodes: Int = 500_000,
    val maxNesting: Int = 64,
    val maxTableRows: Int = 20_000,
    val maxTableColumns: Int = 256,
    val maxTableCells: Int = 250_000,
    val maxLogicalWidthMm: Double = 1_000.0,
)

data class TdDiagnostic(val code: String, val message: String)

sealed class TdException(val diagnostic: TdDiagnostic) : IllegalArgumentException(diagnostic.message) {
    class Invalid(message: String, code: String = "TD100") : TdException(TdDiagnostic(code, message))
    class Cancelled : TdException(TdDiagnostic("TD102", ".td read cancelled"))
    class Limit(message: String, code: String = "TD120") : TdException(TdDiagnostic(code, message))
}
