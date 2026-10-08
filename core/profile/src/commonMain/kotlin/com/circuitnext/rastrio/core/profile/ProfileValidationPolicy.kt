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

package com.circuitnext.rastrio.core.profile

/**
 * Trusted operational policy, never profile data or permanent schema semantics.
 * Numerical/collection ceilings are mandatory until measured application defaults are established.
 * The string baseline is the provisional 64 KiB UTF-8 policy from RESOURCE_LIMITS.md §10.3.
 */
data class PrinterProfileValidationLimits(
    val maxDpi: Int,
    val maxPrintableWidthDots: Int,
    val maxFontDimensionDots: Int,
    val maxTextScale: Int,
    val maxRasterBandHeightDots: Int,
    val maxRasterBandBytes: Long,
    val maxQrModuleSize: Int,
    val maxBurstBytes: Int,
    val maxPauseMs: Int,
    val maxCollectionEntries: Int,
    val maxStringBytes: Int = 64 * 1024,
)

enum class StrategyPurpose { INITIALIZATION, FONT_SELECTION, CODE_PAGE_SELECTION, RASTER, QR, BARCODE, CUT, STATUS_QUERY }

/** Absent selector/default line advance is accepted only with explicit trusted metadata. */
data class DialectDefinition(
    val defaultFontId: String? = null,
    val defaultRepertoire: String? = null,
    val defaultFontLineAdvanceFromCellHeight: Boolean = false,
    val maxWidthScale: Int? = null,
    val maxHeightScale: Int? = null,
)

data class StrategyDefinition(
    val purpose: StrategyPurpose,
    val dialectIds: ProfileList<String>,
    val selectorParameterRange: IntRange? = null,
    val fontLineAdvanceFromCellHeight: Boolean = false,
)

data class QuirkDefinition(val dialectIds: ProfileList<String>)

/**
 * Application-owned, deterministic metadata lookup. Implementations must be side-effect free.
 * No imported callbacks, executable programs, reflection targets or external resource resolution.
 * The production strategy catalog/encoders are deliberately outside Phase 5A.
 */
interface TrustedProfileRegistry {
    fun dialect(id: String): DialectDefinition?
    fun strategy(id: String): StrategyDefinition?
    fun knowsRepertoire(id: String): Boolean
    fun knowsQrModel(id: String): Boolean
    fun knowsBarcodeSymbology(id: String): Boolean
    fun quirk(id: String): QuirkDefinition?
}
