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

/** Synthetic capability data from TCFG_SPEC §§30–32, never claims about commercial hardware.
 * Candidates must pass the same validator and application-owned policy as imported profiles.
 */
object SyntheticReferenceProfiles {
    val narrow = PrinterProfile(
        profileId = "org.rastrio.test.synthetic-narrow",
        profileRevision = 1,
        display = ProfileDisplay("Synthetic Narrow 203 dpi", "RastrIO Test Fixture", "Synthetic-Narrow"),
        geometry = PrinterGeometry(203, 203, 384),
        nativeText = NativeTextCapability(
            supported = true,
            fonts = profileListOf(
                NativeFontCapability("font-a", 12, 24, 24, RegisteredSelector("escpos.font.esc-m", 0)),
                NativeFontCapability("font-b", 9, 17, 17, RegisteredSelector("escpos.font.esc-m", 1)),
            ),
            styles = NativeStyleCapability(true, true, profileListOf(1, 2), profileListOf(1, 2)),
            codePages = profileListOf(CodePageCapability("cp437", "cp437", RegisteredSelector("escpos.code-page.esc-t", 0))),
        ),
        raster = RasterCapability(true, profileListOf("escpos.raster.gs-v-0"), "escpos.raster.gs-v-0", 384,
            RasterBandHeight(1, 128, 255)),
        nativeQr = NativeQrCapability(false),
        nativeBarcode = NativeBarcodeCapability(false),
        cutter = CutterCapability(false),
        printerBuffer = PrinterBufferGuidance(4096, 10),
        statusQuery = StatusQueryCapability(false),
        protocol = EscPosProtocolProfile("escpos.generic", "escpos.init.standard"),
    )

    val wide80mm = narrow.copy(
        profileId = "org.rastrio.test.synthetic-80mm",
        display = ProfileDisplay("Synthetic 80 mm 203 dpi", "RastrIO Test Fixture", "Synthetic-80"),
        geometry = PrinterGeometry(203, 203, 576),
        raster = RasterCapability(true, profileListOf("escpos.raster.gs-v-0"), "escpos.raster.gs-v-0", 576,
            RasterBandHeight(1, 192, 512)),
        nativeQr = NativeQrCapability(true, profileListOf("escpos.qr.model2"), "escpos.qr.model2",
            profileListOf("model2"), QrModuleSize(1, 8), profileListOf("L", "M", "Q", "H")),
        nativeBarcode = NativeBarcodeCapability(true, profileListOf("escpos.barcode.gs-k"), "escpos.barcode.gs-k",
            profileListOf("code128", "ean13")),
        cutter = CutterCapability(true, profileListOf("full", "partial"), "escpos.cut.gs-v"),
        printerBuffer = PrinterBufferGuidance(8192, 5),
        statusQuery = StatusQueryCapability(true, profileListOf("escpos.status.dle-eot"), "escpos.status.dle-eot"),
    )

    val rasterOnly = narrow.copy(
        profileId = "org.rastrio.test.synthetic-raster-only",
        display = ProfileDisplay("Synthetic Raster-Only Printer"),
        nativeText = NativeTextCapability(false),
        raster = RasterCapability(true, profileListOf("escpos.raster.gs-v-0"), "escpos.raster.gs-v-0", 384,
            RasterBandHeight(1, 64, 128)),
        printerBuffer = null,
    )

    val all: ProfileList<PrinterProfile> = profileListOf(narrow, wide80mm, rasterOnly)
}
