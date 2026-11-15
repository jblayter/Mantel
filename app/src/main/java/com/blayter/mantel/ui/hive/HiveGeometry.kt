package com.blayter.mantel.ui.hive

import kotlin.math.ceil
import kotlin.math.min

/** How many cells go in a row, and how much every cell is scaled down to fit. */
data class HiveGeometry(val perRow: Int, val scale: Float)

/**
 * Fits [count] cells into [availWidth] x [availHeight] without scrolling.
 * Up to three rows of [basePerRow] it is the layout from DESIGN.md at full
 * size; beyond that it picks the row length that keeps cells largest and
 * shrinks them proportionally. All lengths are in the same unit.
 */
fun hiveGeometry(
    count: Int,
    cellWidth: Float,
    cellHeight: Float,
    gap: Float,
    rowOverlap: Float,
    oddRowOffset: Float,
    basePerRow: Int,
    availWidth: Float,
    availHeight: Float,
): HiveGeometry {
    var best = HiveGeometry(basePerRow, 0f)
    for (perRow in basePerRow..maxOf(basePerRow, count)) {
        val rows = maxOf(1, ceil(count / perRow.toFloat()).toInt())
        val width = perRow * cellWidth + (perRow - 1) * gap + if (rows > 1) oddRowOffset else 0f
        val height = cellHeight + (rows - 1) * (cellHeight + rowOverlap)
        val scale = min(1f, min(availWidth / width, availHeight / height))
        if (scale > best.scale) best = HiveGeometry(perRow, scale)
    }
    return best
}
