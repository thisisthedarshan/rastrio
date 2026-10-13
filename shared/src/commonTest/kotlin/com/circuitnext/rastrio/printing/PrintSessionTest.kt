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

import com.circuitnext.rastrio.core.escpos.EncodedByteSink
import com.circuitnext.rastrio.core.escpos.EncodingResult
import com.circuitnext.rastrio.core.escpos.EscPosEncoder
import com.circuitnext.rastrio.core.printer.PreparedPrint
import com.circuitnext.rastrio.core.preview.PrintPreviewTileResult
import com.circuitnext.rastrio.presentation.PhysicalPreviewResult
import com.circuitnext.rastrio.presentation.PhysicalPreviewSession
import com.circuitnext.rastrio.presentation.createAuthoringSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class PrintSessionTest {
    private fun ready(source: String = "Receipt\n\nTotal 12.50"): PhysicalPreviewResult.Ready {
        val authoring = createAuthoringSession()
        authoring.edit(source)
        authoring.compile()
        val physical = PhysicalPreviewSession()
        physical.prepare(authoring.state.result)
        return assertIs<PhysicalPreviewResult.Ready>(physical.state.result)
    }

    private fun instance(print: PreparedPrint): PrinterInstance = PrinterInstance(
        "fixture", "Development fixture", PrinterTransportKind.DEVELOPMENT, "opaque-fixture",
        print.target.profileId, print.target.profileRevision,
    )

    @Test fun markdownPreviewAndTransmissionUseTheSameFinalizedPlan() = runTest {
        val ready = ready("Café\n\n**Total 12.50**")
        val expected = mutableListOf<Byte>()
        assertIs<EncodingResult.Success>(EscPosEncoder().encode(ready.print, EncodedByteSink {
            assertTrue(expected.size + it.size <= 64 * 1024)
            expected.addAll(it.toList())
            true
        }))
        val transport = FakePrinterTransport(TransportConstraints(maxWriteBytes = 3))
        val session = PrintSession(transport)
        session.print(instance(ready.print), ready.print)
        assertContentEquals(expected.toByteArray(), transport.capturedBytes())
        assertEquals(ready.print.target.printableWidthDots, ready.preview.widthDots)
        val tile = assertIs<PrintPreviewTileResult.Success>(ready.preview.tile(0)).tile
        for (primitive in tile.primitives) assertTrue(ready.print.operations.any { it === primitive.operation })
        assertEquals(expected.size.toLong(), session.state.value.bytesTransferred)
        assertEquals(PrintSessionPhase.TRANSMITTED_UNCONFIRMED, session.state.value.phase)
        assertTrue(session.state.value.outputMayExist)
        assertEquals(3, transport.largestWrite)
        assertFalse(transport.connected)
    }

    @Test fun transitionSequenceDoesNotClaimPhysicalPrinting() = runTest {
        val plan = ready().print
        val session = PrintSession(FakePrinterTransport())
        val phases = mutableListOf<PrintSessionPhase>()
        val collect = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            session.state.collect { phases.add(it.phase) }
        }
        session.print(instance(plan), plan)
        assertEquals(
            listOf(PrintSessionPhase.IDLE, PrintSessionPhase.VALIDATING, PrintSessionPhase.CONNECTING,
                PrintSessionPhase.READY, PrintSessionPhase.SENDING, PrintSessionPhase.TRANSMITTED_UNCONFIRMED),
            phases.distinct(),
        )
        collect.cancel()
    }

    @Test fun policyTakesEverySafeConstraintAndPreservesUnknownGuidance() {
        val app = PrintSessionPolicy(maxWriteBytes = 100, writeTimeoutMillis = 500, connectTimeoutMillis = 800)
        val platform = TransportConstraints(200, 1000, 400, 3)
        val unknown = app.intersect(platform, PrinterWriteGuidance())
        assertEquals(TransportConstraints(100, 500, 400, 3), unknown)
        assertEquals(TransportConstraints(20, 500, 400, 7),
            app.intersect(platform, PrinterWriteGuidance(20, 7)))
        assertNull(PrinterWriteGuidance().maxWriteBytes)
        assertNull(PrinterWriteGuidance().minWriteIntervalMillis)
        assertFailsWith<IllegalArgumentException> { PrintSessionPolicy(maxWriteBytes = 65537) }
        assertFailsWith<IllegalArgumentException> { TransportConstraints(0) }
    }

    @Test fun mismatchFailsBeforeConnectionWithoutRevealingLocator() = runTest {
        val plan = ready().print
        val transport = FakePrinterTransport()
        val session = PrintSession(transport)
        session.print(instance(plan).copy(profileRevision = plan.target.profileRevision + 1), plan)
        assertEquals(PrintSessionPhase.FAILED_BEFORE_TRANSMISSION, session.state.value.phase)
        assertEquals("PROFILE_MISMATCH", session.state.value.diagnosticCode)
        assertEquals(0, transport.connections)
        assertFalse(session.state.value.outputMayExist)
        assertFalse(session.state.value.toString().contains("opaque-fixture"))
    }

    @Test fun connectFailureGuaranteesNoAttemptedOutput() = runTest {
        val plan = ready().print
        val transport = object : PrinterTransport {
            override val constraints = TransportConstraints(10)
            var closes = 0
            override suspend fun connect(instance: PrinterInstance): Unit = error("private address")
            override suspend fun write(bytes: ByteArray, offset: Int, length: Int): Int = error("Unexpected write")
            override fun close() { closes++ }
        }
        val session = PrintSession(transport)
        session.print(instance(plan), plan)
        assertEquals(PrintSessionPhase.FAILED_BEFORE_TRANSMISSION, session.state.value.phase)
        assertEquals("TRANSPORT_FAILURE", session.state.value.diagnosticCode)
        assertFalse(session.state.value.outputMayExist)
        assertEquals(1, transport.closes)
    }

    @Test fun firstWriteFailureIsAmbiguousEvenWithZeroAcknowledgedBytes() = runTest {
        val plan = ready().print
        val transport = FakePrinterTransport(failAfterBytes = 0)
        val session = PrintSession(transport)
        session.print(instance(plan), plan)
        assertEquals(PrintSessionPhase.FAILED_PARTIAL_OR_AMBIGUOUS, session.state.value.phase)
        assertEquals(0L, session.state.value.bytesTransferred)
        assertTrue(session.state.value.outputMayExist)
        assertEquals(1, transport.writes)
    }

    @Test fun partialWriteIsCountedAndNeverAutomaticallyReplayed() = runTest {
        val plan = ready().print
        val transport = FakePrinterTransport(failAfterBytes = 7)
        val session = PrintSession(transport)
        session.print(instance(plan), plan)
        assertEquals(7L, session.state.value.bytesTransferred)
        assertEquals(PrintSessionPhase.FAILED_PARTIAL_OR_AMBIGUOUS, session.state.value.phase)
        session.print(instance(plan), plan)
        assertEquals(1, transport.connections)
        assertFalse(session.confirmRetry())
        assertTrue(session.requestRetry())
        assertEquals(PrintSessionPhase.AWAITING_RETRY_CONFIRMATION, session.state.value.phase)
        session.print(instance(plan), plan)
        assertEquals(1, transport.connections)
        assertTrue(session.confirmRetry())
        assertEquals(PrintSessionPhase.RETRY_REQUESTED, session.state.value.phase)
        session.print(instance(plan), plan)
        assertEquals(2, transport.connections)
    }

    @Test fun repeatedCancellationDuringWriteClosesAndPreservesAmbiguity() = runTest {
        val plan = ready().print
        val transport = FakePrinterTransport(writeDelayMillis = 1000)
        val session = PrintSession(transport)
        val job = launch { session.print(instance(plan), plan) }
        runCurrent()
        assertEquals(1, transport.writes)
        session.cancel()
        session.cancel()
        job.join()
        assertEquals(PrintSessionPhase.CANCELLED_PARTIAL_OR_AMBIGUOUS, session.state.value.phase)
        assertEquals(0L, session.state.value.bytesTransferred)
        assertFalse(transport.connected)
        session.print(instance(plan), plan)
        assertEquals(1, transport.connections)
    }

    @Test fun cancellationBeforeConnectionCompletesDoesNotClaimOutput() = runTest {
        val plan = ready().print
        val entered = CompletableDeferred<Unit>()
        val transport = object : PrinterTransport {
            override val constraints = TransportConstraints(10)
            override suspend fun connect(instance: PrinterInstance) { entered.complete(Unit); awaitCancellation() }
            override suspend fun write(bytes: ByteArray, offset: Int, length: Int): Int = error("Unexpected write")
            override fun close() = Unit
        }
        val session = PrintSession(transport)
        val job = launch { session.print(instance(plan), plan) }
        entered.await()
        session.cancel()
        job.join()
        assertEquals(PrintSessionPhase.CANCELLED_BEFORE_TRANSMISSION, session.state.value.phase)
        assertFalse(session.state.value.outputMayExist)
    }

    @Test fun stalledWriteTimesOutWithoutResend() = runTest {
        val plan = ready().print
        val transport = FakePrinterTransport(TransportConstraints(10, writeTimeoutMillis = 50), writeDelayMillis = 1000)
        val session = PrintSession(transport)
        session.print(instance(plan), plan)
        assertEquals("TRANSPORT_TIMEOUT", session.state.value.diagnosticCode)
        assertEquals(PrintSessionPhase.FAILED_PARTIAL_OR_AMBIGUOUS, session.state.value.phase)
        assertEquals(0L, session.state.value.bytesTransferred)
        assertEquals(1, transport.writes)
        assertFalse(transport.connected)
    }

    @Test fun slowConsumerAppliesBackpressureAndConcurrentPrintIsRefused() = runTest {
        val plan = ready().print
        val expected = InMemoryByteSink()
        assertIs<EncodingResult.Success>(EscPosEncoder().encode(plan, expected))
        val transport = FakePrinterTransport(TransportConstraints(1), writeDelayMillis = 100)
        val session = PrintSession(transport)
        val job = launch { session.print(instance(plan), plan) }
        runCurrent()
        assertEquals(1, transport.writes)
        assertEquals(0, transport.byteCount)
        val activeState = session.state.value
        session.print(instance(plan), plan)
        assertEquals(activeState, session.state.value)
        assertEquals(1, transport.connections)
        assertEquals(0, transport.closes)
        advanceTimeBy(50)
        runCurrent()
        assertEquals(1, transport.writes)
        assertEquals(0, transport.byteCount)
        job.join()
        assertEquals(PrintSessionPhase.TRANSMITTED_UNCONFIRMED, session.state.value.phase)
        assertEquals(1, transport.largestWrite)
        assertEquals(1, transport.connections)
        assertEquals(1, transport.closes)
        assertContentEquals(expected.bytes(), transport.capturedBytes())
        assertEquals(expected.bytes().size.toLong(), session.state.value.bytesTransferred)
    }

    @Test fun applicationOutputLimitIsCheckedBeforeConnection() = runTest {
        val plan = ready().print
        val bytes = assertIs<EncodingResult.Success>(EscPosEncoder().validate(plan)).bytesEmitted
        for ((limit, expectedConnections) in listOf(bytes to 1, bytes - 1 to 0)) {
            val transport = FakePrinterTransport()
            val session = PrintSession(transport, PrintSessionPolicy(maxEncodedBytes = limit))
            session.print(instance(plan), plan)
            assertEquals(expectedConnections, transport.connections)
            if (expectedConnections == 0) assertEquals("ENCODED_OUTPUT_LIMIT", session.state.value.diagnosticCode)
        }
    }

    @Test fun zeroReturnDoesNotBusyLoopOrClaimGuaranteedZeroOutput() = runTest {
        val plan = ready().print
        var calls = 0
        val transport = object : PrinterTransport {
            override val constraints = TransportConstraints(10)
            override suspend fun connect(instance: PrinterInstance) = Unit
            override suspend fun write(bytes: ByteArray, offset: Int, length: Int): Int { calls++; return 0 }
            override fun close() = Unit
        }
        val session = PrintSession(transport)
        session.print(instance(plan), plan)
        assertEquals(1, calls)
        assertEquals("INVALID_WRITE_COUNT", session.state.value.diagnosticCode)
        assertTrue(session.state.value.outputMayExist)
    }

    @Test fun disconnectedIdleStateDoesNotInventOutputAndActiveDisconnectCancels() = runTest {
        val plan = ready().print
        val transport = FakePrinterTransport(writeDelayMillis = 1000)
        val session = PrintSession(transport)
        session.disconnect()
        assertEquals(PrintSessionPhase.DISCONNECTED, session.state.value.phase)
        assertFalse(session.state.value.outputMayExist)
        val job = launch { session.print(instance(plan), plan) }
        runCurrent()
        session.disconnect()
        job.join()
        assertEquals(PrintSessionPhase.CANCELLED_PARTIAL_OR_AMBIGUOUS, session.state.value.phase)
        assertFalse(transport.connected)
    }

    @Test fun metadataRejectsControlsAndOversizeAndDoesNotExposePrivateLocator() {
        val plan = ready().print
        val printer = instance(plan)
        assertFalse(printer.toString().contains(printer.transportLocator))
        for (name in listOf("bad\u001bname", "bad\u0085name", "bad\u2028name", "x".repeat(257))) {
            assertFailsWith<IllegalArgumentException> { printer.copy(displayName = name) }
        }
        assertFailsWith<IllegalArgumentException> { printer.copy(transportLocator = "x".repeat(1025)) }
        assertFailsWith<IllegalArgumentException> { printer.copy(profileRevision = 0) }
    }

    @Test fun boundedMemorySinkRejectsOneOverWithoutChangingCapture() = runTest {
        val sink = InMemoryByteSink(3)
        assertTrue(sink.write(byteArrayOf(1, 2)))
        assertTrue(sink.write(byteArrayOf(3)))
        assertFalse(sink.write(byteArrayOf(4)))
        assertContentEquals(byteArrayOf(1, 2, 3), sink.bytes())
        val snapshot = sink.bytes()
        snapshot[0] = 9
        assertEquals(1, sink.bytes()[0].toInt())
        assertFailsWith<IllegalArgumentException> { InMemoryByteSink(256 * 1024 + 1) }
    }

    @Test fun retryDismissalPreservesFailureAndDoesNotReconnect() = runTest {
        val plan = ready().print
        val transport = FakePrinterTransport(failAfterBytes = 2)
        val session = PrintSession(transport)
        session.print(instance(plan), plan)
        val previous = session.state.value
        session.requestRetry()
        session.dismissRetry()
        assertEquals(previous, session.state.value)
        assertFalse(session.confirmRetry())
        session.print(instance(plan), plan)
        assertEquals(1, transport.connections)
    }

    @Test fun connectTimeoutRemainsBeforeAnyOutput() = runTest {
        val plan = ready().print
        val transport = object : PrinterTransport {
            override val constraints = TransportConstraints(10, connectTimeoutMillis = 20)
            override suspend fun connect(instance: PrinterInstance) = awaitCancellation()
            override suspend fun write(bytes: ByteArray, offset: Int, length: Int): Int = error("Unexpected write")
            override fun close() = Unit
        }
        val session = PrintSession(transport)
        session.print(instance(plan), plan)
        assertEquals(PrintSessionPhase.FAILED_BEFORE_TRANSMISSION, session.state.value.phase)
        assertEquals("TRANSPORT_TIMEOUT", session.state.value.diagnosticCode)
        assertFalse(session.state.value.outputMayExist)
    }

    @Test fun cancellationAfterAcknowledgementKeepsTheTransferredCount() = runTest {
        val plan = ready().print
        val transport = FakePrinterTransport(TransportConstraints(1), writeDelayMillis = 100)
        val session = PrintSession(transport)
        val job = launch { session.print(instance(plan), plan) }
        runCurrent()
        advanceTimeBy(100)
        runCurrent()
        assertEquals(1L, session.state.value.bytesTransferred)
        session.cancel()
        job.join()
        assertEquals(PrintSessionPhase.CANCELLED_PARTIAL_OR_AMBIGUOUS, session.state.value.phase)
        assertEquals(1L, session.state.value.bytesTransferred)
        assertEquals(1, transport.byteCount)
    }

    @Test fun closeFailureDoesNotEscapeLifecycleCleanupOrExposePrivateMetadata() = runTest {
        val plan = ready().print
        val transport = object : PrinterTransport {
            override val constraints = TransportConstraints(100)
            override suspend fun connect(instance: PrinterInstance) = Unit
            override suspend fun write(bytes: ByteArray, offset: Int, length: Int) = length
            override fun close(): Unit = error("private device identity")
        }
        val session = PrintSession(transport)
        session.cancel()
        session.print(instance(plan), plan)
        assertEquals(PrintSessionPhase.FAILED_PARTIAL_OR_AMBIGUOUS, session.state.value.phase)
        assertEquals("TRANSPORT_CLOSE_FAILURE", session.state.value.diagnosticCode)
        assertFalse(session.state.value.toString().contains("private device identity"))
    }
    @Test fun trustedBluetoothFailuresStayDistinctAndNeverWrite() = runTest {
        val plan = ready().print
        for (failure in TransportFailure.entries) {
            var writes = 0
            val transport = object : PrinterTransport {
                override val constraints = TransportConstraints(10)
                override suspend fun connect(instance: PrinterInstance): Unit = throw PrinterTransportException(failure)
                override suspend fun write(bytes: ByteArray, offset: Int, length: Int): Int { writes++; return length }
                override fun close() = Unit
            }
            val session = PrintSession(transport)
            session.print(instance(plan), plan)
            assertEquals(failure.name, session.state.value.diagnosticCode)
            assertEquals(PrintSessionPhase.FAILED_BEFORE_TRANSMISSION, session.state.value.phase)
            assertFalse(session.state.value.outputMayExist)
            assertEquals(0, writes)
        }
    }

}
