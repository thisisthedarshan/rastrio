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

import korlibs.crypto.sha256
import korlibs.io.compression.deflate.DeflatePortable
import korlibs.io.compression.uncompress
import korlibs.io.stream.SyncInputStream
import korlibs.io.stream.SyncOutputStream

/** ZIP32 reader for stored and raw-deflated entries; all paths remain logical archive names. */
internal fun zipRead(bytes: ByteArray, policy: TdResourcePolicy, isCancelled: () -> Boolean): List<ArchiveEntry> {
    if (bytes.size.toLong() > policy.maxArchiveBytes) throw TdException.Limit("Archive input exceeds policy")
    try {
        val resident = ResidentArchiveBudget(bytes.size.toLong(), policy.maxResidentArchiveBytes)
        val directory = ZipInput(bytes, policy).entries(resident)
        val output = ArrayList<ArchiveEntry>(directory.size)
        var totalExpanded = 0L
        for (entry in directory) {
            if (isCancelled()) throw TdException.Cancelled()
            val maximum = entryLimit(entry.path, entry.directory, policy)
            val limitCode = entryLimitCode(entry.path)
            if (entry.expandedSize > maximum) throw TdException.Limit("Archive entry exceeds policy", limitCode)
            val local = entry.dataOffset(bytes)
            val data: ByteArray
            if (entry.method == 0) {
                if (entry.compressedSize != entry.expandedSize) throw TdException.Invalid("Stored ZIP size mismatch")
                totalExpanded = checkedAdd(totalExpanded, entry.expandedSize, policy.maxExpandedBytes, "Archive expansion exceeds policy", "TD121")
                resident.reserve(entry.expandedSize)
                data = bytes.copyOfRange(local, local + entry.compressedSize.toInt())
            } else {
                resident.ensureTemporary(checkedAdd(entry.expandedSize, DEFLATE_SCRATCH_BYTES, Long.MAX_VALUE, "Archive resident memory exceeds policy"))
                resident.reserve(entry.expandedSize)
                val expanded = ByteArray(entry.expandedSize.toInt())
                var written = 0L
                val input = ArchiveSliceInput(bytes, local, entry.compressedSize.toInt(), isCancelled)
                val sink = object : SyncOutputStream {
                    override fun write(buffer: ByteArray, offset: Int, len: Int) {
                        if (isCancelled()) throw TdException.Cancelled()
                        totalExpanded = checkedAdd(totalExpanded, len.toLong(), policy.maxExpandedBytes, "Archive expansion exceeds policy", "TD121")
                        written = checkedAdd(written, len.toLong(), maximum, "Archive entry exceeds policy", limitCode)
                        if (written > expanded.size) throw TdException.Invalid("ZIP entry size mismatch")
                        buffer.copyInto(expanded, (written - len).toInt(), offset, offset + len)
                    }
                }
                DeflatePortable.uncompress(input, sink)
                data = expanded
            }
            if (data.size.toLong() != entry.expandedSize || crc32(data) != entry.crc) throw TdException.Invalid("ZIP entry size or CRC mismatch")
            if (entry.directory && data.isNotEmpty()) throw TdException.Invalid("Archive directory has content")
            output += ArchiveEntry(entry.path, data, entry.directory)
        }
        return output
    } catch (error: TdException) {
        throw error
    } catch (_: Exception) {
        throw TdException.Invalid("Invalid or corrupt .td ZIP archive")
    }
}

// Korlibs' portable inflater can retain two 8 MiB buffers plus smaller windows while expanding.
private const val DEFLATE_SCRATCH_BYTES = 17L * 1024 * 1024

private class ResidentArchiveBudget(inputBytes: Long, private val maximum: Long) {
    private var retained = checkedAdd(0, inputBytes, maximum, "Archive resident memory exceeds policy")

    fun reserve(bytes: Long) {
        retained = checkedAdd(retained, bytes, maximum, "Archive resident memory exceeds policy")
    }

    fun ensureTemporary(bytes: Long) {
        checkedAdd(retained, bytes, maximum, "Archive resident memory exceeds policy")
    }
}

/** Stored entries keep writer behavior deterministic without requiring a compressor. */
internal fun zipWrite(entries: List<ArchiveEntry>, policy: TdResourcePolicy): ByteArray {
    if (entries.size > policy.maxEntries) throw TdException.Limit("Archive entry count exceeds policy", "TD122")
    val output = BoundedBytes(policy.maxArchiveBytes)
    val records = ArrayList<WrittenEntry>(entries.size)
    for (entry in entries) {
        val name = entry.path.encodeToByteArray()
        if (name.size > 0xffff) throw TdException.Invalid("ZIP entry name is too long")
        if (entry.bytes.size.toLong() > entryLimit(entry.path, entry.directory, policy)) throw TdException.Limit("Archive entry exceeds policy", entryLimitCode(entry.path))
        val offset = output.size
        val crc = crc32(entry.bytes)
        output.u32(0x04034b50L)
        output.u16(20)
        output.u16(0x0800)
        output.u16(0)
        output.u16(0)
        output.u16(0)
        output.u32(crc)
        output.u32(entry.bytes.size.toLong())
        output.u32(entry.bytes.size.toLong())
        output.u16(name.size)
        output.u16(0)
        output.put(name)
        output.put(entry.bytes)
        records += WrittenEntry(name, entry.bytes.size, crc, offset)
    }
    val directoryOffset = output.size
    for (record in records) {
        output.u32(0x02014b50L)
        output.u16(20)
        output.u16(20)
        output.u16(0x0800)
        output.u16(0)
        output.u16(0)
        output.u16(0)
        output.u32(record.crc)
        output.u32(record.size.toLong())
        output.u32(record.size.toLong())
        output.u16(record.name.size)
        output.u16(0)
        output.u16(0)
        output.u16(0)
        output.u16(0)
        output.u32(0)
        output.u32(record.offset.toLong())
        output.put(record.name)
    }
    val directorySize = output.size - directoryOffset
    output.u32(0x06054b50L)
    output.u16(0)
    output.u16(0)
    output.u16(records.size)
    output.u16(records.size)
    output.u32(directorySize.toLong())
    output.u32(directoryOffset.toLong())
    output.u16(0)
    return output.bytes()
}

internal fun sha256(bytes: ByteArray): String = bytes.sha256().hexLower

private data class WrittenEntry(val name: ByteArray, val size: Int, val crc: Long, val offset: Int)

private data class CentralEntry(
    val path: String,
    val name: ByteArray,
    val flags: Int,
    val method: Int,
    val compressedSize: Long,
    val expandedSize: Long,
    val crc: Long,
    val localOffset: Long,
    val directory: Boolean,
) {
    fun dataOffset(archive: ByteArray): Int {
        val input = ZipInput(archive, null)
        val base = localOffset.toInt()
        if (localOffset < 0 || localOffset + 30 > archive.size || input.u32(base) != 0x04034b50L) throw TdException.Invalid("Corrupt ZIP local header")
        if (input.u16(base + 6) != flags || input.u16(base + 8) != method) throw TdException.Invalid("ZIP local header mismatch")
        val nameLength = input.u16(base + 26)
        val extraLength = input.u16(base + 28)
        val dataStart = localOffset + 30L + nameLength + extraLength
        if (dataStart + compressedSize > archive.size || dataStart < 0 || nameLength != name.size) throw TdException.Invalid("Corrupt ZIP local entry")
        for (i in name.indices) if (archive[base + 30 + i] != name[i]) throw TdException.Invalid("ZIP local name mismatch")
        return dataStart.toInt()
    }
}

private class ZipInput(private val bytes: ByteArray, private val policy: TdResourcePolicy?) {
    fun u16(at: Int): Int {
        if (at < 0 || at.toLong() + 2 > bytes.size) throw TdException.Invalid("Truncated ZIP metadata")
        return (bytes[at].toInt() and 0xff) or ((bytes[at + 1].toInt() and 0xff) shl 8)
    }
    fun u32(at: Int): Long = u16(at).toLong() or (u16(at + 2).toLong() shl 16)

    fun entries(resident: ResidentArchiveBudget): List<CentralEntry> {
        val activePolicy = policy ?: throw TdException.Invalid("Missing ZIP resource policy")
        val first = (bytes.size - 22 - 65_535).coerceAtLeast(0)
        var end = -1
        for (at in bytes.size - 22 downTo first) {
            if (u32(at) == 0x06054b50L && at + 22 + u16(at + 20) == bytes.size) { end = at; break }
        }
        if (end < 0) throw TdException.Invalid("Missing ZIP central directory")
        if (u16(end + 4) != 0 || u16(end + 6) != 0) throw TdException.Invalid("Multi-disk ZIP is unsupported")
        val count = u16(end + 10)
        if (count != u16(end + 8) || count == 0xffff) throw TdException.Invalid("Unsupported or corrupt ZIP entry count")
        if (count > activePolicy.maxEntries) throw TdException.Limit("Archive entry count exceeds policy", "TD122")
        val centralSize = u32(end + 12)
        val centralOffset = u32(end + 16)
        if (centralSize == 0xffffffffL || centralOffset == 0xffffffffL || centralOffset + centralSize > end) throw TdException.Invalid("Unsupported or corrupt ZIP directory")
        var at = centralOffset.toInt()
        val results = ArrayList<CentralEntry>(count)
        val seenPaths = mutableSetOf<String>()
        repeat(count) {
            if (at.toLong() + 46 > end || u32(at) != 0x02014b50L) throw TdException.Invalid("Corrupt ZIP directory entry")
            val flags = u16(at + 8)
            if ((flags and 0x2041) != 0) throw TdException.Invalid("Encrypted ZIP entry is unsupported")
            val method = u16(at + 10)
            if (method != 0 && method != 8) throw TdException.Invalid("Unsupported ZIP compression method")
            val compressedSize = u32(at + 20)
            val expandedSize = u32(at + 24)
            if (compressedSize == 0xffffffffL || expandedSize == 0xffffffffL) throw TdException.Invalid("ZIP64 entry is unsupported")
            val nameLength = u16(at + 28)
            val extraLength = u16(at + 30)
            val commentLength = u16(at + 32)
            val recordEnd = at.toLong() + 46 + nameLength + extraLength + commentLength
            if (recordEnd > end) throw TdException.Invalid("Corrupt ZIP directory entry")
            if (nameLength > activePolicy.maxPathBytes + 1) throw TdException.Limit("Archive path exceeds policy", "TD126")
            resident.reserve(nameLength.toLong() * 6 + 256)
            val name = bytes.copyOfRange(at + 46, at + 46 + nameLength)
            val path = try { name.decodeToString(throwOnInvalidSequence = true) } catch (_: Exception) { throw TdException.Invalid("Invalid UTF-8 ZIP path") }
            val directory = path.endsWith('/')
            val normalizedPath = TdJson.validateArchivePath(if (directory) path.removeSuffix("/") else path, activePolicy)
            if (!seenPaths.add(normalizedPath)) throw TdException.Invalid("Duplicate normalized archive path")
            val madeBySystem = bytes[at + 5].toInt() and 0xff
            if (madeBySystem == 3) {
                val kind = ((u32(at + 38) ushr 16).toInt() and 0xf000)
                if (kind != 0 && kind != 0x8000 && kind != 0x4000) throw TdException.Invalid("Special ZIP entry is unsupported")
            }
            val localOffset = u32(at + 42)
            if (localOffset == 0xffffffffL) throw TdException.Invalid("ZIP64 entry is unsupported")
            results += CentralEntry(path, name, flags, method, compressedSize, expandedSize, u32(at + 16), localOffset, directory)
            at = recordEnd.toInt()
        }
        if (at.toLong() != centralOffset + centralSize) throw TdException.Invalid("ZIP directory size mismatch")
        return results
    }
}

private class ArchiveSliceInput(private val bytes: ByteArray, start: Int, length: Int, private val isCancelled: () -> Boolean) : SyncInputStream {
    private var position = start
    private val end = start + length
    override fun read(buffer: ByteArray, offset: Int, len: Int): Int {
        if (isCancelled()) throw TdException.Cancelled()
        if (position >= end) return -1
        val count = minOf(len, end - position)
        bytes.copyInto(buffer, offset, position, position + count)
        position += count
        return count
    }
}

private class BoundedBytes(private val maximum: Long, private val limitCode: String = "TD120") {
    private var buffer = ByteArray(256)
    var size = 0
        private set

    fun put(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset) {
        if (offset < 0 || length < 0 || offset.toLong() + length > bytes.size) throw TdException.Invalid("Invalid byte range")
        val needed = checkedAdd(size.toLong(), length.toLong(), minOf(maximum, Int.MAX_VALUE.toLong()), "Archive byte limit exceeded", limitCode).toInt()
        if (needed > buffer.size) {
            var capacity = buffer.size.toLong()
            while (capacity < needed) capacity = minOf(maximum, maxOf(capacity * 2, needed.toLong()))
            if (capacity < needed || capacity > Int.MAX_VALUE) throw TdException.Limit("Archive byte limit exceeded", limitCode)
            buffer = buffer.copyOf(capacity.toInt())
        }
        bytes.copyInto(buffer, size, offset, offset + length)
        size = needed
    }
    fun u16(value: Int) { put(byteArrayOf(value.toByte(), (value ushr 8).toByte())) }
    fun u32(value: Long) { put(byteArrayOf(value.toByte(), (value ushr 8).toByte(), (value ushr 16).toByte(), (value ushr 24).toByte())) }
    fun bytes(): ByteArray = buffer.copyOf(size)
}

private fun entryLimit(path: String, directory: Boolean, policy: TdResourcePolicy): Long = when {
    directory -> 0
    path == "manifest.json" -> policy.maxManifestBytes.toLong()
    path == "document.json" -> policy.maxDocumentBytes.toLong()
    path.startsWith("assets/") -> policy.maxAssetBytes
    path.startsWith("source/") -> policy.maxSourceBytes
    else -> policy.maxEntryBytes
}.coerceAtMost(policy.maxEntryBytes)

private fun entryLimitCode(path: String): String = if (path == "manifest.json" || path == "document.json") "TD124" else "TD123"

private fun crc32(bytes: ByteArray): Long {
    var crc = -1
    for (byte in bytes) {
        crc = crc xor (byte.toInt() and 0xff)
        repeat(8) { crc = (crc ushr 1) xor (if ((crc and 1) != 0) 0xedb88320.toInt() else 0) }
    }
    return crc.inv().toLong() and 0xffffffffL
}
