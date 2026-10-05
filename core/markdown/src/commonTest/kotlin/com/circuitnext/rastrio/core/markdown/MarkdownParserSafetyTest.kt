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

package com.circuitnext.rastrio.core.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MarkdownParserSafetyTest {
    @Test fun unmatchedImageCachedScansConsumeTheParserWorkBudget() {
        val result = MarkdownCompiler.compile("![".repeat(1000), MarkdownResourcePolicy(maxParserOperations = 100_000))
        assertNull(result.document)
        assertEquals(listOf("MD121"), result.diagnostics.map { it.code })
        assertEquals("Markdown parser work exceeds policy", result.diagnostics.single().message)
    }
    @Test fun workBudgetHasAnExactBoundary() {
        val source = "![".repeat(12)
        var low = 1L
        var high = 100_000L
        assertNotNull(MarkdownCompiler.compile(source, MarkdownResourcePolicy(maxParserOperations = high)).document)
        while (low < high) {
            val middle = low + (high - low) / 2
            if (MarkdownCompiler.compile(source, MarkdownResourcePolicy(maxParserOperations = middle)).document == null) low = middle + 1
            else high = middle
        }
        assertNotNull(MarkdownCompiler.compile(source, MarkdownResourcePolicy(maxParserOperations = low)).document)
        val over = MarkdownCompiler.compile(source, MarkdownResourcePolicy(maxParserOperations = low - 1))
        assertNull(over.document)
        assertEquals("MD121", over.diagnostics.single().code)
    }

    @Test fun legitimateImagesAndNestedBracketsUseWorkRatherThanSyntaxQuotas() {
        val policy = MarkdownResourcePolicy(maxParserOperations = 1_000_000)
        val images = MarkdownCompiler.compile("![alt](asset.png)\n\n".repeat(1000), policy)
        assertNotNull(images.document)
        assertEquals(1000, images.document.blocks.size)
        assertTrue(images.diagnostics.all { it.code == "MD201" })
        assertNotNull(MarkdownCompiler.compile("[outer [inner] text](destination)\n\n".repeat(1000), policy).document)
        assertNull(MarkdownCompiler.compile("![".repeat(1000), policy).document)
    }

    @Test fun sourceLinesCountNormalizedTerminatorsAndFinalLine() {
        for (terminator in listOf("\n", "\r", "\r\n")) {
            val policy = MarkdownResourcePolicy(maxSourceLines = 3)
            assertNotNull(MarkdownCompiler.compile(terminator.repeat(2), policy).document)
            val over = MarkdownCompiler.compile(terminator.repeat(3), policy)
            assertNull(over.document)
            assertEquals("MD121", over.diagnostics.single().code)
            assertEquals("Markdown line count exceeds policy", over.diagnostics.single().message)
        }
        assertNotNull(MarkdownCompiler.compile("", MarkdownResourcePolicy(maxSourceLines = 1)).document)
        assertNotNull(MarkdownCompiler.compile("x", MarkdownResourcePolicy(maxSourceLines = 1)).document)
    }

    @Test fun manyShortLinesRespectIndependentLineAndByteLimits() {
        val policy = MarkdownResourcePolicy(maxSourceLines = 1000)
        assertNotNull(MarkdownCompiler.compile("x\n".repeat(999), policy).document)
        val over = MarkdownCompiler.compile("x\n".repeat(1000), policy)
        assertEquals("Markdown line count exceeds policy", over.diagnostics.single().message)
        assertEquals("MD120", MarkdownCompiler.compile("x\n", policy.copy(maxSourceBytes = 1)).diagnostics.single().code)
    }

    @Test fun originalAttacksReturnResourceDiagnostics() {
        for (source in listOf("![".repeat(12_000), "\n".repeat(4_000_000))) {
            val result = MarkdownCompiler.compile(source)
            assertNull(result.document)
            assertEquals("MD121", result.diagnostics.single().code)
        }
    }
}
