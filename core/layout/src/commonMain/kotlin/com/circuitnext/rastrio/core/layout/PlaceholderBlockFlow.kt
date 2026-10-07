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

package com.circuitnext.rastrio.core.layout

import com.circuitnext.rastrio.core.document.*
import com.circuitnext.rastrio.core.text.LogicalGeometry

internal fun placeholderLength(length: Length, policy: LayoutResourcePolicy, index: Int): Double {
    if (length.unit != LengthUnit.MM || !length.value.isFinite() || length.value <= 0.0)
        abort("LAY105", "Placeholder length is invalid", index)
    if (length.value > policy.maxWidthMm) abort("LAY120", "Placeholder length exceeds layout policy", index)
    val size = LogicalGeometry.normalize(length.value)
    if (size == 0.0) abort("LAY122", "Placeholder cannot make coordinate progress", index)
    return size
}

internal fun blockSpacing(constraints: LayoutConstraints, factor: Double, index: Int): Double {
    val gap = LogicalGeometry.scale(constraints.typography.body.metrics.lineHeightMm, factor)
    if (gap == 0.0) abort("LAY122", "Block spacing cannot make coordinate progress", index)
    return gap
}

/** Square placeholders preserve intent without resolving resources or encoding content. */
internal class PlaceholderBlockFlow(private val constraints: LayoutConstraints, private val charge: (Int, Int) -> Unit) {
    fun layout(block: DocumentBlock, index: Int, originX: Double, width: Double, initialY: Double): Pair<LogicalBlock, Double> {
        charge(1, index)
        val size: Double
        val alignment: Alignment
        val factor: Double
        when (block) {
            is Image -> {
                size = when (val sizing = block.sizing) {
                    AutoSizing, FitWidthSizing -> width
                    is RequestedWidthSizing -> placeholderLength(sizing.width, constraints.resources, index)
                }
                alignment = block.alignment
                factor = TextLayoutPolicyV1.imageSpacingLineFactor
            }
            is QrCode -> {
                size = block.requestedSize?.let { placeholderLength(it, constraints.resources, index) }
                    ?: minOf(TextLayoutPolicyV1.qrDefaultSizeMm, width)
                alignment = block.alignment
                factor = TextLayoutPolicyV1.qrSpacingLineFactor
            }
            else -> error("Not a placeholder")
        }
        if (!LogicalGeometry.fits(size, width)) abort("LAY107", "Placeholder exceeds available content width", index)
        val spare = LogicalGeometry.subtract(width, size)
        val x = LogicalGeometry.add(originX, when (alignment) {
            Alignment.LEFT -> 0.0
            Alignment.CENTER -> LogicalGeometry.scale(spare, 0.5)
            Alignment.RIGHT -> spare
        })
        val gap = blockSpacing(constraints, factor, index)
        val y = LogicalGeometry.add(initialY, gap)
        val bounds = LogicalBounds(x, y, size, size)
        val output = when (block) {
            is Image -> LogicalImagePlaceholder(index, bounds, alignment, block.asset, block.sizing, block.altText, gap, gap)
            is QrCode -> LogicalQrPlaceholder(index, bounds, alignment, block.payload, block.errorCorrection, block.requestedSize, gap, gap)
        }
        return output to LogicalGeometry.add(LogicalGeometry.add(y, size), gap)
    }
}
