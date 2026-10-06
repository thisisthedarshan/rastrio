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

package com.circuitnext.rastrio.core.layout

import com.circuitnext.rastrio.core.document.*
import com.circuitnext.rastrio.core.text.*
import kotlin.test.*

class LayoutFoundationTest {
    private val body = ResolvedTypography("fixture-v1", TextStyle(), TypographyMetrics(3.0, 1.0, 1.0, 5.0, 3.0), FixedCellGeometry(2.0, 2.0, 4.0))
    private val constraints = LayoutConstraints(Length(20.0), Orientation.PORTRAIT, TypographyContext(body, body, body))
    private val engine: LogicalLayoutEngine = FoundationLayoutEngine(AsciiFixedCellMeasurer())
    private fun p(text: String, alignment: Alignment = Alignment.LEFT) = Paragraph(alignment, listOf(Text(text)))
    private fun layout(document: ThermalDocument, input: LayoutConstraints = constraints) =
        assertIs<LayoutResult.Success>(engine.layout(document, input)).document

    @Test fun emptyDocumentAndConstraintIdentity() {
        val empty = layout(ThermalDocument())
        assertEquals(constraints, empty.constraints)
        assertEquals(20.0, empty.widthMm)
        assertEquals(0.0, empty.heightMm)
        assertEquals(emptyList(), empty.blocks)
        assertEquals(empty, layout(ThermalDocument()))
        val wide = constraints.copy(canvasWidth = Length(40.0), orientation = Orientation.LANDSCAPE)
        assertEquals(40.0, layout(ThermalDocument(), wide).widthMm)
        assertNotEquals(empty, layout(ThermalDocument(), wide))
        assertEquals(constraints, constraints.copy())
    }

    @Test fun exactResolvedParagraphGeometry() {
        val document = ThermalDocument(blocks = listOf(p("ABC"), p("AB", Alignment.CENTER), p("A", Alignment.RIGHT)))
        val logical = layout(document)
        assertEquals(22.5, logical.heightMm)
        assertEquals(listOf(0.0, 7.5, 15.0), logical.blocks.map { it.bounds.yMm })
        val blocks = logical.blocks.map { assertIs<LogicalTextBlock>(it) }
        assertEquals(listOf(0, 1, 2), blocks.map { it.sourceBlockIndex })
        assertEquals(listOf(0.0, 8.0, 18.0), blocks.map { it.lines.single().bounds.xMm })
        assertEquals(listOf(6.0, 4.0, 2.0), blocks.map { it.lines.single().advanceMm })
        assertEquals(listOf(3.0, 10.5, 18.0), blocks.map { it.lines.single().baselineMm })
        assertEquals(20.0, blocks.first().lines.single().availableWidthMm)
        assertEquals(5.0, blocks.first().lines.single().bounds.heightMm)
        assertEquals("ABC", blocks.first().lines.single().runs.single().text)
        assertEquals(body, blocks.first().lines.single().runs.single().typography)
        assertEquals(logical, layout(document))
        assertEquals(18.0, assertIs<LogicalTextBlock>(layout(ThermalDocument(blocks = listOf(p("AB", Alignment.CENTER))),
            constraints.copy(canvasWidth = Length(40.0))).blocks.single()).lines.single().bounds.xMm)
        val doubled = body.copy(metrics = TypographyMetrics(6.0, 2.0, 2.0, 10.0, 6.0), fixedCell = FixedCellGeometry(4.0, 4.0, 8.0))
        assertEquals(15.0, layout(ThermalDocument(blocks = listOf(p("AB"))), constraints.copy(typography = TypographyContext(doubled, doubled, doubled))).heightMm)
    }

    @Test fun emptyParagraphAndTextNodesPreserveContent() {
        val semantic = mutableListOf<DocumentBlock>(Paragraph(Alignment.LEFT, mutableListOf(Text("A "), Text("B"))))
        val logical = layout(ThermalDocument(blocks = semantic))
        semantic.clear()
        assertEquals("A B", assertIs<LogicalTextBlock>(logical.blocks.single()).lines.single().runs.single().text)
        assertFalse((logical.blocks as List<*>) is MutableList<*>)
        assertEquals(7.5, layout(ThermalDocument(blocks = listOf(p("")))).heightMm)
    }

    @Test fun unsupportedContentFailsAtomically() {
        for (block in listOf<DocumentBlock>(Separator, CodeBlock("code"))) {
            val failure = assertIs<LayoutResult.Failure>(engine.layout(ThermalDocument(blocks = listOf(p("ok"), block)), constraints))
            assertEquals("LAY100", failure.diagnostics.single().code)
            assertEquals(1, failure.diagnostics.single().sourceBlockIndex)
        }
        assertEquals("TXT100", assertIs<LayoutResult.Failure>(engine.layout(ThermalDocument(blocks = listOf(p("é"))), constraints)).diagnostics.single().code)
        assertEquals("LAY102", assertIs<LayoutResult.Failure>(engine.layout(ThermalDocument(schemaVersion = 2), constraints)).diagnostics.single().code)
    }

    @Test fun constraintsRejectInvalidGeometry() {
        for (width in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, 1000.01)) {
            assertFailsWith<IllegalArgumentException> { constraints.copy(canvasWidth = Length(width)) }
        }
        assertEquals(1000.0, layout(ThermalDocument(), constraints.copy(canvasWidth = Length(1000.0))).widthMm)
        assertFailsWith<IllegalArgumentException> { LogicalBounds(0.0, 0.0, -1.0, 2.0) }
        assertFailsWith<IllegalArgumentException> { LogicalBounds(Double.MAX_VALUE, 0.0, Double.MAX_VALUE, 1.0) }
    }

    @Test fun resourceLimitsAreCheckedBeforeServiceWork() {
        var calls = 0
        val counting = FoundationLayoutEngine(TextMeasurer { request -> calls++; AsciiFixedCellMeasurer().measure(request) })
        val policy = LayoutResourcePolicy(maxBlocks = 1, maxInlineNodes = 1, maxTextCodeUnits = 3, maxItems = 7)
        val limited = constraints.copy(resources = policy)
        assertIs<LayoutResult.Success>(counting.layout(ThermalDocument(blocks = listOf(p("abc"))), limited))
        calls = 0
        for (document in listOf(ThermalDocument(blocks = listOf(p("A"), p("B"))),
            ThermalDocument(blocks = listOf(Paragraph(Alignment.LEFT, listOf(Text("A"), Text("B"))))),
            ThermalDocument(blocks = listOf(p("abcd"))))) {
            assertEquals("LAY120", assertIs<LayoutResult.Failure>(counting.layout(document, limited)).diagnostics.single().code)
        }
        assertEquals(0, calls)
        assertEquals("LAY120", assertIs<LayoutResult.Failure>(engine.layout(ThermalDocument(blocks = listOf(p("A"))), limited.copy(resources = policy.copy(maxItems = 3)))).diagnostics.single().code)
    }

    @Test fun injectedServiceDeterminesGeometry() {
        val wideService = TextMeasurer { request -> AsciiFixedCellMeasurer().measure(request.copy(
            typography = request.typography.copy(fixedCell = FixedCellGeometry(3.0, 3.0, 4.0)))) }
        val output = assertIs<LayoutResult.Success>(FoundationLayoutEngine(wideService).layout(ThermalDocument(blocks = listOf(p("AB"))), constraints)).document
        assertEquals(6.0, assertIs<LogicalTextBlock>(output.blocks.single()).lines.single().advanceMm)
    }
    @Test fun shapedClusterExtensionDoesNotUseCharacterCellAssumptions() {
        val resolved = body.copy(identity = "controlled-shaped-fixture", fixedCell = null)
        val combiningService = TextMeasurer { request ->
            require(request.text == "e\u0301")
            TextMeasureResult.Success(TextMeasurement(2, 3.0, request.typography.metrics,
                SnapshotList(listOf(MeasuredCluster(0, 2, 3.0))), SnapshotList(emptyList())))
        }
        val output = assertIs<LayoutResult.Success>(FoundationLayoutEngine(combiningService).layout(
            ThermalDocument(blocks = listOf(p("e\u0301"))), constraints.copy(typography = TypographyContext(resolved, resolved, resolved)))).document
        val run = assertIs<LogicalTextBlock>(output.blocks.single()).lines.single().runs.single()
        assertEquals(3.0, run.bounds.widthMm)
        assertEquals(listOf(MeasuredCluster(0, 2, 3.0)), run.measurement.clusters)
    }

    @Test fun coordinateOverflowAndIncompatibleMetricsFailWithDiagnostics() {
        val huge = body.copy(metrics = TypographyMetrics(1.0, 1.0, 0.0, Double.MAX_VALUE, 1.0))
        val input = constraints.copy(typography = TypographyContext(huge, huge, huge))
        assertEquals("LAY122", assertIs<LayoutResult.Failure>(engine.layout(ThermalDocument(blocks = listOf(p("A"))), input)).diagnostics.single().code)
        val incompatible = TextMeasurer { request -> AsciiFixedCellMeasurer().measure(request.copy(
            typography = request.typography.copy(metrics = TypographyMetrics(2.0, 1.0, 0.0, 3.0, 2.0)))) }
        assertEquals("LAY103", assertIs<LayoutResult.Failure>(FoundationLayoutEngine(incompatible).layout(
            ThermalDocument(blocks = listOf(p("A"))), constraints)).diagnostics.single().code)
    }

    @Test fun foundationDoesNotIgnoreMandatoryBreaksFromAnInjectedBackend() {
        val service = TextMeasurer { request -> TextMeasureResult.Success(TextMeasurement(2, 4.0, request.typography.metrics,
            SnapshotList(listOf(MeasuredCluster(0, 1, 2.0), MeasuredCluster(1, 2, 2.0))),
            SnapshotList(listOf(BreakOpportunity(1, BreakKind.MANDATORY))))) }
        val output = assertIs<LayoutResult.Success>(FoundationLayoutEngine(service).layout(
            ThermalDocument(blocks = listOf(p("AB"))), constraints)).document
        assertEquals(listOf("A", "B"), assertIs<LogicalTextBlock>(output.blocks.single()).lines.map { it.runs.single().text })
    }

    @Test fun fractionalFitBoundariesAndAlignmentAreDeterministic() {
        val fractional = body.copy(fixedCell = FixedCellGeometry(0.1, 0.1, 4.0))
        val input = constraints.copy(canvasWidth = Length(0.3), typography = TypographyContext(fractional, fractional, fractional))
        for (alignment in Alignment.entries) {
            val document = ThermalDocument(blocks = listOf(p("ABC", alignment)))
            val output = layout(document, input)
            val line = assertIs<LogicalTextBlock>(output.blocks.single()).lines.single()
            assertEquals(0.3, line.advanceMm)
            assertEquals(0.0, line.bounds.xMm)
            assertEquals(output, layout(document, input))
            // Within half of the 0.000001 mm grid step, both widths canonicalize to 0.3.
            assertEquals(0.0, assertIs<LogicalTextBlock>(layout(document,
                input.copy(canvasWidth = Length(0.2999996))).blocks.single()).lines.single().bounds.xMm)
            // One whole grid step short is genuinely too narrow; rounding cannot hide it.
            val wrapped = assertIs<LogicalTextBlock>(layout(document,
                input.copy(canvasWidth = Length(0.299999))).blocks.single()).lines
            assertEquals(listOf("AB", "C"), wrapped.map { it.runs.single().text })
            assertEquals(listOf(0.2, 0.1), wrapped.map { it.advanceMm })
            assertEquals(when (alignment) {
                Alignment.LEFT -> listOf(0.0, 0.0)
                Alignment.CENTER -> listOf(0.05, 0.1)
                Alignment.RIGHT -> listOf(0.099999, 0.199999)
            }, wrapped.map { it.bounds.xMm })
        }
        val right = layout(ThermalDocument(blocks = listOf(p("AB", Alignment.RIGHT))), input)
        assertEquals(0.1, assertIs<LogicalTextBlock>(right.blocks.single()).lines.single().bounds.xMm)
    }

    @Test fun oversizedLineIsValidGeometryButInvalidValuesRemainRejected() {
        val bounds = LogicalBounds(0.0, 0.0, 12.0, 5.0)
        val line = LogicalTextLine(bounds, 10.0, 12.0, 3.0, Alignment.LEFT, SnapshotList(emptyList()))
        val diagnostic = LayoutDiagnostic("LAY101", "Cluster exceeds available width", 0)
        val document = LogicalDocument(constraints, 5.0,
            SnapshotList(listOf(LogicalTextBlock(0, bounds, SnapshotList(listOf(line))))), SnapshotList(listOf(diagnostic)))
        assertEquals(12.0, assertIs<LogicalTextBlock>(document.blocks.single()).lines.single().advanceMm)
        assertEquals(listOf(diagnostic), document.diagnostics)
        for (invalid in listOf(-0.1, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { line.copy(advanceMm = invalid) }
            assertFailsWith<IllegalArgumentException> { line.copy(availableWidthMm = invalid) }
            assertFailsWith<IllegalArgumentException> { line.copy(baselineMm = invalid) }
        }
    }

    @Test fun precisionCannotRelaxResourceLimitsOrPermitZeroProgress() {
        assertFailsWith<IllegalArgumentException> { constraints.copy(canvasWidth = Length(1000.0000001)) }
        assertFailsWith<IllegalArgumentException> { constraints.copy(canvasWidth = Length(0.0000004)) }
        val tiny = body.copy(metrics = TypographyMetrics(0.0, 0.0, 0.0, 0.0000004, 0.0))
        assertEquals("LAY122", assertIs<LayoutResult.Failure>(engine.layout(ThermalDocument(blocks = listOf(p("A"))),
            constraints.copy(typography = TypographyContext(tiny, tiny, tiny)))).diagnostics.single().code)
        val fractional = body.copy(metrics = TypographyMetrics(0.1, 0.0, 0.0, 0.1, 0.1))
        val output = layout(ThermalDocument(blocks = listOf(p("A"), p("B"), p("C"))),
            constraints.copy(typography = TypographyContext(fractional, fractional, fractional)))
        assertEquals(0.45, output.heightMm)
        assertEquals(listOf(0.0, 0.15, 0.3), output.blocks.map { it.bounds.yMm })
    }

}
