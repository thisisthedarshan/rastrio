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

package com.circuitnext.rastrio.presentation

import com.circuitnext.rastrio.core.document.Length
import com.circuitnext.rastrio.core.document.Orientation
import com.circuitnext.rastrio.core.layout.*
import com.circuitnext.rastrio.core.text.*

/** Controlled logical authoring preset, independent of a physical printer.
 * Metrics come from the bundled JetBrains Mono 2.304 font; see its resource manifest.
 * This deliberately uses the existing restricted ASCII backend, including coverage diagnostics.
 */
fun createAuthoringSession(widthMm: Double = 120.0, orientation: Orientation = Orientation.PORTRAIT): MarkdownAuthoringSession {
    fun face(em: Double, style: TextStyle): ResolvedTypography {
        val ascent = em * 1.020
        val descent = em * 0.300
        return ResolvedTypography("jetbrains-mono-2.304-ascii-$em", style,
            TypographyMetrics(ascent, descent, 0.0, ascent + descent, ascent),
            FixedCellGeometry(em * 0.600, em * 0.600, em * 1.320))
    }
    val body = face(3.0, TextStyle())
    val typography = TypographyContext(body, face(3.0, TextStyle(role = TextRole.CODE)),
        face(3.0, TextStyle(role = TextRole.LIST_MARKER)),
        SnapshotList(listOf(1.8, 1.6, 1.4, 1.2, 1.1, 1.0).map {
            face(3.0 * it, TextStyle(weight = TextWeight.BOLD, role = TextRole.HEADING))
        }))
    return MarkdownAuthoringSession(LayoutConstraints(Length(widthMm), orientation, typography),
        FoundationLayoutEngine(AsciiFixedCellMeasurer()))
}
