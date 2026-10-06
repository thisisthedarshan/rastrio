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

plugins {
    id("rastrio.core")
}

val jvmTestCompilation = kotlin.targets.getByName("jvm").compilations.getByName("test")
tasks.register<JavaExec>("resourceHeapProbe") {
    group = "verification"
    description = "Checks text and structured layout resource amplification in a dedicated 128 MiB JVM."
    dependsOn("jvmTestClasses")
    classpath = files(jvmTestCompilation.output.allOutputs, jvmTestCompilation.runtimeDependencyFiles)
    mainClass.set("com.circuitnext.rastrio.core.layout.LayoutResourceHeapProbe")
    maxHeapSize = "128m"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:document"))
            api(project(":core:text"))
        }
    }
}
