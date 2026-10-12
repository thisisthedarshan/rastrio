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
    description = "Checks long full-width raster streaming in a dedicated 32 MiB JVM."
    dependsOn("jvmTestClasses")
    classpath = files(jvmTestCompilation.output.allOutputs, jvmTestCompilation.runtimeDependencyFiles)
    mainClass.set("com.circuitnext.rastrio.core.raster.RasterResourceHeapProbe")
    maxHeapSize = "32m"
}

tasks.register<JavaExec>("qrResourceHeapProbe") {
    group = "verification"
    description = "Checks version-40 QR generation and band streaming in a 16 MiB JVM."
    dependsOn("jvmTestClasses")
    classpath = files(jvmTestCompilation.output.allOutputs, jvmTestCompilation.runtimeDependencyFiles)
    mainClass.set("com.circuitnext.rastrio.core.raster.QrResourceHeapProbe")
    maxHeapSize = "16m"
}

tasks.register<JavaExec>("qrReferenceDecodeFixtures") {
    group = "verification"
    description = "Exports synthetic final raster fixtures for the independent offline decoder."
    dependsOn("jvmTestClasses")
    classpath = files(jvmTestCompilation.output.allOutputs, jvmTestCompilation.runtimeDependencyFiles)
    mainClass.set("com.circuitnext.rastrio.core.raster.QrReferenceDecodeProbe")
    args(providers.gradleProperty("qrFixtureDir").getOrElse("/tmp/rastrio-qr-decode-fixtures"))
}
