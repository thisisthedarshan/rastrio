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

package com.circuitnext.rastrio.core.profile

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Phase5CoreBoundaryTest {
    @Test fun genericCoreProductionSourcesContainNoH50iConstantsOrBranches() {
        val root = assertNotNull(generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile && File(it, "PRD.md").isFile })
        val sources = File(root, "core").listFiles().orEmpty().flatMap { module ->
            File(module, "src").listFiles().orEmpty().filter { it.isDirectory && it.name.endsWith("Main") }
                .flatMap { sourceSet -> sourceSet.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
        }
        assertTrue(sources.isNotEmpty(), "Core production source discovery must not be empty")
        for (source in sources) {
            val code = source.readText().replace(Regex("/\\*.*?\\*/|//[^\\r\\n]*", RegexOption.DOT_MATCHES_ALL), "")
            assertFalse(code.contains("h50i", ignoreCase = true), source.relativeTo(root).path)
        }
    }
}
