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

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class PortableArchiveTest {
    @Test fun checklistEmptyBlockArraysAreRejectedByReaderAndWriter() {
        val policy = TdResourcePolicy()
        val privateTitle = "private-checklist-title"
        val manifest = """{"format":"rastrio-td","containerVersion":1,"documentSchemaVersion":1,"encoding":"json","assets":[]}"""
        for (checked in listOf(false, true)) {
            val json = """{"schemaVersion":1,"metadata":{"title":"$privateTitle"},"layout":{"orientation":"portrait"},"blocks":[{"type":"checklist","items":[{"checked":$checked,"blocks":[]}]}]}"""
            val archive = zipWrite(listOf(ArchiveEntry("manifest.json", manifest.encodeToByteArray()),
                ArchiveEntry("document.json", json.encodeToByteArray())), policy)
            val readerFailure = assertFailsWith<TdException.Invalid> { TdArchive.load(archive) }
            val writerFailure = assertFailsWith<TdException.Invalid> { TdArchive.save(ThermalDocument(
                metadata = DocumentMetadata(privateTitle), blocks = listOf(Checklist(listOf(ChecklistItem(checked, emptyList())))))) }
            for (failure in listOf(readerFailure, writerFailure)) {
                assertEquals("TD100", failure.diagnostic.code)
                assertFalse(failure.diagnostic.message.contains(privateTitle))
            }
        }
    }

    @Test fun checklistItemsContainingEmptyParagraphsRoundTrip() {
        val paragraph = Paragraph(Alignment.LEFT, emptyList())
        val document = ThermalDocument(blocks = listOf(Checklist(listOf(
            ChecklistItem(false, listOf(paragraph)), ChecklistItem(true, listOf(paragraph))))))
        assertEquals(document, TdArchive.load(TdArchive.save(document)).document)
    }

    @Test fun storedArchiveRoundTripsOnEveryCoreTarget() {
        val document = ThermalDocument(
            metadata = DocumentMetadata("portable"),
            layout = DocumentLayout(Orientation.LANDSCAPE, Length(180.0)),
            blocks = listOf(Paragraph(Alignment.LEFT, listOf(Text("Hello"), Strong(listOf(Text("world"))), LineBreak))),
        )
        val asset = EmbeddedAsset("image", "assets/logo.png", "image/png", byteArrayOf(1, 2, 3))
        val source = RetainedSource("source/original.md", "text/markdown", "# Hello".encodeToByteArray())
        val saved = TdArchive.save(document, listOf(asset), SourceRetention.Include(source))
        val loaded = TdArchive.load(saved)
        assertEquals(document, loaded.document)
        assertContentEquals(asset.bytes, loaded.assets.single().bytes)
        assertContentEquals(source.bytes, loaded.source!!.bytes)
        assertEquals(loaded, TdArchive.load(TdArchive.save(loaded.document, loaded.assets, SourceRetention.Include(loaded.source!!))))
    }

    @Test fun ordinaryDeflatedZipLoadsOnEveryCoreTarget() {
        // Independent ZIP_DEFLATED fixture made with Python's zipfile, with fixed entry timestamps.
        val hex = "504b03041400000008000000210080c163f654000000640000000d0000006d616e69666573742e6a736f6e4dc9310a85400c05c0bbbc7a2d6cf71ac26fc422ecc66f844d20899578775ba79d1bbbf9a0448553a48b4dd951d04c9344d97fec21a6a87341b7760dd65cdac183bec3daac8bfe517186290a28823350d7ed7901504b03041400000008000000210066a180734c000000510000000d000000646f63756d656e742e6a736f6e1dca310e80200c05d0bbfc99c5b50771310e059b48046a4a1d0ce1ee26bef90df4744ae555ac676da025a08af3c1cea031030abffa3868402d4b73f6ffe15673e3ec9801b168ba3a68dbe707504b010214031400000008000000210080c163f654000000640000000d00000000000000000000008001000000006d616e69666573742e6a736f6e504b010214031400000008000000210066a180734c000000510000000d000000000000000000000080017f000000646f63756d656e742e6a736f6e504b0506000000000200020076000000f60000000000"
        val bytes = ByteArray(hex.length / 2) { index -> hex.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
        assertEquals(ThermalDocument(), TdArchive.load(bytes).document)
        assertFailsWith<TdException.Limit> { TdArchive.load(bytes, TdResourcePolicy(maxExpandedBytes = 10)) }
        val metadataBytes = 2L * (13L * 6 + 256)
        val exactResidentBytes = bytes.size + metadataBytes + 100 + 81 + 17L * 1024 * 1024
        assertEquals(ThermalDocument(), TdArchive.load(bytes, TdResourcePolicy(maxResidentArchiveBytes = exactResidentBytes)).document)
        assertEquals("TD120", assertFailsWith<TdException.Limit> {
            TdArchive.load(bytes, TdResourcePolicy(maxResidentArchiveBytes = exactResidentBytes - 1))
        }.diagnostic.code)
    }

    @Test fun storedArchiveResidentBudgetHasExactBoundaryOnEveryCoreTarget() {
        val manifest = """{"format":"rastrio-td","containerVersion":1,"documentSchemaVersion":1,"encoding":"json","assets":[]}"""
        val document = """{"schemaVersion":1,"metadata":{},"layout":{"orientation":"portrait"},"blocks":[]}"""
        val archive = zipWrite(listOf(
            ArchiveEntry("manifest.json", manifest.encodeToByteArray()),
            ArchiveEntry("document.json", document.encodeToByteArray()),
        ), TdResourcePolicy())
        val metadataBytes = 2L * (13L * 6 + 256)
        val exactResidentBytes = archive.size + metadataBytes + manifest.encodeToByteArray().size + document.encodeToByteArray().size
        assertEquals(ThermalDocument(), TdArchive.load(archive, TdResourcePolicy(maxResidentArchiveBytes = exactResidentBytes)).document)
        assertEquals("TD120", assertFailsWith<TdException.Limit> {
            TdArchive.load(archive, TdResourcePolicy(maxResidentArchiveBytes = exactResidentBytes - 1))
        }.diagnostic.code)
    }

    @Test fun undeclaredEntryCountsTowardResidentBudgetOnEveryCoreTarget() {
        val manifest = """{"format":"rastrio-td","containerVersion":1,"documentSchemaVersion":1,"encoding":"json","assets":[]}"""
        val document = """{"schemaVersion":1,"metadata":{},"layout":{"orientation":"portrait"},"blocks":[]}"""
        val archive = zipWrite(listOf(
            ArchiveEntry("manifest.json", manifest.encodeToByteArray()),
            ArchiveEntry("document.json", document.encodeToByteArray()),
            ArchiveEntry("assets/unused", ByteArray(8)),
        ), TdResourcePolicy())
        val metadataBytes = 2L * (13L * 6 + 256) + 13L * 6 + 256
        val budgetWithoutLastByte = archive.size + metadataBytes + manifest.encodeToByteArray().size + document.encodeToByteArray().size + 7
        assertEquals("TD120", assertFailsWith<TdException.Limit> {
            TdArchive.load(archive, TdResourcePolicy(maxResidentArchiveBytes = budgetWithoutLastByte))
        }.diagnostic.code)
    }

    @Test fun strictReaderAndPathsApplyOnEveryCoreTarget() {
        val policy = TdResourcePolicy()
        val manifest = """{"format":"rastrio-td","containerVersion":1,"documentSchemaVersion":1,"encoding":"json","assets":[]}"""
        val document = """{"schemaVersion":1,"metadata":{},"layout":{"orientation":"portrait"},"blocks":[]}"""
        fun archive(manifestJson: String, documentJson: String, path: String? = null): ByteArray = zipWrite(
            listOf(ArchiveEntry("manifest.json", manifestJson.encodeToByteArray()), ArchiveEntry("document.json", documentJson.encodeToByteArray())) +
                (path?.let { listOf(ArchiveEntry(it, byteArrayOf(1))) } ?: emptyList()),
            policy,
        )
        assertFailsWith<TdException> { TdArchive.load(archive(manifest.replace("\"assets\":[]", "\"assets\":[],\"assets\":[]"), document)) }
        assertFailsWith<TdException> { TdArchive.load(archive(manifest, document.replace("\"blocks\":[]", "\"blocks\":[],\"future\":1"))) }
        assertFailsWith<TdException> { TdArchive.load(archive(manifest, document, "../escape")) }
    }
}
