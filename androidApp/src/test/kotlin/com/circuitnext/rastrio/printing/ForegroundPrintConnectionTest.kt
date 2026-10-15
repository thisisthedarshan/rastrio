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

import kotlinx.coroutines.*
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.*

class ForegroundPrintConnectionTest {
    private fun instance() = PrinterInstance("local", "Printer", PrinterTransportKind.BLUETOOTH_CLASSIC,
        "private-locator", "documented.target", 1)
    private class Transport : PrinterTransport {
        override val constraints = TransportConstraints(4)
        var closes = 0
        override suspend fun connect(instance: PrinterInstance) = Unit
        override suspend fun write(bytes: ByteArray, offset: Int, length: Int) = length
        override fun close() { closes++ }
    }

    @Test fun cachedConnectionIsReactivatedBeforeForegroundClose(): Unit = runBlocking {
        var foreground = true
        val owner = ForegroundPrintConnection { foreground }
        val first = Transport(); val second = Transport()
        val a = owner.wrap(first); val b = owner.wrap(second)
        assertFalse(owner.isActive)
        a.connect(instance()); b.connect(instance()); a.connect(instance())
        assertTrue(owner.isActive)
        assertEquals(1, first.closes); assertEquals(1, second.closes)
        foreground = false
        owner.close()
        assertFalse(owner.isActive)
        assertEquals(2, first.closes); assertEquals(1, second.closes)
        assertFailsWith<IllegalStateException> { a.write(ByteArray(1), 0, 1) }
        assertFailsWith<IllegalStateException> { b.connect(instance()) }
    }

    @Test fun foregroundCloseUnblocksTheReselectedSocketsPendingWrite(): Unit = runBlocking {
        val entered = CountDownLatch(1); val released = CountDownLatch(1)
        val bluetooth = BluetoothPrinterTransport({ object : PrinterSocket {
            override fun connect() = Unit
            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                entered.countDown(); released.await(); throw IOException("Closed")
            }
            override fun close() { released.countDown() }
        } }, TransportConstraints(4))
        val owner = ForegroundPrintConnection { true }
        val a = owner.wrap(bluetooth); val b = owner.wrap(Transport())
        // First select B, then connect an already-created A wrapper.
        b.connect(instance()); a.connect(instance())
        val worker = launch(Dispatchers.Default) {
            assertFailsWith<IOException> { a.write(ByteArray(4), 0, 4) }
        }
        assertTrue(entered.await(1, TimeUnit.SECONDS))
        owner.close()
        withTimeout(1000) { worker.join() }
        assertEquals(0L, released.count)
    }
}
