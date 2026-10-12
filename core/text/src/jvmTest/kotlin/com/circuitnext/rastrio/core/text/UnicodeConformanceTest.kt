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

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UnicodeConformanceTest {
    private fun fixtures(name: String, boundaries: (String) -> List<Int>) {
        val failures = ArrayList<String>()
        var count = 0
        File(System.getProperty("rastrio.unicode.data"), name).forEachLine { line ->
            val test = line.substringBefore('#').trim()
            if (test.isEmpty()) return@forEachLine
            val text = StringBuilder()
            val expected = ArrayList<Int>()
            for (token in test.split(Regex("\\s+"))) {
                when (token) {
                    "÷" -> expected.add(text.length)
                    "×" -> Unit
                    else -> text.appendCodePoint(token.toInt(16))
                }
            }
            val actual = boundaries(text.toString())
            if (actual != expected && failures.size < 20) failures.add("case $count $test expected=$expected actual=$actual")
            count++
        }
        assertTrue(count > 500)
        assertEquals(emptyList(), failures, "$name conformance failures")
    }
    @Test fun officialGraphemeBreakTests() = fixtures("GraphemeBreakTest.txt") { UnicodeSegmentation.graphemeBoundaries(it) }
    @Test fun officialLineBreakTests() = fixtures("LineBreakTest.txt") { UnicodeLineBreaking.opportunities(it).map { opportunity -> opportunity.offset } }
}
