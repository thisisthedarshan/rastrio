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

/** Defensive read-only value; elements must themselves be immutable domain values. */
class ProfileList<out T>(values: Collection<T>) : AbstractList<T>() {
    private val snapshot = values.toList()
    override val size: Int get() = snapshot.size
    override fun get(index: Int): T = snapshot[index]
}

fun <T> profileListOf(vararg values: T): ProfileList<T> = ProfileList(values.asList())

/** Candidate data is immutable, but is not trusted until semantic validation succeeds. */
data class PrinterProfile(
    val format: String = "rastrio-tcfg",
    val schemaVersion: Int = 1,
    val profileId: String,
    val profileRevision: Int,
    val display: ProfileDisplay,
    val geometry: PrinterGeometry,
    val nativeText: NativeTextCapability,
    val raster: RasterCapability,
    val nativeQr: NativeQrCapability,
    val nativeBarcode: NativeBarcodeCapability,
    val cutter: CutterCapability,
    val printerBuffer: PrinterBufferGuidance? = null,
    val statusQuery: StatusQueryCapability,
    val protocol: ProtocolProfile,
    val quirks: ProfileList<String> = profileListOf(),
)

data class ProfileDisplay(val name: String, val manufacturer: String? = null, val model: String? = null)
data class PrinterGeometry(val horizontalDpi: Int, val verticalDpi: Int, val printableWidthDots: Int)

/** The only selector parameter shape defined by v1 is a registered integer selector. */
data class RegisteredSelector(val strategy: String, val parameter: Int)
data class NativeFontCapability(
    val id: String,
    val cellWidthDots: Int,
    val cellHeightDots: Int,
    val lineAdvanceDots: Int? = null,
    val selector: RegisteredSelector? = null,
)
data class NativeStyleCapability(
    val bold: Boolean = false,
    val underline: Boolean = false,
    val widthScales: ProfileList<Int> = profileListOf(),
    val heightScales: ProfileList<Int> = profileListOf(),
)
data class CodePageCapability(val id: String, val repertoire: String, val selector: RegisteredSelector? = null)
data class NativeTextCapability(
    val supported: Boolean,
    val fonts: ProfileList<NativeFontCapability> = profileListOf(),
    val styles: NativeStyleCapability = NativeStyleCapability(),
    val codePages: ProfileList<CodePageCapability> = profileListOf(),
)
data class RasterBandHeight(val min: Int, val preferred: Int, val max: Int)
data class RasterCapability(
    val supported: Boolean,
    val strategies: ProfileList<String> = profileListOf(),
    val preferredStrategy: String? = null,
    val maxWidthDots: Int? = null,
    val bandHeightDots: RasterBandHeight? = null,
)
data class QrModuleSize(val min: Int, val max: Int)
data class NativeQrCapability(
    val supported: Boolean,
    val strategies: ProfileList<String> = profileListOf(),
    val preferredStrategy: String? = null,
    val models: ProfileList<String> = profileListOf(),
    val moduleSize: QrModuleSize? = null,
    val errorCorrection: ProfileList<String> = profileListOf(),
)
data class NativeBarcodeCapability(
    val supported: Boolean,
    val strategies: ProfileList<String> = profileListOf(),
    val preferredStrategy: String? = null,
    val symbologies: ProfileList<String> = profileListOf(),
)
data class CutterCapability(
    val supported: Boolean,
    val modes: ProfileList<String> = profileListOf(),
    val strategy: String? = null,
)
data class PrinterBufferGuidance(val recommendedMaxBurstBytes: Int? = null, val recommendedPauseAfterBurstMs: Int? = null)
data class StatusQueryCapability(
    val supported: Boolean,
    val strategies: ProfileList<String> = profileListOf(),
    val preferredStrategy: String? = null,
)

/** Closed v1 family boundary; a future family requires a schema and validator change. */
sealed interface ProtocolProfile { val family: String }
data class EscPosProtocolProfile(val dialectId: String, val initializationStrategy: String) : ProtocolProfile {
    override val family: String get() = "escpos"
}

/** Only validation in this module can create a trusted value; no copy method can bypass it. */
class ValidatedPrinterProfile internal constructor(val profile: PrinterProfile)
class EffectivePrinterProfile internal constructor(val profile: PrinterProfile)

/** Capability blocks and lists replace completely. Identity/display/version are not calibration. */
data class PrinterProfileOverride(
    val geometry: PrinterGeometry? = null,
    val nativeText: NativeTextCapability? = null,
    val raster: RasterCapability? = null,
    val nativeQr: NativeQrCapability? = null,
    val nativeBarcode: NativeBarcodeCapability? = null,
    val cutter: CutterCapability? = null,
    val printerBuffer: PrinterBufferOverride = PrinterBufferOverride.Inherit,
    val statusQuery: StatusQueryCapability? = null,
    val protocol: ProtocolProfile? = null,
    val quirks: ProfileList<String>? = null,
)

/** Distinguishes omission from explicitly clearing unknown printer-side guidance. */
sealed interface PrinterBufferOverride {
    data object Inherit : PrinterBufferOverride
    data class Replace(val value: PrinterBufferGuidance?) : PrinterBufferOverride
}

data class ProfileDiagnostic(val code: String, val path: String, val message: String)
sealed interface ProfileValidationResult<out T> {
    data class Success<T>(val value: T) : ProfileValidationResult<T>
    data class Failure(val diagnostics: ProfileList<ProfileDiagnostic>) : ProfileValidationResult<Nothing>
}
