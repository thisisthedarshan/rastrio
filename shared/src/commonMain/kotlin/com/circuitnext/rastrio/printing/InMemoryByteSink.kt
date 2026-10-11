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

package com.circuitnext.rastrio.printing

import com.circuitnext.rastrio.core.escpos.EncodedByteSink

/** Small explicit fixtures only; rejected writes retain the previous bytes unchanged. */
class InMemoryByteSink(val maxBytes: Int = 256 * 1024) : EncodedByteSink {
    init { require(maxBytes in 1..256 * 1024) }
    private val buffer = ByteArray(maxBytes)
    var byteCount: Int = 0
        private set

    override suspend fun write(bytes: ByteArray): Boolean {
        if (bytes.size > maxBytes - byteCount) return false
        bytes.copyInto(buffer, byteCount)
        byteCount += bytes.size
        return true
    }

    fun bytes(): ByteArray = buffer.copyOf(byteCount)
}
