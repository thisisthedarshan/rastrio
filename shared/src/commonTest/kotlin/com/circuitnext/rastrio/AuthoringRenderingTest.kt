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

package com.circuitnext.rastrio

import com.circuitnext.rastrio.core.document.*
import com.circuitnext.rastrio.core.layout.*
import com.circuitnext.rastrio.core.preview.*
import com.circuitnext.rastrio.core.text.*
import com.circuitnext.rastrio.presentation.*
import kotlin.test.*

class AuthoringRenderingTest {
    @Test fun productionConstructionAndPaintIndexConsumeTheSameCoreObjects() {
        val session = createAuthoringSession()
        session.edit("# Title\n\nBody")
        session.compile()
        val ready = assertIs<MarkdownAuthoringResult.Ready>(session.state.result)
        val firstRun = assertIs<LogicalTextBlock>(ready.preview.blocks.first()).lines.first().runs.first()
        val element = PreviewBands(ready.preview).elements(0).filterIsInstance<PreviewElement.Text>().first()
        assertSame(firstRun, element.run)
        assertEquals(120.0, ready.preview.widthMm)
        assertEquals(Orientation.PORTRAIT, ready.preview.orientation)
    }

    @Test fun qrAndImageRemainOpaquePlaceholdersAtCoreBounds() {
        val session = createAuthoringSession()
        session.compile()
        val constraints = assertIs<MarkdownAuthoringResult.Ready>(session.state.result).logicalDocument.constraints
        val result = FoundationLayoutEngine(AsciiFixedCellMeasurer()).layout(ThermalDocument(blocks = listOf(
            QrCode("opaque payload", Alignment.RIGHT, QrErrorCorrection.HIGH, Length(7.0)),
            Image(ExternalAssetReference("https://must-not-fetch.invalid/photo"), Alignment.CENTER,
                RequestedWidthSizing(Length(8.0)), "Photo description"), Separator)), constraints)
        val preview = assertIs<LayoutResult.Success>(result).document.toLogicalPreview()
        val elements = PreviewBands(preview).elements(0)
        val placeholders = elements.filterIsInstance<PreviewElement.Placeholder>()
        assertEquals(listOf("QR placeholder", "Image placeholder"), placeholders.map { it.label })
        assertSame(preview.blocks[0].bounds, placeholders[0].bounds)
        assertSame(preview.blocks[1].bounds, placeholders[1].bounds)
        assertEquals("Image placeholder: Photo description", placeholders[1].description)
        assertSame(preview.blocks[2].bounds, elements.filterIsInstance<PreviewElement.Fill>().single().bounds)
    }

    @Test fun extremelyTallPlaceholderKeepsABoundedReferenceIndex() {
        val bounds = LogicalBounds(0.0, 0.0, 120.0, 1_000_000_000.0)
        val preview = LogicalPreview(120.0, bounds.heightMm, Orientation.PORTRAIT,
            SnapshotList(listOf(LogicalPlaceholder(0, bounds, PlaceholderKind.IMAGE, Alignment.LEFT))),
            SnapshotList(emptyList()))
        val index = PreviewBands(preview)
        assertTrue(index.count > 1_000_000)
        assertSame(bounds, index.elements(0).single().bounds)
        assertSame(bounds, index.elements(index.count - 1).single().bounds)
    }
}
