package com.blayter.mantel.ui.hive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import com.blayter.mantel.domain.ContainerStatus
import com.blayter.mantel.ui.theme.HiveTokens

/** Height of the standard three rows: one cell plus two overlapped rows. */
val HiveHeight: Dp = HiveTokens.Hex.height + (HiveTokens.Hex.height + HiveTokens.Hex.rowOverlap) * 2

/**
 * The honeycomb. Cells are placed straight from the hex tokens: rows overlap
 * and odd rows shift right. With more cells than three rows hold, every cell
 * shrinks by one factor (applied through density, so text scales too); the
 * grid never scrolls.
 */
@Composable
fun HiveGrid(containers: List<ContainerStatus>, modifier: Modifier = Modifier, stale: Boolean = false) {
    val hex = HiveTokens.Hex
    val geometry = hiveGeometry(
        count = containers.size,
        cellWidth = hex.width.value,
        cellHeight = hex.height.value,
        gap = hex.gap.value,
        rowOverlap = hex.rowOverlap.value,
        oddRowOffset = hex.oddRowOffset.value,
        basePerRow = hex.perRow,
        availWidth = HiveTokens.Screen.hiveColumnWidth.value,
        availHeight = HiveHeight.value,
    )
    Box(modifier.size(HiveTokens.Screen.hiveColumnWidth, HiveHeight)) {
        val outer = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(outer.density * geometry.scale, outer.fontScale)) {
            Layout(content = { containers.forEach { key(it.name) { HexCell(it, stale = stale) } } }) { measurables, constraints ->
                val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
                val stepX = (hex.width + hex.gap).roundToPx()
                val stepY = (hex.height + hex.rowOverlap).roundToPx()
                val oddOffset = hex.oddRowOffset.roundToPx()
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeables.forEachIndexed { i, p ->
                        val row = i / geometry.perRow
                        val col = i % geometry.perRow
                        p.place(col * stepX + if (row % 2 == 1) oddOffset else 0, row * stepY)
                    }
                }
            }
        }
    }
}

/** [rows] rows of [perRow] empty cells on the same honeycomb spacing, for screens with no containers to show. */
@Composable
fun EmptyHive(rows: Int, perRow: Int, modifier: Modifier = Modifier) {
    val hex = HiveTokens.Hex
    val stepX = hex.width + hex.gap
    val stepY = hex.height + hex.rowOverlap
    Box(
        modifier.size(
            width = stepX * perRow - hex.gap + if (rows > 1) hex.oddRowOffset else Dp(0f),
            height = hex.height + stepY * (rows - 1),
        ),
    ) {
        for (row in 0 until rows) {
            for (col in 0 until perRow) {
                EmptyHexCell(Modifier.offset(x = stepX * col + if (row % 2 == 1) hex.oddRowOffset else Dp(0f), y = stepY * row))
            }
        }
    }
}
