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

import com.circuitnext.rastrio.core.layout.LogicalDocument
import com.circuitnext.rastrio.core.profile.RegisteredSelector
import com.circuitnext.rastrio.core.text.SnapshotList

enum class CutIntent { NONE, MANUAL_GUIDE, AUTOMATIC, AUTOMATIC_OR_MANUAL }
enum class CutMode { FULL, PARTIAL }
enum class PreparedAlignment { LEFT, CENTER, RIGHT }

data class PrintOptions(
    val nativeFontId: String? = null,
    val codePageId: String? = null,
    val widthScale: Int = 1,
    val heightScale: Int = 1,
    val cutIntent: CutIntent = CutIntent.NONE,
    val cutMode: CutMode = CutMode.FULL,
    val finalFeedDots: Int = 0,
) {
    init {
        require(nativeFontId == null || nativeFontId.isNotBlank())
        require(codePageId == null || codePageId.isNotBlank())
        require(widthScale > 0 && heightScale > 0 && finalFeedDots >= 0)
    }
}

/** Trusted application ceilings, independent of printer capability data. */
data class PreparationResourcePolicy(
    val maxOperations: Int = 100_000,
    val maxDiagnostics: Int = 1_000,
    val maxTextCodeUnits: Int = 8 * 1024 * 1024,
    val maxHeightDots: Int = 100_000_000,
    val maxGraphicBandBytes: Int = 2 * 1024 * 1024,
    val maxPreparedGraphicBytes: Long = 128L * 1024 * 1024,
    val maxResidentGraphicBytes: Int = 8 * 1024 * 1024,
) {
    init {
        require(maxOperations > 0 && maxDiagnostics > 0 && maxTextCodeUnits > 0 && maxHeightDots > 0)
        require(maxGraphicBandBytes in 1..2 * 1024 * 1024)
        require(maxPreparedGraphicBytes in 1..128L * 1024 * 1024)
        require(maxResidentGraphicBytes in 1..8 * 1024 * 1024)
    }
}

data class PreparationDiagnostic(val code: String, val message: String, val sourceBlockIndex: Int? = null) {
    init { require(code.isNotBlank() && message.isNotBlank() && (sourceBlockIndex == null || sourceBlockIndex >= 0)) }
}

/** Integer coordinates in the printable strip; no unverified paper margins are implied. */
data class DotBounds(val x: Int, val y: Int, val width: Int, val height: Int) {
    init {
        require(x >= 0 && y >= 0 && width >= 0 && height >= 0)
        require(x <= Int.MAX_VALUE - width && y <= Int.MAX_VALUE - height)
    }
    val right: Int get() = x + width
    val bottom: Int get() = y + height
}

class PreparedTarget internal constructor(
    val profileId: String,
    val profileRevision: Int,
    val horizontalDpi: Int,
    val verticalDpi: Int,
    val printableWidthDots: Int,
    val dialectId: String,
    val initializationStrategy: String,
) {
    init {
        require(profileId.isNotBlank() && profileRevision > 0)
        require(horizontalDpi > 0 && verticalDpi > 0 && printableWidthDots > 0)
        require(dialectId.isNotBlank() && initializationStrategy.isNotBlank())
    }
}

/** A null selector means the validated, unambiguous dialect default was selected. */
class PreparedNativeFont internal constructor(
    val id: String,
    val selector: RegisteredSelector?,
    val cellWidthDots: Int,
    val cellHeightDots: Int,
    val lineAdvanceDots: Int,
) {
    init { require(id.isNotBlank() && cellWidthDots > 0 && cellHeightDots > 0 && lineAdvanceDots > 0) }
}
class PreparedCodePage internal constructor(val id: String, val repertoire: String, val selector: RegisteredSelector?) {
    init { require(id.isNotBlank() && repertoire.isNotBlank()) }
}
data class PreparedNativeStyle(val bold: Boolean = false, val underline: Boolean = false,
    val widthScale: Int = 1, val heightScale: Int = 1) {
    init { require(widthScale > 0 && heightScale > 0) }
}

/** Packed MSB-first rows, copied once; consumers read the finalized bits without rendering again. */
class PreparedMonochrome internal constructor(val widthDots: Int, val heightDots: Int, bytes: ByteArray) {
    val rowBytes: Int
    private val content: ByteArray
    val sizeBytes: Int get() = content.size
    init {
        require(widthDots > 0 && heightDots > 0)
        rowBytes = widthDots / 8 + if (widthDots % 8 == 0) 0 else 1
        require(rowBytes.toLong() * heightDots <= 2L * 1024 * 1024)
        require(bytes.size.toLong() == rowBytes.toLong() * heightDots)
        content = bytes.copyOf()
        if (widthDots % 8 != 0) {
            val paddingMask = (1 shl (8 - widthDots % 8)) - 1
            for (row in 0 until heightDots) require(content[(row + 1) * rowBytes - 1].toInt() and paddingMask == 0)
        }
    }
    fun byteAt(index: Int): Byte = content[index]
    fun isBlack(x: Int, y: Int): Boolean {
        require(x in 0 until widthDots && y in 0 until heightDots)
        return content[y * rowBytes + x / 8].toInt() and (0x80 ushr (x % 8)) != 0
    }
    fun copyBytes(): ByteArray = content.copyOf()
}

sealed interface PreparedOperation {
    val bounds: DotBounds
    val sourceBlockIndex: Int?
}

class NativeText internal constructor(
    val text: String,
    val characterBytes: SnapshotList<Int>,
    val font: PreparedNativeFont,
    val codePage: PreparedCodePage,
    val style: PreparedNativeStyle,
    val alignment: PreparedAlignment,
    override val bounds: DotBounds,
    /** Paper advance after this run; zero continues a styled line at the same cell top. */
    val advanceDots: Int,
    override val sourceBlockIndex: Int? = null,
) : PreparedOperation {
    val cellWidthDots: Int
    val cellHeightDots: Int
    init {
        require(sourceBlockIndex == null || sourceBlockIndex >= 0)
        require(font.cellWidthDots <= Int.MAX_VALUE / style.widthScale)
        require(font.cellHeightDots <= Int.MAX_VALUE / style.heightScale)
        cellWidthDots = font.cellWidthDots * style.widthScale
        cellHeightDots = font.cellHeightDots * style.heightScale
        require(characterBytes.size == text.length && characterBytes.all { it in 0x20..0x7e || it in 0x80..0xff })
        require(text.none { it.code < 0x20 || it.code == 0x7f })
        require(characterBytes.size.toLong() * cellWidthDots == bounds.width.toLong())
        require(bounds.height == cellHeightDots && advanceDots >= 0)
        require(bounds.y <= Int.MAX_VALUE - advanceDots)
    }
}
class Separator internal constructor(
    override val bounds: DotBounds,
    val graphic: PreparedMonochrome,
    val strategy: String,
    override val sourceBlockIndex: Int? = null,
) : PreparedOperation {
    init { validateGraphic(bounds, graphic, strategy, sourceBlockIndex) }
}
class ManualCutGuide internal constructor(
    override val bounds: DotBounds,
    val graphic: PreparedMonochrome,
    val strategy: String,
    override val sourceBlockIndex: Int? = null,
) : PreparedOperation {
    init { validateGraphic(bounds, graphic, strategy, sourceBlockIndex) }
}
class Feed internal constructor(
    override val bounds: DotBounds,
    val advanceDots: Int,
    override val sourceBlockIndex: Int? = null,
) : PreparedOperation {
    init {
        require(bounds.x == 0 && bounds.width == 0 && advanceDots > 0 && bounds.height == advanceDots)
        require(sourceBlockIndex == null || sourceBlockIndex >= 0)
    }
}
class AutomaticCut internal constructor(
    override val bounds: DotBounds,
    val mode: CutMode,
    val strategy: String,
    override val sourceBlockIndex: Int? = null,
) : PreparedOperation {
    init {
        require(bounds.x == 0 && bounds.width == 0 && bounds.height == 0 && strategy.isNotBlank())
        require(sourceBlockIndex == null || sourceBlockIndex >= 0)
    }
}
private fun validateGraphic(bounds: DotBounds, graphic: PreparedMonochrome, strategy: String, source: Int?) {
    require(bounds.width == graphic.widthDots && bounds.height == graphic.heightDots && strategy.isNotBlank())
    require(source == null || source >= 0)
}

/** Only printer preparation can publish a plan. No public copy can bypass completeness checks. */
class PreparedPrint internal constructor(
    val logicalDocument: LogicalDocument,
    val target: PreparedTarget,
    val operations: SnapshotList<PreparedOperation>,
    val diagnostics: SnapshotList<PreparationDiagnostic>,
    val heightDots: Int,
    resources: PreparationResourcePolicy = PreparationResourcePolicy(),
) {
    init {
        require(heightDots in 0..resources.maxHeightDots)
        require(operations.size <= resources.maxOperations && diagnostics.size <= resources.maxDiagnostics)
        var lastY = 0
        var textUnits = 0L
        var graphicBytes = 0L
        for (operation in operations) {
            require(operation.bounds.right <= target.printableWidthDots && operation.bounds.bottom <= heightDots)
            require(operation.bounds.y >= lastY)
            lastY = operation.bounds.y
            when (operation) {
                is NativeText -> {
                    textUnits += operation.text.length
                    require(operation.bounds.y.toLong() + operation.advanceDots <= heightDots)
                }
                is Separator -> {
                    require(operation.graphic.sizeBytes <= resources.maxGraphicBandBytes)
                    graphicBytes += operation.graphic.sizeBytes
                }
                is ManualCutGuide -> {
                    require(operation.graphic.sizeBytes <= resources.maxGraphicBandBytes)
                    graphicBytes += operation.graphic.sizeBytes
                }
                is Feed, is AutomaticCut -> Unit
            }
            require(textUnits <= resources.maxTextCodeUnits && graphicBytes <= resources.maxPreparedGraphicBytes &&
                graphicBytes <= resources.maxResidentGraphicBytes)
        }
    }
}
sealed interface PreparationResult {
    data class Success(val print: PreparedPrint) : PreparationResult
    data class Failure(val diagnostics: SnapshotList<PreparationDiagnostic>) : PreparationResult {
        init { require(diagnostics.isNotEmpty()) }
    }
}
