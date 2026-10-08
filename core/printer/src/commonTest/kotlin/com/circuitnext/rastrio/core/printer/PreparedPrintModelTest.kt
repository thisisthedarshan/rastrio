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

package com.circuitnext.rastrio.core.printer

import com.circuitnext.rastrio.core.document.Length
import com.circuitnext.rastrio.core.document.CodeBlock
import com.circuitnext.rastrio.core.document.ThermalDocument
import com.circuitnext.rastrio.core.profile.SyntheticReferenceProfiles
import com.circuitnext.rastrio.core.document.LengthUnit
import com.circuitnext.rastrio.core.document.Orientation
import com.circuitnext.rastrio.core.layout.LayoutConstraints
import com.circuitnext.rastrio.core.layout.LogicalDocument
import com.circuitnext.rastrio.core.profile.RegisteredSelector
import com.circuitnext.rastrio.core.text.*
import kotlin.test.*

class PreparedPrintModelTest {
    private val font = PreparedNativeFont("font-a", RegisteredSelector("escpos.font.esc-m", 0), 12, 24, 24)
    private val page = PreparedCodePage("cp437", "cp437", RegisteredSelector("escpos.code-page.esc-t", 0))
    private val metrics = TypographyMetrics(2.0, 1.0, 0.0, 3.0, 2.0)
    private val typography = ResolvedTypography("fixture", TextStyle(), metrics, FixedCellGeometry(1.5, 1.5, 3.0))
    private val logical = LogicalDocument(LayoutConstraints(Length(48.0, LengthUnit.MM), Orientation.PORTRAIT,
        TypographyContext(typography, typography, typography)), 3.0, SnapshotList(emptyList()))
    private val target = PreparedTarget("fixture", 1, 203, 203, 384, "escpos.generic", "escpos.init.standard")
    private fun text(y: Int = 0, bytes: SnapshotList<Int> = SnapshotList(listOf(65))) = NativeText("A", bytes,
        font, page, PreparedNativeStyle(), PreparedAlignment.LEFT, DotBounds(0, y, 12, 24), 24)
    private fun plan(operations: List<PreparedOperation>, height: Int = 24,
        resources: PreparationResourcePolicy = PreparationResourcePolicy()) = PreparedPrint(logical, target,
        SnapshotList(operations), SnapshotList(emptyList()), height, resources)

    @Test fun preparedCollectionsCannotBeChangedByTheirSources() {
        val bytes = mutableListOf(65)
        val text = text(bytes = SnapshotList(bytes))
        val operations = mutableListOf<PreparedOperation>(text)
        val prepared = plan(operations)
        bytes[0] = 27
        operations.clear()
        assertEquals(65, (prepared.operations.single() as NativeText).characterBytes.single())
        assertFalse((prepared.operations as Any) is MutableList<*>)
        assertFalse((text.characterBytes as Any) is MutableList<*>)
    }

    @Test fun graphicsDefensivelyCopyInputAndOutputAndPreserveOddWidthPadding() {
        val input = byteArrayOf(0x80.toByte(), 0x80.toByte())
        val graphic = PreparedMonochrome(9, 1, input)
        input.fill(0)
        graphic.copyBytes().fill(0)
        assertTrue(graphic.isBlack(0, 0))
        assertTrue(graphic.isBlack(8, 0))
        assertFalse(graphic.isBlack(7, 0))
        assertEquals(2, graphic.rowBytes)
        assertFailsWith<IllegalArgumentException> { PreparedMonochrome(9, 1, byteArrayOf(0, 1)) }
        assertFailsWith<IllegalArgumentException> { PreparedMonochrome(9, 2, byteArrayOf(0, 0)) }
    }

    @Test fun graphicsRejectOverflowAndOversizedBandsBeforeCopying() {
        assertFailsWith<IllegalArgumentException> { PreparedMonochrome(Int.MAX_VALUE, Int.MAX_VALUE, byteArrayOf()) }
        assertFailsWith<IllegalArgumentException> { PreparedMonochrome(8, 2 * 1024 * 1024 + 1, byteArrayOf()) }
        assertFailsWith<IllegalArgumentException> { DotBounds(Int.MAX_VALUE, 0, 1, 0) }
        assertFailsWith<IllegalArgumentException> { DotBounds(0, Int.MAX_VALUE, 0, 1) }
    }

    @Test fun textRejectsControlBytesAndContradictoryCellGeometry() {
        assertFailsWith<IllegalArgumentException> { text(bytes = SnapshotList(listOf(27))) }
        assertFailsWith<IllegalArgumentException> { text(bytes = SnapshotList(listOf(127))) }
        assertFailsWith<IllegalArgumentException> { text(bytes = SnapshotList(listOf(65, 66))) }
        assertFailsWith<IllegalArgumentException> {
            NativeText("A", SnapshotList(listOf(65)), font, page, PreparedNativeStyle(widthScale = Int.MAX_VALUE),
                PreparedAlignment.LEFT, DotBounds(0, 0, 12, 24), 24)
        }
        assertFailsWith<IllegalArgumentException> {
            NativeText("A", SnapshotList(listOf(65)), font, page, PreparedNativeStyle(),
                PreparedAlignment.LEFT, DotBounds(0, 0, 13, 24), 24)
        }
    }

    @Test fun exactGeometryAndResourceBoundariesAreEnforced() {
        assertEquals(24, plan(listOf(text())).heightDots)
        assertFailsWith<IllegalArgumentException> { plan(listOf(text()), height = 23) }
        assertFailsWith<IllegalArgumentException> { plan(listOf(text(24), text()), height = 48) }
        assertEquals(1, plan(listOf(text()), resources = PreparationResourcePolicy(maxOperations = 1,
            maxTextCodeUnits = 1, maxHeightDots = 24)).operations.size)
        assertFailsWith<IllegalArgumentException> {
            plan(listOf(text(), text(24)), height = 48, resources = PreparationResourcePolicy(maxOperations = 1))
        }
        assertFailsWith<IllegalArgumentException> {
            plan(listOf(text(), text(24)), height = 48, resources = PreparationResourcePolicy(maxTextCodeUnits = 1))
        }
    }

    @Test fun graphicsBudgetIsCumulativeAndGeometryMustMatchContent() {
        val graphic = PreparedMonochrome(8, 1, byteArrayOf(0xff.toByte()))
        val rule = Separator(DotBounds(0, 0, 8, 1), graphic, "escpos.raster.gs-v-0")
        val guide = ManualCutGuide(DotBounds(0, 1, 8, 1), graphic, "escpos.raster.gs-v-0")
        assertEquals(2, plan(listOf(rule, guide), 2,
            PreparationResourcePolicy(maxPreparedGraphicBytes = 2)).operations.size)
        assertFailsWith<IllegalArgumentException> {
            plan(listOf(rule, guide), 2, PreparationResourcePolicy(maxPreparedGraphicBytes = 1))
        }
        assertFailsWith<IllegalArgumentException> { Separator(DotBounds(0, 0, 9, 1), graphic, "escpos.raster.gs-v-0") }
        assertFailsWith<IllegalArgumentException> { Separator(DotBounds(380, 0, 8, 1), graphic, "") }
        assertFailsWith<IllegalArgumentException> {
            plan(listOf(Separator(DotBounds(380, 0, 8, 1), graphic, "escpos.raster.gs-v-0")), 1)
        }
    }

    @Test fun emptyCodeLinesRetainAllPhysicalPaperAdvances() {
        for (content in listOf("", "\n", "\n\n")) {
            val prepared = assertIs<PreparationResult.Success>(BasicPreparationEngine().prepare(
                ThermalDocument(blocks = listOf(CodeBlock(content))), SyntheticReferenceProfiles.narrow)).print
            val advances = prepared.operations.sumOf {
                when (it) { is NativeText -> it.advanceDots; is Feed -> it.advanceDots; else -> 0 }
            }
            assertEquals(prepared.heightDots, advances)
            assertEquals(content.length + 1, prepared.operations.filterIsInstance<NativeText>().size)
        }
    }

    @Test fun styledRunsCanContinueWithoutAdvancingPaper() {
        val first = NativeText("A", SnapshotList(listOf(65)), font, page, PreparedNativeStyle(),
            PreparedAlignment.LEFT, DotBounds(0, 0, 12, 24), 0)
        val last = NativeText("B", SnapshotList(listOf(66)), font, page, PreparedNativeStyle(bold = true),
            PreparedAlignment.LEFT, DotBounds(12, 0, 12, 24), 24)
        assertEquals(listOf(0, 24), plan(listOf(first, last)).operations.filterIsInstance<NativeText>().map { it.advanceDots })
        assertFailsWith<IllegalArgumentException> {
            NativeText("A", SnapshotList(listOf(65)), font, page, PreparedNativeStyle(),
                PreparedAlignment.LEFT, DotBounds(0, 0, 12, 24), -1)
        }
    }

    @Test fun feedAndCutHaveExplicitMechanicalGeometry() {
        val feed = Feed(DotBounds(0, 0, 0, 3), 3)
        val cut = AutomaticCut(DotBounds(0, 3, 0, 0), CutMode.PARTIAL, "escpos.cut.gs-v")
        assertEquals(3, plan(listOf(feed, cut), 3).heightDots)
        assertEquals(CutMode.PARTIAL, cut.mode)
        assertFailsWith<IllegalArgumentException> { Feed(DotBounds(0, 0, 0, 2), 3) }
        assertFailsWith<IllegalArgumentException> { AutomaticCut(DotBounds(0, 0, 1, 0), CutMode.FULL, "escpos.cut.gs-v") }
        assertFailsWith<IllegalArgumentException> { PreparationResult.Failure(SnapshotList(emptyList())) }
    }
}
