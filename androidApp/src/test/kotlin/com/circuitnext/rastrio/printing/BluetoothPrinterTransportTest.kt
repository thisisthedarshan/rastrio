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

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.concurrent.CountDownLatch
import kotlin.test.*

class BluetoothPrinterTransportTest {
    private fun instance() = PrinterInstance("local", "Printer", PrinterTransportKind.BLUETOOTH_CLASSIC,
        "opaque", "synthetic.reference", 1)
    private class Socket : PrinterSocket {
        var connected = false
        var closed = false
        var block = false
        val released = CountDownLatch(1)
        val bytes = mutableListOf<Byte>()
        override fun connect() { connected = true }
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            if (block) { released.await(); throw IOException("Closed") }
            this.bytes.addAll(bytes.copyOfRange(offset, offset + length).toList())
        }
        override fun close() { closed = true; released.countDown() }
    }
    @Test fun profileAcquisitionHasExactLimitAndRejectsNonProgress() {
        assertEquals(1024 * 1024, readProfileBytes(java.io.ByteArrayInputStream(ByteArray(1024 * 1024))).size)
        assertFailsWith<IllegalArgumentException> {
            readProfileBytes(java.io.ByteArrayInputStream(ByteArray(1024 * 1024 + 1)))
        }
        assertFailsWith<IllegalArgumentException> {
            readProfileBytes(object : java.io.InputStream() {
                override fun read(): Int = 0
                override fun read(bytes: ByteArray, offset: Int, length: Int): Int = 0
            })
        }
    }
    @Test fun permissionsOnlyRequireConnectOnModernAndroid() {
        assertNull(bluetoothPermission(24)); assertNull(bluetoothPermission(30))
        assertEquals("android.permission.BLUETOOTH_CONNECT", bluetoothPermission(31))
        assertEquals("android.permission.BLUETOOTH_CONNECT", bluetoothPermission(37))
    }
    @Test fun metadataIsBoundedAndControlsRemoved() {
        assertEquals("Printer", safeDeviceName("\u001bPrinter\u202e"))
        assertEquals(80, safeDeviceName("x".repeat(500)).length)
        assertEquals("Paired Bluetooth device", safeDeviceName(null))
    }
    @Test fun exactBoundedWriteAndIdempotentClose(): Unit = runBlocking {
        val socket = Socket()
        val transport = BluetoothPrinterTransport({ socket }, TransportConstraints(4))
        transport.connect(instance())
        assertEquals(2, transport.write(byteArrayOf(0,1,2,3),1,2))
        assertEquals(listOf<Byte>(1,2), socket.bytes)
        assertFailsWith<IllegalArgumentException> { transport.write(ByteArray(5),0,5) }
        transport.close(); transport.close(); assertTrue(socket.closed)
        assertFailsWith<IOException> { transport.write(ByteArray(1),0,1) }
    }
    @Test fun failureClosesSocket() = runBlocking {
        val socket = Socket()
        val transport = BluetoothPrinterTransport({ object : PrinterSocket {
            override fun connect() { throw IOException("Failure") }
            override fun write(bytes: ByteArray, offset: Int, length: Int) = Unit
            override fun close() { socket.close() }
        } }, TransportConstraints(4))
        assertFailsWith<IOException> { transport.connect(instance()) }
        assertTrue(socket.closed)
    }
    @Test fun cancelledStalledWriteClosesAndUnblocksSocket() = runBlocking {
        val socket = Socket()
        val transport = BluetoothPrinterTransport({ socket }, TransportConstraints(4))
        transport.connect(instance()); socket.block = true
        val job = launch { transport.write(ByteArray(4),0,4) }
        delay(30); job.cancel()
        withTimeout(1000) { job.join() }
        assertTrue(socket.closed)
    }

    @Test fun deniedPermissionAndUnavailableSavedLocatorDoNotOpenAnOutputPath(): Unit = runBlocking {
        for (failure in listOf(SecurityException("Permission denied"), IOException("Saved device unavailable"))) {
            val transport = BluetoothPrinterTransport({ throw failure }, TransportConstraints(4))
            assertFails { transport.connect(instance()) }
            assertFailsWith<IOException> { transport.write(ByteArray(1), 0, 1) }
            transport.close()
        }
    }

    @Test fun revokedPermissionDuringWriteClosesTheConnectedSocket(): Unit = runBlocking {
        val socket = Socket()
        val transport = BluetoothPrinterTransport({ object : PrinterSocket {
            override fun connect() = socket.connect()
            override fun write(bytes: ByteArray, offset: Int, length: Int): Unit = throw SecurityException("Permission revoked")
            override fun close() = socket.close()
        } }, TransportConstraints(4))
        transport.connect(instance())
        assertFailsWith<SecurityException> { transport.write(ByteArray(1), 0, 1) }
        assertTrue(socket.closed)
        assertTrue(socket.bytes.isEmpty())
        assertFailsWith<IOException> { transport.write(ByteArray(1), 0, 1) }
    }

    @Test fun immediatelyResumedWritesRemainSerialized(): Unit = runBlocking {
        val socket = Socket()
        val transport = BluetoothPrinterTransport({ socket }, TransportConstraints(4))
        transport.connect(instance())
        repeat(256) { assertEquals(1, transport.write(byteArrayOf(1), 0, 1)) }
        assertEquals(256, socket.bytes.size)
        transport.close()
    }
}
