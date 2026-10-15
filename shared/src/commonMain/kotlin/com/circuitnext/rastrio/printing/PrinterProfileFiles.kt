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

import com.circuitnext.rastrio.core.profile.*

/** Application file policy; portable syntax and capability validation remain Core-owned. */
object PrinterProfileFiles {
    private val limits = PrinterProfileValidationLimits(600, 1024, 256, 8, 1024, 131072, 16, 65536, 1000, 16)
    private val validator = PrinterProfileValidator(BaselineTrustedProfileRegistry, limits)
    private val codec = TcfgCodec(BaselineTrustedProfileRegistry, limits)

    fun validate(profile: PrinterProfile): ProfileValidationResult<ValidatedPrinterProfile> =
        physical(validator.validate(profile))

    fun resolve(profile: PrinterProfile, overrides: PrinterProfileOverride): ProfileValidationResult<EffectivePrinterProfile> =
        when (val base = validate(profile)) {
            is ProfileValidationResult.Failure -> base
            is ProfileValidationResult.Success -> validator.resolve(base.value, overrides)
        }

    fun decode(bytes: ByteArray): ProfileValidationResult<ValidatedPrinterProfile> = physical(codec.decode(bytes))

    fun encode(profile: PrinterProfile): ProfileValidationResult<ByteArray> = when (val result = validate(profile)) {
        is ProfileValidationResult.Failure -> result
        is ProfileValidationResult.Success -> codec.encode(result.value)
    }

    private fun physical(result: ProfileValidationResult<ValidatedPrinterProfile>): ProfileValidationResult<ValidatedPrinterProfile> =
        when (result) {
            is ProfileValidationResult.Failure -> result
            is ProfileValidationResult.Success -> if (isSyntheticProfile(result.value.profile))
                ProfileValidationResult.Failure(profileListOf(ProfileDiagnostic("PRF150", "$.profileId",
                    "Synthetic test profiles cannot be assigned through physical printer profile files")))
                else result
        }
}
