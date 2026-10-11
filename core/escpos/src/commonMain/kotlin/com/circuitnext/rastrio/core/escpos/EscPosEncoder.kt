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

import com.circuitnext.rastrio.core.printer.*
import com.circuitnext.rastrio.core.profile.*

/** Awaiting each write provides backpressure. False stops delivery; exceptions belong to the caller. */
fun interface EncodedByteSink {
    suspend fun write(bytes: ByteArray): Boolean
}

data class EncodingDiagnostic(val code: String, val message: String, val operationIndex: Int? = null)
sealed interface EncodingResult {
    data class Success(val bytesEmitted: Long) : EncodingResult
    data class Failure(val diagnostic: EncodingDiagnostic, val bytesEmitted: Long = 0) : EncodingResult
    data class Cancelled(val bytesEmitted: Long) : EncodingResult
}

data class EncodingResourcePolicy(val maxChunkBytes: Int = 64 * 1024,
    val maxEncodedBytes: Long = 256L * 1024 * 1024) {
    init {
        require(maxChunkBytes in 1..64 * 1024)
        require(maxEncodedBytes in 1..256L * 1024 * 1024)
    }
}

/** Serializes finalized decisions only. No character encoding, geometry or raster work occurs here. */
class EscPosEncoder(private val resources: EncodingResourcePolicy = EncodingResourcePolicy()) {
    /** Complete preflight before a transport is connected or receives any bytes. */
    fun validate(print: PreparedPrint): EncodingResult = try {
        checkPlan(print)
        var count = 0L
        for (part in parts(print)) {
            if (part.size > resources.maxEncodedBytes - count) abort("ESC120", "Encoded output exceeds policy")
            count += part.size
        }
        EncodingResult.Success(count)
    } catch (failure: EncodingAbort) {
        EncodingResult.Failure(failure.diagnostic)
    }

    suspend fun encode(print: PreparedPrint, sink: EncodedByteSink,
        cancelled: () -> Boolean = { false }): EncodingResult {
        if (cancelled()) return EncodingResult.Cancelled(0)
        val checked = validate(print)
        if (checked !is EncodingResult.Success) return checked
        var delivered = 0L
        val buffer = ByteArray(resources.maxChunkBytes)
        var used = 0
        suspend fun flush(): EncodingResult? {
            if (used == 0) return null
            if (cancelled()) return EncodingResult.Cancelled(delivered)
            if (!sink.write(buffer.copyOf(used))) return EncodingResult.Failure(
                EncodingDiagnostic("ESC130", "Byte sink rejected delivery"), delivered)
            delivered += used
            used = 0
            return null
        }
        for (part in parts(print)) {
            var offset = 0
            while (offset < part.size) {
                if (cancelled()) return EncodingResult.Cancelled(delivered)
                val length = minOf(part.size - offset, buffer.size - used)
                part.copyInto(buffer, used, offset, offset + length)
                used += length
                offset += length
                if (used == buffer.size) flush()?.let { return it }
            }
        }
        flush()?.let { return it }
        return if (cancelled()) EncodingResult.Cancelled(delivered) else EncodingResult.Success(delivered)
    }

    private fun checkPlan(print: PreparedPrint) {
        val target = print.target
        if (target.dialectId != "escpos.generic" || target.initializationStrategy != "escpos.init.standard")
            abort("ESC100", "Unsupported initialization or dialect")
        strategy(target.initializationStrategy, StrategyPurpose.INITIALIZATION, target.dialectId)
        if (target.horizontalDpi !in 1..255 || target.verticalDpi !in 1..255 || target.printableWidthDots > 65535)
            abort("ESC101", "Exact dot motion cannot be represented by this strategy")
        var y = 0
        var pending = false
        var pendingHeight = 0
        for ((index, operation) in print.operations.withIndex()) {
            if (operation.bounds.y != y) abort("ESC102", "Prepared paper position is inconsistent", index)
            when (operation) {
                is NativeText -> {
                    selector(operation.font.selector, StrategyPurpose.FONT_SELECTION, target.dialectId, index)
                    selector(operation.codePage.selector, StrategyPurpose.CODE_PAGE_SELECTION, target.dialectId, index)
                    if (operation.style.widthScale !in 1..8 || operation.style.heightScale !in 1..8)
                        abort("ESC103", "Native scaling cannot be represented", index)
                    if (operation.characterBytes.any { it !in 0x20..0x7e && it !in 0x80..0xff })
                        abort("ESC104", "Unsafe native character byte", index)
                    pendingHeight = maxOf(pendingHeight, operation.cellHeightDots)
                    if (operation.advanceDots > 0) {
                        if (minOf(operation.advanceDots, 255) < pendingHeight)
                            abort("ESC106", "Native advance is smaller than the buffered character height", index)
                        pendingHeight = 0
                    }
                    pending = operation.advanceDots == 0
                    y += operation.advanceDots
                }
                is Feed -> {
                    if (pending && minOf(operation.advanceDots, 255) < pendingHeight)
                        abort("ESC106", "Feed would change buffered native geometry", index)
                    pending = false
                    pendingHeight = 0
                    y += operation.advanceDots
                }
                is RasterOperation -> { graphic(operation.strategy, operation.graphic, target.dialectId, pending, index); y += operation.bounds.height }
                is Separator -> { graphic(operation.strategy, operation.graphic, target.dialectId, pending, index); y += operation.bounds.height }
                is ManualCutGuide -> { graphic(operation.strategy, operation.graphic, target.dialectId, pending, index); y += operation.bounds.height }
                is AutomaticCut -> {
                    strategy(operation.strategy, StrategyPurpose.CUT, target.dialectId, index)
                    if (operation.strategy != "escpos.cut.gs-v" || pending)
                        abort("ESC105", "Cut cannot represent the prepared state", index)
                }
            }
        }
        if (pending || y != print.heightDots) abort("ESC102", "Prepared plan has unflushed content or inconsistent height")
    }

    private fun strategy(id: String, purpose: StrategyPurpose, dialect: String, index: Int? = null) {
        val definition = BaselineTrustedProfileRegistry.strategy(id)
        if (definition == null || definition.purpose != purpose || dialect !in definition.dialectIds)
            abort("ESC100", "Unregistered protocol strategy", index)
    }
    private fun selector(value: RegisteredSelector?, purpose: StrategyPurpose, dialect: String, index: Int) {
        if (value == null) abort("ESC100", "Native selection requires an explicit registered selector", index)
        val expected = if (purpose == StrategyPurpose.FONT_SELECTION) "escpos.font.esc-m" else "escpos.code-page.esc-t"
        if (value.strategy != expected) abort("ESC100", "Selector has no implemented serializer", index)
        strategy(value.strategy, purpose, dialect, index)
        val range = BaselineTrustedProfileRegistry.strategy(value.strategy)?.selectorParameterRange
        if (range == null || value.parameter !in range) abort("ESC100", "Invalid selector parameter", index)
    }
    private fun graphic(id: String, content: PreparedMonochrome, dialect: String, pending: Boolean, index: Int) {
        strategy(id, StrategyPurpose.RASTER, dialect, index)
        if (id != "escpos.raster.gs-v-0" || content.rowBytes > 65535 || content.heightDots > 65535 || pending)
            abort("ESC105", "Graphic cannot represent the prepared state", index)
    }

    private fun parts(print: PreparedPrint): Sequence<ByteArray> = sequence {
        yield(bytes(0x1b, 0x40))
        yield(bytes(0x1d, 0x50, print.target.horizontalDpi, print.target.verticalDpi))
        yield(bytes(0x1d, 0x4c, 0, 0))
        yield(bytes(0x1d, 0x57, print.target.printableWidthDots and 255, print.target.printableWidthDots ushr 8))
        // Prepared x coordinates already include alignment; protocol alignment must not apply it twice.
        yield(bytes(0x1b, 0x61, 0))
        for (operation in print.operations) {
            when (operation) {
                is NativeText -> {
                    yieldAll(nativeStateCommands(operation.font.selector!!.parameter,
                        operation.codePage.selector!!.parameter, operation.style, operation.bounds.x))
                    var position = 0
                    while (position < operation.characterBytes.size) {
                        val size = minOf(resources.maxChunkBytes, operation.characterBytes.size - position)
                        yield(ByteArray(size) { operation.characterBytes[position + it].toByte() })
                        position += size
                    }
                    yieldAll(feed(operation.advanceDots))
                }
                is Feed -> yieldAll(feed(operation.advanceDots))
                is RasterOperation -> yieldAll(raster(operation.bounds, operation.graphic))
                is Separator -> yieldAll(raster(operation.bounds, operation.graphic))
                is ManualCutGuide -> yieldAll(raster(operation.bounds, operation.graphic))
                is AutomaticCut -> yield(bytes(0x1d, 0x56, if (operation.mode == CutMode.FULL) 0 else 1))
            }
        }
    }
    private fun feed(dots: Int): Sequence<ByteArray> = sequence {
        var remaining = dots
        while (remaining > 0) {
            val advance = minOf(remaining, 255)
            yield(bytes(0x1b, 0x4a, advance))
            remaining -= advance
        }
    }
    private fun raster(bounds: DotBounds, content: PreparedMonochrome): Sequence<ByteArray> = sequence {
        yield(bytes(0x1d, 0x4c, bounds.x and 255, bounds.x ushr 8))
        yield(bytes(0x1d, 0x76, 0x30, 0, content.rowBytes and 255, content.rowBytes ushr 8,
            content.heightDots and 255, content.heightDots ushr 8))
        var position = 0
        while (position < content.sizeBytes) {
            val size = minOf(resources.maxChunkBytes, content.sizeBytes - position)
            yield(ByteArray(size) { content.byteAt(position + it) })
            position += size
        }
        yield(bytes(0x1d, 0x4c, 0, 0))
    }
}
private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }
private class EncodingAbort(val diagnostic: EncodingDiagnostic) : RuntimeException()
private fun abort(code: String, message: String, index: Int? = null): Nothing =
    throw EncodingAbort(EncodingDiagnostic(code, message, index))

/** Protocol state serialization; takes resolved safe scalars, never content or geometry decisions. */
internal fun nativeStateCommands(font: Int, codePage: Int, style: PreparedNativeStyle, x: Int): Sequence<ByteArray> {
    require(font in 0..1 && codePage in 0..255 && x in 0..65535)
    require(style.widthScale in 1..8 && style.heightScale in 1..8)
    return sequenceOf(
        bytes(0x1b, 0x4d, font), bytes(0x1b, 0x74, codePage),
        bytes(0x1b, 0x45, if (style.bold) 1 else 0),
        bytes(0x1b, 0x2d, if (style.underline) 1 else 0),
        bytes(0x1d, 0x21, ((style.widthScale - 1) shl 4) or (style.heightScale - 1)),
        bytes(0x1b, 0x24, x and 255, x ushr 8),
    )
}
