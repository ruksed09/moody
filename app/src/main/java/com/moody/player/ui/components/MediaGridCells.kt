package com.moody.player.ui.components

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moody.player.domain.gridColumns

/** Grid columns for thumbnails and covers: at least two on a phone, more as the screen allows. */
class MediaGridCells(private val minCell: Dp = 150.dp, private val minColumns: Int = 2) : GridCells {
    override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> {
        val n = gridColumns(availableSize.toFloat(), minCell.toPx(), spacing.toFloat(), minColumns)
        val total = (availableSize - spacing * (n - 1)).coerceAtLeast(0)
        val base = total / n
        val rest = total % n
        return List(n) { base + if (it < rest) 1 else 0 }
    }

    override fun equals(other: Any?) = other is MediaGridCells && other.minCell == minCell && other.minColumns == minColumns
    override fun hashCode() = minCell.hashCode() * 31 + minColumns
}
