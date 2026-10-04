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

import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

val coreCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

kotlin {
    jvm()
    js { browser() }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }

    android {
        namespace = "com.circuitnext.rastrio.core.${project.name}"
        compileSdk = coreCatalog.findVersion("android-compileSdk").get().requiredVersion.toInt()
        minSdk = coreCatalog.findVersion("android-minSdk").get().requiredVersion.toInt()
        compilerOptions { jvmTarget = JvmTarget.JVM_11 }
        withHostTest { }
    }

    sourceSets {
        commonTest.dependencies {
            implementation(coreCatalog.findLibrary("kotlin-test").get())
        }
    }
}
