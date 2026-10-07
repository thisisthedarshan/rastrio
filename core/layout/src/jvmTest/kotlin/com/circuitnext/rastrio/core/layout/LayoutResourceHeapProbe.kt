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

/** Dedicated 128 MiB JVM process; ordinary common tests rely on deterministic count boundaries. */
object LayoutResourceHeapProbe {
    @JvmStatic
    fun main(args: Array<String>) {
        val body = ResolvedTypography("heap-fixture", TextStyle(), TypographyMetrics(3.0, 1.0, 1.0, 5.0, 3.0), FixedCellGeometry(1.0, 1.0, 4.0))
        val input = LayoutConstraints(Length(0.000001), Orientation.PORTRAIT, TypographyContext(body, body, body))
        val engine = FoundationLayoutEngine(AsciiFixedCellMeasurer())
        fun rejected(name: String, document: ThermalDocument, constraints: LayoutConstraints = input) {
            val result = engine.layout(document, constraints)
            check(result is LayoutResult.Failure && result.diagnostics.single().code == "LAY120")
            println("$name: LAY120")
        }
        val text = "A".repeat(65536)
        rejected("maximum ASCII paragraphs", ThermalDocument(blocks = List(8) { Paragraph(Alignment.LEFT, listOf(Text(text))) }))
        rejected("explicit break markers", ThermalDocument(blocks = listOf(Paragraph(Alignment.LEFT, List(300000) { LineBreak }))))
        val zero = body.copy(fixedCell = FixedCellGeometry(0.0000004, 1.0, 4.0))
        rejected("zero-advance styled spans", ThermalDocument(blocks = listOf(Paragraph(Alignment.LEFT, List(100000) { i ->
            if (i % 2 == 0) Text("A") else Strong(listOf(Text("A")))
        }))), input.copy(typography = TypographyContext(zero, zero, zero)))
        val structured = input.copy(canvasWidth = Length(1000.0))
        val emptyParagraph = Paragraph(Alignment.LEFT, emptyList())
        rejected("many checklist items with empty paragraphs", ThermalDocument(blocks = listOf(Checklist(List(40000) { ChecklistItem(false, listOf(emptyParagraph)) }))), structured)
        rejected("tab-heavy code", ThermalDocument(blocks = listOf(CodeBlock("\t".repeat(65536)))), structured)
        rejected("many explicit code lines", ThermalDocument(blocks = listOf(CodeBlock("\n".repeat(65536)))), structured)
        rejected("narrow code amplification", ThermalDocument(blocks = listOf(CodeBlock(text))))
        rejected("large marker columns", ThermalDocument(blocks = listOf(OrderedList(1,
            List(30000) { ListItem(listOf(Paragraph(Alignment.LEFT, listOf(Text("A"))))) }))), structured)
        val emptyCell = TableCell(emptyList())
        val columns = List(10) { TableColumn(Alignment.LEFT) }
        val emptyRow = List(10) { emptyCell }
        rejected("many empty table cells", ThermalDocument(blocks = listOf(Table(columns, emptyRow, List(10000) { emptyRow }))), structured)
        rejected("narrow table cell wrapping", ThermalDocument(blocks = listOf(Table(listOf(TableColumn(Alignment.LEFT)),
            listOf(TableCell(listOf(Text(text)))), emptyList()))), input.copy(canvasWidth = Length(2.000001)))
        rejected("styled table cell amplification", ThermalDocument(blocks = listOf(Table(listOf(TableColumn(Alignment.LEFT)),
            listOf(TableCell(List(100000) { i -> if (i % 2 == 0) Text("A") else Strong(listOf(Text("B"))) })), emptyList()))), structured)
        val manyColumns = List(256) { TableColumn(Alignment.LEFT) }
        val wideRow = List(256) { emptyCell }
        rejected("wide table geometry amplification", ThermalDocument(blocks = listOf(Table(manyColumns, wideRow, List(500) { wideRow }))), structured)
        val image = Image(ExternalAssetReference("opaque"), Alignment.LEFT, AutoSizing)
        val qr = QrCode("opaque", Alignment.LEFT, QrErrorCorrection.AUTO)
        for ((name, placeholder) in listOf("images" to image, "QR placeholders" to qr)) {
            val document = ThermalDocument(blocks = List(100000) { placeholder })
            val normalPlaceholders = engine.layout(document, structured)
            check(normalPlaceholders is LayoutResult.Success && normalPlaceholders.document.blocks.size == 100000)
            println("100000 $name: success at maxItems")
            rejected("$name one over runtime item budget", document,
                structured.copy(resources = structured.resources.copy(maxItems = 99999)))
        }
        val normal = engine.layout(ThermalDocument(blocks = listOf(Paragraph(Alignment.LEFT, listOf(Text(text))))), input.copy(canvasWidth = Length(1000.0)))
        check(normal is LayoutResult.Success && (normal.document.blocks.single() as LogicalTextBlock).lines.size == 66)
        println("normal maximum ASCII paragraph: 66 lines")
    }
}
