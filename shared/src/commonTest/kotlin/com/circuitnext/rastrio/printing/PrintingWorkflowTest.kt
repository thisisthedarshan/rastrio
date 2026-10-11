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

import com.circuitnext.rastrio.core.printer.BasicPreparationEngine
import com.circuitnext.rastrio.core.printer.PreparationResult
import com.circuitnext.rastrio.core.profile.PrinterBufferGuidance
import com.circuitnext.rastrio.core.profile.SyntheticReferenceProfiles
import com.circuitnext.rastrio.presentation.createAuthoringSession
import com.circuitnext.rastrio.presentation.semanticDocument
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class PrintingWorkflowTest {
    private val profile = SyntheticReferenceProfiles.narrow.copy(profileId = "org.rastrio.fixture.session")
    private val instance = PrinterInstance("local", "Fixture", PrinterTransportKind.DEVELOPMENT, "stable-locator",
        profile.profileId, profile.profileRevision)

    private fun prepared() = assertIs<PreparationResult.Success>(BasicPreparationEngine().prepare(
        createAuthoringSession().also { it.edit("Receipt"); it.compile() }.state.result.semanticDocument()!!,
        profile,
    )).print

    @Test fun liveGuardRejectsSyntheticMissingStaleAndMismatchedTargets() {
        val print = prepared()
        assertTrue(canPrintPrepared(instance, profile, print, profile))
        assertFalse(canPrintPrepared(null, profile, print, profile))
        assertFalse(canPrintPrepared(instance, null, print, profile))
        assertFalse(canPrintPrepared(instance, profile, null, profile))
        assertFalse(canPrintPrepared(instance, SyntheticReferenceProfiles.narrow, print, profile))
        assertFalse(canPrintPrepared(instance.copy(profileRevision = 2), profile, print, profile))
        assertFalse(canPrintPrepared(instance, profile.copy(profileRevision = 2), print, profile))
        assertFalse(canPrintPrepared(instance, profile.copy(geometry = profile.geometry.copy(printableWidthDots = 376)), print, profile))
        val changedCapabilities = profile.copy(nativeText = profile.nativeText.copy(
            styles = profile.nativeText.styles.copy(bold = false)))
        assertFalse(canPrintPrepared(instance, changedCapabilities, print, profile))
    }

    @Test fun reselectedDeviceAndRefreshedIdRetainTheSameFailureEvidence() = runTest {
        var creations = 0
        val fake = FakePrinterTransport(failAfterBytes = 2)
        val store = PrinterSessionStore { creations++; fake }
        val first = assertNotNull(store.session(instance))
        first.print(instance, prepared())
        val previous = first.state.value
        store.cancelAll()
        val refreshed = assertNotNull(store.session(instance.copy(id = "refreshed-id")))
        assertSame(first, refreshed)
        assertEquals(previous, refreshed.state.value)
        refreshed.print(instance.copy(id = "refreshed-id"), prepared())
        assertEquals(1, creations)
        assertEquals(1, fake.connections)
    }

    @Test fun sessionRegistryIsBoundedWithoutEvictingAmbiguousEvidence() {
        val store = PrinterSessionStore {
            object : PrinterTransport {
                override val constraints = TransportConstraints(100)
                override suspend fun connect(instance: PrinterInstance) = Unit
                override suspend fun write(bytes: ByteArray, offset: Int, length: Int) = length
                override fun close() = Unit
            }
        }
        val first = assertNotNull(store.session(instance))
        repeat(99) { assertNotNull(store.session(instance.copy(transportLocator = "locator-$it"))) }
        assertNull(store.session(instance.copy(transportLocator = "one-over")))
        assertSame(first, store.session(instance))
    }

    @Test fun knownProfileGuidanceIsPassedSeparatelyFromRasterBanding() {
        val unknown = profile.copy(printerBuffer = null).writeGuidance()
        assertNull(unknown.maxWriteBytes)
        assertNull(unknown.minWriteIntervalMillis)
        assertEquals(PrinterWriteGuidance(128, 5),
            profile.copy(printerBuffer = PrinterBufferGuidance(128, 5)).writeGuidance())
    }
}
