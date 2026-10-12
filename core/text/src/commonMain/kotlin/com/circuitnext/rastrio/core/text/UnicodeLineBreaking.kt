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

/** Default UAX #14 revision 57, without dictionary tailoring or inserted hyphens. */
object UnicodeLineBreaking {
    private val hard = setOf(LINE_BK, LINE_CR, LINE_LF, LINE_NL)
    private val alpha = setOf(LINE_AL, LINE_HL)
    private val hangul = setOf(LINE_JL, LINE_JV, LINE_JT, LINE_H2, LINE_H3)
    fun opportunities(text: String): SnapshotList<BreakOpportunity> {
        val points = UnicodeSegmentation.points(text)
        if (points.isEmpty()) return SnapshotList(emptyList())
        val classes = IntArray(points.size)
        val retained = ArrayList<Int>()
        for (i in points.indices) {
            classes[i] = when (val original = line(points[i].code)) {
                LINE_OTHER, LINE_AI, LINE_SG, LINE_XX -> LINE_AL
                LINE_SA -> if (category(points[i].code) in setOf(CATEGORY_MN, CATEGORY_MC)) LINE_CM else LINE_AL
                LINE_CJ -> LINE_NS
                else -> original
            }
            if (classes[i] in setOf(LINE_CM, LINE_ZWJ)) {
                val previous = retained.lastOrNull()
                if (previous != null && classes[previous] !in hard && classes[previous] !in setOf(LINE_SP, LINE_ZW)) {
                    classes[i] = classes[previous]
                    continue
                }
                classes[i] = LINE_AL
            }
            retained.add(i)
        }
        fun c(index: Int) = if (index in retained.indices) classes[retained[index]] else -1
        fun code(index: Int) = if (index in retained.indices) points[retained[index]].code else -1
        fun cat(index: Int) = category(code(index))
        fun ea(index: Int) = eastAsian(code(index)) in setOf(EASTASIAN_F, EASTASIAN_W, EASTASIAN_H)
        fun aksara(index: Int) = c(index) in setOf(LINE_AK, LINE_AS) || code(index) == 0x25cc
        val beforeSpaces = IntArray(retained.size)
        val beforeNumeric = IntArray(retained.size)
        val regionalCount = IntArray(retained.size)
        for (j in retained.indices) {
            beforeSpaces[j] = if (c(j) == LINE_SP && j > 0) beforeSpaces[j - 1] else j
            beforeNumeric[j] = if (c(j) in setOf(LINE_SY, LINE_IS) && j > 0) beforeNumeric[j - 1] else j
            regionalCount[j] = if (c(j) == LINE_RI) 1 + if (j > 0) regionalCount[j - 1] else 0 else 0
        }
        fun allowed(j: Int): Boolean {
            val a = c(j - 1)
            val b = c(j)
            val left = j - 1
            val right = j
            val before = c(j - 2)
            val after = c(j + 1)
            val p = beforeSpaces[left]
            val priorSpace = c(p)
            if (a == LINE_BK) return true // LB4
            if (a == LINE_CR && b == LINE_LF) return false
            if (a in hard) return true // LB5
            if (b in hard || b in setOf(LINE_SP, LINE_ZW)) return false // LB6–7
            if (priorSpace == LINE_ZW) return true // LB8
            if (line(points[retained[right] - 1].code) == LINE_ZWJ) return false // LB8a
            if (a == LINE_WJ || b == LINE_WJ || a == LINE_GL) return false // LB11–12
            if (b == LINE_GL && a !in setOf(LINE_SP, LINE_HY, LINE_HH)) return false
            if (b in setOf(LINE_CL, LINE_CP, LINE_EX, LINE_SY)) return false // LB13
            if (priorSpace == LINE_OP) return false // LB14
            if (priorSpace == LINE_QU && cat(p) == CATEGORY_PI &&
                (p == 0 || c(p - 1) in hard || c(p - 1) in setOf(LINE_OP, LINE_QU, LINE_GL, LINE_SP, LINE_ZW))) return false
            if (b == LINE_QU && cat(right) == CATEGORY_PF &&
                (right == retained.lastIndex || after in hard || after in setOf(LINE_SP, LINE_GL, LINE_WJ, LINE_CL, LINE_QU, LINE_CP, LINE_EX, LINE_IS, LINE_SY, LINE_ZW))) return false
            if (a == LINE_SP && b == LINE_IS && after == LINE_NU) return true // LB15c
            if (b == LINE_IS) return false
            if (priorSpace in setOf(LINE_CL, LINE_CP) && b == LINE_NS) return false
            if (priorSpace == LINE_B2 && b == LINE_B2) return false
            if (a == LINE_SP) return true // LB18
            if (b == LINE_QU && cat(right) != CATEGORY_PI || a == LINE_QU && cat(left) != CATEGORY_PF) return false
            if (b == LINE_QU && (!ea(left) || right == retained.lastIndex || !ea(right + 1))) return false
            if (a == LINE_QU && (!ea(right) || left == 0 || !ea(left - 1))) return false
            if (a == LINE_CB || b == LINE_CB) return true
            if (a in setOf(LINE_HY, LINE_HH) && b in alpha &&
                (left == 0 || before in hard || before in setOf(LINE_SP, LINE_ZW, LINE_CB, LINE_GL))) return false
            if (b in setOf(LINE_BA, LINE_HH, LINE_HY, LINE_NS) || a == LINE_BB) return false
            if (before == LINE_HL && a in setOf(LINE_HY, LINE_HH) && b != LINE_HL) return false
            if (a == LINE_SY && b == LINE_HL || b == LINE_IN) return false
            if (a in alpha && b == LINE_NU || a == LINE_NU && b in alpha) return false
            if (a == LINE_PR && b in setOf(LINE_ID, LINE_EB, LINE_EM) || a in setOf(LINE_ID, LINE_EB, LINE_EM) && b == LINE_PO) return false
            if (a in setOf(LINE_PR, LINE_PO) && b in alpha || a in alpha && b in setOf(LINE_PR, LINE_PO)) return false
            // LB25 numeric expression context, precomputed to remain linear for long numbers.
            val numericStart = if (a in setOf(LINE_CL, LINE_CP) && left > 0) beforeNumeric[left - 1] else beforeNumeric[left]
            if (b in setOf(LINE_PO, LINE_PR) && c(numericStart) == LINE_NU) return false
            if (a in setOf(LINE_PO, LINE_PR) && (b == LINE_NU || b == LINE_OP &&
                (after == LINE_NU || after == LINE_IS && c(right + 2) == LINE_NU))) return false
            if (a in setOf(LINE_HY, LINE_IS) && b == LINE_NU) return false
            if (b == LINE_NU && c(beforeNumeric[left]) == LINE_NU) return false
            if (a == LINE_JL && b in setOf(LINE_JL, LINE_JV, LINE_H2, LINE_H3) ||
                a in setOf(LINE_JV, LINE_H2) && b in setOf(LINE_JV, LINE_JT) ||
                a in setOf(LINE_JT, LINE_H3) && b == LINE_JT) return false
            if (a in hangul && b == LINE_PO || a == LINE_PR && b in hangul) return false
            if (a in alpha && b in alpha) return false
            if (a == LINE_AP && aksara(right) || aksara(left) && b in setOf(LINE_VF, LINE_VI) ||
                aksara(left - 1) && a == LINE_VI && (b == LINE_AK || code(right) == 0x25cc) ||
                aksara(left) && aksara(right) && after == LINE_VF) return false
            if (a == LINE_IS && b in alpha) return false
            if ((a in alpha || a == LINE_NU) && b == LINE_OP && !ea(right) ||
                a == LINE_CP && !ea(left) && (b in alpha || b == LINE_NU)) return false
            if (a == LINE_RI && b == LINE_RI && regionalCount[left] % 2 == 1) return false
            if (b == LINE_EM && (a == LINE_EB || emoji(code(left)) == EMOJI_EXTENDED_PICTOGRAPHIC && cat(left) == CATEGORY_CN)) return false
            return true
        }
        val breaks = ArrayList<BreakOpportunity>()
        for (j in 1 until retained.size) if (allowed(j)) {
            breaks.add(BreakOpportunity(points[retained[j]].start,
                if (c(j - 1) in hard) BreakKind.MANDATORY else BreakKind.ALLOWED))
        }
        breaks.add(BreakOpportunity(text.length, BreakKind.MANDATORY))
        return SnapshotList(breaks)
    }
}
