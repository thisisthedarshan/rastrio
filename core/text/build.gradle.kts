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


kotlin.applyDefaultHierarchyTemplate {
    common {
        group("jvmAndAndroid") {
            withCompilations {
                it.target.platformType == org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.jvm ||
                    it.target.platformType == org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.androidJvm
            }
        }
    }
}

val javaToolchains = extensions.getByType<JavaToolchainService>()
val nativeBuildDirectory = layout.buildDirectory.dir("native-text")
val configureNativeText by tasks.registering(Exec::class) {
    inputs.files(fileTree("native"))
    outputs.file(nativeBuildDirectory.map { it.file("CMakeCache.txt") })
    val javaLauncher = javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(JavaVersion.current().majorVersion))
    }
    val jdkHome = javaLauncher.get().metadata.installationPath.asFile
    val platform = when {
        System.getProperty("os.name").startsWith("Windows") -> "win32"
        System.getProperty("os.name").startsWith("Mac") -> "darwin"
        else -> "linux"
    }
    commandLine("cmake", "-S", file("native"), "-B", nativeBuildDirectory.get().asFile,
        "-DCMAKE_BUILD_TYPE=Release", "-DJAVA_HOME=$jdkHome",
        "-DJAVA_INCLUDE_PATH=${jdkHome.resolve("include")}",
        "-DJAVA_INCLUDE_PATH2=${jdkHome.resolve("include/$platform")}")
}
val buildNativeText by tasks.registering(Exec::class) {
    dependsOn(configureNativeText)
    inputs.files(fileTree("native"))
    outputs.file(nativeBuildDirectory.map { it.file(System.mapLibraryName("rastrio_text")) })
    commandLine("cmake", "--build", nativeBuildDirectory.get().asFile, "--config", "Release", "-j2")
}
tasks.named<ProcessResources>("jvmProcessResources") {
    dependsOn(buildNativeText)
    from(nativeBuildDirectory) {
        include(System.mapLibraryName("rastrio_text"))
        into("rastrio-native")
    }
}
tasks.withType<Test>().configureEach {
    dependsOn(buildNativeText)
    systemProperty("java.library.path", nativeBuildDirectory.get().asFile.absolutePath)
    systemProperty("rastrio.unicode.data", file("unicode").absolutePath)
}


val jvmTestCompilation = kotlin.targets.getByName("jvm").compilations.getByName("test")
tasks.register<JavaExec>("resourceHeapProbe") {
    group = "verification"
    description = "Checks bounded controlled-font measurement in a dedicated 32 MiB JVM."
    dependsOn("jvmTestClasses", buildNativeText)
    classpath = files(jvmTestCompilation.output.allOutputs, jvmTestCompilation.runtimeDependencyFiles)
    mainClass.set("com.circuitnext.rastrio.core.text.TextResourceHeapProbe")
    maxHeapSize = "32m"
    systemProperty("java.library.path", nativeBuildDirectory.get().asFile.absolutePath)
}
