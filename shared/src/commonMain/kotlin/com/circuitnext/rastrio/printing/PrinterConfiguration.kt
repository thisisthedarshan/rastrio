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

import com.circuitnext.rastrio.core.printer.*
import com.circuitnext.rastrio.core.profile.*

/** Private per-printer preferences; feed units are printer dots, never an inferred tear-bar distance. */
data class PrinterPrintDefaults(
    val cutIntent: CutIntent = CutIntent.NONE,
    val cutMode: CutMode = CutMode.FULL,
    val finalFeedDots: Int = 0,
) {
    init { require(finalFeedDots in 0..MAX_FINAL_FEED_DOTS) }
    fun toPrintOptions(): PrintOptions = PrintOptions(cutIntent = cutIntent, cutMode = cutMode, finalFeedDots = finalFeedDots)
    fun supports(profile: PrinterProfile): Boolean = cutIntent != CutIntent.AUTOMATIC ||
        (profile.cutter.supported && (if (cutMode == CutMode.FULL) "full" else "partial") in profile.cutter.modes)
    // Application safety ceiling, not a measured feed or tear-bar distance.
    companion object { const val MAX_FINAL_FEED_DOTS = 100_000 }
}

enum class PrinterPresence {
    NOT_CHECKED, CHECKING, DETECTED, NOT_DETECTED, BLUETOOTH_DISABLED, PERMISSION_NEEDED, UNABLE_TO_CHECK,
}

/** Exact immutable base capability snapshot; equal identity strings alone cannot authorize a different profile. */
data class ConfiguredPrinter(val instance: PrinterInstance, val baseProfile: PrinterProfile) {
    fun effectiveProfile(): PrinterProfile? {
        if (instance.profileId != baseProfile.profileId || instance.profileRevision != baseProfile.profileRevision) return null
        return when (val result = PrinterProfileFiles.resolve(baseProfile, instance.overrides ?: PrinterProfileOverride())) {
            is ProfileValidationResult.Success -> result.value.profile.takeIf { instance.defaults.supports(it) }
            is ProfileValidationResult.Failure -> null
        }
    }
}
