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

package com.circuitnext.rastrio.presentation

import com.circuitnext.rastrio.core.document.*
import com.circuitnext.rastrio.core.layout.*
import com.circuitnext.rastrio.core.markdown.*
import com.circuitnext.rastrio.core.text.*
import kotlin.test.*

class MarkdownAuthoringSessionTest {
    private val body = ResolvedTypography("authoring-test", TextStyle(),
        TypographyMetrics(3.0, 1.0, 1.0, 5.0, 3.0), FixedCellGeometry(1.0, 1.0, 4.0))
    private val constraints = LayoutConstraints(Length(48.0), Orientation.PORTRAIT, TypographyContext(body, body, body))
    private fun session(input: LayoutConstraints = constraints,
        engine: LogicalLayoutEngine = FoundationLayoutEngine(AsciiFixedCellMeasurer()),
        policy: MarkdownResourcePolicy = MarkdownResourcePolicy()) = MarkdownAuthoringSession(input, engine, policy)
    private fun ready(session: MarkdownAuthoringSession) = assertIs<MarkdownAuthoringResult.Ready>(session.state.result)
    private fun compile(session: MarkdownAuthoringSession, source: String): MarkdownAuthoringResult {
        session.edit(source)
        session.compile()
        return session.state.result
    }
    private fun text(result: MarkdownAuthoringResult.Ready) =
        result.logicalDocument.blocks.filterIsInstance<LogicalTextBlock>().flatMap { it.lines }
            .map { line -> line.runs.joinToString("") { it.text } }
    private fun engine(action: (ThermalDocument, LayoutConstraints) -> LayoutResult) = object : LogicalLayoutEngine {
        override fun layout(document: ThermalDocument, constraints: LayoutConstraints) = action(document, constraints)
    }

    @Test fun initialStateHasEmptySourceAndNoGeneratedOutput() {
        val session = session()
        assertEquals("", session.state.source)
        assertSame(MarkdownAuthoringResult.NotCompiled, session.state.result)
    }

    @Test fun editingRetainsSourceWithoutInvokingThePipeline() {
        val session = session(engine = engine { _, _ -> error("Editing must not layout") })
        val initial = session.state
        session.edit("# Draft\r\n\r\nBody")
        assertEquals("# Draft\r\n\r\nBody", session.state.source)
        assertSame(MarkdownAuthoringResult.NotCompiled, session.state.result)
        assertEquals("", initial.source)
        assertSame(MarkdownAuthoringResult.NotCompiled, initial.result)
    }

    @Test fun realPipelineExposesSemanticStylesAndResolvedWrapping() {
        val session = session(constraints.copy(canvasWidth = Length(4.0)))
        val result = assertIs<MarkdownAuthoringResult.Ready>(compile(session, "AB **CD**"))
        assertEquals(ThermalDocument(blocks = listOf(Paragraph(Alignment.LEFT,
            listOf(Text("AB "), Strong(listOf(Text("CD"))))))), result.document)
        assertEquals(listOf("AB ", "CD"), text(result))
        val block = assertIs<LogicalTextBlock>(result.logicalDocument.blocks.single())
        assertEquals(listOf(0.0, 5.0), block.lines.map { it.bounds.yMm })
        assertEquals(TextWeight.BOLD, block.lines.last().runs.single().typography.style.weight)
        assertEquals(12.5, result.preview.heightMm)
        assertEquals(4.0, result.preview.widthMm)
        assertSame(result.logicalDocument.blocks, result.preview.blocks)
        assertSame(result.logicalDocument.diagnostics, result.preview.diagnostics)
        assertTrue(result.markdownDiagnostics.isEmpty())
    }

    @Test fun emptySourceCanBeExplicitlyCompiledToAnEmptyPreview() {
        val session = session()
        session.compile()
        val result = ready(session)
        assertTrue(result.document.blocks.isEmpty())
        assertTrue(result.preview.blocks.isEmpty())
        assertEquals(0.0, result.preview.heightMm)
    }

    @Test fun realMixedGfmPipelinePreservesAllLogicalBlockFamiliesOnAWideCanvas() {
        val source = "# Heading\n\nBody\n\n- outer\n  - nested\n\n- [x] done\n\n> quote\n\n```\nA\tB\n```\n\n---\n\n| H | Q |\n| --- | ---: |\n| A | B |\n\n![alt](https://invalid.example/never-fetch.png)"
        val session = session(constraints.copy(canvasWidth = Length(180.0), orientation = Orientation.LANDSCAPE))
        val result = assertIs<MarkdownAuthoringResult.Ready>(compile(session, source))
        assertEquals(listOf(Heading::class, Paragraph::class, UnorderedList::class, Checklist::class,
            Quote::class, CodeBlock::class, Separator::class, Table::class, Image::class), result.document.blocks.map { it::class })
        assertEquals(9, result.preview.blocks.size)
        assertEquals(180.0, result.preview.widthMm)
        assertEquals(Orientation.LANDSCAPE, result.preview.orientation)
        assertSame(result.logicalDocument.blocks, result.preview.blocks)
        assertEquals(listOf("MD201"), result.markdownDiagnostics.map { it.code })
        assertIs<LogicalImagePlaceholder>(result.preview.blocks.last())
        assertIs<LogicalTableBlock>(result.preview.blocks[7])
        assertIs<LogicalQuoteBlock>(result.preview.blocks[4])
        assertEquals(LogicalTextKind.CODE, assertIs<LogicalTextBlock>(result.preview.blocks[5]).kind)
    }

    @Test fun markdownWarningKeepsStructuredOffsetsSeverityAndMessage() {
        val source = "![alt](https://invalid.example/never-fetch.png)"
        val result = assertIs<MarkdownAuthoringResult.Ready>(compile(session(), source))
        val expected = MarkdownCompiler.compile(source).diagnostics
        assertEquals(expected, result.markdownDiagnostics)
        val diagnostic = result.markdownDiagnostics.single()
        assertEquals("MD201", diagnostic.code)
        assertEquals(MarkdownSeverity.WARNING, diagnostic.severity)
        assertEquals(0, diagnostic.startOffset)
        assertEquals(source.length, diagnostic.endOffset)
        assertFalse((result.markdownDiagnostics as List<*>) is MutableList<*>)
    }

    @Test fun invalidSourceAndSourceBudgetFailuresDoNotInvokeLayout() {
        val engine = engine { _, _ -> error("Rejected Markdown must not reach layout") }
        for ((source, policy, code) in listOf(Triple("\uD800", MarkdownResourcePolicy(), "MD100"),
            Triple("ABC", MarkdownResourcePolicy(maxSourceBytes = 2), "MD120"))) {
            val session = session(engine = engine, policy = policy)
            val failure = assertIs<MarkdownAuthoringResult.CompilationFailed>(compile(session, source))
            assertEquals(listOf(code), failure.diagnostics.map { it.code })
            assertEquals(MarkdownSeverity.ERROR, failure.diagnostics.single().severity)
            assertEquals(source, session.state.source)
        }
    }

    @Test fun unsupportedTextBecomesALayoutFailureWithTheCompiledDocument() {
        val result = assertIs<MarkdownAuthoringResult.LayoutFailed>(compile(session(), "é"))
        assertEquals(ThermalDocument(blocks = listOf(Paragraph(Alignment.LEFT, listOf(Text("é"))))), result.document)
        assertTrue(result.markdownDiagnostics.isEmpty())
        assertEquals(listOf("TXT100"), result.diagnostics.map { it.code })
        assertEquals(0, result.diagnostics.single().sourceBlockIndex)
    }

    @Test fun layoutFailureRetainsEarlierMarkdownWarnings() {
        val source = "<div>é</div>"
        val result = assertIs<MarkdownAuthoringResult.LayoutFailed>(compile(session(), source))
        assertEquals(MarkdownCompiler.compile(source).diagnostics, result.markdownDiagnostics)
        assertEquals(listOf("MD101"), result.markdownDiagnostics.map { it.code })
        assertEquals(listOf("TXT100"), result.diagnostics.map { it.code })
    }

    @Test fun layoutBudgetFailureIsExposedWithoutAPreview() {
        val input = constraints.copy(resources = constraints.resources.copy(maxBlocks = 1))
        val result = assertIs<MarkdownAuthoringResult.LayoutFailed>(compile(session(input), "A\n\nB"))
        assertEquals(2, result.document.blocks.size)
        assertEquals(listOf("LAY120"), result.diagnostics.map { it.code })
    }

    @Test fun previewUsesTheExactSingleLayoutAndSuppliedMeasurements() {
        var layoutCalls = 0
        var measurementCalls = 0
        var inputDocument: ThermalDocument? = null
        var logical: LogicalDocument? = null
        val measurer = TextMeasurer { request ->
            measurementCalls++
            TextMeasureResult.Success(TextMeasurement(request.text.length, 12.0, request.typography.metrics,
                SnapshotList(listOf(MeasuredCluster(0, request.text.length, 12.0))), SnapshotList(emptyList())))
        }
        val input = constraints.copy(canvasWidth = Length(5.0))
        val session = session(input, engine { document, passedConstraints ->
            layoutCalls++
            assertSame(input, passedConstraints)
            inputDocument = document
            FoundationLayoutEngine(measurer).layout(document, passedConstraints).also {
                logical = assertIs<LayoutResult.Success>(it).document
            }
        })
        val result = assertIs<MarkdownAuthoringResult.Ready>(compile(session, "AB"))
        assertEquals(1, layoutCalls)
        assertEquals(1, measurementCalls)
        assertSame(inputDocument, result.document)
        val produced = assertNotNull(logical)
        assertSame(produced, result.logicalDocument)
        assertSame(produced.blocks, result.preview.blocks)
        assertSame(produced.diagnostics, result.preview.diagnostics)
        assertEquals(listOf("LAY101"), result.preview.diagnostics.map { it.code })
        val line = assertIs<LogicalTextBlock>(result.preview.blocks.single()).lines.single()
        assertEquals(12.0, line.advanceMm)
        assertEquals("AB", line.runs.single().text)
    }

    @Test fun editingAfterCompileInvalidatesAllResultsAndKeepsOldSnapshotsUnchanged() {
        var calls = 0
        val session = session(engine = engine { document, input ->
            calls++
            FoundationLayoutEngine(AsciiFixedCellMeasurer()).layout(document, input)
        })
        compile(session, "A")
        val old = session.state
        session.edit("B")
        assertEquals("B", session.state.source)
        assertSame(MarkdownAuthoringResult.NotCompiled, session.state.result)
        assertEquals(1, calls)
        assertEquals("A", old.source)
        assertEquals(listOf("A"), text(assertIs<MarkdownAuthoringResult.Ready>(old.result)))
    }

    @Test fun unchangedSourceRetainsTheValidResult() {
        val session = session()
        compile(session, "A")
        val state = session.state
        session.edit("A")
        assertSame(state, session.state)
    }

    @Test fun recompilationPublishesOneCoherentReplacementAfterLayoutFinishes() {
        lateinit var session: MarkdownAuthoringSession
        var observed: MarkdownAuthoringState? = null
        val engine = engine { document, input ->
            observed = session.state
            assertSame(MarkdownAuthoringResult.NotCompiled, session.state.result)
            FoundationLayoutEngine(AsciiFixedCellMeasurer()).layout(document, input)
        }
        session = session(engine = engine)
        val first = assertIs<MarkdownAuthoringResult.Ready>(compile(session, "![alt](https://invalid.example/inert)"))
        assertEquals(listOf("MD201"), first.markdownDiagnostics.map { it.code })
        session.edit("B")
        val edited = session.state
        session.compile()
        val second = ready(session)
        assertSame(edited, observed)
        assertEquals("B", session.state.source)
        assertEquals(listOf("B"), text(second))
        assertTrue(second.markdownDiagnostics.isEmpty())
        assertTrue(second.preview.diagnostics.isEmpty())
        assertNotSame(first.logicalDocument, second.logicalDocument)
        assertNotSame(first.preview, second.preview)
        assertEquals(listOf("MD201"), first.markdownDiagnostics.map { it.code })
    }

    @Test fun failureReplacesPreviousSuccessAndRecoveryClearsFailureDiagnostics() {
        val session = session()
        compile(session, "A")
        val first = ready(session)
        assertIs<MarkdownAuthoringResult.CompilationFailed>(compile(session, "\uD800"))
        assertIs<MarkdownAuthoringResult.LayoutFailed>(compile(session, "é"))
        val recovered = assertIs<MarkdownAuthoringResult.Ready>(compile(session, "B"))
        assertEquals(listOf("B"), text(recovered))
        assertTrue(recovered.markdownDiagnostics.isEmpty())
        assertTrue(recovered.preview.diagnostics.isEmpty())
        assertEquals(listOf("A"), text(first))
    }

    @Test fun readingThePreviewRetainsTheCompleteAuthoringSessionForReturn() {
        val session = session()
        val result = assertIs<MarkdownAuthoringResult.Ready>(compile(session, "![alt](https://invalid.example/inert)"))
        val authoring = session.state
        val preview = ready(session).preview
        assertSame(result.preview, preview)
        // A route can display the preview then return using the same retained session.
        assertSame(authoring, session.state)
        assertSame(result, session.state.result)
        assertEquals("![alt](https://invalid.example/inert)", session.state.source)
        assertEquals(listOf("MD201"), ready(session).markdownDiagnostics.map { it.code })
    }

    @Test fun equivalentSessionsAndRepeatedCompilesHaveEquivalentOutputs() {
        val first = session()
        val second = session()
        compile(first, "# Heading\n\nAB **CD**")
        compile(second, "# Heading\n\nAB **CD**")
        assertEquals(first.state, second.state)
        val state = first.state
        first.compile()
        assertEquals(state, first.state)
    }

    @Test fun programmerErrorsAreNotConvertedIntoGenericDiagnostics() {
        val problem = IllegalStateException("broken adapter invariant")
        val session = session(engine = engine { _, _ -> throw problem })
        session.edit("A")
        val state = session.state
        assertSame(problem, assertFailsWith<IllegalStateException> { session.compile() })
        assertSame(state, session.state)
    }

    @Test fun anEditReenteredDuringLayoutCannotPublishAnObsoletePreview() {
        lateinit var session: MarkdownAuthoringSession
        session = session(engine = engine { document, input ->
            session.edit("B")
            FoundationLayoutEngine(AsciiFixedCellMeasurer()).layout(document, input)
        })
        session.edit("A")
        session.compile()
        assertEquals("B", session.state.source)
        assertSame(MarkdownAuthoringResult.NotCompiled, session.state.result)
    }
}
