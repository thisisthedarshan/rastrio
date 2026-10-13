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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout

enum class PrintSessionPhase {
    IDLE, VALIDATING, CONNECTING, READY, SENDING, TRANSMITTED_UNCONFIRMED,
    FAILED_BEFORE_TRANSMISSION, FAILED_PARTIAL_OR_AMBIGUOUS,
    CANCELLED_BEFORE_TRANSMISSION, CANCELLED_PARTIAL_OR_AMBIGUOUS,
    DISCONNECTED, AWAITING_RETRY_CONFIRMATION, RETRY_REQUESTED,
}

data class PrintSessionState(
    val phase: PrintSessionPhase = PrintSessionPhase.IDLE,
    val bytesTransferred: Long = 0,
    val outputMayExist: Boolean = false,
    val diagnosticCode: String? = null,
) {
    val isActive: Boolean get() = phase == PrintSessionPhase.VALIDATING || phase == PrintSessionPhase.CONNECTING ||
        phase == PrintSessionPhase.READY || phase == PrintSessionPhase.SENDING
}

/** No spool, replay or encoded queue: the encoder suspends directly on each bounded transport write. */
class PrintSession(
    private val transport: PrinterTransport,
    private val policy: PrintSessionPolicy = PrintSessionPolicy(),
    private val printerGuidance: PrinterWriteGuidance = PrinterWriteGuidance(),
    private val encoder: EscPosEncoder = EscPosEncoder(),
) {
    private val mutableState = MutableStateFlow(PrintSessionState())
    val state: StateFlow<PrintSessionState> = mutableState.asStateFlow()
    private val operation = Mutex()
    private val activeJob = MutableStateFlow<Job?>(null)
    private var previousRetryState: PrintSessionState? = null

    fun cancel() {
        activeJob.value?.cancel(CancellationException("Print cancelled"))
        closeTransport()
    }

    fun disconnect() {
        cancel()
        if (!operation.isLocked) mutableState.value = state.value.copy(phase = PrintSessionPhase.DISCONNECTED)
    }

    /** First action opens the warning; it does not authorize another transmission. */
    fun requestRetry(): Boolean {
        if (operation.isLocked || !state.value.outputMayExist ||
            state.value.phase == PrintSessionPhase.AWAITING_RETRY_CONFIRMATION) return false
        previousRetryState = state.value
        mutableState.value = state.value.copy(phase = PrintSessionPhase.AWAITING_RETRY_CONFIRMATION)
        return true
    }

    /** Call only after the user acknowledges that earlier paper output may already exist. */
    fun confirmRetry(): Boolean {
        if (operation.isLocked || state.value.phase != PrintSessionPhase.AWAITING_RETRY_CONFIRMATION) return false
        mutableState.value = state.value.copy(phase = PrintSessionPhase.RETRY_REQUESTED)
        previousRetryState = null
        return true
    }

    fun dismissRetry() {
        if (operation.isLocked || state.value.phase != PrintSessionPhase.AWAITING_RETRY_CONFIRMATION) return
        previousRetryState?.let { mutableState.value = it }
        previousRetryState = null
    }

    suspend fun print(instance: PrinterInstance, prepared: PreparedPrint,
        guidance: PrinterWriteGuidance = printerGuidance) {
        if (!operation.tryLock()) return
        try {
            // Refusal preserves the prior evidence and does not connect or encode the job again.
            if (state.value.outputMayExist && state.value.phase != PrintSessionPhase.RETRY_REQUESTED) return
            activeJob.value = currentCoroutineContext().job
            mutableState.value = PrintSessionState(PrintSessionPhase.VALIDATING)
            currentCoroutineContext().ensureActive()
            if (instance.profileId != prepared.target.profileId || instance.profileRevision != prepared.target.profileRevision) {
                fail("PROFILE_MISMATCH")
                return
            }
            when (val validated = encoder.validate(prepared)) {
                is EncodingResult.Failure -> { fail(validated.diagnostic.code); return }
                is EncodingResult.Cancelled -> { cancelled(); return }
                is EncodingResult.Success -> if (validated.bytesEmitted > policy.maxEncodedBytes) {
                    fail("ENCODED_OUTPUT_LIMIT")
                    return
                }
            }
            val effective = policy.intersect(transport.constraints, guidance)
            mutableState.value = state.value.copy(phase = PrintSessionPhase.CONNECTING)
            withTimeout(effective.connectTimeoutMillis) { transport.connect(instance) }
            currentCoroutineContext().ensureActive()
            mutableState.value = state.value.copy(phase = PrintSessionPhase.READY)
            var totalEncoded = 0L
            var wrote = false
            val sink = EncodedByteSink { bytes ->
                currentCoroutineContext().ensureActive()
                if (bytes.size > policy.maxEncoderChunkBytes || bytes.size.toLong() > policy.maxEncodedBytes - totalEncoded) {
                    throw PrintDeliveryFailure("ENCODED_OUTPUT_LIMIT")
                }
                totalEncoded += bytes.size
                var offset = 0
                while (offset < bytes.size) {
                    currentCoroutineContext().ensureActive()
                    if (wrote && effective.minWriteIntervalMillis > 0) delay(effective.minWriteIntervalMillis)
                    val length = minOf(bytes.size - offset, effective.maxWriteBytes)
                    // A failed/cancelled socket call cannot prove that no bytes reached the printer.
                    mutableState.value = state.value.copy(phase = PrintSessionPhase.SENDING, outputMayExist = true)
                    val accepted = withTimeout(effective.writeTimeoutMillis) { transport.write(bytes, offset, length) }
                    if (accepted !in 1..length) throw PrintDeliveryFailure("INVALID_WRITE_COUNT")
                    offset += accepted
                    wrote = true
                    mutableState.value = state.value.copy(bytesTransferred = state.value.bytesTransferred + accepted)
                }
                true
            }
            when (val encoded = encoder.encode(prepared, sink)) {
                is EncodingResult.Failure -> fail(encoded.diagnostic.code)
                is EncodingResult.Cancelled -> cancelled()
                is EncodingResult.Success -> {
                    currentCoroutineContext().ensureActive()
                    mutableState.value = state.value.copy(phase = PrintSessionPhase.TRANSMITTED_UNCONFIRMED)
                }
            }
        } catch (_: TimeoutCancellationException) {
            fail("TRANSPORT_TIMEOUT")
        } catch (failure: CancellationException) {
            cancelled()
            throw failure
        } catch (failure: PrintDeliveryFailure) {
            fail(failure.code)
        } catch (failure: PrinterTransportException) {
            fail(failure.failure.name)
        } catch (_: Exception) {
            fail("TRANSPORT_FAILURE")
        } finally {
            closeTransport()
            activeJob.value = null
            operation.unlock()
        }
    }

    private fun fail(code: String) {
        mutableState.value = state.value.copy(
            phase = if (state.value.outputMayExist) PrintSessionPhase.FAILED_PARTIAL_OR_AMBIGUOUS else PrintSessionPhase.FAILED_BEFORE_TRANSMISSION,
            diagnosticCode = code,
        )
    }

    private fun closeTransport() {
        try { transport.close() } catch (_: Exception) { fail("TRANSPORT_CLOSE_FAILURE") }
    }

    private fun cancelled() {
        mutableState.value = state.value.copy(
            phase = if (state.value.outputMayExist) PrintSessionPhase.CANCELLED_PARTIAL_OR_AMBIGUOUS else PrintSessionPhase.CANCELLED_BEFORE_TRANSMISSION,
            diagnosticCode = "CANCELLED",
        )
    }
}

private class PrintDeliveryFailure(val code: String) : Exception(code)
