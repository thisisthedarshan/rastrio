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

import org.gradle.api.artifacts.ProjectDependency

val architectureCheck = tasks.register<RastrioArchitectureTask>("verifyArchitecture") {
    group = "verification"
    description = "Checks Phase 0 module direction and portable Core imports."
    repositoryRoot.set(layout.projectDirectory)
}

gradle.projectsEvaluated {
    architectureCheck.configure {
        coreSources.from(allprojects.filter { it.path.startsWith(":core:") }.map { module ->
            module.fileTree("src") { include("**/*.kt") }
        })
        projectEdges.set(allprojects.associate { module ->
            module.path to module.configurations
                .flatMap { it.dependencies }
                .filterIsInstance<ProjectDependency>()
                .map { it.path }
                .filter { it != module.path }
                .distinct()
                .sorted()
                .joinToString(",")
        })
        forbiddenDependencies.set(allprojects.filter { it.path.startsWith(":core:") }.flatMap { module ->
            val pluginViolations = listOf(
                "org.jetbrains.compose", "org.jetbrains.kotlin.plugin.compose", "com.android.application",
            ).filter(module.plugins::hasPlugin)
                .map { "${module.path}: forbidden plugin $it" }
            pluginViolations + module.configurations.flatMap { it.dependencies }
                .filter { dependency ->
                    dependency.group == "com.android" ||
                        dependency.group == "org.jetbrains.compose" ||
                        dependency.group?.startsWith("androidx") == true
                }
                .map { "${module.path}: forbidden dependency ${it.group}:${it.name}" }
        })
    }
}

subprojects {
    tasks.matching { it.name == "check" }.configureEach {
        dependsOn(architectureCheck)
    }
}

tasks.matching { it.name == "check" }.configureEach {
    dependsOn(architectureCheck)
}
