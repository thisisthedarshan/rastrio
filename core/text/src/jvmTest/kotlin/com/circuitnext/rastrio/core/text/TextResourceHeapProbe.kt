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

/** Separate 32 MiB JVM; native handles remain scoped to bounded calls. */
object TextResourceHeapProbe {
    @JvmStatic fun main(args: Array<String>) {
        val backend = BundledRasterTextBackend.create()
        val typography = backend.typography()
        val maximum = backend.measure(TextMeasureRequest("Ж".repeat(65536), typography))
        check(maximum is TextMeasureResult.Success && maximum.measurement.clusters.size == 65536)
        println("maximum uniform 65536-unit text: success")
        val exact = "Жक".repeat(128)
        check(backend.measure(TextMeasureRequest(exact, typography)) is TextMeasureResult.Success)
        println("exact 256 fallback runs: success")
        for (text in listOf(exact + "Ж", "Жक".repeat(32768))) {
            val rejected = backend.measure(TextMeasureRequest(text, typography))
            check(rejected is TextMeasureResult.Failure && rejected.diagnostics.single().code == "TXT120")
        }
        println("257 and 65536 alternating fallback runs: TXT120")
        var polls = 0
        val cancelled = backend.measure(TextMeasureRequest("Жक".repeat(16), typography)) { ++polls >= 10 }
        check(cancelled is TextMeasureResult.Failure && cancelled.diagnostics.single().code == "TXT130" && polls == 10)
        println("cancellation during fallback measurement: TXT130")
        val raster = backend.rasterize(RasterTextRequest(exact + "Ж", typography, 8192, 64, 40, 203, 203))
        check(raster is RasterTextResult.Failure && raster.diagnostics.single().code == "TXT120")
        println("over-limit raster work: TXT120 before native rendering")
    }
}
