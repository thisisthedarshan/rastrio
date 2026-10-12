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

class ContextualWrapTest {
    private val body = ResolvedTypography("context-fixture", TextStyle(),
        TypographyMetrics(3.0, 1.0, 1.0, 5.0, 3.0), FixedCellGeometry(1.0, 1.0, 4.0))
    private val context = TypographyContext(body, body, body)
    private val growing = TextMeasurer { request -> measurement(request, if (request.text.length == 4) 1.0 else 1.5) }
    private fun measurement(request: TextMeasureRequest, unit: Double): TextMeasureResult {
        val advance = LogicalGeometry.normalize(unit)
        return TextMeasureResult.Success(TextMeasurement(request.text.length,
            LogicalGeometry.millimetres(LogicalGeometry.ticks(advance) * request.text.length), request.typography.metrics,
            SnapshotList(request.text.indices.map { MeasuredCluster(it, it + 1, advance) }), SnapshotList(emptyList())))
    }
    private fun layout(block: DocumentBlock, width: Double = 3.0, measurer: TextMeasurer = growing): LogicalDocument =
        assertIs<LayoutResult.Success>(FoundationLayoutEngine(measurer).layout(ThermalDocument(blocks = listOf(block)),
            LayoutConstraints(Length(width), Orientation.PORTRAIT, context))).document
    private fun paragraph(text: String, alignment: Alignment = Alignment.LEFT) = Paragraph(alignment, listOf(Text(text)))
    private fun lines(document: LogicalDocument) = assertIs<LogicalTextBlock>(document.blocks.single()).lines
    private fun texts(document: LogicalDocument) = lines(document).map { it.runs.joinToString("") { run -> run.text } }
    private fun assertFinalMeasurements(lines: List<LogicalTextLine>, measurer: TextMeasurer = growing) {
        for (line in lines) for (run in line.runs) if (run.text != "\t") {
            val fresh = assertIs<TextMeasureResult.Success>(measurer.measure(TextMeasureRequest(run.text, run.typography))).measurement
            assertEquals(fresh, run.measurement)
            assertEquals(fresh.advanceMm, run.bounds.widthMm)
        }
    }

    @Test fun explicitSliceIndependenceRetainsTheAdditiveFastPath() {
        val requests = mutableListOf<String>()
        val independent = TextMeasurer { request ->
            requests.add(request.text)
            val result = assertIs<TextMeasureResult.Success>(measurement(request, 1.5))
            TextMeasureResult.Success(result.measurement.copy(canSliceWithoutReshaping = true))
        }
        val document = layout(paragraph("ABCD"), measurer = independent)
        assertEquals(listOf("AB", "CD"), texts(document))
        assertEquals(listOf("ABCD"), requests)
        assertTrue(lines(document).all { it.runs.single().measurement.canSliceWithoutReshaping })
    }

    @Test fun contextualGrowthContractsAtFreshBoundariesAndAlignsExactWidths() {
        val document = layout(paragraph("ABCD", Alignment.RIGHT))
        assertEquals(listOf("AB", "CD"), texts(document))
        assertEquals(listOf(3.0, 3.0), lines(document).map { it.advanceMm })
        assertEquals(listOf(0.0, 0.0), lines(document).map { it.bounds.xMm })
        assertEquals(listOf(0, 2), lines(document).map { it.runs.single().sourceStartOffset })
        assertFinalMeasurements(lines(document))
    }

    @Test fun contextualShrinkRetainsConservativeBoundaryWithExactRightAlignment() {
        val shrinking = TextMeasurer { request -> measurement(request, if (request.text.length == 4) 2.0 else 1.0) }
        val document = layout(paragraph("ABCD", Alignment.RIGHT), measurer = shrinking)
        assertEquals(listOf("A", "B", "C", "D"), texts(document))
        assertEquals(listOf(2.0, 2.0, 2.0, 2.0), lines(document).map { it.bounds.xMm })
        assertFinalMeasurements(lines(document), shrinking)
    }

    @Test fun finalWidthsPositionAdjacentStylesAndRetainSourceRanges() {
        val block = Paragraph(Alignment.RIGHT, listOf(Strong(listOf(Text("X"))), Text("ABCD"), Strong(listOf(Text("E")))))
        val document = layout(block, width = 4.0)
        assertEquals(listOf("XA", "BC", "DE"), texts(document))
        val runs = lines(document).flatMap { it.runs }
        assertEquals(listOf(0, 1, 2, 4, 5), runs.map { it.sourceStartOffset })
        assertEquals(listOf(1.0, 2.5, 1.0, 1.0, 2.5), runs.map { it.bounds.xMm })
        assertFinalMeasurements(lines(document))
    }

    @Test fun codeTabsAndTableCellsUseCorrectedFinalRunWidths() {
        val tabContext = TextMeasurer { request -> measurement(request, if (request.text.length == 6) 1.0 else 1.5) }
        val document = layout(CodeBlock("ABCDEF\tX"), width = 5.0, measurer = tabContext)
        // The old DEF width puts the tab at stop 4; final DEF width requires stop 8 and another line.
        assertEquals(listOf("ABC", "DEF", "\t", "X"), texts(document))
        val tab = lines(document)[2].runs.single()
        assertEquals(0.0, tab.bounds.xMm)
        assertEquals(4.0, tab.bounds.widthMm)
        assertEquals(listOf(0, 3, 6, 7), lines(document).flatMap { it.runs }.map { it.sourceStartOffset })
        assertFinalMeasurements(lines(document), tabContext)
        val table = Table(listOf(TableColumn(Alignment.RIGHT), TableColumn(Alignment.LEFT)),
            listOf(TableCell(listOf(Text("ABCD"))), TableCell(listOf(Text("XY")))), emptyList())
        val cells = assertIs<LogicalTableBlock>(layout(table, width = 10.0).blocks.single()).rows.single().cells
        assertEquals(listOf("AB", "CD"), cells.first().text.lines.map { it.runs.single().text })
        assertFinalMeasurements(cells.flatMap { it.text.lines })
    }

    @Test fun freshIndivisibleClusterIsRetainedRatherThanSplitAtOldBoundary() {
        val merged = TextMeasurer { request ->
            if (request.text.length == 4) measurement(request, 1.0)
            else TextMeasureResult.Success(TextMeasurement(request.text.length, request.text.length * 1.5, request.typography.metrics,
                SnapshotList(listOf(MeasuredCluster(0, request.text.length, request.text.length * 1.5))), SnapshotList(emptyList())))
        }
        val document = layout(paragraph("ABCD"), measurer = merged)
        assertEquals(listOf("ABC", "D"), texts(document))
        assertEquals("LAY101", document.diagnostics.single().code)
        assertFinalMeasurements(lines(document), merged)
    }

    @Test fun contextualCorrectionHasBoundedFailureAndDocumentWorkBudget() {
        val hostile = TextMeasurer { request -> measurement(request, when (request.text.length) {
            40 -> 1.0
            1 -> 20.0
            else -> 30.0 / (request.text.length - 1)
        }) }
        val input = ThermalDocument(blocks = listOf(paragraph("A".repeat(40))))
        val failed = FoundationLayoutEngine(hostile).layout(input,
            LayoutConstraints(Length(30.0), Orientation.PORTRAIT, context))
        assertEquals("LAY123", assertIs<LayoutResult.Failure>(failed).diagnostics.single().code)
        val workHeavy = TextMeasurer { request -> measurement(request,
            if (request.text.length == 100) 1.0 else 90.0 / (request.text.length - 1)) }
        val workFailure = FoundationLayoutEngine(workHeavy).layout(
            ThermalDocument(blocks = listOf(paragraph("A".repeat(100)))),
            LayoutConstraints(Length(90.0), Orientation.PORTRAIT, context,
                LayoutResourcePolicy(maxTextCodeUnits = 100)))
        val diagnostic = assertIs<LayoutResult.Failure>(workFailure).diagnostics.single()
        assertEquals("LAY120", diagnostic.code)
        assertTrue(diagnostic.message.contains("measurement work"))
    }
}
