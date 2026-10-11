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

import kotlinx.coroutines.delay

/** Explicit development capture only. It does not establish physical printer compatibility. */
class FakePrinterTransport(
    override val constraints: TransportConstraints = TransportConstraints(maxWriteBytes = 1024),
    private val maxCaptureBytes: Int = 256 * 1024,
    private val writeDelayMillis: Long = 0,
    private val failAfterBytes: Int? = null,
) : PrinterTransport {
    init {
        require(maxCaptureBytes in 1..256 * 1024 && writeDelayMillis >= 0)
        require(failAfterBytes == null || failAfterBytes >= 0)
    }

    private val capture = ByteArray(maxCaptureBytes)
    var byteCount: Int = 0
        private set
    var connections: Int = 0
        private set
    var closes: Int = 0
        private set
    var writes: Int = 0
        private set
    var largestWrite: Int = 0
        private set
    var connected: Boolean = false
        private set

    fun capturedBytes(): ByteArray = capture.copyOf(byteCount)

    override suspend fun connect(instance: PrinterInstance) {
        check(instance.transportKind == PrinterTransportKind.DEVELOPMENT)
        connected = true
        connections++
    }

    override suspend fun write(bytes: ByteArray, offset: Int, length: Int): Int {
        require(offset >= 0 && length > 0 && offset <= bytes.size - length)
        require(length <= constraints.maxWriteBytes)
        check(connected)
        writes++
        largestWrite = maxOf(largestWrite, length)
        if (writeDelayMillis > 0) delay(writeDelayMillis)
        check(connected)
        if (failAfterBytes != null && byteCount >= failAfterBytes) error("Injected transport failure")
        val accepted = minOf(length, maxCaptureBytes - byteCount, failAfterBytes?.minus(byteCount) ?: Int.MAX_VALUE)
        check(accepted > 0) { "Development capture limit" }
        bytes.copyInto(capture, byteCount, offset, offset + accepted)
        byteCount += accepted
        return accepted
    }

    override fun close() {
        connected = false
        closes++
    }
}
