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

package com.circuitnext.rastrio.core.text

/** Repository-pinned Unicode 18.0 segmentation; offsets are UTF-16 boundaries. */
object UnicodeSegmentation {
    const val version = "18.0.0"
    internal data class CodePoint(val code: Int, val start: Int, val end: Int)
    internal fun points(text: String): List<CodePoint> {
        require(text.length <= 65536)
        val result = ArrayList<CodePoint>()
        var i = 0
        while (i < text.length) {
            val start = i
            val first = text[i++].code
            val code = if (first in 0xD800..0xDBFF) {
                require(i < text.length && text[i].code in 0xDC00..0xDFFF) { "Invalid UTF-16" }
                0x10000 + ((first - 0xD800) shl 10) + (text[i++].code - 0xDC00)
            } else { require(first !in 0xDC00..0xDFFF) { "Invalid UTF-16" }; first }
            result.add(CodePoint(code, start, i))
        }
        return result
    }
    fun graphemeBoundaries(text: String): SnapshotList<Int> {
        val p = points(text)
        val boundaries = mutableListOf(0)
        var regionalCount = 0
        var emojiExtend = false
        var emojiZwj = false
        var indicLinker = false
        for (i in p.indices) {
            val current = grapheme(p[i].code)
            val previous = if (i > 0) grapheme(p[i - 1].code) else GRAPHEME_OTHER
            val ind = indic(p[i].code)
            val ep = emoji(p[i].code) == EMOJI_EXTENDED_PICTOGRAPHIC
            val joined = i > 0 && when {
                previous == GRAPHEME_CR && current == GRAPHEME_LF -> true
                previous in listOf(GRAPHEME_CR, GRAPHEME_LF, GRAPHEME_CONTROL) -> false
                current in listOf(GRAPHEME_CR, GRAPHEME_LF, GRAPHEME_CONTROL) -> false
                previous == GRAPHEME_L && current in listOf(GRAPHEME_L, GRAPHEME_V, GRAPHEME_LV, GRAPHEME_LVT) -> true
                previous in listOf(GRAPHEME_LV, GRAPHEME_V) && current in listOf(GRAPHEME_V, GRAPHEME_T) -> true
                previous in listOf(GRAPHEME_LVT, GRAPHEME_T) && current == GRAPHEME_T -> true
                current in listOf(GRAPHEME_EXTEND, GRAPHEME_ZWJ, GRAPHEME_SPACINGMARK) -> true
                previous == GRAPHEME_PREPEND -> true
                ind == INDIC_CONSONANT && indicLinker -> true
                ep && emojiZwj -> true
                previous == GRAPHEME_REGIONAL_INDICATOR && current == GRAPHEME_REGIONAL_INDICATOR && regionalCount % 2 == 1 -> true
                else -> false
            }
            if (i > 0 && !joined) boundaries.add(p[i].start)
            regionalCount = if (current == GRAPHEME_REGIONAL_INDICATOR) regionalCount + 1 else 0
            emojiZwj = current == GRAPHEME_ZWJ && emojiExtend
            emojiExtend = ep || (current == GRAPHEME_EXTEND && emojiExtend)
            when (ind) {
                INDIC_CONSONANT -> indicLinker = false
                INDIC_LINKER -> indicLinker = true
                INDIC_EXTEND -> Unit
                else -> indicLinker = false
            }
        }
        if (text.isNotEmpty()) boundaries.add(text.length)
        return SnapshotList(boundaries)
    }
}
