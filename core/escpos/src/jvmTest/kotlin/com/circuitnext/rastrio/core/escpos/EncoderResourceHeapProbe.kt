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

package com.circuitnext.rastrio.core.escpos

import com.circuitnext.rastrio.core.document.CodeBlock
import com.circuitnext.rastrio.core.document.ThermalDocument
import com.circuitnext.rastrio.core.printer.BasicPreparationEngine
import com.circuitnext.rastrio.core.printer.PreparationResult
import com.circuitnext.rastrio.core.profile.SyntheticReferenceProfiles
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine

/** A fixed prepared input is streamed repeatedly; the sink retains only counters and one continuation. */
object EncoderResourceHeapProbe {
    @JvmStatic
    fun main(args: Array<String>) {
        val prepared = BasicPreparationEngine().prepare(
            ThermalDocument(blocks = listOf(CodeBlock("A".repeat(64_000)))),
            SyntheticReferenceProfiles.narrow,
        )
        check(prepared is PreparationResult.Success)
        val encoder = EscPosEncoder()
        val checked = encoder.validate(prepared.print)
        check(checked is EncodingResult.Success)
        check(checked.bytesEmitted > 64 * 1024)
        var totalBytes = 0L
        var writes = 0
        var maximumChunk = 0
        var pending: Continuation<Boolean>? = null
        val sink = EncodedByteSink { bytes ->
            check(pending == null)
            check(bytes.size in 1..64 * 1024)
            maximumChunk = maxOf(maximumChunk, bytes.size)
            totalBytes += bytes.size
            writes++
            suspendCoroutine { pending = it }
        }
        val repetitions = 2_048
        repeat(repetitions) {
            var completed: EncodingResult? = null
            val operation: suspend () -> EncodingResult = { encoder.encode(prepared.print, sink) }
            val callsBeforeStart = writes
            operation.startCoroutine(object : Continuation<EncodingResult> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<EncodingResult>) { completed = result.getOrThrow() }
            })
            check(writes == callsBeforeStart + 1 && completed == null)
            while (completed == null) {
                val continuation = checkNotNull(pending)
                pending = null
                val callsBeforeResume = writes
                continuation.resume(true)
                // No producer runs ahead of the one write whose acknowledgement was just resumed.
                check(writes == callsBeforeResume || writes == callsBeforeResume + 1)
                check((completed == null) == (pending != null))
            }
            check(completed == checked)
        }
        check(totalBytes == checked.bytesEmitted * repetitions)
        check(totalBytes > 128L * 1024 * 1024)
        check(maximumChunk == 64 * 1024 && pending == null)
        println("$repetitions encodings: $totalBytes bytes, $writes suspended writes, maximum chunk $maximumChunk bytes under 64 MiB heap")
    }
}
