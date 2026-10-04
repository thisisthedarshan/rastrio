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

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TdArchiveTest {
    private val minimalManifest = """{"format":"rastrio-td","containerVersion":1,"documentSchemaVersion":1,"encoding":"json","assets":[]}"""
    private val minimalDocument = """{"schemaVersion":1,"metadata":{},"layout":{"orientation":"portrait"},"blocks":[]}"""

    @Test fun minimalRoundTrip() {
        val original = ThermalDocument()
        val archive = TdArchive.save(original)
        val loaded = TdArchive.load(archive)
        assertEquals(original, loaded.document)
        assertEquals(emptyList(), loaded.assets)
        assertNull(loaded.source)
        assertContentEquals(archive, TdArchive.save(loaded.document))
    }

    @Test fun everyBlockInlineAndLayoutRoundTrip() {
        val allInline = listOf(
            Text("plain"), Strong(listOf(Text("bold"))), Emphasis(listOf(Text("em"))),
            Strike(listOf(Text("strike"))), InlineCode("code"),
            Link("https://example.com", listOf(Text("link"))), LineBreak,
        )
        val paragraph = Paragraph(Alignment.LEFT, allInline)
        val original = ThermalDocument(
            metadata = DocumentMetadata("Receipt title"),
            layout = DocumentLayout(Orientation.LANDSCAPE, Length(180.0)),
            blocks = listOf(
                paragraph,
                Heading(2, Alignment.CENTER, allInline),
                UnorderedList(listOf(ListItem(listOf(paragraph)))),
                OrderedList(3, listOf(ListItem(listOf(paragraph)))),
                Checklist(listOf(ChecklistItem(true, listOf(paragraph)))),
                Quote(listOf(paragraph)),
                CodeBlock("fun main() {}", "kotlin"),
                Separator,
                Image(EmbeddedAssetReference("image-1"), Alignment.CENTER, RequestedWidthSizing(Length(40.0)), "logo"),
                Image(ExternalAssetReference("https://example.com/logo.png"), Alignment.RIGHT, AutoSizing),
                Image(ExternalAssetReference("file:unresolved"), Alignment.LEFT, FitWidthSizing),
                Table(
                    columns = listOf(TableColumn(Alignment.LEFT), TableColumn(Alignment.RIGHT)),
                    header = listOf(TableCell(listOf(Text("Item"))), TableCell(listOf(Text("Qty")), Alignment.CENTER)),
                    rows = listOf(listOf(TableCell(allInline), TableCell(listOf(Text("2"))))),
                ),
                QrCode("hello", Alignment.CENTER, QrErrorCorrection.MEDIUM, Length(30.0)),
            ),
        )
        val asset = EmbeddedAsset("image-1", "assets/image-1.png", "image/png", byteArrayOf(1, 2, 3))
        val source = RetainedSource("source/original.md", "text/markdown", "# Receipt".encodeToByteArray())
        val loaded = TdArchive.load(TdArchive.save(original, listOf(asset), SourceRetention.Include(source)))
        assertEquals(original, loaded.document)
        assertEquals(asset.id, loaded.assets.single().id)
        assertContentEquals(asset.bytes, loaded.assets.single().bytes)
        assertContentEquals(source.bytes, loaded.source!!.bytes)
        assertNull(TdArchive.load(TdArchive.save(original, listOf(asset), SourceRetention.None)).source)
    }

    @Test fun versionsAndRequiredEntries() {
        assertFailsWith<TdException> { TdArchive.load(byteArrayOf(0, 1, 2)) }
        assertFailsWith<TdException> { TdArchive.load(zipOf("document.json" to minimalDocument)) }
        assertFailsWith<TdException> { TdArchive.load(zipOf("manifest.json" to minimalManifest)) }
        invalidManifest(minimalManifest.replace("rastrio-td", "other"))
        invalidManifest(minimalManifest.replace("\"containerVersion\":1", "\"containerVersion\":2"))
        invalidManifest(minimalManifest.replace("\"documentSchemaVersion\":1", "\"documentSchemaVersion\":2"))
        invalidDocument(minimalDocument.replace("\"schemaVersion\":1", "\"schemaVersion\":2"))
        assertFailsWith<TdException> { TdArchive.load(zipOf("manifest.json" to minimalManifest, "manifest.json/" to "", "document.json" to minimalDocument)) }
        val truncated = zipOf("manifest.json" to minimalManifest, "document.json" to minimalDocument).copyOfRange(0, 50)
        assertFailsWith<TdException> { TdArchive.load(truncated) }
    }

    @Test fun strictJsonProductionReaderPath() {
        val invalidManifests = listOf(
            minimalManifest.replace("\"format\":", "\"format\":\"rastrio-td\",\"format\":"),
            minimalManifest.replace("\"assets\":[]", "\"assets\":[],\"future\":1"),
            minimalManifest.replace("\"containerVersion\":1", "\"containerVersion\":01"),
            minimalManifest.replace("\"containerVersion\":1", "\"containerVersion\":1e"),
            minimalManifest.replace("\"containerVersion\":1", "\"containerVersion\":1."),
            minimalManifest.replace("\"assets\":[]", "\"assets\":[],"),
            minimalManifest.replace("\"format\":", "/*comment*/\"format\":"),
            minimalManifest.replace("\"format\"", "'format'"),
            minimalManifest.replace("\"format\"", "format"),
            minimalManifest.replace("\"containerVersion\":1", "\"containerVersion\":NaN"),
            minimalManifest.replace("\"containerVersion\":1", "\"containerVersion\":Infinity"),
            minimalManifest.replace("\"containerVersion\":1", "\"containerVersion\":-Infinity"),
            minimalManifest.replace("\"assets\":[]", "\"assets\":null"),
        )
        invalidManifests.forEach(::invalidManifest)
        invalidDocument(minimalDocument.replace("\"metadata\":{}", "\"metadata\":{\"title\":\"a\",\"title\":\"b\"}"))
        invalidDocument(minimalDocument.replace("\"layout\":{\"orientation\":\"portrait\"}", "\"layout\":{\"orientation\":\"portrait\",\"future\":1}"))
        invalidDocument(minimalDocument.replace("\"blocks\":[]", "\"blocks\":[{\"type\":\"future\"}]"))
        invalidDocument(minimalDocument.replace("\"blocks\":[]", "\"blocks\":[{\"type\":\"paragraph\",\"alignment\":\"left\",\"content\":[{\"type\":\"future\"}]}]"))
        invalidDocument(minimalDocument.replace("\"blocks\":[]", "\"blocks\":[{\"type\":\"separator\",\"extra\":true}]"))
        invalidDocument(minimalDocument.replace("\"orientation\":\"portrait\"", "\"orientation\":null"))
        invalidDocument(minimalDocument.replace("\"orientation\":\"portrait\"", "\"orientation\":\"landscape\" //comment"))
        invalidDocument(minimalDocument.replace("\"title\"", "\"title\"" ) + " trailing")
    }

    @Test fun malformedUtf8AndUnicodeEscapes() {
        assertFailsWith<TdException> {
            TdArchive.load(zipBytes("manifest.json" to byteArrayOf(0xc3.toByte(), 0x28), "document.json" to minimalDocument.encodeToByteArray()))
        }
        invalidDocument(minimalDocument.replace("\"metadata\":{}", "\"metadata\":{\"title\":\"\\ud800\"}"))
        assertFailsWith<TdException> { TdArchive.save(ThermalDocument(metadata = DocumentMetadata("\ud800"))) }
    }

    @Test fun unsafeArchivePathsAndUndeclaredFiles() {
        val invalid = listOf("../x", "foo/../../x", "/x", "C:/x", "C:\\x", "\\\\server\\share\\x", "foo\\..\\x", "foo//bar", "foo/./bar", "outside.txt")
        invalid.forEach { path ->
            assertFailsWith<TdException> { TdArchive.load(zipOf("manifest.json" to minimalManifest, "document.json" to minimalDocument, path to "x")) }
        }
        assertFailsWith<TdException> { TdArchive.load(zipOf("manifest.json" to minimalManifest, "document.json" to minimalDocument, "assets/a" to "x", "assets/a/" to "")) }
    }

    @Test fun encryptedAndSpecialZipEntriesAreRejected() {
        val ordinary = zipOf("manifest.json" to minimalManifest, "document.json" to minimalDocument)
        val encrypted = ordinary.copyOf()
        val central = centralEntryOffset(encrypted)
        encrypted[central + 8] = (encrypted[central + 8].toInt() or 1).toByte()
        assertFailsWith<TdException> { TdArchive.load(encrypted) }

        val symlink = ordinary.copyOf()
        val linkCentral = centralEntryOffset(symlink)
        symlink[linkCentral + 5] = 3
        symlink[linkCentral + 40] = 0
        symlink[linkCentral + 41] = 0xa0.toByte()
        assertFailsWith<TdException> { TdArchive.load(symlink) }
    }

    @Test fun assetsAreCrossCheckedAndExternalReferencesStayOffline() {
        val assetBytes = byteArrayOf(1, 2, 3)
        val asset = EmbeddedAsset("a", "assets/a.png", "image/png", assetBytes)
        val document = ThermalDocument(blocks = listOf(Image(EmbeddedAssetReference("a"), Alignment.LEFT, AutoSizing)))
        assertFailsWith<TdException> { TdArchive.save(document) }
        val archive = TdArchive.save(document, listOf(asset))
        assertEquals(document, TdArchive.load(archive).document)
        val manifest = """{"format":"rastrio-td","containerVersion":1,"documentSchemaVersion":1,"encoding":"json","assets":[{"id":"a","path":"assets/a.png","mediaType":"image/png","byteSize":3,"sha256":"${"0".repeat(64)}"}]}"""
        assertFailsWith<TdException> { TdArchive.load(zipBytes("manifest.json" to manifest.encodeToByteArray(), "document.json" to TdJson.encodeDocument(document, TdResourcePolicy()), "assets/a.png" to assetBytes)) }
        assertFailsWith<TdException> { TdArchive.load(zipBytes("manifest.json" to manifest.replace("\"byteSize\":3", "\"byteSize\":2").encodeToByteArray(), "document.json" to TdJson.encodeDocument(document, TdResourcePolicy()), "assets/a.png" to assetBytes)) }
        val external = ThermalDocument(blocks = listOf(Image(ExternalAssetReference("https://example.com/x"), Alignment.LEFT, AutoSizing)))
        assertEquals(external, TdArchive.load(TdArchive.save(external)).document)
        val noHash = manifest.replace(",\"sha256\":\"${"0".repeat(64)}\"", "")
        assertEquals(document, TdArchive.load(zipBytes("manifest.json" to noHash.encodeToByteArray(), "document.json" to TdJson.encodeDocument(document, TdResourcePolicy()), "assets/a.png" to assetBytes)).document)
        assertFailsWith<TdException> { TdArchive.load(zipBytes("manifest.json" to noHash.replace("\"id\":\"a\"", "\"id\":\"a\",\"future\":1").encodeToByteArray(), "document.json" to TdJson.encodeDocument(document, TdResourcePolicy()), "assets/a.png" to assetBytes)) }
        assertFailsWith<TdException> { TdArchive.load(zipBytes("manifest.json" to noHash.replace("\"path\":\"assets/a.png\"", "\"path\":\"assets/../a.png\"").encodeToByteArray(), "document.json" to TdJson.encodeDocument(document, TdResourcePolicy()), "assets/a.png" to assetBytes)) }
        assertFailsWith<TdException> { TdArchive.load(zipBytes("manifest.json" to noHash.encodeToByteArray(), "document.json" to TdJson.encodeDocument(document, TdResourcePolicy()))) }
    }

    @Test fun resourceBoundaries() {
        val minimal = zipOf("manifest.json" to minimalManifest, "document.json" to minimalDocument)
        assertEquals(ThermalDocument(), TdArchive.load(minimal, TdResourcePolicy(maxArchiveBytes = minimal.size.toLong())).document)
        assertFailsWith<TdException.Limit> { TdArchive.load(minimal, TdResourcePolicy(maxArchiveBytes = minimal.size.toLong() - 1)) }
        assertFailsWith<TdException.Limit> { TdArchive.load(minimal, TdResourcePolicy(maxEntries = 1)) }
        assertFailsWith<TdException.Limit> { TdArchive.load(minimal, TdResourcePolicy(maxManifestBytes = minimalManifest.encodeToByteArray().size - 1)) }
        assertFailsWith<TdException.Limit> { TdArchive.load(minimal, TdResourcePolicy(maxDocumentBytes = minimalDocument.encodeToByteArray().size - 1)) }
        assertFailsWith<TdException.Limit> { TdArchive.load(minimal, TdResourcePolicy(maxExpandedBytes = 10)) }
        val exactExpanded = minimalManifest.encodeToByteArray().size + minimalDocument.encodeToByteArray().size
        assertEquals(ThermalDocument(), TdArchive.load(minimal, TdResourcePolicy(maxExpandedBytes = exactExpanded.toLong())).document)
        assertFailsWith<TdException.Limit> { TdArchive.load(minimal, TdResourcePolicy(maxExpandedBytes = exactExpanded.toLong() - 1)) }
        assertFailsWith<TdException.Limit> { TdArchive.load(zipOf("manifest.json" to minimalManifest, "document.json" to minimalDocument, "assets/bomb" to "x".repeat(500_000)), TdResourcePolicy(maxEntryBytes = 100_000)) }
        assertFailsWith<TdException> { TdArchive.load(zipOf("manifest.json" to minimalManifest, "document.json" to minimalDocument, "assets/${"a".repeat(200)}" to ""), TdResourcePolicy(maxPathBytes = 30)) }
        assertFailsWith<TdException.Limit> { TdArchive.save(ThermalDocument(blocks = listOf(Separator)), policy = TdResourcePolicy(maxBlocks = 0)) }
        assertEquals(ThermalDocument(layout = DocumentLayout(maxWidth = Length(1000.0))), TdArchive.load(TdArchive.save(ThermalDocument(layout = DocumentLayout(maxWidth = Length(1000.0))))).document)
        assertFailsWith<TdException> { TdArchive.save(ThermalDocument(layout = DocumentLayout(maxWidth = Length(1001.0)))) }
        assertFailsWith<TdException.Limit> { TdArchive.save(ThermalDocument(metadata = DocumentMetadata("x".repeat(65_537)))) }
    }

    private fun invalidManifest(manifest: String) {
        assertFailsWith<TdException> { TdArchive.load(zipOf("manifest.json" to manifest, "document.json" to minimalDocument)) }
    }
    private fun invalidDocument(document: String) {
        assertFailsWith<TdException> { TdArchive.load(zipOf("manifest.json" to minimalManifest, "document.json" to document)) }
    }
    private fun zipOf(vararg entries: Pair<String, String>): ByteArray = zipBytes(*entries.map { it.first to it.second.encodeToByteArray() }.toTypedArray())
    private fun zipBytes(vararg entries: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (path, bytes) -> zip.putNextEntry(ZipEntry(path)); zip.write(bytes); zip.closeEntry() }
        }
        return output.toByteArray()
    }

    private fun centralEntryOffset(bytes: ByteArray): Int {
        for (index in 0 until bytes.size - 4) {
            if (bytes[index] == 0x50.toByte() && bytes[index + 1] == 0x4b.toByte() && bytes[index + 2] == 1.toByte() && bytes[index + 3] == 2.toByte()) return index
        }
        error("Test ZIP has no central directory entry")
    }
}
