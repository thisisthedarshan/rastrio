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

import kotlin.math.round

/**
 * Shared Phase 3 logical precision: 0.000001 mm ticks, nearest with ties to even.
 * Quantize each operand before arithmetic; sum advances in checked integer ticks, never binary
 * floating-point accumulation. Fits/equality compare ticks. Returned millimetres are canonical.
 * The 10^15 tick ceiling keeps integer arithmetic exact and tick/mm round trips stable on all
 * common Double targets (well below 2^53). This is a representability bound, not a page height.
 * Reject invalid inputs before rounding; this policy never relaxes raw resource-limit checks.
 * This is logical precision, not printer-dot rounding. Positive sub-tick heights cannot progress.
 */
object LogicalGeometry {
    private const val ticksPerMm = 1_000_000.0
    internal const val maxTicks = 1_000_000_000_000_000L

    internal fun ticks(mm: Double): Long {
        require(mm.isFinite() && mm >= 0.0)
        val scaled = mm * ticksPerMm
        require(scaled.isFinite() && scaled <= maxTicks.toDouble())
        return round(scaled).toLong()
    }

    internal fun millimetres(ticks: Long): Double {
        require(ticks in 0..maxTicks)
        return ticks.toDouble() / ticksPerMm
    }

    fun normalize(mm: Double): Double = millimetres(ticks(mm))
    fun fits(advanceMm: Double, availableWidthMm: Double): Boolean = ticks(advanceMm) <= ticks(availableWidthMm)

    fun add(firstMm: Double, secondMm: Double): Double {
        val first = ticks(firstMm)
        val second = ticks(secondMm)
        require(first <= maxTicks - second)
        return millimetres(first + second)
    }

    fun subtract(firstMm: Double, secondMm: Double): Double {
        val first = ticks(firstMm)
        val second = ticks(secondMm)
        require(first >= second)
        return millimetres(first - second)
    }

    fun scale(mm: Double, factor: Double): Double {
        require(factor.isFinite() && factor >= 0.0)
        return normalize(normalize(mm) * factor)
    }
}
