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
import kotlin.test.*

class BluetoothPresenceTest {
    private class Discovery : BluetoothPresenceAccess {
        var stopped = 0
        var released = 0
        var started = 0
        var accepted = true
        var listener: ((String?) -> Unit)? = null
        override fun register(foundOrFinished: (String?) -> Unit): () -> Unit {
            listener = foundOrFinished
            return { released++; listener = null }
        }
        override fun start(): Boolean { started++; return accepted }
        override fun stop() { stopped++ }
    }
    @Test fun matchingDiscoveryAndTimeoutAlwaysReleaseWithoutConnecting() = runBlocking {
        val access = Discovery()
        val detected = async(start = CoroutineStart.UNDISPATCHED) { checkBluetoothPresence("printer", access, 1000) }
        access.listener!!("other")
        assertTrue(detected.isActive)
        access.listener!!("printer")
        assertEquals(PrinterPresence.DETECTED, detected.await())
        assertEquals(1, access.stopped)
        assertEquals(1, access.released)
        assertEquals(PrinterPresence.NOT_DETECTED, checkBluetoothPresence("printer", access, 1))
        assertEquals(2, access.released)
    }
    @Test fun rejectionCancellationAndDiscoveryFinishedReleaseResources() = runBlocking {
        val access = Discovery()
        access.accepted = false
        assertEquals(PrinterPresence.UNABLE_TO_CHECK, checkBluetoothPresence("printer", access, 1000))
        access.accepted = true
        val cancelled = launch(start = CoroutineStart.UNDISPATCHED) { checkBluetoothPresence("printer", access, 1000) }
        cancelled.cancelAndJoin()
        assertNull(access.listener)
        val finished = async(start = CoroutineStart.UNDISPATCHED) { checkBluetoothPresence("printer", access, 1000) }
        access.listener!!(null)
        assertEquals(PrinterPresence.NOT_DETECTED, finished.await())
        assertEquals(3, access.released)
        assertEquals(3, access.stopped)
    }
}
