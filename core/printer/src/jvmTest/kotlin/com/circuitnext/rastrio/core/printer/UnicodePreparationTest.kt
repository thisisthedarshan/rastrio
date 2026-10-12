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
import com.circuitnext.rastrio.core.layout.*
import com.circuitnext.rastrio.core.profile.*
import com.circuitnext.rastrio.core.text.*
import kotlin.test.*

class UnicodePreparationTest {
    private val backend = BundledRasterTextBackend.create()
    private fun document(text: String) = ThermalDocument(blocks = listOf(Paragraph(Alignment.LEFT, listOf(Text(text)))))
    private fun prepare(input: ThermalDocument, profile: PrinterProfile = SyntheticReferenceProfiles.narrow,
        options: PrintOptions = PrintOptions(), policy: PreparationResourcePolicy = PreparationResourcePolicy()): PreparationResult =
        BasicPreparationEngine(resources = policy, rasterTextBackend = backend).prepare(input, profile, options)
    private fun print(input: ThermalDocument, profile: PrinterProfile = SyntheticReferenceProfiles.narrow): PreparedPrint {
        val result = prepare(input, profile)
        return assertIs<PreparationResult.Success>(result, result.toString()).print
    }

    @Test fun finalizedWrappedKerningAndIndicRunsMatchTheirActualShapingContext() {
        val typography = backend.typography(sizeMm = 3.0)
        val context = TypographyContext(typography, typography, typography)
        for ((text, width) in listOf("AV" to 2.0, "To" to 1.7, "क्षि क्षि" to 4.0)) {
            val input = ThermalDocument(blocks = listOf(Paragraph(Alignment.RIGHT, listOf(Text(text)))))
            val logical = assertIs<LayoutResult.Success>(FoundationLayoutEngine(backend).layout(input,
                LayoutConstraints(Length(width), Orientation.PORTRAIT, context))).document
            val lines = assertIs<LogicalTextBlock>(logical.blocks.single()).lines
            assertTrue(lines.size > 1)
            for (line in lines) for (run in line.runs) {
                val actual = assertIs<TextMeasureResult.Success>(backend.measure(TextMeasureRequest(run.text, run.typography))).measurement
                assertEquals(actual, run.measurement)
                assertEquals(LogicalGeometry.normalize(actual.advanceMm), run.bounds.widthMm)
                if (actual.advanceMm <= width) assertTrue(run.bounds.xMm + run.bounds.widthMm <= width + 0.000001)
            }
        }
    }

    @Test fun preparationPropagatesCancellationInsideFallbackMeasurement() {
        val observed = object : RasterTextBackend by backend {
            override fun measure(request: TextMeasureRequest, isCancelled: () -> Boolean): TextMeasureResult {
                var polls = 0
                return backend.measure(request) { isCancelled() || ++polls >= 5 }
            }
        }
        val result = BasicPreparationEngine(rasterTextBackend = observed).prepare(document("Жक".repeat(16)),
            SyntheticReferenceProfiles.narrow)
        assertTrue(assertIs<PreparationResult.Failure>(result).diagnostics.any { it.code == "TXT130" })
    }

    @Test fun missingOrInconsistentRasterOutputFailsWithoutReconstruction() {
        for (wrongDimensions in listOf(false, true)) {
            var calls = 0
            val producer = object : RasterTextBackend by backend {
                override fun rasterize(request: RasterTextRequest): RasterTextResult {
                    calls++
                    if (!wrongDimensions) return RasterTextResult.Failure(SnapshotList(listOf(
                        TextDiagnostic("TXT132", "Fixture artifact unavailable"))))
                    val width = request.widthDots - 1
                    return RasterTextResult.Success(TextBitmap(width, request.heightDots,
                        ByteArray((width + 7) / 8 * request.heightDots)), identity, "fixture-font")
                }
            }
            val result = BasicPreparationEngine(rasterTextBackend = producer).prepare(document("हिन्दी"),
                SyntheticReferenceProfiles.narrow)
            assertEquals(if (wrongDimensions) "PRN120" else "TXT132",
                assertIs<PreparationResult.Failure>(result).diagnostics.single().code)
            assertEquals(1, calls)
        }
    }

    @Test fun orderedFallbackMarkersUseResolvedMetricsAndCannotOverlapBodyText() {
        val base = SyntheticReferenceProfiles.narrow
        val profile = base.copy(nativeText = base.nativeText.copy(fonts = profileListOf(
            base.nativeText.fonts.first().copy(cellWidthDots = 1))))
        val input = ThermalDocument(blocks = listOf(OrderedList(1, listOf(
            ListItem(listOf(Paragraph(Alignment.LEFT, listOf(Text("हिन्दी"))))),
            ListItem(listOf(Paragraph(Alignment.LEFT, listOf(Text("ASCII item")))))
        ))))
        val plan = print(input, profile)
        val list = assertIs<LogicalListBlock>(plan.logicalDocument.blocks.single())
        val first = list.items.first()
        val marker = assertIs<LogicalOrderedMarker>(first.marker).run
        assertTrue(marker.sourceFlowId > 0)
        val actual = assertIs<TextMeasureResult.Success>(backend.measure(TextMeasureRequest(marker.text, marker.typography))).measurement
        assertEquals(actual, marker.measurement)
        val body = assertIs<LogicalTextBlock>(first.blocks.single()).lines.first().runs.first()
        assertTrue(marker.bounds.xMm + actual.advanceMm <= body.bounds.xMm)
        assertTrue(plan.operations.filterIsInstance<NativeText>().any { it.text == "ASCII item" })
        assertTrue(plan.operations.filterIsInstance<NativeText>().any { it.text == "2." })
    }

    @Test fun supportedAsciiAndCp437StayNativeWithRealBackendAvailable() {
        for (text in listOf("ASCII text", "café £")) {
            val plan = print(document(text))
            assertTrue(plan.operations.none { it is RasterText })
            assertEquals(text, plan.operations.filterIsInstance<NativeText>().joinToString("") { it.text })
        }
    }
    @Test fun combiningDevanagariConjunctAndMixedLinesFinalizeRealRaster() {
        for (text in listOf("a\u0301", "हिन्दी", "क्षि", "native हिन्दी suffix")) {
            val plan = print(document(text))
            val graphic = plan.operations.filterIsInstance<RasterText>()
            assertTrue(graphic.isNotEmpty(), text)
            assertTrue(plan.operations.none { it is NativeText })
            assertTrue(graphic.any { it.graphic.copyBytes().any { byte -> byte.toInt() != 0 } })
            assertTrue(graphic.all { "harfbuzz-14.6.0" in it.backendIdentity && "@" in it.fontIdentity })
        }
    }
    @Test fun explicitNativeLineSurvivesAdjacentFallbackAndSourceRangesRemainStable() {
        val input = ThermalDocument(blocks = listOf(Paragraph(Alignment.LEFT, listOf(Text("ASCII"), LineBreak, Text("हिन्दी")))))
        val plan = print(input)
        assertEquals("ASCII", plan.operations.filterIsInstance<NativeText>().joinToString("") { it.text })
        assertTrue(plan.operations.any { it is RasterText })
        val lines = assertIs<LogicalTextBlock>(plan.logicalDocument.blocks.single()).lines
        assertTrue(lines.flatMap { it.runs }.all { it.sourceFlowId > 0 })
    }
    @Test fun styledAndTabbedFallbackPreservesResolvedLineGeometry() {
        val input = ThermalDocument(blocks = listOf(CodeBlock("a\tहिन्दी"), Paragraph(Alignment.RIGHT,
            listOf(Strong(listOf(Text("bold "))), Text("हिन्दी")))))
        val plan = print(input)
        assertTrue(plan.operations.filterIsInstance<RasterText>().isNotEmpty())
        assertTrue(plan.operations.zipWithNext().all { (a,b) -> a.bounds.y <= b.bounds.y })
        assertTrue(plan.operations.all { it.bounds.right <= plan.target.printableWidthDots })
    }
    @Test fun semanticMarkersAreFinalizedAndCheckedStateChangesPixels() {
        fun todo(checked: Boolean) = ThermalDocument(blocks = listOf(Checklist(listOf(
            ChecklistItem(checked, listOf(Paragraph(Alignment.LEFT, listOf(Text("task")))))
        ))))
        val unchecked = print(todo(false)).operations.filterIsInstance<RasterText>()
        val checked = print(todo(true)).operations.filterIsInstance<RasterText>()
        assertTrue(unchecked.isNotEmpty())
        assertEquals(unchecked.map { it.bounds }, checked.map { it.bounds })
        assertFalse(unchecked.first().graphic.copyBytes().contentEquals(checked.first().graphic.copyBytes()))
    }
    @Test fun unavailableEmojiRtlAndMalformedUnicodeNeverPublishPartialPlan() {
        for (text in listOf("🙂", "שלום", "A\u200eB", "A\u200fB", "A\u206aB", "A\u206fB", "x\uD800", "x\uDC00")) {
            val result = assertIs<PreparationResult.Failure>(prepare(document(text)), text)
            assertTrue(result.diagnostics.isNotEmpty())
            assertTrue(result.diagnostics.none { text in it.message })
        }
    }
    @Test fun residentBandAndCancellationCeilingsApplyToRasterText() {
        val input = document("हिन्दी")
        val plan = print(input)
        val bytes = plan.operations.filterIsInstance<RasterText>().sumOf { it.graphic.sizeBytes }
        assertIs<PreparationResult.Success>(prepare(input, policy = PreparationResourcePolicy(maxResidentGraphicBytes = bytes)))
        assertIs<PreparationResult.Failure>(prepare(input, policy = PreparationResourcePolicy(maxResidentGraphicBytes = bytes - 1)))
        assertIs<PreparationResult.Failure>(BasicPreparationEngine(rasterTextBackend = backend).prepare(input,
            SyntheticReferenceProfiles.narrow, isCancelled = { true }))
    }
    @Test fun crossProfileNativeAndRasterDecisionsUseEffectiveCapabilities() {
        val native = print(document("hello"))
        val raster = print(document("hello"), SyntheticReferenceProfiles.rasterOnly)
        assertTrue(native.operations.any { it is NativeText })
        assertTrue(raster.operations.any { it is RasterText })
        assertTrue(raster.operations.none { it is NativeText })
    }
}
