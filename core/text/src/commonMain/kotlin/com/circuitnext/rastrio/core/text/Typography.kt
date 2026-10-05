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

/** Defensive, read-only collection value. Elements must themselves be immutable domain values. */
class SnapshotList<out T>(values: Collection<T>) : AbstractList<T>() {
    private val snapshot = values.toList()
    override val size: Int get() = snapshot.size
    override fun get(index: Int): T = snapshot[index]
}

enum class TextWeight { NORMAL, BOLD }
enum class TextRole { BODY, HEADING, CODE, LIST_MARKER }
data class TextStyle(
    val weight: TextWeight = TextWeight.NORMAL,
    val emphasis: Boolean = false,
    val strike: Boolean = false,
    val underline: Boolean = false,
    val role: TextRole = TextRole.BODY,
)

/**
 * All distances are portable millimetres. Positive y points down from the line-box top.
 * Ascent/descent are positive distances above/below the baseline; gap is included in height.
 * Baseline placement is explicit, with no host font padding or lazily queried font object.
 */
data class TypographyMetrics(
    val ascentMm: Double,
    val descentMm: Double,
    val lineGapMm: Double,
    val lineHeightMm: Double,
    val baselineOffsetMm: Double,
) {
    init {
        require(listOf(ascentMm, descentMm, lineGapMm, lineHeightMm, baselineOffsetMm).all { it.isFinite() && it >= 0.0 })
        require(lineHeightMm > 0.0)
        require(ascentMm + descentMm + lineGapMm <= lineHeightMm)
        require(baselineOffsetMm >= ascentMm && baselineOffsetMm + descentMm <= lineHeightMm)
    }
}

/** Supplied geometry, not inferred from a visually similar font; not a native/raster decision. */
data class FixedCellGeometry(val advanceMm: Double, val nominalWidthMm: Double, val nominalHeightMm: Double) {
    init { require(listOf(advanceMm, nominalWidthMm, nominalHeightMm).all { it.isFinite() && it > 0.0 }) }
}

/** Identity names controlled metric/font/backend configuration. No platform resource handle. */
data class ResolvedTypography(
    val identity: String,
    val style: TextStyle,
    val metrics: TypographyMetrics,
    val fixedCell: FixedCellGeometry? = null,
) {
    init { require(identity.isNotBlank() && identity.length <= 1024) }
}

data class TypographyContext(
    val body: ResolvedTypography,
    val code: ResolvedTypography,
    val listMarker: ResolvedTypography,
    val headings: SnapshotList<ResolvedTypography> = SnapshotList(List(6) { body }),
) {
    init { require(headings.size == 6) }
    fun heading(level: Int): ResolvedTypography {
        require(level in 1..6)
        return headings[level - 1]
    }
}
