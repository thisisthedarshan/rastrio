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
import kotlin.test.*

class PrinterProfileFilesTest {
    private val profile = SyntheticReferenceProfiles.narrow.copy(profileId = "org.rastrio.fixture.file-boundary")

    @Test fun exportedConfigurationRoundTripsWithoutInstanceIdentity() {
        val encoded = assertIs<ProfileValidationResult.Success<ByteArray>>(PrinterProfileFiles.encode(profile)).value
        val restored = assertIs<ProfileValidationResult.Success<ValidatedPrinterProfile>>(PrinterProfileFiles.decode(encoded)).value
        assertEquals(profile, restored.profile)
        assertContentEquals(encoded, assertIs<ProfileValidationResult.Success<ByteArray>>(PrinterProfileFiles.encode(restored.profile)).value)
        val json = encoded.decodeToString()
        assertFalse(json.contains("transportLocator")); assertFalse(json.contains("transportKind"))
    }

    @Test fun syntheticAndInvalidCandidatesCannotCrossTheLiveProfileFileBoundary() {
        assertIs<ProfileValidationResult.Failure>(PrinterProfileFiles.encode(SyntheticReferenceProfiles.narrow))
        assertIs<ProfileValidationResult.Failure>(PrinterProfileFiles.encode(profile.copy(
            geometry = profile.geometry.copy(horizontalDpi = 0))))
        assertIs<ProfileValidationResult.Failure>(PrinterProfileFiles.encode(profile.copy(
            nativeQr = NativeQrCapability(true, profileListOf("untrusted.program")))))
    }

    @Test fun strictImportPreservesDuplicateUnknownVersionAndResourceRejections() {
        val encoded = assertIs<ProfileValidationResult.Success<ByteArray>>(PrinterProfileFiles.encode(profile)).value.decodeToString()
        for (source in listOf(
            "{\"format\":\"rastrio-tcfg\"," + encoded.drop(1),
            "{\"unexpected\":true," + encoded.drop(1),
            encoded.replace("\"schemaVersion\":1", "\"schemaVersion\":2"),
        )) assertIs<ProfileValidationResult.Failure>(PrinterProfileFiles.decode(source.encodeToByteArray()))
        val overLimit = PrinterProfileFiles.decode(ByteArray(1024 * 1024 + 1))
        assertTrue(assertIs<ProfileValidationResult.Failure>(overLimit).diagnostics.any { it.code == "PRF138" })
    }
}
