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

class TypographyResolverFlowTest {
    private val body = ResolvedTypography("body", TextStyle(),
        TypographyMetrics(3.0, 1.0, 1.0, 5.0, 3.0), FixedCellGeometry(2.0, 2.0, 4.0))
    private val narrow = body.copy(identity = "narrow", fixedCell = FixedCellGeometry(1.0, 1.0, 4.0))
    private val constraints = LayoutConstraints(Length(10.0), Orientation.PORTRAIT,
        TypographyContext(body, body, body))
    private val identity = TextFlowTypographyResolver { _, _, text, typography ->
        SnapshotList(listOf(ResolvedTextSpan(text, typography)))
    }
    private fun result(blocks: List<DocumentBlock>, resolver: TextFlowTypographyResolver = identity,
        measurer: TextMeasurer = AsciiFixedCellMeasurer()) =
        FoundationLayoutEngine(measurer, resolver).layout(ThermalDocument(blocks = blocks), constraints)
    private fun paragraph(text: String) = Paragraph(Alignment.LEFT, listOf(Text(text)))
    private fun runs(document: LogicalDocument) = document.blocks.flatMap {
        assertIs<LogicalTextBlock>(it).lines.flatMap { line -> line.runs }
    }

    @Test fun mixedTypographyControlsWrappingAndRetainsPartitionSourceOffsets() {
        val resolver = TextFlowTypographyResolver { flow, offset, text, typography ->
            assertEquals(1, flow)
            assertEquals(0, offset)
            assertEquals("ABCDEFGHIJ", text)
            SnapshotList(listOf(ResolvedTextSpan("AB", typography), ResolvedTextSpan("CDEFGHIJ", narrow)))
        }
        val document = assertIs<LayoutResult.Success>(result(listOf(paragraph("ABCDEFGHIJ")), resolver)).document
        val block = assertIs<LogicalTextBlock>(document.blocks.single())
        assertEquals(listOf("ABCDEFGH", "IJ"), block.lines.map { it.runs.joinToString("") { run -> run.text } })
        assertEquals(listOf("AB", "CDEFGH", "IJ"), runs(document).map { it.text })
        assertEquals(listOf(0, 2, 8), runs(document).map { it.sourceStartOffset })
        assertEquals(listOf(1, 1, 1), runs(document).map { it.sourceFlowId })
        assertEquals(listOf(4.0, 6.0, 2.0), runs(document).map { it.bounds.widthMm })
    }

    @Test fun tabsAndNormalizedBreaksRetainFlowLocalOffsetsAcrossStyles() {
        val paragraph = Paragraph(Alignment.LEFT, listOf(Text("A\t"), Strong(listOf(Text("B"))), LineBreak, Text("C")))
        val code = CodeBlock("D\tE\r\nF\rG\nH")
        val document = assertIs<LayoutResult.Success>(result(listOf(paragraph, code))).document
        val allRuns = runs(document).filter { it.text.isNotEmpty() }
        assertEquals(listOf("A", "\t", "B", "C", "D", "\t", "E", "F", "G", "H"), allRuns.map { it.text })
        assertEquals(listOf(0, 1, 2, 4, 0, 1, 2, 4, 6, 8), allRuns.map { it.sourceStartOffset })
        assertEquals(listOf(1, 1, 1, 1, 2, 2, 2, 2, 2, 2), allRuns.map { it.sourceFlowId })
        assertEquals(document, assertIs<LayoutResult.Success>(result(listOf(paragraph, code))).document)
    }

    @Test fun tableCellsAndFollowingParagraphHaveDistinctStableFlows() {
        fun cell(text: String) = TableCell(listOf(Text(text)))
        val table = Table(listOf(TableColumn(Alignment.LEFT), TableColumn(Alignment.LEFT)),
            listOf(cell("A"), cell("B")), listOf(listOf(cell("C"), cell("D"))))
        val document = assertIs<LayoutResult.Success>(result(listOf(table, paragraph("E")))).document
        val tableRuns = assertIs<LogicalTableBlock>(document.blocks.first()).rows.flatMap { row ->
            row.cells.flatMap { it.text.lines.flatMap { line -> line.runs } }
        }
        val following = assertIs<LogicalTextBlock>(document.blocks.last()).lines.single().runs.single()
        assertEquals(listOf(1, 2, 3, 4, 5), (tableRuns + following).map { it.sourceFlowId })
        assertEquals(listOf(0, 0, 0, 0, 0), (tableRuns + following).map { it.sourceStartOffset })
        assertEquals(document, assertIs<LayoutResult.Success>(result(listOf(table, paragraph("E")))).document)
    }

    @Test fun invalidPartitionsFailBeforeMeasurement() {
        var measurements = 0
        val forbidden = TextMeasurer { measurements++; error("Invalid partitions must fail before measurement") }
        // Construct code units at runtime: JS compilation may replace lone literal surrogates.
        val high = CharArray(1) { 0xd83d.toChar() }.concatToString()
        val low = CharArray(1) { 0xde00.toChar() }.concatToString()
        assertEquals(0xd83d, high.single().code)
        assertEquals(0xde00, low.single().code)
        val cases = listOf(
            "AB" to listOf("AC"), "AB" to listOf("A"), "AB" to listOf("", "AB"),
            "AB" to emptyList(), "AB" to listOf("A", "B", "C"), "A\u0301B" to listOf("A", "\u0301B"),
            (high + low + "B") to listOf(high, low + "B"), high to listOf(high),
        )
        for ((text, partition) in cases) {
            val resolver = TextFlowTypographyResolver { _, _, _, typography ->
                SnapshotList(partition.map { ResolvedTextSpan(it, typography) })
            }
            val failure = assertIs<LayoutResult.Failure>(result(listOf(paragraph(text)), resolver, forbidden))
            assertEquals("LAY103", failure.diagnostics.single().code)
        }
        assertEquals(0, measurements)
    }

    @Test fun adjacentEqualTypographyPartitionsKeepShapingContext() {
        val measuredTexts = mutableListOf<String>()
        val measurer = TextMeasurer { request ->
            measuredTexts.add(request.text)
            TextMeasureResult.Success(TextMeasurement(request.text.length, 3.0, request.typography.metrics,
                SnapshotList(listOf(MeasuredCluster(0, request.text.length, 3.0))), SnapshotList(emptyList())))
        }
        val resolver = TextFlowTypographyResolver { _, _, _, typography ->
            SnapshotList(listOf(ResolvedTextSpan("f", typography), ResolvedTextSpan("i", typography)))
        }
        val document = assertIs<LayoutResult.Success>(result(listOf(paragraph("fi")), resolver, measurer)).document
        assertEquals(listOf("fi"), measuredTexts)
        assertEquals("fi", runs(document).single().text)
        assertEquals(3.0, runs(document).single().bounds.widthMm)
    }

    @Test fun resolverOutputAndEffectiveMeasurementRemainResourceAndMetricBounded() {
        val limited = constraints.copy(resources = constraints.resources.copy(maxItems = 3))
        val resolver = TextFlowTypographyResolver { _, _, text, typography ->
            SnapshotList(text.map { ResolvedTextSpan(it.toString(), typography) })
        }
        var measurements = 0
        val service = TextMeasurer { measurements++; error("Resource reservation must fail first") }
        val exhausted = FoundationLayoutEngine(service, resolver).layout(
            ThermalDocument(blocks = listOf(paragraph("AB"))), limited)
        assertEquals("LAY120", assertIs<LayoutResult.Failure>(exhausted).diagnostics.single().code)
        assertEquals(0, measurements)
        val wrongMetric = TextMeasurer { request ->
            TextMeasureResult.Success(TextMeasurement(request.text.length, 2.0,
                body.metrics.copy(lineHeightMm = 6.0),
                SnapshotList(listOf(MeasuredCluster(0, request.text.length, 2.0))), SnapshotList(emptyList())))
        }
        val badMetric = result(listOf(paragraph("A")), identity, wrongMetric)
        assertEquals("LAY103", assertIs<LayoutResult.Failure>(badMetric).diagnostics.single().code)
    }

    @Test fun semanticStyleCannotSplitGraphemeInResolvedFlow() {
        val block = Paragraph(Alignment.LEFT, listOf(Text("A"), Strong(listOf(Text("\u0301")))))
        val failure = assertIs<LayoutResult.Failure>(result(listOf(block)))
        assertEquals("LAY103", failure.diagnostics.single().code)
    }
}
