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

import com.circuitnext.rastrio.core.layout.LogicalQrPlaceholder
import com.circuitnext.rastrio.core.document.Alignment
import com.circuitnext.rastrio.core.document.QrErrorCorrection
import com.circuitnext.rastrio.core.profile.PrinterProfile
import com.circuitnext.rastrio.core.raster.*
import kotlin.coroutines.cancellation.CancellationException

internal data class ResolvedQr(
    val payload: ByteArray, val matrix: QrMatrix, val correction: String,
    val scale: Int, val size: Int, val x: Int, val native: Boolean,
    val nativeGraphic: PreparedMonochrome?,
)

internal fun resolveQr(block: LogicalQrPlaceholder, profile: PrinterProfile, target: PreparedTarget,
    options: PrintOptions, policy: PreparationResourcePolicy, graphicBytes: Long,
    isCancelled: () -> Boolean): ResolvedQr {
    fun failure(code: String, message: String): Nothing = fail(code, message, block.sourceBlockIndex)
    if (isCancelled()) failure("PRN130", "Preparation cancelled")
    if (block.payload.isEmpty() || block.payload.length > 2953) failure("PRN140", "QR payload is empty or exceeds capacity")
    var offset = 0
    while (offset < block.payload.length) {
        val c = block.payload[offset++]
        if (c.isHighSurrogate()) {
            if (offset == block.payload.length || !block.payload[offset++].isLowSurrogate()) failure("PRN140", "QR payload contains malformed Unicode")
        } else if (c.isLowSurrogate()) failure("PRN140", "QR payload contains malformed Unicode")
    }
    val payload = block.payload.encodeToByteArray()
    if (payload.size > 2953) failure("PRN140", "QR payload exceeds byte capacity")
    val correction = when (block.errorCorrection) {
        QrErrorCorrection.AUTO, QrErrorCorrection.MEDIUM -> QrCorrection.MEDIUM
        QrErrorCorrection.LOW -> QrCorrection.LOW
        QrErrorCorrection.QUARTILE -> QrCorrection.QUARTILE
        QrErrorCorrection.HIGH -> QrCorrection.HIGH
    }
    val code = when (correction) { QrCorrection.LOW -> "L"; QrCorrection.MEDIUM -> "M"; QrCorrection.QUARTILE -> "Q"; QrCorrection.HIGH -> "H" }
    val nonAscii = payload.any { it.toInt() and 255 >= 128 }
    val matrix = try { QrMatrixEncoder.encode(payload, correction, isCancelled, utf8Eci = nonAscii) }
        catch (_: CancellationException) { failure("PRN130", "Preparation cancelled") }
        catch (_: IllegalArgumentException) { failure("PRN140", "QR payload exceeds selected error correction capacity") }
    if (target.horizontalDpi != target.verticalDpi) failure("PRN142", "QR requires verified square-dot geometry")
    val boxWidth = dots(block.bounds.widthMm, target.horizontalDpi, target.printableWidthDots)
    val boxX = dots(block.bounds.xMm, target.horizontalDpi, target.printableWidthDots)
    val cap = profile.nativeQr
    // Firmware may optimize byte data into numeric/alphanumeric modes. Version 1 is invariant
    // across those optimizations; larger intended geometry needs a verified strategy extension.
    val knownNativeGeometry = matrix.version == 1 && !nonAscii
    val nativeScale = minOf(boxWidth / (matrix.size + 8), cap.moduleSize?.max ?: 0)
    val native = options.qrStrategy != QrStrategy.RASTER && cap.supported &&
        cap.preferredStrategy == "escpos.qr.model2" && "model2" in cap.models && code in cap.errorCorrection &&
        nativeScale >= (cap.moduleSize?.min ?: 1) && nativeScale in 1..8 && knownNativeGeometry
    if (options.qrStrategy == QrStrategy.NATIVE && !native)
        failure("PRN141", "Requested native QR configuration has no verified representation")
    if (!native && (!profile.raster.supported || profile.raster.preferredStrategy == null || profile.raster.bandHeightDots == null))
        failure("PRN141", "QR requires an available native or raster strategy")
    val scale = if (native) nativeScale else minOf(boxWidth, profile.raster.maxWidthDots ?: 0) / (matrix.size + 8)
    if (scale <= 0) failure("PRN142", "QR modules and quiet zone cannot fit requested dimensions")
    val size = (matrix.size + 8) * scale
    val x = boxX + when (block.alignment) { Alignment.LEFT -> 0; Alignment.CENTER -> (boxWidth - size) / 2; Alignment.RIGHT -> boxWidth - size }
    val payloadSize = ((size + 7) / 8).toLong() * size
    if (payloadSize > policy.maxPreparedGraphicBytes - graphicBytes || payloadSize > policy.maxResidentGraphicBytes - graphicBytes)
        failure("PRN120", "Finalized QR exceeds prepared graphic budgets")
    if (native && payloadSize > policy.maxGraphicBandBytes) failure("PRN120", "Native QR representative artifact exceeds band budget")
    var representative: PreparedMonochrome? = null
    if (native) {
        val result = matrix.rasterBands(scale, bandHeight = size, policy = RasterResourcePolicy(
            maxPayloadBytes = payloadSize, maxBandBytes = policy.maxGraphicBandBytes), isCancelled = isCancelled) {
            representative = PreparedMonochrome(it.width, it.height, it.copyBytes())
        }
        if (result == RasterResult.Cancelled) failure("PRN130", "Preparation cancelled")
        if (result !is RasterResult.Completed || result.bandCount != 1) failure("PRN120", "Native QR reference artifact cannot be finalized")
    }
    return ResolvedQr(payload, matrix, code, scale, size, x, native, representative)
}
