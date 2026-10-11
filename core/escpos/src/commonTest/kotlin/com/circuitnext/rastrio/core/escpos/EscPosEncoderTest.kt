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

import com.circuitnext.rastrio.core.document.*
import com.circuitnext.rastrio.core.document.Separator as DocumentSeparator
import com.circuitnext.rastrio.core.printer.*
import com.circuitnext.rastrio.core.profile.*
import kotlin.coroutines.*
import kotlin.test.*

class EscPosEncoderTest {
    private val initialization = "1b40 1d50cbcb 1d4c0000 1d578001 1b6100"
    private fun textRun(content: String, x: String = "0000", font: String = "00",
        page: String = "00", bold: String = "00", scale: String = "00", advance: String = "18") =
        "1b4d$font 1b74$page 1b45$bold 1b2d00 1d21$scale 1b24$x $content 1b4a$advance"
    private fun prepare(document: ThermalDocument = ThermalDocument(blocks = listOf(CodeBlock("AB"))),
        profile: PrinterProfile = SyntheticReferenceProfiles.narrow, options: PrintOptions = PrintOptions()) =
        assertIs<PreparationResult.Success>(BasicPreparationEngine().prepare(document, profile, options)).print
    private fun run(block: suspend () -> EncodingResult): EncodingResult {
        var result: Result<EncodingResult>? = null
        block.startCoroutine(object : Continuation<EncodingResult> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(value: Result<EncodingResult>) { result = value }
        })
        return checkNotNull(result).getOrThrow()
    }
    private fun encode(print: PreparedPrint, encoder: EscPosEncoder = EscPosEncoder()): ByteArray {
        val bytes = mutableListOf<Byte>()
        val result = run { encoder.encode(print, EncodedByteSink { bytes.addAll(it.toList()); true }) }
        assertEquals(bytes.size.toLong(), assertIs<EncodingResult.Success>(result).bytesEmitted)
        return bytes.toByteArray()
    }
    private fun hex(value: String): ByteArray = value.filterNot(Char::isWhitespace).chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun golden(expected: String, plan: PreparedPrint) = assertContentEquals(hex(expected), encode(plan))

    @Test fun asciiHasExactInitializationSelectionPositionAndDotAdvance() {
        golden(initialization + "1b4a0c" + textRun("4142") + "1b4a0c", prepare())
    }
    @Test fun multipleLinesFlushIndependentlyAndDoNotEncodeNewlineAsContent() {
        golden(initialization + "1b4a0c" + textRun("41") + textRun("42") + "1b4a0c", prepare(ThermalDocument(blocks = listOf(CodeBlock("A\nB")))))
    }
    @Test fun preparedCp437BytesAreNotReencodedWithHostCharsets() {
        golden(initialization + "1b4a0c" + textRun("82db") + "1b4a0c", prepare(ThermalDocument(blocks = listOf(CodeBlock("é█")))))
    }
    @Test fun validatedCodePageSelectorIsSerializedExactly() {
        val profile = SyntheticReferenceProfiles.narrow.copy(nativeText = SyntheticReferenceProfiles.narrow.nativeText.copy(
            codePages = profileListOf(CodePageCapability("alternate", "cp437", RegisteredSelector("escpos.code-page.esc-t", 17)))))
        golden(initialization + "1b4a0c" + textRun("4142", page = "11") + "1b4a0c", prepare(profile = profile))
    }
    @Test fun alternateFontAndScalingAreAlreadyResolved() {
        golden(initialization + "1b4a11" + textRun("4142", font = "01", scale = "11", advance = "22") + "1b4a11",
            prepare(options = PrintOptions(nativeFontId = "font-b", widthScale = 2, heightScale = 2)))
    }
    @Test fun resolvedAlignmentUsesExactPositionWithoutApplyingAlignmentTwice() {
        for ((alignment, position) in listOf(Alignment.LEFT to "0000", Alignment.CENTER to "ba00", Alignment.RIGHT to "7401")) {
            val plan = prepare(ThermalDocument(blocks = listOf(Paragraph(alignment, listOf(Text("A"))))))
            val text = plan.operations.filterIsInstance<NativeText>().single()
            assertEquals(hex(position)[0].toInt() and 255 or ((hex(position)[1].toInt() and 255) shl 8), text.bounds.x)
            val expected = initialization + textRun("41", x = position) + plan.operations.filterIsInstance<Feed>().joinToString("") {
                "1b4a" + it.advanceDots.toString(16).padStart(2, '0')
            }
            golden(expected, plan)
        }
    }
    @Test fun boldTransitionsBetweenRunsKeepTheSamePhysicalLine() {
        val plan = prepare(ThermalDocument(blocks = listOf(Paragraph(Alignment.LEFT,
            listOf(Strong(listOf(Text("A"))), Text("B"))))))
        val tail = plan.operations.filterIsInstance<Feed>().joinToString("") { "1b4a" + it.advanceDots.toString(16).padStart(2, '0') }
        golden(initialization + "1b4d00 1b7400 1b4501 1b2d00 1d2100 1b240000 41" +
            textRun("42", x = "0c00") + tail, plan)
    }
    @Test fun feedBoundariesUseExactDotAdvances() {
        for ((feed, expected) in listOf(1 to "1b4a01", 255 to "1b4aff", 256 to "1b4aff1b4a01"))
            golden(initialization + "1b4a0c" + textRun("4142") + "1b4a0c" + expected, prepare(options = PrintOptions(finalFeedDots = feed)))
    }
    @Test fun fullAndPartialCutsSerializeOnlyPreparedDecisions() {
        val wideInit = initialization.replace("1d578001", "1d574002")
        for ((mode, command) in listOf(CutMode.FULL to "1d5600", CutMode.PARTIAL to "1d5601"))
            golden(wideInit + "1b4a0c" + textRun("4142") + "1b4a0c" + command, prepare(profile = SyntheticReferenceProfiles.wide80mm,
                options = PrintOptions(cutIntent = CutIntent.AUTOMATIC, cutMode = mode)))
        assertFalse(prepare().operations.any { it is AutomaticCut })
        assertIs<PreparationResult.Failure>(BasicPreparationEngine().prepare(ThermalDocument(blocks = listOf(CodeBlock("A"))),
            SyntheticReferenceProfiles.narrow, PrintOptions(cutIntent = CutIntent.AUTOMATIC)))
    }
    @Test fun manualGuideConsumesFinalizedBitsAndAddsNoAutomaticCut() {
        val plan = prepare(options = PrintOptions(cutIntent = CutIntent.MANUAL_GUIDE))
        val guide = plan.operations.filterIsInstance<ManualCutGuide>().single()
        assertEquals("ff00".repeat(24), guide.graphic.copyBytes().joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') })
        golden(initialization + "1b4a0c" + textRun("4142") + "1b4a0c 1d4c0000 1d76300030000100" + "ff00".repeat(24) + "1d4c0000", plan)
    }
    @Test fun repeatEncodingReadsTheSameFinalizedGraphicBits() {
        val plan = prepare(options = PrintOptions(cutIntent = CutIntent.MANUAL_GUIDE))
        val guide = plan.operations.filterIsInstance<ManualCutGuide>().single()
        val expected = guide.graphic.copyBytes()
        guide.graphic.copyBytes().fill(0)
        assertContentEquals(expected, guide.graphic.copyBytes())
        assertContentEquals(encode(plan), encode(plan))
    }
    @Test fun unsupportedExactMotionFailsBeforeAnySinkCall() {
        val profile = SyntheticReferenceProfiles.narrow.copy(geometry = PrinterGeometry(203, 406, 384))
        var writes = 0
        val result = run { EscPosEncoder().encode(prepare(profile = profile), EncodedByteSink { writes++; true }) }
        assertEquals("ESC101", assertIs<EncodingResult.Failure>(result).diagnostic.code)
        assertEquals(0, writes)
    }
    @Test fun nativeControlsCannotReachPreparedBytes() {
        for (control in listOf('\u001b', '\u001d', '\u0000', '\u007f'))
            assertIs<PreparationResult.Failure>(BasicPreparationEngine().prepare(ThermalDocument(blocks = listOf(CodeBlock("A${control}B"))),
                SyntheticReferenceProfiles.narrow))
    }
    @Test fun encodedLimitIsCheckedBeforeEmissionAtExactBoundary() {
        val plan = prepare()
        val size = encode(plan).size.toLong()
        assertIs<EncodingResult.Success>(EscPosEncoder(EncodingResourcePolicy(maxEncodedBytes = size)).validate(plan))
        assertEquals("ESC120", assertIs<EncodingResult.Failure>(EscPosEncoder(EncodingResourcePolicy(maxEncodedBytes = size - 1)).validate(plan)).diagnostic.code)
    }
    @Test fun chunksAreBoundedAndHaveNoEffectOnExactBytes() {
        val plan = prepare(ThermalDocument(blocks = listOf(CodeBlock("A".repeat(400)))))
        val expected = encode(plan)
        for (size in listOf(1, 3, 7, 64)) {
            val chunks = mutableListOf<ByteArray>()
            val result = run { EscPosEncoder(EncodingResourcePolicy(size)).encode(plan, EncodedByteSink { chunks.add(it); true }) }
            assertIs<EncodingResult.Success>(result)
            assertTrue(chunks.all { it.size in 1..size })
            assertContentEquals(expected, chunks.flatMap { it.toList() }.toByteArray())
        }
        assertFailsWith<IllegalArgumentException> { EncodingResourcePolicy(65537) }
    }
    @Test fun failedSinkAndCancellationStopWithoutReplay() {
        val encoder = EscPosEncoder(EncodingResourcePolicy(3))
        var writes = 0
        val failed = run { encoder.encode(prepare(), EncodedByteSink { ++writes < 2 }) }
        assertEquals(3L, assertIs<EncodingResult.Failure>(failed).bytesEmitted)
        assertEquals(2, writes)
        writes = 0
        val cancelled = run { encoder.encode(prepare(), EncodedByteSink { writes++; true }, { writes >= 1 }) }
        assertEquals(3L, assertIs<EncodingResult.Cancelled>(cancelled).bytesEmitted)
        assertEquals(1, writes)
        assertEquals(0L, assertIs<EncodingResult.Cancelled>(run { encoder.encode(prepare(), EncodedByteSink { error("No delivery") }, { true }) }).bytesEmitted)
    }
    @Test fun transientUnderlineIsPreparedAndEncodedOnlyWithDeclaredCapability() {
        val document = ThermalDocument(blocks = listOf(CodeBlock("AB")))
        val options = PrintOptions(nativeUnderline = true)
        val plan = prepare(document, options = options)
        assertTrue(plan.operations.filterIsInstance<NativeText>().single().style.underline)
        golden(initialization + "1b4a0c" + textRun("4142").replace("1b2d00", "1b2d01") + "1b4a0c", plan)
        assertFalse(prepare(document).operations.filterIsInstance<NativeText>().single().style.underline)
        val unsupported = SyntheticReferenceProfiles.narrow.copy(nativeText = SyntheticReferenceProfiles.narrow.nativeText.copy(
            styles = SyntheticReferenceProfiles.narrow.nativeText.styles.copy(underline = false)))
        val rejected = assertIs<PreparationResult.Failure>(BasicPreparationEngine().prepare(document, unsupported, options))
        assertEquals("PRN102", rejected.diagnostics.first().code)
        val imageOnly = assertIs<PreparationResult.Failure>(BasicPreparationEngine().prepare(
            ThermalDocument(blocks = emptyList()), SyntheticReferenceProfiles.rasterOnly, options))
        assertEquals("PRN101", imageOnly.diagnostics.first().code)
    }

    @Test fun resolvedUnderlineAndCodePageTransitionsUseTheProductionStateSerializer() {
        val first = nativeStateCommands(0, 0, PreparedNativeStyle(underline = true), 0)
        val second = nativeStateCommands(1, 17, PreparedNativeStyle(), 12)
        assertContentEquals(hex("1b4d00 1b7400 1b4500 1b2d01 1d2100 1b240000" +
            "1b4d01 1b7411 1b4500 1b2d00 1d2100 1b240c00"),
            (first + second).flatMap { it.asSequence() }.toList().toByteArray())
        assertFailsWith<IllegalArgumentException> { nativeStateCommands(0, 256, PreparedNativeStyle(), 0) }
        assertFailsWith<IllegalArgumentException> { nativeStateCommands(2, 0, PreparedNativeStyle(), 0) }
    }

    @Test fun suspendedSinkProvidesBackpressureWithoutQueuedOutput() {
        var pending: Continuation<Boolean>? = null
        var calls = 0
        var result: EncodingResult? = null
        val job: suspend () -> EncodingResult = {
            EscPosEncoder(EncodingResourcePolicy(3)).encode(prepare(), EncodedByteSink {
                calls++
                suspendCoroutine { continuation ->
                    check(pending == null)
                    pending = continuation
                }
            })
        }
        job.startCoroutine(object : Continuation<EncodingResult> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(value: Result<EncodingResult>) { result = value.getOrThrow() }
        })
        assertEquals(1, calls)
        assertNull(result)
        val continuation = checkNotNull(pending)
        pending = null
        continuation.resume(false)
        assertEquals(1, calls)
        assertIs<EncodingResult.Failure>(result)
    }

}
