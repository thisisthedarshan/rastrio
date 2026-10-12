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

class OrderedMarkerTypographyTest {
    private val body = ResolvedTypography("body", TextStyle(),
        TypographyMetrics(3.0, 1.0, 1.0, 5.0, 3.0), FixedCellGeometry(1.0, 1.0, 4.0))
    private val marker = body.copy(style = TextStyle(role = TextRole.LIST_MARKER))
    private val wideMarker = marker.copy(identity = "wide-marker",
        metrics = TypographyMetrics(4.0, 1.0, 1.0, 6.0, 4.0), fixedCell = FixedCellGeometry(3.0, 3.0, 4.0))
    private val constraints = LayoutConstraints(Length(30.0), Orientation.PORTRAIT, TypographyContext(body, body, marker))
    private fun paragraph(text: String) = Paragraph(Alignment.LEFT, listOf(Text(text)))
    private fun document(start: Long = 9) = ThermalDocument(blocks = listOf(
        OrderedList(start, listOf(ListItem(listOf(paragraph("A"))), ListItem(listOf(paragraph("B")))))))
    private fun layout(resolver: TextFlowTypographyResolver? = null) = assertIs<LayoutResult.Success>(
        FoundationLayoutEngine(AsciiFixedCellMeasurer(), resolver).layout(document(), constraints)).document
    private fun list(document: LogicalDocument) = assertIs<LogicalListBlock>(document.blocks.single())
    private fun markers(document: LogicalDocument) = list(document).items.map { assertIs<LogicalOrderedMarker>(it.marker) }
    private fun bodyRuns(document: LogicalDocument) = list(document).items.map {
        assertIs<LogicalTextBlock>(it.blocks.single()).lines.single().runs.single()
    }

    @Test fun resolvedMarkersReserveTheirActualColumnAndRetainStableSourceFlows() {
        val calls = mutableListOf<Pair<Int, String>>()
        val resolver = TextFlowTypographyResolver { flow, _, text, typography ->
            calls.add(flow to text)
            SnapshotList(listOf(ResolvedTextSpan(text, if (typography.style.role == TextRole.LIST_MARKER) wideMarker else typography)))
        }
        val output = layout(resolver)
        assertEquals(listOf(1 to "9.", 2 to "10.", 3 to "A", 4 to "B"), calls)
        assertEquals(9.0, list(output).markerColumnWidthMm)
        assertEquals(14.0, list(output).contentOriginMm)
        assertEquals(listOf(7.0, 4.0), markers(output).map { it.bounds.xMm })
        assertEquals(listOf(6.0, 9.0), markers(output).map { it.bounds.widthMm })
        assertTrue(markers(output).all { it.bounds.heightMm == 6.0 && it.run.typography == wideMarker })
        assertEquals(listOf(1, 2), markers(output).map { it.run.sourceFlowId })
        assertEquals(listOf(3, 4), bodyRuns(output).map { it.sourceFlowId })
        assertTrue((markers(output).map { it.run } + bodyRuns(output)).all { it.sourceStartOffset == 0 })
        assertEquals(output, layout(resolver))
    }

    @Test fun unchangedNativeMarkersKeepOriginalGeometryWithPositiveDistinctFlows() {
        val output = layout()
        assertEquals(3.0, list(output).markerColumnWidthMm)
        assertEquals(8.0, list(output).contentOriginMm)
        assertTrue(markers(output).all { it.run.typography == marker })
        assertEquals(listOf(1, 2, 3, 4), markers(output).map { it.run.sourceFlowId } + bodyRuns(output).map { it.sourceFlowId })
        assertEquals(output, layout())
    }

    @Test fun orderedMarkerPartitionMustPreserveOneWholeRunBeforeMeasurement() {
        for (parts in listOf(emptyList(), listOf("9", "."), listOf("8."))) {
            var calls = 0
            val service = TextMeasurer { calls++; error("Invalid marker partition cannot reach measurement") }
            val resolver = TextFlowTypographyResolver { _, _, _, typography ->
                SnapshotList(parts.map { ResolvedTextSpan(it, typography) })
            }
            val result = FoundationLayoutEngine(service, resolver).layout(document(), constraints)
            assertEquals("LAY103", assertIs<LayoutResult.Failure>(result).diagnostics.single().code)
            assertEquals(0, calls)
        }
    }

    @Test fun markerMeasurementUsesTheSameDocumentWorkBudgetAsBodyText() {
        var calls = 0
        val service = TextMeasurer { calls++; error("Marker work budget must be reserved before measurement") }
        val input = ThermalDocument(blocks = listOf(OrderedList(1_000_000_000,
            listOf(ListItem(listOf(paragraph("A")))))))
        val result = FoundationLayoutEngine(service).layout(input,
            constraints.copy(resources = constraints.resources.copy(maxTextCodeUnits = 1)))
        assertEquals("LAY120", assertIs<LayoutResult.Failure>(result).diagnostics.single().code)
        assertEquals(0, calls)
    }
}
