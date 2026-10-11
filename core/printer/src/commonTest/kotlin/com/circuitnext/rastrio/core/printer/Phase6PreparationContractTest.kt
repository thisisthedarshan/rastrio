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

import com.circuitnext.rastrio.core.document.*
import com.circuitnext.rastrio.core.document.Separator as DocumentSeparator
import com.circuitnext.rastrio.core.profile.*
import kotlin.test.*

class Phase6PreparationContractTest {
    private val narrow = SyntheticReferenceProfiles.narrow
    private val wide = SyntheticReferenceProfiles.wide80mm
    private fun document(text: String = "AB", alignment: Alignment = Alignment.LEFT) =
        ThermalDocument(blocks = listOf(Paragraph(alignment, listOf(Text(text)))))
    private fun prepare(input: ThermalDocument = document(), profile: PrinterProfile = narrow,
        options: PrintOptions = PrintOptions(), engine: BasicPreparationEngine = BasicPreparationEngine()) =
        assertIs<PreparationResult.Success>(engine.prepare(input, profile, options)).print
    private fun failed(input: ThermalDocument = document(), profile: PrinterProfile = narrow,
        options: PrintOptions = PrintOptions(), engine: BasicPreparationEngine = BasicPreparationEngine()) =
        assertIs<PreparationResult.Failure>(engine.prepare(input, profile, options)).diagnostics
    private fun assertCode(code: String, diagnostics: List<PreparationDiagnostic>) {
        assertTrue(diagnostics.any { it.code == code }, diagnostics.toString())
    }
    private fun text(plan: PreparedPrint) = plan.operations.filterIsInstance<NativeText>()

    @Test fun widthsResolveDifferentWrappingAndExactNativeGeometry() {
        val input = document("A".repeat(40))
        val small = prepare(input)
        val large = prepare(input, wide)
        assertEquals(384, small.target.printableWidthDots)
        assertEquals(576, large.target.printableWidthDots)
        assertEquals(listOf("A".repeat(32), "A".repeat(8)), text(small).map { it.text })
        assertEquals(listOf("A".repeat(40)), text(large).map { it.text })
        assertEquals(listOf(DotBounds(0, 0, 384, 24), DotBounds(0, 24, 96, 24)), text(small).map { it.bounds })
        assertEquals(DotBounds(0, 0, 480, 24), text(large).single().bounds)
        assertTrue(small.heightDots > large.heightDots)
    }

    @Test fun asymmetricDpiDeterminesMillimetresIndependentlyOnBothAxes() {
        val asymmetric = narrow.copy(geometry = PrinterGeometry(203, 406, 384))
        val plan = prepare(profile = asymmetric)
        assertEquals(203, plan.target.horizontalDpi)
        assertEquals(406, plan.target.verticalDpi)
        assertEquals(384 * 25.4 / 203, plan.logicalDocument.constraints.canvasWidth.value, 0.000001)
        val cell = plan.logicalDocument.constraints.typography.body.fixedCell!!
        assertEquals(12 * 25.4 / 203, cell.advanceMm, 0.000001)
        assertEquals(24 * 25.4 / 406, cell.nominalHeightMm, 0.000001)
        assertEquals(DotBounds(0, 0, 24, 24), text(plan).single().bounds)
        assertEquals(prepare().heightDots, plan.heightDots)
    }

    @Test fun nativeFontSelectionUsesValidatedCellAndSelector() {
        val plan = prepare(document("A".repeat(43)), options = PrintOptions(nativeFontId = "font-b"))
        assertEquals(listOf(42, 1), text(plan).map { it.text.length })
        for (run in text(plan)) {
            assertEquals("font-b", run.font.id)
            assertEquals(RegisteredSelector("escpos.font.esc-m", 1), run.font.selector)
            assertEquals(9, run.cellWidthDots)
            assertEquals(17, run.cellHeightDots)
            assertEquals(run.text.length * 9, run.bounds.width)
        }
        assertCode("PRN101", failed(options = PrintOptions(nativeFontId = "unknown")))
    }

    @Test fun registeredCodePageAndSupportedGlyphsHaveFinalBytes() {
        val run = text(prepare(document("Aé"), options = PrintOptions(codePageId = "cp437"))).single()
        assertEquals("cp437", run.codePage.id)
        assertEquals("cp437", run.codePage.repertoire)
        assertEquals(RegisteredSelector("escpos.code-page.esc-t", 0), run.codePage.selector)
        assertEquals(listOf(0x41, 0x82), run.characterBytes)
        assertCode("PRN101", failed(options = PrintOptions(codePageId = "unknown")))
    }

    @Test fun unrepresentableAndComplexGlyphsFailWithoutSubstitution() {
        for (unsupported in listOf("अ", "🙂", "e\u0301")) {
            val diagnostics = failed(document(unsupported))
            assertTrue(diagnostics.any { it.code.startsWith("TXT") }, diagnostics.toString())
            assertTrue(diagnostics.none { unsupported in it.message })
        }
    }

    @Test fun controlsCannotBecomeNativeCommandBytes() {
        for (control in listOf("\u0000", "\u001b@", "\u001dV", "\u007f", "\u0085")) {
            val diagnostics = failed(document("private" + control))
            assertTrue(diagnostics.any { it.code.startsWith("TXT") || it.code == "PRN100" }, diagnostics.toString())
            assertTrue(diagnostics.none { "private" in it.message })
        }
    }

    @Test fun styleAndScaleMustBeSupportedRatherThanSilentlyDropped() {
        val strong = ThermalDocument(blocks = listOf(Paragraph(Alignment.LEFT, listOf(Strong(listOf(Text("AB")))))))
        assertTrue(text(prepare(strong)).single().style.bold)
        val noBold = narrow.copy(nativeText = narrow.nativeText.copy(styles = narrow.nativeText.styles.copy(bold = false)))
        assertCode("PRN102", failed(strong, noBold))
        val italic = ThermalDocument(blocks = listOf(Paragraph(Alignment.LEFT, listOf(Emphasis(listOf(Text("AB")))))))
        assertCode("PRN102", failed(italic))
        assertCode("PRN102", failed(options = PrintOptions(widthScale = 3)))
        val doubled = text(prepare(options = PrintOptions(widthScale = 2, heightScale = 2))).single()
        assertEquals(24, doubled.cellWidthDots)
        assertEquals(48, doubled.cellHeightDots)
        assertEquals(DotBounds(0, 0, 48, 48), doubled.bounds)
    }

    @Test fun alignmentIsResolvedInPhysicalDots() {
        assertEquals(180, text(prepare(document(alignment = Alignment.CENTER))).single().bounds.x)
        assertEquals(360, text(prepare(document(alignment = Alignment.RIGHT))).single().bounds.x)
    }

    @Test fun noCutterRejectsRequiredAutomaticCutAndHonorsExplicitFallback() {
        assertCode("PRN103", failed(options = PrintOptions(cutIntent = CutIntent.AUTOMATIC)))
        val fallback = prepare(options = PrintOptions(cutIntent = CutIntent.AUTOMATIC_OR_MANUAL))
        assertEquals(1, fallback.operations.filterIsInstance<ManualCutGuide>().size)
        assertTrue(fallback.operations.none { it is AutomaticCut })
    }

    @Test fun supportedFullAndPartialCutsKeepTheirDistinctValidatedMode() {
        for (mode in CutMode.entries) {
            val plan = prepare(profile = wide, options = PrintOptions(cutIntent = CutIntent.AUTOMATIC, cutMode = mode))
            val cut = assertIs<AutomaticCut>(plan.operations.last())
            assertEquals(mode, cut.mode)
            assertEquals("escpos.cut.gs-v", cut.strategy)
            assertEquals(DotBounds(0, plan.heightDots, 0, 0), cut.bounds)
            assertTrue(plan.operations.none { it is ManualCutGuide })
        }
        val fullOnly = wide.copy(cutter = wide.cutter.copy(modes = profileListOf("full")))
        assertCode("PRN103", failed(profile = fullOnly, options = PrintOptions(cutIntent = CutIntent.AUTOMATIC, cutMode = CutMode.PARTIAL)))
    }

    @Test fun manualGuideContainsFinalPrintedBitsAndNoAutomaticCut() {
        val plan = prepare(options = PrintOptions(cutIntent = CutIntent.MANUAL_GUIDE))
        val guide = plan.operations.filterIsInstance<ManualCutGuide>().single()
        assertEquals(384, guide.bounds.width)
        assertEquals(guide.bounds.width, guide.graphic.widthDots)
        assertEquals(guide.bounds.height, guide.graphic.heightDots)
        assertEquals("escpos.raster.gs-v-0", guide.strategy)
        assertTrue(guide.graphic.copyBytes().any { it.toInt() != 0 })
        assertTrue(plan.operations.none { it is AutomaticCut })
    }

    @Test fun defaultAndSeparatorDoNotInventCuts() {
        for (profile in listOf(narrow, wide)) {
            val plan = prepare(ThermalDocument(blocks = listOf(DocumentSeparator)), profile)
            val separator = plan.operations.filterIsInstance<Separator>().single()
            assertEquals(profile.geometry.printableWidthDots, separator.bounds.width)
            assertTrue(separator.graphic.copyBytes().any { it.toInt() != 0 })
            assertTrue(plan.operations.none { it is AutomaticCut || it is ManualCutGuide })
        }
    }

    @Test fun finalFeedIsAnExactOrderedPhysicalAdvance() {
        val original = prepare(profile = wide)
        val plan = prepare(profile = wide, options = PrintOptions(finalFeedDots = 25, cutIntent = CutIntent.AUTOMATIC))
        val feed = assertIs<Feed>(plan.operations[plan.operations.lastIndex - 1])
        assertEquals(DotBounds(0, original.heightDots, 0, 25), feed.bounds)
        assertEquals(25, feed.advanceDots)
        assertEquals(original.heightDots + 25, plan.heightDots)
        assertIs<AutomaticCut>(plan.operations.last())
    }

    private fun signature(plan: PreparedPrint): List<Any> = listOf(plan.heightDots, plan.logicalDocument,
        plan.target.printableWidthDots, plan.diagnostics.toList(), plan.operations.map { operation ->
            when (operation) {
                is NativeText -> listOf(operation.bounds, operation.text, operation.characterBytes.toList(),
                    operation.font.id, operation.codePage.id, operation.style, operation.alignment, operation.advanceDots)
                is Separator -> listOf(operation.bounds, operation.strategy, operation.graphic.copyBytes().toList())
                is ManualCutGuide -> listOf(operation.bounds, operation.strategy, operation.graphic.copyBytes().toList())
                is RasterOperation -> listOf(operation.bounds, operation.strategy, operation.bandOrdinal, operation.graphic.copyBytes().toList())
                is Feed -> listOf(operation.bounds, operation.advanceDots)
                is AutomaticCut -> listOf(operation.bounds, operation.mode, operation.strategy)
            }
        })

    @Test fun repeatedPreparationIsDeterministicAndInputCollectionsAreSnapshotted() {
        val inline = mutableListOf<InlineContent>(Text("AB"))
        val blocks = mutableListOf<DocumentBlock>(Paragraph(Alignment.LEFT, inline), DocumentSeparator)
        val input = ThermalDocument(blocks = blocks)
        val options = PrintOptions(cutIntent = CutIntent.MANUAL_GUIDE)
        val first = prepare(input, options = options)
        val before = signature(first)
        assertEquals(before, signature(prepare(input, options = options)))
        inline.clear()
        blocks.clear()
        assertEquals(before, signature(first))
        val operations: Any = first.operations
        val characterBytes: Any = text(first).single().characterBytes
        assertFalse(operations is MutableList<*>)
        assertFalse(characterBytes is MutableList<*>)
        val graphic = first.operations.filterIsInstance<ManualCutGuide>().single().graphic
        val bytes = graphic.copyBytes()
        bytes.fill(0)
        assertEquals(before, signature(first))
    }

    @Test fun coordinateOverflowAndPhysicalHeightLimitsFailAtomically() {
        assertCode("PRN120", failed(options = PrintOptions(finalFeedDots = Int.MAX_VALUE),
            engine = BasicPreparationEngine(resources = PreparationResourcePolicy(maxHeightDots = Int.MAX_VALUE))))
        val height = prepare().heightDots
        assertEquals(height, prepare(engine = BasicPreparationEngine(resources = PreparationResourcePolicy(maxHeightDots = height))).heightDots)
        assertCode("PRN120", failed(engine = BasicPreparationEngine(resources = PreparationResourcePolicy(maxHeightDots = height - 1))))
    }

    @Test fun operationAndTextBudgetsAcceptBoundaryAndRejectOneBeyond() {
        val count = prepare().operations.size
        prepare(engine = BasicPreparationEngine(resources = PreparationResourcePolicy(maxOperations = count, maxTextCodeUnits = 2)))
        assertCode("PRN120", failed(document("ABC"), engine = BasicPreparationEngine(resources = PreparationResourcePolicy(maxTextCodeUnits = 2))))
        assertCode("PRN120", failed(ThermalDocument(blocks = listOf(Paragraph(Alignment.LEFT, listOf(Text("AB"))), DocumentSeparator)),
            engine = BasicPreparationEngine(resources = PreparationResourcePolicy(maxOperations = count))))
        val guideOptions = PrintOptions(cutIntent = CutIntent.MANUAL_GUIDE)
        val bytes = prepare(options = guideOptions).operations.filterIsInstance<ManualCutGuide>().single().graphic.sizeBytes
        prepare(options = guideOptions, engine = BasicPreparationEngine(resources = PreparationResourcePolicy(maxGraphicBandBytes = bytes, maxPreparedGraphicBytes = bytes.toLong())))
        assertCode("PRN120", failed(options = guideOptions, engine = BasicPreparationEngine(resources = PreparationResourcePolicy(maxGraphicBandBytes = bytes - 1))))
    }

    @Test fun invalidSemanticAndProfileInputsFailWithStructuredDiagnostics() {
        assertCode("PRN100", failed(document().copy(schemaVersion = 2)))
        assertCode("PRN104", failed(document().copy(layout = DocumentLayout(Orientation.LANDSCAPE))))
        assertCode("PRN100", failed(document().copy(layout = DocumentLayout(maxWidth = Length(Double.NaN)))))
        val invalid = narrow.copy(geometry = narrow.geometry.copy(printableWidthDots = 0))
        assertTrue(failed(profile = invalid).any { it.code.startsWith("PRF") })
        val invalidOverride = BasicPreparationEngine().prepare(document(), narrow,
            override = PrinterProfileOverride(geometry = narrow.geometry.copy(printableWidthDots = 1)))
        assertTrue(assertIs<PreparationResult.Failure>(invalidOverride).diagnostics.any { it.code.startsWith("PRF") })
    }

    @Test fun printerNamesCannotChangeCapabilityDrivenOutput() {
        val renamed = narrow.copy(display = ProfileDisplay("H50i", "Unknown", "H50i"))
        assertEquals(signature(prepare()), signature(prepare(profile = renamed)))
        assertEquals(failed(options = PrintOptions(cutIntent = CutIntent.AUTOMATIC)),
            failed(profile = renamed, options = PrintOptions(cutIntent = CutIntent.AUTOMATIC)))
    }

    @Test fun styledRunsShareOneLineAndOnlyFinalRunAdvancesPaper() {
        val input = ThermalDocument(blocks = listOf(Paragraph(Alignment.LEFT,
            listOf(Text("A"), Strong(listOf(Text("B"))), Text("C")))))
        val plan = prepare(input)
        val runs = text(plan)
        assertEquals(listOf(DotBounds(0, 0, 12, 24), DotBounds(12, 0, 12, 24), DotBounds(24, 0, 12, 24)), runs.map { it.bounds })
        assertEquals(listOf(false, true, false), runs.map { it.style.bold })
        assertEquals(listOf(0, 0, 24), runs.map { it.advanceDots })
        assertEquals(36, plan.heightDots)
    }

    @Test fun tabsResolveToFinalSpacesAtPreparedCellStops() {
        val plan = prepare(ThermalDocument(blocks = listOf(CodeBlock("A\tB"))))
        val runs = text(plan)
        assertEquals("A   B", runs.joinToString("") { it.text })
        assertTrue(runs.flatMap { it.characterBytes }.none { it == 0x09 })
        val tab = runs.first { it.text == "   " }
        assertEquals(DotBounds(12, 12, 36, 24), tab.bounds)
        assertEquals(48, runs.last().bounds.x)
    }

    @Test fun unavailableRasterFallbackAndUnsupportedContentFailExplicitly() {
        assertCode("PRN101", failed(profile = SyntheticReferenceProfiles.rasterOnly))
        for (block in listOf(QrCode("private", Alignment.LEFT, QrErrorCorrection.AUTO),
            Image(EmbeddedAssetReference("private"), Alignment.LEFT, AutoSizing))) {
            val input = ThermalDocument(blocks = document().blocks + block)
            assertCode("PRN104", failed(input))
        }
    }

    @Test fun residentGraphicLimitIsSeparateFromCumulativePayloadLimit() {
        val input = ThermalDocument(blocks = listOf(DocumentSeparator, DocumentSeparator))
        val original = prepare(input)
        val bytes = original.operations.filterIsInstance<Separator>().sumOf { it.graphic.sizeBytes }
        val exact = PreparationResourcePolicy(maxResidentGraphicBytes = bytes)
        prepare(input, engine = BasicPreparationEngine(resources = exact))
        val failure = failed(input, engine = BasicPreparationEngine(resources = exact.copy(maxResidentGraphicBytes = bytes - 1)))
        assertCode("PRN120", failure)
        assertTrue(failure.single().message.contains("Resident graphics"))
    }
}
