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

package com.circuitnext.rastrio.presentation

import com.circuitnext.rastrio.core.document.*
import com.circuitnext.rastrio.core.escpos.*
import com.circuitnext.rastrio.core.preview.*
import com.circuitnext.rastrio.core.printer.*
import com.circuitnext.rastrio.core.profile.*
import com.circuitnext.rastrio.core.templates.*
import com.circuitnext.rastrio.core.text.UnsupportedRasterTextBackend
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class QrPreviewMatrixTest {
    @Test fun asciiAndUnicodeQrTemplatesShareFinalBandsWithPreviewAndEncoding() = runTest {
        for (payload in listOf("QR ASCII", "QR हिन्दी")) for (size in listOf(12.0, 20.0, 30.0))
            for (correction in listOf(QrErrorCorrection.LOW, QrErrorCorrection.MEDIUM,
                QrErrorCorrection.QUARTILE, QrErrorCorrection.HIGH)) {
                val document = assertNotNull(TemplateDocumentBuilder.build(QrMessageState(payload,
                    errorCorrection = correction, requestedSize = Length(size))).document)
                val session = PhysicalPreviewSession()
                session.prepare(document, SyntheticReferenceProfiles.narrow, PrintOptions(qrStrategy = QrStrategy.RASTER))
                val ready = assertIs<PhysicalPreviewResult.Ready>(session.state.result)
                val bands = ready.print.operations.filterIsInstance<RasterQr>()
                assertTrue(bands.isNotEmpty())
                assertTrue(bands.all { it.bounds.width in 1..384 })
                val seen = mutableSetOf<PreparedOperation>()
                repeat(ready.preview.tileCount) { index ->
                    val tile = assertIs<PrintPreviewTileResult.Success>(ready.preview.tile(index)).tile
                    for (primitive in tile.primitives.filterIsInstance<MonochromePrimitive>()) {
                        if (primitive.operation is RasterQr) {
                            assertTrue(bands.any { it === primitive.operation })
                            assertSame((primitive.operation as RasterQr).graphic, primitive.graphic)
                            assertEquals(PhysicalPreviewAccuracy.EXACT_PREPARED_DOTS, primitive.accuracy)
                            seen += primitive.operation
                        }
                    }
                }
                assertTrue(bands.all { it in seen })
                val encoded = mutableListOf<Byte>()
                assertIs<EncodingResult.Success>(EscPosEncoder().encode(ready.print,
                    EncodedByteSink { encoded.addAll(it.toList()); true }))
                var offset = 0
                for (band in bands) {
                    val graphic = band.graphic
                    val frame = byteArrayOf(0x1d, 0x76, 0x30, 0,
                        (graphic.rowBytes and 255).toByte(), (graphic.rowBytes ushr 8).toByte(),
                        (graphic.heightDots and 255).toByte(), (graphic.heightDots ushr 8).toByte()) + graphic.copyBytes()
                    val start = (offset..encoded.size - frame.size).firstOrNull { position ->
                        frame.indices.all { encoded[position + it] == frame[it] }
                    }
                    assertNotNull(start, "Encoder must consume each finalized band in order")
                    offset = start + frame.size
                }
            }
    }

    @Test fun unsupportedNativeGeometryAndMissingRuntimeProduceDiagnostics() {
        val document = ThermalDocument(blocks = listOf(QrCode("QR", Alignment.CENTER, QrErrorCorrection.MEDIUM)))
        val session = PhysicalPreviewSession()
        session.prepare(document, options = PrintOptions(qrStrategy = QrStrategy.NATIVE))
        assertTrue(assertIs<PhysicalPreviewResult.PreparationFailed>(session.state.result).diagnostics.any { it.code == "PRN141" })
        session.prepare(document, profile = SyntheticReferenceProfiles.narrow.copy(
            geometry = PrinterGeometry(203, 180, 384)), options = PrintOptions(qrStrategy = QrStrategy.RASTER))
        assertTrue(assertIs<PhysicalPreviewResult.PreparationFailed>(session.state.result).diagnostics.any { it.code == "PRN142" })
        session.prepare(document, profile = SyntheticReferenceProfiles.narrow.copy(
            geometry = PrinterGeometry(0, 203, 384)))
        assertIs<PhysicalPreviewResult.PreparationFailed>(session.state.result)
        val unavailable = PhysicalPreviewSession(rasterTextBackend = UnsupportedRasterTextBackend())
        val caption = assertNotNull(TemplateDocumentBuilder.build(QrMessageState("QR", "हिन्दी caption")).document)
        unavailable.prepare(caption)
        assertTrue(assertIs<PhysicalPreviewResult.PreparationFailed>(unavailable.state.result).diagnostics.any { it.code == "TXT132" })
    }

    @Test fun nativeQrPreviewRetainsRepresentativeAccuracyAndFinalizedArtifact() {
        val session = PhysicalPreviewSession()
        session.prepare(ThermalDocument(blocks = listOf(QrCode("QR", Alignment.CENTER, QrErrorCorrection.MEDIUM))), SyntheticReferenceProfiles.wide80mm)
        val ready = assertIs<PhysicalPreviewResult.Ready>(session.state.result)
        val native = ready.print.operations.filterIsInstance<NativeQr>().single()
        val primitives = (0 until ready.preview.tileCount).flatMap {
            assertIs<PrintPreviewTileResult.Success>(ready.preview.tile(it)).tile.primitives
        }.filterIsInstance<MonochromePrimitive>().filter { it.operation === native }
        assertTrue(primitives.isNotEmpty())
        for (primitive in primitives) {
            assertSame(native.representativeGraphic, primitive.graphic)
            assertEquals(PhysicalPreviewAccuracy.EXACT_GEOMETRY_REPRESENTATIVE_QR, primitive.accuracy)
        }
    }
}
