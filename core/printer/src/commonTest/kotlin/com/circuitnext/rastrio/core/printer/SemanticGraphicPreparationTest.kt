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

package com.circuitnext.rastrio.core.printer

import com.circuitnext.rastrio.core.document.*
import com.circuitnext.rastrio.core.profile.*
import kotlin.test.*

class SemanticGraphicPreparationTest {
    private fun qr(payload: String, correction: QrErrorCorrection = QrErrorCorrection.MEDIUM,
        size: Double = 30.0) = ThermalDocument(blocks = listOf(QrCode(payload, Alignment.CENTER, correction, Length(size))))
    private fun prepare(document: ThermalDocument, profile: PrinterProfile = SyntheticReferenceProfiles.wide80mm,
        strategy: QrStrategy = QrStrategy.AUTO, override: PrinterProfileOverride = PrinterProfileOverride(),
        policy: PreparationResourcePolicy = PreparationResourcePolicy()) =
        BasicPreparationEngine(resources = policy).prepare(document, profile,
            PrintOptions(qrStrategy = strategy), override)
    private fun failure(result: PreparationResult, code: String) {
        assertTrue(assertIs<PreparationResult.Failure>(result).diagnostics.any { it.code == code })
    }

    @Test fun qrStrategiesRespectEffectiveCapabilitiesAndNeverDowngradeForcedNative() {
        val document = qr("HW-017")
        assertTrue(assertIs<PreparationResult.Success>(prepare(document)).print.operations.any { it is NativeQr })
        assertTrue(assertIs<PreparationResult.Success>(prepare(document, strategy = QrStrategy.RASTER)).print.operations.any { it is RasterQr })
        val disabled = PrinterProfileOverride(nativeQr = NativeQrCapability(false))
        assertTrue(assertIs<PreparationResult.Success>(prepare(document, override = disabled)).print.operations.any { it is RasterQr })
        failure(prepare(document, strategy = QrStrategy.NATIVE, override = disabled), "PRN141")
        val onlyLow = SyntheticReferenceProfiles.wide80mm.nativeQr.copy(errorCorrection = profileListOf("L"))
        failure(prepare(qr("A", QrErrorCorrection.HIGH), strategy = QrStrategy.NATIVE,
            override = PrinterProfileOverride(nativeQr = onlyLow)), "PRN141")
        val tooLargeModules = SyntheticReferenceProfiles.wide80mm.nativeQr.copy(moduleSize = QrModuleSize(8, 8))
        failure(prepare(qr("A", size = 12.0), strategy = QrStrategy.NATIVE,
            override = PrinterProfileOverride(nativeQr = tooLargeModules)), "PRN141")
    }

    @Test fun qrPayloadCapacityUnicodeAndOpaqueFramingAreValidatedBeforePublication() {
        // JS tooling can replace lone-surrogate literals before runtime; construct the hostile input explicitly.
        val high = charArrayOf('x', 0xD800.toChar()).concatToString()
        val low = charArrayOf('x', 0xDC00.toChar()).concatToString()
        assertEquals(0xD800, high[1].code)
        assertEquals(0xDC00, low[1].code)
        for (payload in listOf("", high, low, "é".repeat(1500))) failure(prepare(qr(payload)), "PRN140")
        failure(prepare(qr("a".repeat(1274), QrErrorCorrection.HIGH)), "PRN140")
        for (payload in listOf("é", "a".repeat(15))) {
            assertTrue(assertIs<PreparationResult.Success>(prepare(qr(payload))).print.operations.any { it is RasterQr })
            failure(prepare(qr(payload), strategy = QrStrategy.NATIVE), "PRN141")
        }
        val opaque = "A\u001d\u0000\n"
        val native = assertIs<PreparationResult.Success>(prepare(qr(opaque), strategy = QrStrategy.NATIVE))
            .print.operations.filterIsInstance<NativeQr>().single()
        assertEquals(opaque.map { it.code }, native.payloadBytes)
    }

    @Test fun qrRequiresSquareDotsAndEnoughQuietZoneSpace() {
        failure(prepare(qr("A"), override = PrinterProfileOverride(geometry = PrinterGeometry(203, 180, 576))), "PRN142")
        failure(prepare(qr("A", size = 1.0), strategy = QrStrategy.RASTER), "PRN142")
    }

    @Test fun qrResidentBudgetHasExactAndOneOverBoundary() {
        val input = qr("https://example.invalid/qr", QrErrorCorrection.HIGH)
        val print = assertIs<PreparationResult.Success>(prepare(input, strategy = QrStrategy.RASTER)).print
        val bytes = print.operations.filterIsInstance<RasterQr>().sumOf { it.graphic.sizeBytes }
        assertIs<PreparationResult.Success>(prepare(input, strategy = QrStrategy.RASTER,
            policy = PreparationResourcePolicy(maxResidentGraphicBytes = bytes)))
        failure(prepare(input, strategy = QrStrategy.RASTER,
            policy = PreparationResourcePolicy(maxResidentGraphicBytes = bytes - 1)), "PRN120")
    }
    @Test fun qrIntentReachesPhysicalPreparationWithoutNativeCapability() {
        val document = ThermalDocument(blocks = listOf(QrCode("hello", Alignment.CENTER, QrErrorCorrection.MEDIUM)))
        val print = assertIs<PreparationResult.Success>(BasicPreparationEngine().prepare(document, SyntheticReferenceProfiles.narrow)).print
        assertTrue(print.operations.any { it !is Feed })
        assertTrue(print.operations.all { it.bounds.right <= print.target.printableWidthDots })
    }

    @Test fun checklistMarkersRequireAnExplicitControlledTextBackend() {
        val document = ThermalDocument(blocks = listOf(Checklist(listOf(
            ChecklistItem(false, listOf(Paragraph(Alignment.LEFT, listOf(Text("first"))))),
            ChecklistItem(true, listOf(Paragraph(Alignment.LEFT, listOf(Text("second")))))
        ))))
        val failure = assertIs<PreparationResult.Failure>(BasicPreparationEngine().prepare(document, SyntheticReferenceProfiles.narrow))
        assertEquals("TXT132", failure.diagnostics.single().code)
    }
}
