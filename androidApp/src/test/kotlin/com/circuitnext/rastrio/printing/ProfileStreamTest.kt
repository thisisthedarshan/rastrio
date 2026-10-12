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

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlin.test.*

class ProfileStreamTest {
    @Test fun validatedExportWritesExactBytesInBoundedChunksAndCloses(): Unit = runBlocking {
        val bytes = ByteArray(20_000) { (it % 251).toByte() }
        var closed = false
        var maxWrite = 0
        val destination = object : ByteArrayOutputStream() {
            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                maxWrite = maxOf(maxWrite, length); super.write(bytes, offset, length)
            }
            override fun close() { closed = true; super.close() }
        }
        writeProfileStream(bytes) { destination }
        assertContentEquals(bytes, destination.toByteArray())
        assertTrue(closed); assertTrue(maxWrite in 1..8192)
    }

    @Test fun byteLimitsAreEnforcedBeforeOpeningAnExportDestination(): Unit = runBlocking {
        var opened = false
        for (bytes in listOf(ByteArray(0), ByteArray(1024 * 1024 + 1))) {
            assertFailsWith<IllegalArgumentException> { writeProfileStream(bytes) {
                opened = true; ByteArrayOutputStream()
            } }
        }
        assertFalse(opened)
        assertEquals(1024 * 1024, readProfileBytes(ByteArrayInputStream(ByteArray(1024 * 1024))).size)
        assertFailsWith<IllegalArgumentException> { readProfileBytes(ByteArrayInputStream(ByteArray(1024 * 1024 + 1))) }
    }

    @Test fun cancellingBlockedExportClosesItsStreamWithoutRetry(): Unit = runBlocking {
        val entered = CountDownLatch(1)
        val released = CountDownLatch(1)
        var writes = 0
        val destination = object : OutputStream() {
            override fun write(value: Int) = error("Expected bounded array writes")
            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                writes++; entered.countDown(); released.await(); throw IOException("Closed")
            }
            override fun close() { released.countDown() }
        }
        val task = launch(Dispatchers.IO) { writeProfileStream(ByteArray(10_000)) { destination } }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            task.cancel()
            withTimeout(5000) { task.join() }
            assertEquals(0L, released.count); assertEquals(1, writes)
        } finally { destination.close(); task.cancel() }
    }
}
