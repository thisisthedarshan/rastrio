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

package com.circuitnext.rastrio.core.text

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.lang.reflect.InvocationTargetException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Real classpath faults, without adding a production font-source override. */
class BundledFontPackagingTest {
    private enum class Fault { MISSING, CORRUPT, UNREADABLE }
    private class IsolatedTextLoader(parent: ClassLoader, private val fault: Fault?) : ClassLoader(parent) {
        override fun loadClass(name: String, resolve: Boolean): Class<*> = synchronized(getClassLoadingLock(name)) {
            if (!name.startsWith("com.circuitnext.rastrio.core.text.")) return@synchronized super.loadClass(name, resolve)
            val loaded = findLoadedClass(name) ?: parent.getResourceAsStream(name.replace('.', '/') + ".class")!!.use {
                val bytes = it.readBytes()
                defineClass(name, bytes, 0, bytes.size)
            }
            if (resolve) resolveClass(loaded)
            loaded
        }
        override fun getResourceAsStream(name: String): InputStream? {
            if (name == "rastrio-fonts/JetBrainsMono-BoldItalic.ttf") when (fault) {
                Fault.MISSING -> return null
                Fault.CORRUPT -> return ByteArrayInputStream(byteArrayOf(0, 1, 2))
                Fault.UNREADABLE -> return object : InputStream() { override fun read(): Int = throw IOException("fixture read failure") }
                null -> Unit
            }
            return parent.getResourceAsStream(name)
        }
    }
    private fun create(loader: ClassLoader): Any {
        val type = loader.loadClass("com.circuitnext.rastrio.core.text.BundledRasterTextBackend")
        val companion = type.getField("Companion").get(null)
        return companion.javaClass.getMethod("create").invoke(companion)
    }
    @Test fun missingCorruptOrUnreadableBundledFaceFailsBeforeBackendPublication() {
        for (fault in Fault.entries) {
            val loader = IsolatedTextLoader(javaClass.classLoader, fault)
            val thrown = assertFailsWith<InvocationTargetException> { create(loader) }.cause!!
            assertEquals("com.circuitnext.rastrio.core.text.TextBackendUnavailableException", thrown.javaClass.name)
            val diagnostics = thrown.javaClass.getMethod("getDiagnostics").invoke(thrown) as List<*>
            assertEquals("TXT132", diagnostics.single()!!.javaClass.getMethod("getCode").invoke(diagnostics.single()))
            val platform = loader.loadClass("com.circuitnext.rastrio.core.text.BundledRasterTextBackendKt")
                .getMethod("createPlatformRasterTextBackend").invoke(null)
            assertEquals("com.circuitnext.rastrio.core.text.UnsupportedRasterTextBackend", platform.javaClass.name)
        }
    }
    @Test fun healthyIsolatedPackagingPublishesRealBackend() {
        val backend = create(IsolatedTextLoader(javaClass.classLoader, null))
        assertEquals("com.circuitnext.rastrio.core.text.BundledRasterTextBackend", backend.javaClass.name)
    }
}
