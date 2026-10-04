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

data class EmbeddedAsset(val id: String, val path: String, val mediaType: String, val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean = other is EmbeddedAsset && id == other.id && path == other.path &&
        mediaType == other.mediaType && bytes.contentEquals(other.bytes)
    override fun hashCode(): Int = (((id.hashCode() * 31 + path.hashCode()) * 31 + mediaType.hashCode()) * 31) + bytes.contentHashCode()
}

data class RetainedSource(val path: String, val mediaType: String, val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean = other is RetainedSource && path == other.path && mediaType == other.mediaType && bytes.contentEquals(other.bytes)
    override fun hashCode(): Int = ((path.hashCode() * 31 + mediaType.hashCode()) * 31) + bytes.contentHashCode()
}
sealed interface SourceRetention {
    data object None : SourceRetention
    data class Include(val source: RetainedSource) : SourceRetention
}
data class LoadedTd(
    val document: ThermalDocument,
    val assets: List<EmbeddedAsset>,
    val source: RetainedSource?,
)

/** Portable semantic API; the ZIP and SHA-256 primitives are implemented per target. */
object TdArchive {
    fun save(
        document: ThermalDocument,
        assets: List<EmbeddedAsset> = emptyList(),
        sourceRetention: SourceRetention = SourceRetention.None,
        policy: TdResourcePolicy = TdResourcePolicy(),
    ): ByteArray {
        TdJson.validateDocument(document, policy)
        if (assets.size > policy.maxAssets) throw TdException.Limit("Asset count exceeds policy", "TD125")
        val source = (sourceRetention as? SourceRetention.Include)?.source
        var payloadBytes = 0L
        assets.forEach { asset ->
            if (asset.bytes.size.toLong() > policy.maxAssetBytes) throw TdException.Limit("Asset exceeds policy", "TD123")
            payloadBytes = checkedAdd(payloadBytes, asset.bytes.size.toLong(), policy.maxExpandedBytes, "Archive expansion exceeds policy", "TD121")
        }
        source?.let {
            if (it.bytes.size.toLong() > policy.maxSourceBytes) throw TdException.Limit("Retained source exceeds policy", "TD123")
            payloadBytes = checkedAdd(payloadBytes, it.bytes.size.toLong(), policy.maxExpandedBytes, "Archive expansion exceeds policy", "TD121")
        }
        val descriptorsWithoutHashes = assets.map { asset ->
            AssetMetadata(asset.id, asset.path, asset.mediaType, asset.bytes.size.toLong())
        }
        val sourceWithoutHash = source?.let { SourceMetadata(it.path, it.mediaType, it.bytes.size.toLong()) }
        TdJson.validateManifest(Manifest(descriptorsWithoutHashes, sourceWithoutHash), policy)
        val descriptors = descriptorsWithoutHashes.mapIndexed { index, descriptor -> descriptor.copy(sha256 = sha256(assets[index].bytes)) }
        val sourceMetadata = sourceWithoutHash?.copy(sha256 = sha256(source!!.bytes))
        val manifest = Manifest(descriptors, sourceMetadata)
        validateReferences(document, descriptors)
        val documentBytes = TdJson.encodeDocument(document, policy)
        val manifestBytes = TdJson.encodeManifest(manifest, policy)
        if (manifestBytes.size > policy.maxManifestBytes || documentBytes.size > policy.maxDocumentBytes) throw TdException.Limit("JSON entry exceeds policy", "TD124")
        val entries = buildList {
            add(ArchiveEntry("manifest.json", manifestBytes))
            add(ArchiveEntry("document.json", documentBytes))
            assets.sortedBy { it.path }.forEach { add(ArchiveEntry(it.path, it.bytes)) }
            source?.let { add(ArchiveEntry(it.path, it.bytes)) }
        }
        if (entries.size > policy.maxEntries) throw TdException.Limit("Archive entry count exceeds policy", "TD122")
        var expanded = 0L
        entries.forEach { entry ->
            if (entry.bytes.size.toLong() > policy.maxEntryBytes) throw TdException.Limit("Archive entry exceeds policy", "TD123")
            expanded = checkedAdd(expanded, entry.bytes.size.toLong(), policy.maxExpandedBytes, "Archive expansion exceeds policy", "TD121")
        }
        return zipWrite(entries, policy)
    }

    fun load(
        bytes: ByteArray,
        policy: TdResourcePolicy = TdResourcePolicy(),
        isCancelled: () -> Boolean = { false },
    ): LoadedTd {
        if (isCancelled()) throw TdException.Cancelled()
        if (bytes.size.toLong() > policy.maxArchiveBytes) throw TdException.Limit("Archive input exceeds policy")
        val entries = zipRead(bytes, policy, isCancelled)
        if (isCancelled()) throw TdException.Cancelled()
        val files = linkedMapOf<String, ByteArray>()
        val seen = mutableSetOf<String>()
        entries.forEach { entry ->
            if (isCancelled()) throw TdException.Cancelled()
            val path = TdJson.validateArchivePath(if (entry.directory) entry.path.removeSuffix("/") else entry.path, policy)
            if (!seen.add(path)) throw TdException.Invalid("Duplicate normalized archive path")
            if (entry.directory) {
                if (path != "assets" && path != "source" && !path.startsWith("assets/") && !path.startsWith("source/")) throw TdException.Invalid("Unknown archive directory")
            } else {
                if (path != "manifest.json" && path != "document.json" && !path.startsWith("assets/") && !path.startsWith("source/")) throw TdException.Invalid("Unknown archive entry")
                files[path] = entry.bytes
            }
        }
        val manifestBytes = files["manifest.json"] ?: throw TdException.Invalid("Missing manifest.json")
        val documentBytes = files["document.json"] ?: throw TdException.Invalid("Missing document.json")
        if (manifestBytes.size > policy.maxManifestBytes || documentBytes.size > policy.maxDocumentBytes) throw TdException.Limit("JSON entry exceeds policy", "TD124")
        val manifest = TdJson.decodeManifest(manifestBytes, policy)
        val document = TdJson.decodeDocument(documentBytes, policy)
        val expected = mutableSetOf("manifest.json", "document.json")
        val assets = manifest.assets.map { descriptor ->
            expected += descriptor.path
            val content = files[descriptor.path] ?: throw TdException.Invalid("Missing embedded asset")
            if (content.size.toLong() != descriptor.byteSize) throw TdException.Invalid("Asset byte size mismatch")
            if (content.size.toLong() > policy.maxAssetBytes) throw TdException.Limit("Asset exceeds policy", "TD123")
            if (descriptor.sha256 != null && sha256(content) != descriptor.sha256) throw TdException.Invalid("Asset SHA-256 mismatch")
            EmbeddedAsset(descriptor.id, descriptor.path, descriptor.mediaType, content)
        }
        val source = manifest.source?.let { descriptor ->
            expected += descriptor.path
            val content = files[descriptor.path] ?: throw TdException.Invalid("Missing retained source")
            if (content.size.toLong() != descriptor.byteSize) throw TdException.Invalid("Source byte size mismatch")
            if (content.size.toLong() > policy.maxSourceBytes) throw TdException.Limit("Source exceeds policy", "TD123")
            if (descriptor.sha256 != null && sha256(content) != descriptor.sha256) throw TdException.Invalid("Source SHA-256 mismatch")
            RetainedSource(descriptor.path, descriptor.mediaType, content)
        }
        if (files.keys != expected) throw TdException.Invalid("Undeclared archive file")
        validateReferences(document, manifest.assets)
        return LoadedTd(document, assets, source)
    }

    private fun validateReferences(document: ThermalDocument, assets: List<AssetMetadata>) {
        val known = assets.map { it.id }.toSet()
        fun visit(block: DocumentBlock) {
            when (block) {
                is Image -> if (block.asset is EmbeddedAssetReference && block.asset.assetId !in known) throw TdException.Invalid("Missing embedded asset reference", "TD101")
                is UnorderedList -> block.items.forEach { it.blocks.forEach(::visit) }
                is OrderedList -> block.items.forEach { it.blocks.forEach(::visit) }
                is Checklist -> block.items.forEach { it.blocks.forEach(::visit) }
                is Quote -> block.blocks.forEach(::visit)
                else -> Unit
            }
        }
        document.blocks.forEach(::visit)
    }
}

internal data class ArchiveEntry(val path: String, val bytes: ByteArray, val directory: Boolean = false)
internal fun checkedAdd(current: Long, increment: Long, maximum: Long, message: String, code: String = "TD120"): Long {
    if (increment < 0 || current < 0 || current > maximum || increment > maximum - current) throw TdException.Limit(message, code)
    return current + increment
}
