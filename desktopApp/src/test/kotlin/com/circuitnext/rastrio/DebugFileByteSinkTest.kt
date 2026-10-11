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

package com.circuitnext.rastrio

import java.nio.file.Files
import kotlin.test.*
import kotlinx.coroutines.runBlocking

class DebugFileByteSinkTest {
    @Test fun exactLimitRejectsFurtherBytesAndExistingFiles(): Unit = runBlocking {
        val directory = Files.createTempDirectory("rastrio-debug-test")
        val path = directory.resolve("job.bin")
        try {
            DebugFileByteSink(path, 3).use { sink ->
                assertTrue(sink.write(byteArrayOf(1,2,3)))
                assertFalse(sink.write(byteArrayOf(4)))
                sink.close()
                assertFalse(sink.write(byteArrayOf(4)))
            }
            assertContentEquals(byteArrayOf(1,2,3), Files.readAllBytes(path))
            assertFailsWith<java.nio.file.FileAlreadyExistsException> { DebugFileByteSink(path) }
        } finally { Files.deleteIfExists(path); Files.deleteIfExists(directory) }
    }
}
