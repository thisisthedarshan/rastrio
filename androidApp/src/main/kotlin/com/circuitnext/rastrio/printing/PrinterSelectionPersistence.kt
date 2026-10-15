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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

/** Serialize private writes so an older selection cannot overwrite or publish after the newest request. */
internal class PrinterSelectionPersistence(private val write: (PrinterInstance) -> Boolean) {
    private val generation = AtomicLong()
    private val writes = Mutex()
    fun request(): Long = generation.incrementAndGet()
    fun isCurrent(request: Long): Boolean = generation.get() == request
    suspend fun clear(request: Long, clear: () -> Boolean): Boolean = writes.withLock {
        if (!isCurrent(request)) return@withLock false
        val cleared = withContext(Dispatchers.IO) { clear() }
        cleared && isCurrent(request)
    }
    suspend fun save(request: Long, instance: PrinterInstance): Boolean = writes.withLock {
        if (!isCurrent(request)) return@withLock false
        val saved = withContext(Dispatchers.IO) { write(instance) }
        saved && isCurrent(request)
    }
}
