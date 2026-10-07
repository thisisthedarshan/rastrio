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

class TablePlaceholderAdmissionTest {
    private val body = ResolvedTypography("table-placeholder", TextStyle(),
        TypographyMetrics(3.0, 1.0, 1.0, 5.0, 3.0), FixedCellGeometry(1.0, 1.0, 4.0))
    private val constraints = LayoutConstraints(Length(48.0), Orientation.PORTRAIT, TypographyContext(body, body, body))
    private fun output(block: DocumentBlock) = assertIs<LayoutResult.Success>(
        FoundationLayoutEngine(AsciiFixedCellMeasurer()).layout(ThermalDocument(blocks = listOf(block)), constraints)).document

    @Test fun headerOnlyTableParticipatesInVerticalFlow() {
        val document = output(Table(listOf(TableColumn(Alignment.LEFT)), listOf(TableCell(emptyList())), emptyList()))
        assertEquals(LogicalBounds(0.0, 2.5, 48.0, 7.0), document.blocks.single().bounds)
        assertEquals(12.0, document.heightMm)
    }

    @Test fun imageHasExactCenteredSquarePlaceholder() {
        val document = output(Image(ExternalAssetReference("https://opaque.invalid/private"), Alignment.CENTER,
            RequestedWidthSizing(Length(20.0))))
        assertEquals(LogicalBounds(14.0, 2.5, 20.0, 20.0), document.blocks.single().bounds)
        assertEquals(25.0, document.heightMm)
    }

    @Test fun qrHasVersionedDefaultSquarePlaceholder() {
        val document = output(QrCode("https://opaque.invalid/private", Alignment.CENTER, QrErrorCorrection.AUTO))
        assertEquals(LogicalBounds(9.0, 2.5, 30.0, 30.0), document.blocks.single().bounds)
        assertEquals(35.0, document.heightMm)
    }
}
