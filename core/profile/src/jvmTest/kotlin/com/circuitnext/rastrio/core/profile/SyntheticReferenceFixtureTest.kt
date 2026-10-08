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

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class SyntheticReferenceFixtureTest {
    @Test fun checkedInFixturesMatchPortableCandidatesAndCanonicalProductionEncoding() {
        val codec = TcfgCodec(BaselineTrustedProfileRegistry, syntheticReferenceTestLimits)
        for (candidate in SyntheticReferenceProfiles.all) {
            val name = candidate.profileId.removePrefix("org.rastrio.test.") + ".tcfg"
            val bytes = assertNotNull(javaClass.getResourceAsStream("/$name"), name).use { it.readBytes() }
            val validated = assertIs<ProfileValidationResult.Success<ValidatedPrinterProfile>>(codec.decode(bytes)).value
            assertEquals(candidate, validated.profile, name)
            val encoded = assertIs<ProfileValidationResult.Success<ByteArray>>(codec.encode(validated)).value
            assertContentEquals(bytes, encoded, name)
        }
    }
}
