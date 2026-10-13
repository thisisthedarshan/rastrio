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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.*

class PrinterSelectionPersistenceTest {
    private fun instance(id: String) = PrinterInstance(id, "Printer $id", PrinterTransportKind.BLUETOOTH_CLASSIC,
        "private-$id", "user.profile", 1)

    @Test fun newerSelectionWritesLastAndOlderCompletionCannotPublish() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val written = mutableListOf<String>()
        var persisted: PrinterInstance? = null
        val store = PrinterSelectionPersistence { instance ->
            if (instance.id == "a") { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) }
            written += instance.id; persisted = instance; true
        }
        val firstRequest = store.request()
        val first = async(Dispatchers.Default) { store.save(firstRequest, instance("a")) }
        assertTrue(entered.await(1, TimeUnit.SECONDS))
        val latestRequest = store.request()
        val latest = async(Dispatchers.Default) { store.save(latestRequest, instance("b")) }
        release.countDown()
        assertFalse(first.await())
        assertTrue(latest.await())
        assertEquals(listOf("a", "b"), written)
        assertEquals(instance("b"), persisted)
        assertTrue(store.isCurrent(latestRequest))
        assertFalse(store.isCurrent(firstRequest))
    }

    @Test fun supersededQueuedRequestsAndFailedWritesCannotConfirmSelection() = runBlocking {
        var writes = 0
        val store = PrinterSelectionPersistence { writes++; false }
        val older = store.request()
        val current = store.request()
        assertFalse(store.save(older, instance("a")))
        assertEquals(0, writes)
        assertFalse(store.save(current, instance("b")))
        assertEquals(1, writes)
    }
}
