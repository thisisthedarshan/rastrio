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

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import kotlin.test.*

class PrinterInstanceStoreTest {
    private fun instance() = PrinterInstance("private-id", "Printer", PrinterTransportKind.BLUETOOTH_CLASSIC,
        "private-locator", "documented.target", 2)

    private class Preferences {
        val values = mutableMapOf<String, Any?>()
        var commitResult = true
        private val editor = Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)) { proxy, method, args ->
            when (method.name) {
                "putInt", "putString" -> { values[args[0] as String] = args[1]; proxy }
                "commit" -> commitResult
                else -> error("Unexpected editor call: ${method.name}")
            }
        } as SharedPreferences.Editor
        val preferences = Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "edit" -> editor
                "getInt" -> (values[args[0] as String] ?: args[1]) as Int
                "getString" -> (values[args[0] as String] ?: args[1]) as String?
                else -> error("Unexpected preferences call: ${method.name}")
            }
        } as SharedPreferences
    }

    @Test fun selectionRoundTripsOnlyNecessaryPrivateMetadata() {
        val memory = Preferences()
        val store = PrinterInstanceStore(memory.preferences)
        assertNull(store.load())
        assertTrue(store.save(instance()))
        assertEquals(instance(), PrinterInstanceStore(memory.preferences).load())
        assertEquals(setOf("version", "id", "name", "locator", "profile", "revision"), memory.values.keys)
        assertFalse(instance().toString().contains("private-"))
    }

    @Test fun unknownVersionAndCorruptPersistedMetadataFailClosed() {
        val memory = Preferences()
        val store = PrinterInstanceStore(memory.preferences)
        store.save(instance())
        memory.values["version"] = 2
        assertNull(store.load())
        memory.values["version"] = "invalid type"
        assertNull(store.load())
        memory.values["version"] = 1
        memory.values["revision"] = 0
        assertNull(store.load())
        memory.values["revision"] = 2
        memory.values["locator"] = "untrusted\u001blocator"
        assertNull(store.load())
    }

    @Test fun failedPersistenceAndDevelopmentTransportAreNotAccepted() {
        val memory = Preferences()
        memory.commitResult = false
        val store = PrinterInstanceStore(memory.preferences)
        assertFalse(store.save(instance()))
        assertFailsWith<IllegalArgumentException> { store.save(instance().copy(transportKind = PrinterTransportKind.DEVELOPMENT)) }
    }
}
