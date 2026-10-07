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

class PlaceholderFlowTest {
    private val body = ResolvedTypography("placeholder", TextStyle(), TypographyMetrics(3.0, 1.0, 1.0, 5.0, 3.0), FixedCellGeometry(1.0, 1.0, 4.0))
    private val input = LayoutConstraints(Length(48.0), Orientation.PORTRAIT, TypographyContext(body, body, body))
    private val opaque = "https://127.0.0.1/private?token=secret"
    private fun image(alignment: Alignment = Alignment.LEFT, sizing: ImageSizing = AutoSizing) =
        Image(ExternalAssetReference(opaque), alignment, sizing, "private alt")
    private fun qr(alignment: Alignment = Alignment.LEFT, size: Length? = null, correction: QrErrorCorrection = QrErrorCorrection.AUTO) =
        QrCode(opaque, alignment, correction, size)
    private fun layout(vararg blocks: DocumentBlock, constraints: LayoutConstraints = input): LogicalDocument =
        assertIs<LayoutResult.Success>(FoundationLayoutEngine(AsciiFixedCellMeasurer()).layout(ThermalDocument(blocks = blocks.toList()), constraints)).document

    @Test fun requestedImagesResolveAllAlignmentsAndRetainIntent() {
        for ((alignment, x) in listOf(Alignment.LEFT to 0.0, Alignment.CENTER to 14.0, Alignment.RIGHT to 28.0)) {
            val semantic = image(alignment, RequestedWidthSizing(Length(20.0)))
            val document = layout(semantic)
            val placeholder = assertIs<LogicalImagePlaceholder>(document.blocks.single())
            assertEquals(LogicalBounds(x, 2.5, 20.0, 20.0), placeholder.bounds)
            assertEquals(semantic.asset, placeholder.asset)
            assertEquals(semantic.sizing, placeholder.sizing)
            assertEquals("private alt", placeholder.altText)
            assertEquals(alignment, placeholder.alignment)
            assertFalse(placeholder.intrinsicSizeResolved)
            assertEquals(2.5, placeholder.beforeSpacingMm)
            assertEquals(2.5, placeholder.afterSpacingMm)
            assertEquals(25.0, document.heightMm)
            assertTrue(document.diagnostics.isEmpty())
        }
    }

    @Test fun autoFitWidthAndExactRequestedWidthHaveExplicitSquareGeometry() {
        for (alignment in Alignment.entries) for (sizing in listOf(AutoSizing, FitWidthSizing, RequestedWidthSizing(Length(48.0)))) {
            val placeholder = assertIs<LogicalImagePlaceholder>(layout(image(alignment, sizing)).blocks.single())
            assertEquals(LogicalBounds(0.0, 2.5, 48.0, 48.0), placeholder.bounds)
            assertEquals(sizing, placeholder.sizing)
        }
        val embedded = Image(EmbeddedAssetReference("unopened-asset"), Alignment.LEFT, AutoSizing)
        val placeholder = assertIs<LogicalImagePlaceholder>(layout(embedded).blocks.single())
        assertEquals(embedded.asset, placeholder.asset)
        assertNull(placeholder.altText)
        assertEquals(53.0, layout(embedded).heightMm)
    }

    @Test fun imageOuterBoxAndFollowingFlowAreFinalIndependentOfUnresolvedAsset() {
        val following = Paragraph(Alignment.LEFT, listOf(Text("After")))
        for (sizing in listOf(AutoSizing, FitWidthSizing, RequestedWidthSizing(Length(20.0)))) {
            val embedded = Image(EmbeddedAssetReference("portrait-or-landscape"), Alignment.CENTER, sizing)
            val external = embedded.copy(asset = ExternalAssetReference(opaque))
            val first = layout(embedded, following)
            val second = layout(external, following)
            val image = assertIs<LogicalImagePlaceholder>(first.blocks.first())
            val expectedBox = if (sizing is RequestedWidthSizing) LogicalBounds(14.0, 2.5, 20.0, 20.0)
                else LogicalBounds(0.0, 2.5, 48.0, 48.0)
            val expectedNextY = if (sizing is RequestedWidthSizing) 25.0 else 53.0
            assertEquals(expectedBox, image.bounds)
            assertEquals(expectedBox, second.blocks.first().bounds)
            assertEquals(expectedNextY, first.blocks.last().bounds.yMm)
            assertEquals(first.blocks.last(), second.blocks.last())
            assertEquals(if (sizing is RequestedWidthSizing) 32.5 else 60.5, first.heightMm)
            assertEquals(first.heightMm, second.heightMm)
            assertFalse(image.intrinsicSizeResolved)
        }
    }

    @Test fun requestedGeometryMayFailNarrowConstraintsAndSucceedWideConstraints() {
        for (block in listOf(image(Alignment.CENTER, RequestedWidthSizing(Length(60.0))), qr(Alignment.CENTER, Length(60.0)))) {
            val narrow = FoundationLayoutEngine(AsciiFixedCellMeasurer()).layout(ThermalDocument(blocks = listOf(block)), input)
            assertEquals("LAY107", assertIs<LayoutResult.Failure>(narrow).diagnostics.single().code)
            val wide = layout(block, constraints = input.copy(canvasWidth = Length(80.0)))
            assertEquals(LogicalBounds(10.0, 2.5, 60.0, 60.0), wide.blocks.single().bounds)
            assertEquals(65.0, wide.heightMm)
        }
    }

    @Test fun qrDefaultAndExplicitSizesResolveAllAlignmentsAndErrorCorrectionValues() {
        for (correction in QrErrorCorrection.entries) for ((alignment, x) in listOf(Alignment.LEFT to 0.0, Alignment.CENTER to 9.0, Alignment.RIGHT to 18.0)) {
            val semantic = qr(alignment, correction = correction)
            val document = layout(semantic)
            val placeholder = assertIs<LogicalQrPlaceholder>(document.blocks.single())
            assertEquals(LogicalBounds(x, 2.5, 30.0, 30.0), placeholder.bounds)
            assertEquals(opaque, placeholder.payload)
            assertEquals(correction, placeholder.errorCorrection)
            assertEquals(alignment, placeholder.alignment)
            assertNull(placeholder.requestedSize)
            assertEquals(35.0, document.heightMm)
        }
        for ((alignment, x) in listOf(Alignment.LEFT to 0.0, Alignment.CENTER to 14.0, Alignment.RIGHT to 28.0)) {
            val placeholder = assertIs<LogicalQrPlaceholder>(layout(qr(alignment, Length(20.0))).blocks.single())
            assertEquals(LogicalBounds(x, 2.5, 20.0, 20.0), placeholder.bounds)
            assertEquals(Length(20.0), placeholder.requestedSize)
        }
        assertEquals(LogicalBounds(0.0, 2.5, 48.0, 48.0), layout(qr(size = Length(48.0))).blocks.single().bounds)
        assertEquals(LogicalBounds(0.0, 2.5, 10.0, 10.0), layout(qr(Alignment.RIGHT), constraints = input.copy(canvasWidth = Length(10.0))).blocks.single().bounds)
    }

    @Test fun opaqueReferencesAndPayloadsNeverInvokeTextMeasurement() {
        val engine = FoundationLayoutEngine(TextMeasurer { error("Placeholder must not query text or assets") })
        val semantic = ThermalDocument(blocks = listOf(image(), qr(), Image(EmbeddedAssetReference("not-opened"), Alignment.RIGHT, FitWidthSizing)))
        val document = assertIs<LayoutResult.Success>(engine.layout(semantic, input)).document
        assertEquals(opaque, assertIs<LogicalQrPlaceholder>(document.blocks[1]).payload)
        assertEquals(ExternalAssetReference(opaque), assertIs<LogicalImagePlaceholder>(document.blocks[0]).asset)
    }

    @Test fun multiplePlaceholdersAmongTextHaveDeterministicFlow() {
        val p = Paragraph(Alignment.LEFT, listOf(Text("A")))
        val document = layout(p, image(Alignment.CENTER, RequestedWidthSizing(Length(20.0))), qr(), p, image(sizing = FitWidthSizing))
        assertEquals(listOf(0.0, 10.0, 35.0, 67.5, 77.5), document.blocks.map { it.bounds.yMm })
        assertEquals(128.0, document.heightMm)
        assertEquals(listOf(0, 1, 2, 3, 4), document.blocks.map { it.sourceBlockIndex })
        assertEquals(document, layout(p, image(Alignment.CENTER, RequestedWidthSizing(Length(20.0))), qr(), p, image(sizing = FitWidthSizing)))
    }

    @Test fun nestedAndWidePlaceholdersUseExplicitContentGeometryInBothOrientations() {
        val semantic = Quote(listOf(image(Alignment.RIGHT, RequestedWidthSizing(Length(20.0))), qr(Alignment.CENTER)))
        val document = layout(semantic)
        val quote = assertIs<LogicalQuoteBlock>(document.blocks.single())
        assertEquals(LogicalBounds(28.0, 2.5, 20.0, 20.0), quote.blocks[0].bounds)
        assertEquals(LogicalBounds(11.0, 27.5, 30.0, 30.0), quote.blocks[1].bounds)
        assertEquals(60.0, document.heightMm)
        for (width in listOf(48.0, 180.0)) {
            val portrait = layout(image(), qr(Alignment.CENTER), constraints = input.copy(canvasWidth = Length(width)))
            val landscape = layout(image(), qr(Alignment.CENTER), constraints = input.copy(canvasWidth = Length(width), orientation = Orientation.LANDSCAPE))
            assertEquals(portrait.blocks, landscape.blocks)
            assertEquals(width, landscape.blocks.first().bounds.widthMm)
            assertEquals(if (width == 48.0) 9.0 else 75.0, landscape.blocks[1].bounds.xMm)
        }
    }

    @Test fun requestedImageAndQrSizesRejectEnclosingWidthEvenWhenTheyFitRootCanvas() {
        val engine = FoundationLayoutEngine(TextMeasurer { error("Placeholders must not invoke measurement") })
        val quote = assertIs<LogicalQuoteBlock>(layout(Quote(listOf(image()))).blocks.single())
        val enclosingWidth = quote.blocks.single().bounds.widthMm
        val requested = Length(45.0)
        assertEquals(48.0, input.canvasWidth.value)
        assertEquals(44.0, enclosingWidth)
        assertTrue(LogicalGeometry.fits(requested.value, input.canvasWidth.value))
        assertFalse(LogicalGeometry.fits(requested.value, enclosingWidth))
        for (block in listOf(image(sizing = RequestedWidthSizing(requested)), qr(size = requested))) {
            val root = assertIs<LayoutResult.Success>(engine.layout(ThermalDocument(blocks = listOf(block)), input))
            assertEquals(45.0, root.document.blocks.single().bounds.widthMm)
            // Earlier geometry must not escape when the later nested request fails.
            val result = engine.layout(ThermalDocument(blocks = listOf(qr(size = Length(1.0)), Quote(listOf(block)))), input)
            val diagnostic = assertIs<LayoutResult.Failure>(result).diagnostics.single()
            assertEquals("LAY107", diagnostic.code)
            assertEquals(1, diagnostic.sourceBlockIndex)
            assertFalse(diagnostic.message.contains(opaque))
            assertFalse(diagnostic.message.contains("private alt"))
            assertFalse(diagnostic.message.contains("secret"))
        }
    }

    @Test fun malformedAndOversizeLengthsAreAtomicAndContentPrivate() {
        for ((size, code) in listOf(Length(0.0) to "LAY105", Length(-1.0) to "LAY105", Length(Double.NaN) to "LAY105",
            Length(Double.POSITIVE_INFINITY) to "LAY105", Length(1000.0000001) to "LAY120", Length(49.0) to "LAY107", Length(0.0000004) to "LAY122")) {
            for (block in listOf(image(sizing = RequestedWidthSizing(size)), qr(size = size))) {
                val result = FoundationLayoutEngine(AsciiFixedCellMeasurer()).layout(ThermalDocument(blocks = listOf(
                    Paragraph(Alignment.LEFT, listOf(Text("A"))), block)), input)
                val diagnostic = assertIs<LayoutResult.Failure>(result).diagnostics.single()
                assertEquals(code, diagnostic.code)
                assertEquals(1, diagnostic.sourceBlockIndex)
                assertFalse(diagnostic.message.contains(opaque))
                assertFalse(diagnostic.message.contains("private alt"))
            }
        }
        assertEquals(0.000001, layout(qr(size = Length(0.000001))).blocks.single().bounds.widthMm)
    }
}
