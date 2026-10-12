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

private var loaded = false
@Synchronized internal actual fun loadTextNativeLibrary() {
    if (loaded) return
    try { System.loadLibrary("rastrio_text") } catch (e: UnsatisfiedLinkError) {
        val name = System.mapLibraryName("rastrio_text")
        val source = BundledRasterTextBackend::class.java.getResourceAsStream("/rastrio-native/$name") ?: throw e
        val directory = java.nio.file.Files.createTempDirectory("rastrio-text-")
        val library = directory.resolve(name)
        try {
            source.use { input -> java.nio.file.Files.newOutputStream(library).use { output ->
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    check(total <= 16 * 1024 * 1024) { "Native library resource limit" }
                    output.write(buffer, 0, count)
                }
            } }
            System.load(library.toAbsolutePath().toString())
            directory.toFile().deleteOnExit()
            library.toFile().deleteOnExit()
        } catch (failure: Throwable) {
            java.nio.file.Files.deleteIfExists(library)
            java.nio.file.Files.deleteIfExists(directory)
            throw failure
        }
    }
    loaded = true
}
