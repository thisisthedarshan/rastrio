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

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TdContractCoverageTest {
    private val minimalManifest = """{"format":"rastrio-td","containerVersion":1,"documentSchemaVersion":1,"encoding":"json","assets":[]}"""
    private val minimalDocument = """{"schemaVersion":1,"metadata":{},"layout":{"orientation":"portrait"},"blocks":[]}"""
    private val embeddedDocument = minimalDocument.replace("\"blocks\":[]", "\"blocks\":[{\"type\":\"image\",\"asset\":{\"kind\":\"embedded\",\"assetId\":\"a\"},\"alignment\":\"left\",\"sizing\":{\"mode\":\"auto\"}}]")

    @Test fun highlyCompressedArchiveHitsResidentBudgetBeforeExpandedLimit() {
        val archive = ByteArrayOutputStream().also { bytes ->
            ZipOutputStream(bytes).use { zip ->
                for (index in 0 until 8) {
                    zip.putNextEntry(ZipEntry("assets/unused-$index"))
                    val zeros = ByteArray(8192)
                    repeat(3968) { zip.write(zeros) } // 31 MiB per entry; 248 MiB total.
                    zip.closeEntry()
                }
            }
        }.toByteArray()
        assertTrue(archive.size < 1024 * 1024)
        assertEquals("TD120", assertFailsWith<TdException.Limit> {
            TdArchive.load(archive)
        }.diagnostic.code)
    }

    @Test fun multipleAssetsAndActualManifestMetadata() {
        val document = ThermalDocument(blocks = listOf(
            Image(EmbeddedAssetReference("a"), Alignment.LEFT, AutoSizing),
            Image(EmbeddedAssetReference("b"), Alignment.RIGHT, FitWidthSizing),
        ))
        val assets = listOf(
            EmbeddedAsset("b", "assets/b.png", "image/png", byteArrayOf(4, 5)),
            EmbeddedAsset("a", "assets/a.png", "image/png", byteArrayOf(1, 2, 3)),
        )
        val archive = TdArchive.save(document, assets)
        val loaded = TdArchive.load(archive)
        assertEquals(document, loaded.document)
        assertEquals(setOf("a", "b"), loaded.assets.map { it.id }.toSet())
        assets.forEach { asset -> assertContentEquals(asset.bytes, loaded.assets.single { it.id == asset.id }.bytes) }
        val manifest = entryText(archive, "manifest.json")
        assertTrue(manifest.indexOf("assets/a.png") < manifest.indexOf("assets/b.png"))
        assertTrue(manifest.contains("\"byteSize\":3"))
        assertTrue(Regex("\"sha256\":\"[0-9a-f]{64}\"").findAll(manifest).count() == 2)
    }

    @Test fun assetAndSourceInventoryRejectsInconsistency() {
        val a = asset("a", "assets/a.png", 3)
        val b = asset("b", "assets/b.png", 1)
        val content = "assets/a.png" to byteArrayOf(1, 2, 3)
        assertFailsWith<TdException> { TdArchive.load(zip(manifest(a), embeddedDocument)) } // missing asset entry
        assertFailsWith<TdException> { TdArchive.load(zip(minimalManifest, embeddedDocument)) } // missing descriptor
        assertFailsWith<TdException> { TdArchive.load(zip(manifest(a.replace("\"byteSize\":3", "\"byteSize\":2")), embeddedDocument, content)) }
        assertFailsWith<TdException> { TdArchive.load(zip(manifest(a.replace("\"assets/a.png\"", "\"source/a.png\"")), embeddedDocument, content)) }
        assertFailsWith<TdException> { TdArchive.load(zip(manifest(a.replace("\"image/png\"", "\"invalid media\"")), embeddedDocument, content)) }
        assertFailsWith<TdException> { TdArchive.load(zip(manifest(a.replace("\"byteSize\":3", "\"byteSize\":3,\"sha256\":\"XYZ\"")), embeddedDocument, content)) }
        assertFailsWith<TdException> { TdArchive.load(zip(manifest("$a,${asset("a", "assets/b.png", 1)}"), embeddedDocument, content, "assets/b.png" to byteArrayOf(4))) }
        assertFailsWith<TdException> { TdArchive.load(zip(manifest("$a,${asset("b", "assets/a.png", 3)}"), embeddedDocument, content)) }
        assertFailsWith<TdException> { TdArchive.load(zip(manifest(a), embeddedDocument, "assets/a.png" to byteArrayOf(1, 2))) }
        assertFailsWith<TdException.Limit> { TdArchive.load(zip(manifest(a), embeddedDocument, content), TdResourcePolicy(maxAssetBytes = 2)) }
        assertFailsWith<TdException> { TdArchive.load(zip(manifest(a), embeddedDocument, content, "assets/undeclared" to byteArrayOf(1))) }

        val sourceManifest = minimalManifest.dropLast(1) + ",\"source\":{\"path\":\"source/original.md\",\"mediaType\":\"text/markdown\",\"byteSize\":4}}"
        assertFailsWith<TdException> { TdArchive.load(zip(sourceManifest, minimalDocument)) }
        assertFailsWith<TdException> { TdArchive.load(zip(sourceManifest, minimalDocument, "source/original.md" to byteArrayOf(1, 2, 3))) }
        val sourceWithWrongHash = sourceManifest.replace("\"byteSize\":4", "\"byteSize\":4,\"sha256\":\"${"0".repeat(64)}\"")
        assertFailsWith<TdException> { TdArchive.load(zip(sourceWithWrongHash, minimalDocument, "source/original.md" to byteArrayOf(1, 2, 3, 4))) }
        assertFailsWith<TdException> { TdArchive.load(zip(sourceManifest, minimalDocument, "source/original.md" to byteArrayOf(1, 2, 3, 4)), TdResourcePolicy(maxSourceBytes = 3)) }
        assertFailsWith<TdException.Limit> { TdArchive.save(ThermalDocument(), sourceRetention = SourceRetention.Include(RetainedSource("source/original.md", "text/markdown", byteArrayOf(1, 2, 3, 4))), policy = TdResourcePolicy(maxSourceBytes = 3)) }
        assertFailsWith<TdException.Limit> { TdArchive.save(ThermalDocument(), assets = listOf(EmbeddedAsset("a", "assets/a.png", "image/png", byteArrayOf(1, 2, 3))), policy = TdResourcePolicy(maxAssetBytes = 2)) }
        assertEquals("TD121", assertFailsWith<TdException.Limit> {
            TdArchive.save(ThermalDocument(), assets = listOf(
                EmbeddedAsset("a", "assets/a.png", "image/png", byteArrayOf(1, 2, 3)),
                EmbeddedAsset("b", "assets/b.png", "image/png", byteArrayOf(4, 5, 6)),
            ), policy = TdResourcePolicy(maxExpandedBytes = 5))
        }.diagnostic.code)
        assertFailsWith<TdException.Limit> { TdArchive.load(zip(manifest("$a,$b"), minimalDocument, content, "assets/b.png" to byteArrayOf(4)), TdResourcePolicy(maxAssets = 1)) }
    }

    @Test fun malformedSchemaAndReaderBudgets() {
        val invalidDocuments = listOf(
            minimalDocument.replace("\"orientation\":\"portrait\"", "\"orientation\":\"diagonal\""),
            minimalDocument.replace("\"orientation\":\"portrait\"", "\"orientation\":12"),
            minimalDocument.replace("\"blocks\":[]", "\"blocks\":[{\"type\":\"quote\",\"blocks\":[]}]"),
            minimalDocument.replace("\"blocks\":[]", "\"blocks\":[{\"type\":\"paragraph\",\"alignment\":\"diagonal\",\"content\":[]}]"),
            minimalDocument.replace("\"blocks\":[]", "\"blocks\":[{\"type\":\"table\",\"columns\":[{\"alignment\":\"left\"}],\"header\":[],\"rows\":[]}]"),
            minimalDocument.replace("\"layout\":{\"orientation\":\"portrait\"}", "\"layout\":{\"orientation\":\"portrait\",\"maxWidth\":{\"value\":0,\"unit\":\"mm\"}}"),
            minimalDocument.replace("\"blocks\":[]", "\"blocks\":[{\"type\":\"qrCode\",\"payload\":\"x\",\"alignment\":\"left\",\"errorCorrection\":\"future\"}]"),
            minimalDocument.replace("\"blocks\":[]", "\"blocks\":[{\"type\":\"image\",\"asset\":{\"kind\":\"external\",\"uri\":\"x\"},\"alignment\":\"left\",\"sizing\":{\"mode\":\"future\"}}]"),
            minimalDocument.dropLast(1),
        )
        invalidDocuments.forEach { assertFailsWith<TdException> { TdArchive.load(zip(minimalManifest, it)) } }
        assertFailsWith<TdException.Limit> { TdArchive.load(zip(minimalManifest, minimalDocument), TdResourcePolicy(maxJsonDepth = 1)) }
        assertFailsWith<TdException.Limit> { TdArchive.load(zip(minimalManifest, minimalDocument), TdResourcePolicy(maxJsonTokens = 4)) }
        assertFailsWith<TdException.Limit> { TdArchive.load(zip(minimalManifest, minimalDocument), TdResourcePolicy(maxPathSegments = 0)) }
        assertFailsWith<TdException.Limit> { TdArchive.save(ThermalDocument(blocks = listOf(Paragraph(Alignment.LEFT, listOf(Text("x"))))), policy = TdResourcePolicy(maxInlineNodes = 0)) }
        assertFailsWith<TdException.Limit> { TdArchive.save(ThermalDocument(blocks = listOf(Table(listOf(TableColumn(Alignment.LEFT)), listOf(TableCell(emptyList())), emptyList()))), policy = TdResourcePolicy(maxTableColumns = 0)) }
    }

    @Test fun falseZipSizesAndTruncatedEntryFailWithinBudget() {
        val ordinary = zip(minimalManifest, minimalDocument)
        val falseSize = ordinary.copyOf()
        val central = centralOffset(falseSize)
        falseSize[central + 24] = 1
        falseSize[central + 25] = 0
        falseSize[central + 26] = 0
        falseSize[central + 27] = 0
        assertFailsWith<TdException.Limit> { TdArchive.load(falseSize, TdResourcePolicy(maxManifestBytes = 90)) }

        val truncatedEntry = ordinary.copyOf()
        val start = centralOffset(truncatedEntry)
        truncatedEntry[start + 20] = 1 // central compressed size; only one byte of deflate input remains
        truncatedEntry[start + 21] = 0
        truncatedEntry[start + 22] = 0
        truncatedEntry[start + 23] = 0
        assertFailsWith<TdException> { TdArchive.load(truncatedEntry) }
    }

    @Test fun cancellationAndDiagnosticCodesAreObservable() {
        val bytes = zip(minimalManifest, minimalDocument)
        assertEquals("TD100", assertFailsWith<TdException.Invalid> { TdArchive.load(byteArrayOf(1, 2, 3)) }.diagnostic.code)
        assertEquals("TD120", assertFailsWith<TdException.Limit> { TdArchive.load(bytes, TdResourcePolicy(maxArchiveBytes = 1)) }.diagnostic.code)
        assertEquals("TD121", assertFailsWith<TdException.Limit> { TdArchive.load(bytes, TdResourcePolicy(maxExpandedBytes = 10)) }.diagnostic.code)
        assertEquals("TD122", assertFailsWith<TdException.Limit> { TdArchive.load(bytes, TdResourcePolicy(maxEntries = 1)) }.diagnostic.code)
        assertEquals("TD123", assertFailsWith<TdException.Limit> { TdArchive.save(ThermalDocument(), sourceRetention = SourceRetention.Include(RetainedSource("source/x", "text/plain", byteArrayOf(1, 2))), policy = TdResourcePolicy(maxSourceBytes = 1)) }.diagnostic.code)
        assertEquals("TD124", assertFailsWith<TdException.Limit> { TdArchive.load(bytes, TdResourcePolicy(maxManifestBytes = 10)) }.diagnostic.code)
        assertEquals("TD125", assertFailsWith<TdException.Limit> { TdArchive.save(ThermalDocument(blocks = listOf(Separator)), policy = TdResourcePolicy(maxBlocks = 0)) }.diagnostic.code)
        assertEquals("TD126", assertFailsWith<TdException.Limit> { TdArchive.load(zip(minimalManifest, minimalDocument, "assets/a" to byteArrayOf(1)), TdResourcePolicy(maxPathSegments = 1)) }.diagnostic.code)
        assertEquals("TD101", assertFailsWith<TdException.Invalid> { TdArchive.save(ThermalDocument(blocks = listOf(Image(EmbeddedAssetReference("missing"), Alignment.LEFT, AutoSizing)))) }.diagnostic.code)
        assertEquals("TD102", assertFailsWith<TdException.Cancelled> { TdArchive.load(bytes, isCancelled = { true }) }.diagnostic.code)
        var polls = 0
        assertFailsWith<TdException.Cancelled> { TdArchive.load(bytes, isCancelled = { ++polls >= 3 }) }
        assertEquals(ThermalDocument(), TdArchive.load(bytes).document)
    }

    private fun asset(id: String, path: String, size: Int) = """{"id":"$id","path":"$path","mediaType":"image/png","byteSize":$size}"""
    private fun manifest(assets: String) = minimalManifest.replace("\"assets\":[]", "\"assets\":[$assets]")
    private fun zip(manifest: String, document: String, vararg files: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            (listOf("manifest.json" to manifest.encodeToByteArray(), "document.json" to document.encodeToByteArray()) + files).forEach { (path, bytes) ->
                zip.putNextEntry(ZipEntry(path)); zip.write(bytes); zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
    private fun entryText(archive: ByteArray, wanted: String): String {
        ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == wanted) return zip.readBytes().decodeToString()
            }
        }
        error("Missing test entry")
    }
    private fun centralOffset(bytes: ByteArray): Int {
        for (index in 0 until bytes.size - 4) {
            if (bytes[index] == 0x50.toByte() && bytes[index + 1] == 0x4b.toByte() && bytes[index + 2] == 1.toByte() && bytes[index + 3] == 2.toByte()) return index
        }
        error("Missing test central entry")
    }
}
