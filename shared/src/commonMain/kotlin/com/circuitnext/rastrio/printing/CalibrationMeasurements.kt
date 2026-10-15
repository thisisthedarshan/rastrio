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

package com.circuitnext.rastrio.printing

import com.circuitnext.rastrio.core.printer.Feed
import com.circuitnext.rastrio.core.printer.PreparedPrint
import com.circuitnext.rastrio.core.printer.RasterOperation
import kotlin.math.roundToInt

/** Operator measurements belong to one exact preview, worksheet and physical device. */
class CalibrationMeasurements private constructor(
    private val draft: ProfileDraft,
    private val plan: PreparedPrint,
    private val deviceLocator: String,
    val widthDots: Int,
    val heightDots: Int,
) {
    data class Result internal constructor(val horizontalDots: Int, val horizontalMm: Double,
        val verticalDots: Int, val verticalMm: Double, val horizontalDpi: Double, val verticalDpi: Double)

    fun measure(horizontalDots: Int, horizontalMm: Double, verticalDots: Int, verticalMm: Double): Result? {
        fun density(dots: Int, mm: Double, extent: Int): Double? {
            if (dots !in 8 until extent || dots % 8 != 0 || !mm.isFinite() || mm <= 0 || mm > 1024) return null
            return (dots * 25.4 / mm).takeIf { it.isFinite() && it in 1.0..600.0 }
        }
        val horizontal = density(horizontalDots, horizontalMm, widthDots) ?: return null
        val vertical = density(verticalDots, verticalMm, heightDots) ?: return null
        return Result(horizontalDots, horizontalMm, verticalDots, verticalMm, horizontal, vertical)
    }

    fun apply(currentDraft: ProfileDraft, currentPlan: PreparedPrint, currentDevice: String, result: Result): ProfileDraft? {
        if (currentDraft != draft || currentPlan !== plan || currentDevice != deviceLocator ||
            measure(result.horizontalDots, result.horizontalMm, result.verticalDots, result.verticalMm) != result) return null
        val source = "HW-037 operator paper measurement: horizontal ${result.horizontalDots} dots / ${result.horizontalMm} mm; " +
            "vertical ${result.verticalDots} dots / ${result.verticalMm} mm. DPI rounded to nearest integer; head width unchanged; repeat and verify separately."
        val notes = listOf(draft.evidenceNotes, source).filter { it.isNotBlank() }.joinToString("\n")
        if (notes.length > 4096) return null
        val horizontal = result.horizontalDpi.roundToInt().toString()
        val vertical = result.verticalDpi.roundToInt().toString()
        val suggestions = listOf(ProfileSuggestion("geometry.horizontalDpi", horizontal, InspectionEvidence.OPERATOR_REPORTED, source, deviceLocator),
            ProfileSuggestion("geometry.verticalDpi", vertical, InspectionEvidence.OPERATOR_REPORTED, source, deviceLocator))
        val updated = draft.withField("geometry.horizontalDpi", horizontal).withField("geometry.verticalDpi", vertical)
            .withEvidence("geometry", ProfileEvidence.HARDWARE_OBSERVED)
            .copy(evidenceNotes = notes, verifications = draft.verifications.filterNot { it.area == "geometry" })
        val combined = updated.suggestions.filterNot { it.path in suggestions.map { value -> value.path } } + suggestions
        if (combined.size > 32) return null
        return updated.withSuggestions(combined).takeIf { runCatching { it.encodeLocal() }.isSuccess }
    }

    companion object {
        fun capture(draft: ProfileDraft, plan: PreparedPrint, deviceLocator: String, widthDots: Int, heightDots: Int): CalibrationMeasurements? {
            if (deviceLocator.isBlank() || deviceLocator.length > 1024 || widthDots !in 9..1024 || heightDots !in 9..64 ||
                plan.target.profileId != draft["profileId"] || plan.target.profileRevision != draft["profileRevision"].toIntOrNull()) return null
            val bands = plan.operations.filterIsInstance<RasterOperation>()
            // Logical image spacing may feed outside the measured contiguous raster.
            if (bands.isEmpty() || plan.operations.any { it !is RasterOperation && it !is Feed } || bands.any { it.graphic.widthDots != widthDots } ||
                bands.sumOf { it.graphic.heightDots } != heightDots ||
                bands.zipWithNext().any { (first, second) -> first.bounds.bottom != second.bounds.y }) return null
            return CalibrationMeasurements(draft, plan, deviceLocator, widthDots, heightDots)
        }
    }
}
