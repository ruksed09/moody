package com.moody.player.domain

/**
 * Columns for a media grid, as VLC does it: as many cells of at least [minCell] as fit, never fewer
 * than [minColumns]. A 360 dp phone gets 2, a 600 dp tablet 3, wider screens 4 or more.
 * All values are in the same unit (dp or px).
 */
fun gridColumns(available: Float, minCell: Float, spacing: Float, minColumns: Int = 2): Int {
    if (available <= 0f || minCell <= 0f) return minColumns
    val fit = ((available + spacing) / (minCell + spacing)).toInt()
    return maxOf(minColumns, fit)
}
