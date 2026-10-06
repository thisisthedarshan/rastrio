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

class ParagraphFlowTest {
    private val body = ResolvedTypography("body", TextStyle(), TypographyMetrics(3.0, 1.0, 1.0, 5.0, 3.0), FixedCellGeometry(2.0, 2.0, 4.0))
    private val code = body.copy(identity = "code", style = TextStyle(role = TextRole.CODE),
        metrics = TypographyMetrics(4.0, 2.0, 0.0, 6.0, 4.0), fixedCell = FixedCellGeometry(1.0, 1.0, 6.0))
    private val headings = SnapshotList((1..6).map { level -> body.copy(identity = "h$level", style = TextStyle(weight = TextWeight.BOLD, role = TextRole.HEADING),
        metrics = TypographyMetrics(4.0, 1.0, 1.0, 6.0, 4.0)) })
    private val input = LayoutConstraints(Length(10.0), Orientation.PORTRAIT, TypographyContext(body, code, body, headings))
    private val engine = FoundationLayoutEngine(AsciiFixedCellMeasurer())
    private fun p(text: String, alignment: Alignment = Alignment.LEFT) = Paragraph(alignment, listOf(Text(text)))
    private fun layout(blocks: List<DocumentBlock>, constraints: LayoutConstraints = input, service: TextMeasurer = AsciiFixedCellMeasurer()) =
        assertIs<LayoutResult.Success>(FoundationLayoutEngine(service).layout(ThermalDocument(blocks = blocks), constraints)).document
    private fun lines(output: LogicalDocument) = output.blocks.flatMap { assertIs<LogicalTextBlock>(it).lines }
    private fun texts(output: LogicalDocument) = lines(output).map { line -> line.runs.joinToString("") { it.text } }

    @Test fun legalBreaksAndEmergencyWrapHaveExactGeometry() {
        val cases = listOf("AB" to listOf("AB"), "ABCDE" to listOf("ABCDE"), "AB CD" to listOf("AB CD"),
            "AB CDE" to listOf("AB ", "CDE"), "AB CD EF" to listOf("AB ", "CD EF"),
            "ABCDEFGHIJK" to listOf("ABCDE", "FGHIJ", "K"))
        for ((text, expected) in cases) {
            val output = layout(listOf(p(text)))
            assertEquals(expected, texts(output))
            assertEquals(expected.map { it.length * 2.0 }, lines(output).map { it.advanceMm })
            assertEquals(expected.indices.map { it * 5.0 }, lines(output).map { it.bounds.yMm })
            assertEquals(expected.indices.map { it * 5.0 + 3.0 }, lines(output).map { it.baselineMm })
            assertEquals(expected.size * 5.0 + 2.5, output.heightMm)
            assertEquals(text, texts(output).joinToString(""))
            assertEquals(output, layout(listOf(p(text))))
        }
        assertEquals(listOf("AB CD EF"), texts(layout(listOf(p("AB CD EF")), input.copy(canvasWidth = Length(16.0)))))
    }

    @Test fun alignmentUsesEachResolvedLineAdvance() {
        for ((alignment, expected) in listOf(Alignment.LEFT to listOf(0.0, 0.0), Alignment.CENTER to listOf(0.0, 3.0), Alignment.RIGHT to listOf(0.0, 6.0))) {
            val output = layout(listOf(p("ABCDEFG", alignment)))
            assertEquals(listOf("ABCDE", "FG"), texts(output))
            assertEquals(expected, lines(output).map { it.bounds.xMm })
            assertEquals(expected, lines(output).map { it.runs.first().bounds.xMm })
        }
    }

    @Test fun explicitBreaksPreserveEmptyLinesAndRemainDistinctFromSoftWraps() {
        val block = Paragraph(Alignment.LEFT, listOf(Text("A"), LineBreak, LineBreak, Text("ABCDEFG"), LineBreak))
        val output = layout(listOf(block))
        assertEquals(listOf("A", "", "ABCDE", "FG", ""), texts(output))
        assertEquals(listOf(true, true, false, true, false), lines(output).map { it.endsWithExplicitBreak })
        assertEquals(listOf(0.0, 5.0, 10.0, 15.0, 20.0), lines(output).map { it.bounds.yMm })
        assertEquals(27.5, output.heightMm)
        assertEquals(listOf("A", "B"), texts(layout(listOf(Paragraph(Alignment.LEFT, listOf(Text("A"), LineBreak, Text("B")))), input.copy(canvasWidth = Length(100.0)))))
    }

    @Test fun nestedStylesCodeAndLinksSurviveWrapping() {
        val block = Paragraph(Alignment.LEFT, listOf(Text("A"), Strong(listOf(Emphasis(listOf(Strike(listOf(Text("BCDEFG"))))))),
            InlineCode("ij"), Link("file:///never-read", listOf(Text("K")))))
        val output = layout(listOf(block))
        assertEquals(listOf("ABCDE", "FGijK"), texts(output))
        val runs = lines(output).flatMap { it.runs }
        assertEquals(listOf("A", "BCDE", "FG", "ij", "K"), runs.map { it.text })
        for (run in runs.filter { it.text == "BCDE" || it.text == "FG" }) {
            assertEquals(TextStyle(weight = TextWeight.BOLD, emphasis = true, strike = true), run.typography.style)
        }
        assertEquals(code, runs[3].typography)
        assertEquals(body, runs.last().typography)
        assertEquals(listOf(0.0, 2.0, 0.0, 4.0, 6.0), runs.map { it.bounds.xMm })
        assertEquals(listOf(3.0, 9.0), lines(output).map { it.baselineMm })
        assertEquals(listOf(5.0, 6.0), lines(output).map { it.bounds.heightMm })
        assertEquals(13.5, output.heightMm)
    }

    @Test fun headingsSelectEachLevelAndFlowBetweenParagraphs() {
        for (level in 1..6) {
            val output = layout(listOf(p("ABCDEF"), Heading(level, Alignment.CENTER, listOf(Text("XYZ"))), p("A")))
            assertEquals(listOf(0.0, 12.5, 21.0), output.blocks.map { it.bounds.yMm })
            assertEquals(listOf(10.0, 6.0, 5.0), output.blocks.map { it.bounds.heightMm })
            val heading = assertIs<LogicalTextBlock>(output.blocks[1]).lines.single()
            assertEquals(headings[level - 1], heading.runs.single().typography)
            assertEquals(2.0, heading.bounds.xMm)
            assertEquals(16.5, heading.baselineMm)
            assertEquals(28.5, output.heightMm)
        }
    }

    @Test fun oversizedIndivisibleClusterIsRetainedWithDiagnostic() {
        val service = TextMeasurer { request ->
            TextMeasureResult.Success(TextMeasurement(request.text.length, 12.0, request.typography.metrics,
                SnapshotList(listOf(MeasuredCluster(0, request.text.length, 12.0))), SnapshotList(emptyList())))
        }
        for (alignment in Alignment.entries) {
            val output = layout(listOf(p("AB", alignment)), service = service)
            val line = lines(output).single()
            assertEquals("AB", line.runs.single().text)
            assertEquals(12.0, line.advanceMm)
            assertEquals(0.0, line.bounds.xMm)
            assertEquals(listOf(MeasuredCluster(0, 2, 12.0)), line.runs.single().measurement.clusters)
            assertEquals("LAY101", output.diagnostics.single().code)
            assertEquals(0, output.diagnostics.single().sourceBlockIndex)
        }
    }

    @Test fun suppliedMandatoryBreakIsHonoredWithoutInventingSemanticBreak() {
        val service = TextMeasurer { request -> TextMeasureResult.Success(TextMeasurement(2, 4.0, request.typography.metrics,
            SnapshotList(listOf(MeasuredCluster(0, 1, 2.0), MeasuredCluster(1, 2, 2.0))), SnapshotList(listOf(BreakOpportunity(1, BreakKind.MANDATORY))))) }
        val output = layout(listOf(p("AB")), service = service)
        assertEquals(listOf("A", "B"), texts(output))
        assertEquals(listOf(false, false), lines(output).map { it.endsWithExplicitBreak })
    }

    @Test fun deepNestingAndNestedCountsFailBeforeMeasurement() {
        var node: InlineContent = Text("A")
        repeat(10_000) { node = Strong(listOf(node)) }
        var calls = 0
        val service = TextMeasurer { request -> calls++; AsciiFixedCellMeasurer().measure(request) }
        val result = FoundationLayoutEngine(service).layout(ThermalDocument(blocks = listOf(Paragraph(Alignment.LEFT, listOf(node)))), input)
        assertEquals("LAY120", assertIs<LayoutResult.Failure>(result).diagnostics.single().code)
        assertEquals(0, calls)
        val countInput = input.copy(resources = input.resources.copy(maxInlineNodes = 2))
        assertEquals("LAY120", assertIs<LayoutResult.Failure>(FoundationLayoutEngine(service).layout(ThermalDocument(blocks = listOf(
            Paragraph(Alignment.LEFT, listOf(Strong(listOf(Text("A"), Text("B"))))))), countInput)).diagnostics.single().code)
        assertEquals(0, calls)
    }

    @Test fun generatedItemsAreBoundedAndUnsupportedBlocksRemainAtomic() {
        val constraints = input.copy(canvasWidth = Length(2.0), resources = input.resources.copy(maxItems = 10))
        val result = engine.layout(ThermalDocument(blocks = listOf(p("ABCDE"))), constraints)
        assertEquals("LAY120", assertIs<LayoutResult.Failure>(result).diagnostics.single().code)
        assertEquals("LAY100", assertIs<LayoutResult.Failure>(engine.layout(ThermalDocument(blocks = listOf(p("A"), Separator)), input)).diagnostics.single().code)
        assertEquals("TXT100", assertIs<LayoutResult.Failure>(engine.layout(ThermalDocument(blocks = listOf(p("é"))), input)).diagnostics.single().code)
    }

    @Test fun whitespaceAndStyleBoundariesUseSuppliedBreaksWithoutNormalization() {
        val output = layout(listOf(Paragraph(Alignment.LEFT, listOf(Text(" A  "), Strong(listOf(Text("BC"))), Text("  ")))))
        assertEquals(listOf(" A  ", "BC  "), texts(output))
        assertEquals(listOf(8.0, 8.0), lines(output).map { it.advanceMm })
        assertEquals(" A  BC  ", texts(output).joinToString(""))
        val sameStyle = layout(listOf(Paragraph(Alignment.LEFT, listOf(Text("AB"), Link("https://unused.invalid", listOf(Text(" CDE")))))))
        assertEquals(listOf("AB ", "CDE"), texts(sameStyle))
        assertEquals(listOf(1, 1), lines(sameStyle).map { it.runs.size })
    }

    @Test fun individualStylesAndCodeInheritanceDoNotLeakToFollowingText() {
        val output = layout(listOf(Paragraph(Alignment.LEFT, listOf(Strong(listOf(Text("A"))), Emphasis(listOf(Text("B"))),
            Strike(listOf(Text("C"))), Strong(listOf(Emphasis(listOf(InlineCode("D"))))), Text("E")))))
        val runs = lines(output).single().runs
        assertEquals(listOf(TextStyle(weight = TextWeight.BOLD), TextStyle(emphasis = true), TextStyle(strike = true),
            TextStyle(weight = TextWeight.BOLD, emphasis = true, role = TextRole.CODE), TextStyle()), runs.map { it.typography.style })
        assertEquals(listOf("body", "body", "body", "code", "body"), runs.map { it.typography.identity })
        assertEquals(4.0, lines(output).single().baselineMm)
        assertEquals(listOf(1.0, 1.0, 1.0, 0.0, 1.0), runs.map { it.bounds.yMm })
    }

    @Test fun longParagraphIsMeasuredOnceAndSlicedAtLocalClusterOffsets() {
        val text = "AB ".repeat(2000)
        var calls = 0
        val service = TextMeasurer { request -> calls++; AsciiFixedCellMeasurer().measure(request) }
        val output = layout(listOf(p(text)), service = service)
        assertEquals(1, calls)
        assertEquals(2000, lines(output).size)
        assertEquals(10002.5, output.heightMm)
        assertEquals(text, texts(output).joinToString(""))
        assertEquals(listOf(MeasuredCluster(0, 1, 2.0), MeasuredCluster(1, 2, 2.0), MeasuredCluster(2, 3, 2.0)), lines(output).last().runs.single().measurement.clusters)
        assertEquals(listOf(BreakOpportunity(3, BreakKind.ALLOWED)), lines(output).last().runs.single().measurement.breakOpportunities)
        val narrow = layout(listOf(p(" ".repeat(100))), input.copy(canvasWidth = Length(0.000001)))
        assertEquals(100, lines(narrow).size)
        assertEquals(1, narrow.diagnostics.size)
        assertEquals(502.5, narrow.heightMm)
        assertEquals("LAY101", narrow.diagnostics.last().code)
        assertEquals("TXT120", assertIs<LayoutResult.Failure>(engine.layout(ThermalDocument(blocks = listOf(p("A".repeat(65537)))), input)).diagnostics.single().code)
    }

    @Test fun veryNarrowWidthAndExactItemBoundaryAreDeterministic() {
        val narrow = input.copy(canvasWidth = Length(2.0), resources = input.resources.copy(maxItems = 17))
        val output = layout(listOf(p("ABCDE")), narrow)
        assertEquals(listOf("A", "B", "C", "D", "E"), texts(output))
        assertEquals(27.5, output.heightMm)
        assertEquals("LAY120", assertIs<LayoutResult.Failure>(engine.layout(ThermalDocument(blocks = listOf(p("ABCDE"))),
            narrow.copy(resources = narrow.resources.copy(maxItems = 16)))).diagnostics.single().code)
        val zeroAdvance = body.copy(fixedCell = FixedCellGeometry(0.0000004, 1.0, 4.0))
        val zeroInput = input.copy(canvasWidth = Length(0.000001), typography = TypographyContext(zeroAdvance, zeroAdvance, zeroAdvance))
        assertEquals(0.0, lines(layout(listOf(p("ABC")), zeroInput)).single().advanceMm)
    }

    @Test fun inlineDepthBoundaryAndTextLimitsArePreflighted() {
        var node: InlineContent = Text("A")
        repeat(63) { node = Strong(listOf(node)) }
        val output = layout(listOf(Paragraph(Alignment.LEFT, listOf(node))))
        assertEquals(TextWeight.BOLD, lines(output).single().runs.single().typography.style.weight)
        assertEquals("LAY120", assertIs<LayoutResult.Failure>(engine.layout(ThermalDocument(blocks = listOf(
            Paragraph(Alignment.LEFT, listOf(Strong(listOf(node)))))), input)).diagnostics.single().code)
        val limited = input.copy(resources = input.resources.copy(maxTextCodeUnits = 2))
        assertEquals("LAY120", assertIs<LayoutResult.Failure>(engine.layout(ThermalDocument(blocks = listOf(
            Paragraph(Alignment.LEFT, listOf(InlineCode("ABC"))))), limited)).diagnostics.single().code)
        for (level in listOf(0, 7)) assertEquals("LAY104", assertIs<LayoutResult.Failure>(engine.layout(
            ThermalDocument(blocks = listOf(Heading(level, Alignment.LEFT, listOf(Text("A"))))), input)).diagnostics.single().code)
        for (text in listOf("a\tb", "a\nb", "e\u0301", "\u001b")) {
            val diagnostic = assertIs<LayoutResult.Failure>(engine.layout(ThermalDocument(blocks = listOf(p(text))), input)).diagnostics.single()
            assertEquals("TXT100", diagnostic.code)
            assertFalse(diagnostic.message.contains(text))
        }
    }

    @Test fun hugeClusterAfterFittingSpanWrapsBeforeCoordinateAddition() {
        val service = TextMeasurer { request ->
            val advance = if (request.text == "B") 1000000000.0 else 2.0
            TextMeasureResult.Success(TextMeasurement(1, advance, request.typography.metrics,
                SnapshotList(listOf(MeasuredCluster(0, 1, advance))), SnapshotList(emptyList())))
        }
        val output = layout(listOf(Paragraph(Alignment.LEFT, listOf(Text("A"), Strong(listOf(Text("B")))))), service = service)
        assertEquals(listOf("A", "B"), texts(output))
        assertEquals(listOf(2.0, 1000000000.0), lines(output).map { it.advanceMm })
        assertEquals("LAY101", output.diagnostics.single().code)
    }

    @Test fun verticalCoordinateOverflowIsAtomicAcrossBlocks() {
        val tall = body.copy(metrics = TypographyMetrics(1.0, 1.0, 0.0, 600000000.0, 1.0))
        val tallInput = input.copy(typography = TypographyContext(tall, tall, tall))
        assertEquals(900000000.0, layout(listOf(p("A")), tallInput).heightMm)
        val result = engine.layout(ThermalDocument(blocks = listOf(p("A"), p("B"))), tallInput)
        assertEquals("LAY122", assertIs<LayoutResult.Failure>(result).diagnostics.single().code)
        assertEquals(1, result.diagnostics.single().sourceBlockIndex)
    }

    @Test fun wrappedHeadingUsesItsOwnMetricsAndEachLineAlignment() {
        val heading = headings[0].copy(metrics = TypographyMetrics(6.0, 2.0, 0.0, 8.0, 6.0), fixedCell = FixedCellGeometry(3.0, 3.0, 8.0))
        val context = input.typography.copy(headings = SnapshotList(listOf(heading) + headings.drop(1)))
        val constraints = input.copy(typography = context)
        val output = layout(listOf(Heading(1, Alignment.RIGHT, listOf(Text("ABCD"))), p("Z")), constraints)
        assertEquals(listOf("ABC", "D", "Z"), texts(output))
        assertEquals(listOf(1.0, 7.0, 0.0), lines(output).map { it.bounds.xMm })
        assertEquals(listOf(0.0, 8.0, 18.5), lines(output).map { it.bounds.yMm })
        assertEquals(listOf(6.0, 14.0, 21.5), lines(output).map { it.baselineMm })
        assertEquals(listOf(16.0, 5.0), output.blocks.map { it.bounds.heightMm })
        assertEquals(26.0, output.heightMm)
    }

    @Test fun mixedLineExtentsContainEveryRunAroundOneBaseline() {
        val highBaseline = body.copy(metrics = TypographyMetrics(8.0, 1.0, 0.0, 10.0, 8.0))
        val lowBaseline = code.copy(metrics = TypographyMetrics(1.0, 8.0, 0.0, 10.0, 1.0))
        val constraints = input.copy(typography = input.typography.copy(body = highBaseline, code = lowBaseline))
        val output = layout(listOf(Paragraph(Alignment.LEFT, listOf(Text("A"), InlineCode("B")))) , constraints)
        val line = lines(output).single()
        assertEquals(17.0, line.bounds.heightMm)
        assertEquals(8.0, line.baselineMm)
        assertEquals(listOf(0.0, 7.0), line.runs.map { it.bounds.yMm })
        assertEquals(listOf(10.0, 10.0), line.runs.map { it.bounds.heightMm })
        assertEquals(22.0, output.heightMm)
    }

    @Test fun finalizedOutputDoesNotRetainMutableSemanticChildren() {
        val children = mutableListOf<InlineContent>(Text("ABCDE"))
        val output = layout(listOf(Paragraph(Alignment.LEFT, listOf(Strong(children)))))
        children.clear()
        assertEquals(listOf("ABCDE"), texts(output))
        assertFalse((lines(output).single().runs as List<*>) is MutableList<*>)
        assertFalse((lines(output).single().runs.single().measurement.clusters as List<*>) is MutableList<*>)
    }
}
