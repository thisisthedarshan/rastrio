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
import com.circuitnext.rastrio.core.markdown.MarkdownCompiler
import com.circuitnext.rastrio.core.printer.*
import com.circuitnext.rastrio.core.preview.*
import com.circuitnext.rastrio.core.escpos.*
import com.circuitnext.rastrio.core.text.BundledRasterTextBackend
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.*

/** Explicit operator-test artifacts only; no transport and no profile activation. */
class EmpiricalPrinterPayloadTest {
    @Test fun reportedCandidateRoundTripsAndMarkdownUsesFinalizedRaster() = runBlocking {
        val file = listOf(File("../test-fixtures/hardware/HELETT-OCT2026-candidate.tcfg"),
            File("test-fixtures/hardware/HELETT-OCT2026-candidate.tcfg")).first { it.exists() }
        val profile = assertIs<ProfileValidationResult.Success<ValidatedPrinterProfile>>(
            PrinterProfileFiles.decode(file.readBytes())).value.profile
        assertEquals(384, profile.geometry.printableWidthDots)
        assertFalse(profile.nativeText.supported)
        assertFalse(profile.cutter.supported)
        assertFalse(profile.statusQuery.supported)
        assertNull(profile.printerBuffer)
        val draft = ProfileDraft.fromProfile(profile, "org.rastrio.local.helett-oct2026")
            .withField("worksheet.paperWidthMm", "58")
            .withField("worksheet.printableWidthMm", "48")
            .withField("worksheet.charset", "CP437")
            .withField("worksheet.nativeFont", "12x24")
            .copy(evidenceNotes = "Operator Linux report PRINT-018..024 / BT-002..004: 384 dots, ruler-derived 203.2 DPI rounded to203. ESC J, GS P/GS L/GS W/ESC $ and reset remain unverified for RastrIO. Experimental candidate only; native font heights/advance unknown.")
        assertIs<ProfileValidationResult.Failure>(draft.validate())
        File("/tmp/rastrio-helett-oct2026.localdraft").writeText(draft.encodeLocal())
        val bytes = assertIs<ProfileValidationResult.Success<ByteArray>>(PrinterProfileFiles.encode(profile)).value
        assertEquals(profile, assertIs<ProfileValidationResult.Success<ValidatedPrinterProfile>>(
            PrinterProfileFiles.decode(bytes)).value.profile)
        val markdown = "# RastrIO\n\n**Markdown test**\n\n- QR test passed\n- Raster text ready\n\nDone."
        val document = assertNotNull(MarkdownCompiler.compile(markdown).document)
        val plan = assertIs<PreparationResult.Success>(BasicPreparationEngine(
            rasterTextBackend = BundledRasterTextBackend.create()).prepare(document, profile,
            PrintOptions(finalFeedDots = 64))).print
        assertTrue(plan.operations.any { it is PreparedRasterGraphic })
        assertFalse(plan.operations.any { it is NativeText || it is NativeQr || it is AutomaticCut })
        assertIs<PrintPreviewResult.Success>(plan.toPrintPreview())
        val output = File("/tmp/rastrio-empirical-markdown.bin")
        output.outputStream().use { stream ->
            assertIs<EncodingResult.Success>(EscPosEncoder(EncodingResourcePolicy(512, 131072)).encode(plan,
                EncodedByteSink { chunk -> assertTrue(chunk.size <= 512); stream.write(chunk); true }))
        }
        File("/tmp/rastrio-empirical-markdown.md").writeText(markdown)
        File("/tmp/rastrio-empirical-plan.txt").writeText("Prepared width=${plan.target.printableWidthDots}; height=${plan.heightDots}; operations=${plan.operations.size}; bytes=${output.length()}; nativeText=false; cutter=false; status=false")
        assertTrue(output.length() in 1..131072)
    }
}
