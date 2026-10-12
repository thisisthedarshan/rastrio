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
    description = "Checks the finalized graphic resident limit in a dedicated 128 MiB JVM."
    dependsOn("jvmTestClasses")
    classpath = files(jvmTestCompilation.output.allOutputs, jvmTestCompilation.runtimeDependencyFiles)
    mainClass.set("com.circuitnext.rastrio.core.printer.PreparationResourceHeapProbe")
    maxHeapSize = "128m"
}

tasks.register<JavaExec>("generatePhase111213HardwareFixtures") {
    group = "verification"
    description = "Writes strict portable semantic QR hardware fixtures and verifies synthetic preparation."
    dependsOn("jvmTestClasses")
    classpath = files(jvmTestCompilation.output.allOutputs, jvmTestCompilation.runtimeDependencyFiles)
    mainClass.set("com.circuitnext.rastrio.core.printer.Phase111213HardwareFixtures")
    args(rootProject.file("test-fixtures/hardware").absolutePath)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:layout"))
            api(project(":core:profile"))
            api(project(":core:raster"))
            implementation(project(":core:text"))
        }
    }
}
