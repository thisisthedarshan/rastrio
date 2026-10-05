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

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class RastrioArchitectureTask : DefaultTask() {
    @get:Input
    abstract val projectEdges: MapProperty<String, String>

    @get:Input
    abstract val forbiddenDependencies: ListProperty<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val coreSources: ConfigurableFileCollection

    @get:Internal
    abstract val repositoryRoot: DirectoryProperty

    @TaskAction
    fun verify() {
        val expected = mapOf(
            ":core:document" to emptySet<String>(),
            ":core:markdown" to setOf(":core:document"),
            ":core:templates" to setOf(":core:document"),
            ":core:text" to emptySet(),
            ":core:layout" to setOf(":core:document", ":core:text"),
            ":core:raster" to emptySet(),
            ":core:profile" to emptySet(),
            ":core:printer" to setOf(":core:layout", ":core:profile", ":core:raster", ":core:text"),
            ":core:preview" to setOf(":core:layout", ":core:printer"),
            ":core:escpos" to setOf(":core:printer"),
        )
        val actual = projectEdges.get().mapValues { (_, value) ->
            value.split(',').filter { it.isNotEmpty() }.toSet()
        }
        val violations = forbiddenDependencies.get().toMutableList()

        for ((module, required) in expected) {
            val observed = actual[module]
            if (observed != required) {
                violations += "$module: expected $required, found $observed"
            }
        }

        val sharedDependencies = actual[":shared"].orEmpty()
        if (sharedDependencies.any { it !in expected }) {
            violations += ":shared may depend only on portable Core modules: $sharedDependencies"
        }

        for (app in listOf(":androidApp", ":desktopApp", ":webApp")) {
            val dependencies = actual[app].orEmpty()
            if (":shared" !in dependencies) {
                violations += "$app must depend on :shared"
            }
            if (dependencies.any { it != ":shared" && it !in expected }) {
                violations += "$app has an unexpected project dependency: $dependencies"
            }
        }

        val forbiddenImports = listOf(
            "android.", "androidx.", "org.jetbrains.compose.",
            "java.awt.", "javax.swing.", "com.circuitnext.rastrio.shared.",
            "org.jetbrains.skia.", "org.jetbrains.skiko.", "kotlinx.browser.", "org.w3c.dom.",
        )
        for (source in coreSources.files) {
            source.readLines().forEachIndexed { index, line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("import ")) {
                    val imported = trimmed.removePrefix("import ")
                    if (forbiddenImports.any(imported::startsWith)) {
                        violations += "${source.relativeTo(repositoryRoot.get().asFile)}:${index + 1}: forbidden Core import $imported"
                    }
                }
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(violations.joinToString("\n", "Architecture violations:\n"))
        }
    }
}
