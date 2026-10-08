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

package com.circuitnext.rastrio.core.printer

import com.circuitnext.rastrio.core.document.ThermalDocument
import com.circuitnext.rastrio.core.document.Separator
import com.circuitnext.rastrio.core.profile.*

/** Reproduces the reviewed retained-band input in a dedicated bounded JVM. */
object PreparationResourceHeapProbe {
    @JvmStatic
    fun main(args: Array<String>) {
        val base = SyntheticReferenceProfiles.wide80mm
        val profile = base.copy(
            geometry = base.geometry.copy(printableWidthDots = 1024),
            nativeText = base.nativeText.copy(fonts = ProfileList(base.nativeText.fonts.map {
                it.copy(cellHeightDots = 256, lineAdvanceDots = 256)
            })),
            raster = base.raster.copy(maxWidthDots = 1024),
        )
        val input = ThermalDocument(blocks = List(40_000) { Separator })
        val result = BasicPreparationEngine().prepare(input, profile, PrintOptions(heightScale = 2))
        check(result is PreparationResult.Failure)
        check(result.diagnostics.single().code == "PRN120")
        check(result.diagnostics.single().message == "Resident graphics exceed preparation policy")
        println("40,000 finalized separators: resident PRN120 under 128 MiB heap")
    }
}
