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

/** Checked nonnegative counts, rejected before externally influenced work/allocation. */
internal fun boundedCount(current: Long, extra: Long, maximum: Long, index: Int): Long {
    if (current < 0 || extra < 0 || current > maximum || extra > maximum - current)
        abort("LAY120", "Count exceeds layout resource policy", index)
    return current + extra
}

internal data class TableShape(val rows: Long, val cells: Long, val geometryCost: Long)
internal fun tableShape(table: Table, policy: LayoutResourcePolicy, index: Int): TableShape {
    val columns = table.columns.size
    val bodyRows = table.rows.size
    if (columns <= 0 || bodyRows < 0 || table.header.size != columns)
        abort("LAY105", "Table has an invalid column or header count", index)
    if (columns > policy.maxTableColumns || bodyRows > policy.maxTableRows)
        abort("LAY120", "Table dimensions exceed layout policy", index)
    val rows = boundedCount(bodyRows.toLong(), 1, Long.MAX_VALUE, index)
    if (rows > policy.maxTableCells.toLong() / columns)
        abort("LAY120", "Table cell count exceeds layout policy", index)
    for (row in table.rows) if (row.size != columns)
        abort("LAY105", "Table row has an invalid cell count", index)
    val cells = rows * columns // division above proves multiplication is bounded
    // Counts are <= 20,001 rows, 256 columns and 250,000 cells before these products.
    val geometry = boundedCount(1L + 2L * columns, 2L * rows, Long.MAX_VALUE, index)
    return TableShape(rows, cells, boundedCount(geometry, 2L * cells, Long.MAX_VALUE, index))
}

/** Equal columns and finalized rows; cell text uses the ordinary shared flow exactly once. */
internal class TableBlockFlow(private val constraints: LayoutConstraints, private val textFlow: TextBlockFlow,
    private val charge: (Int, Int) -> Unit) {
    fun layout(table: Table, index: Int, originX: Double, width: Double, initialY: Double): Pair<LogicalTableBlock, Double> {
        val shape = tableShape(table, constraints.resources, index)
        if (shape.geometryCost > constraints.resources.maxItems)
            abort("LAY120", "Table geometry exceeds layout policy", index)
        charge(shape.geometryCost.toInt(), index) // includes column slots, row builders and content bounds
        val em = LogicalGeometry.add(constraints.typography.body.metrics.ascentMm, constraints.typography.body.metrics.descentMm)
        val padding = LogicalGeometry.scale(em, TextLayoutPolicyV1.tableCellPaddingEm)
        if (padding == 0.0) abort("LAY122", "Table padding cannot make coordinate progress", index)
        val twicePadding = LogicalGeometry.add(padding, padding)
        val gap = blockSpacing(constraints, TextLayoutPolicyV1.tableSpacingLineFactor, index)
        val startY = LogicalGeometry.add(initialY, gap)
        var y = startY
        val count = table.columns.size
        val ticks = LogicalGeometry.ticks(width)
        val quotient = ticks / count
        val remainder = ticks % count
        // Slots were reserved above. No data-dependent sizing pass or rectangular scratch matrix.
        val widths = List(count) { position -> LogicalGeometry.millimetres(quotient + if (position < remainder) 1 else 0) }
        for (columnWidth in widths) if (LogicalGeometry.fits(columnWidth, twicePadding))
            abort("LAY107", "Table padding exhausts cell content width", index)
        val rows = mutableListOf<LogicalTableRow>()
        for (rowIndex in 0 until shape.rows.toInt()) {
            val semantic = if (rowIndex == 0) table.header else table.rows[rowIndex - 1]
            val text = mutableListOf<LogicalTextBlock>()
            val contentY = LogicalGeometry.add(y, padding)
            var columnX = originX
            var tallest = 0.0
            for (columnIndex in 0 until count) {
                val cell = semantic[columnIndex]
                val contentWidth = LogicalGeometry.subtract(widths[columnIndex], twicePadding)
                val paragraph = Paragraph(cell.alignment ?: table.columns[columnIndex].alignment, cell.content)
                val (resolved, _) = textFlow.layout(paragraph, index, LogicalGeometry.add(columnX, padding),
                    contentWidth, contentY, cellContent = true)
                tallest = maxOf(tallest, resolved.bounds.heightMm)
                text.add(resolved)
                columnX = LogicalGeometry.add(columnX, widths[columnIndex])
            }
            val height = LogicalGeometry.add(tallest, twicePadding)
            columnX = originX
            val cells = text.mapIndexed { columnIndex, resolved ->
                val cellWidth = widths[columnIndex]
                val cell = LogicalTableCell(columnIndex, resolved.lines.first().alignment,
                    LogicalBounds(columnX, y, cellWidth, height),
                    LogicalBounds(LogicalGeometry.add(columnX, padding), contentY,
                        LogicalGeometry.subtract(cellWidth, twicePadding), tallest), resolved)
                columnX = LogicalGeometry.add(columnX, cellWidth)
                cell
            }
            rows.add(LogicalTableRow(LogicalBounds(originX, y, width, height), rowIndex == 0, SnapshotList(cells)))
            y = LogicalGeometry.add(y, height)
        }
        val height = LogicalGeometry.subtract(y, startY)
        var columnX = originX
        val columns = widths.mapIndexed { position, columnWidth ->
            val column = LogicalTableColumn(LogicalBounds(columnX, startY, columnWidth, height), table.columns[position].alignment)
            columnX = LogicalGeometry.add(columnX, columnWidth)
            column
        }
        return LogicalTableBlock(index, LogicalBounds(originX, startY, width, height), SnapshotList(columns),
            SnapshotList(rows), padding, gap, gap) to LogicalGeometry.add(y, gap)
    }
}
