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

package com.circuitnext.rastrio

import com.circuitnext.rastrio.core.escpos.EncodedByteSink
import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/** Explicit developer export to a caller-selected path. Never derives paths from document metadata. */
class DebugFileByteSink(path: Path, private val maxBytes: Long = 256L * 1024 * 1024) : EncodedByteSink, Closeable {
    init { require(maxBytes in 1..256L * 1024 * 1024) }
    private val output = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
    private var bytes = 0L
    private var closed = false
    override suspend fun write(bytes: ByteArray): Boolean {
        if (closed || bytes.size > 64 * 1024 || bytes.size.toLong() > maxBytes - this.bytes) return false
        output.write(bytes)
        this.bytes += bytes.size
        return true
    }
    override fun close() {
        if (!closed) { closed = true; output.close() }
    }
}
