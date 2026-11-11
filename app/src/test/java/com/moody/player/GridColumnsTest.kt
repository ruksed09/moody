package com.moody.player

import com.moody.player.domain.gridColumns
import org.junit.Assert.assertEquals
import org.junit.Test

class GridColumnsTest {
    // Content width = screen width minus 16 dp padding on each side; 12 dp gaps; 150 dp minimum cell.
    private fun cols(screen: Int) = gridColumns(screen - 32f, 150f, 12f)

    @Test fun phonesShowTwo() {
        assertEquals(2, cols(320))
        assertEquals(2, cols(360))
        assertEquals(2, cols(411))
    }

    @Test fun tabletsShowThreeOrMore() {
        assertEquals(3, cols(600))
        assertEquals(5, cols(840))
        assertEquals(6, cols(1000))
    }

    @Test fun neverBelowTheMinimum() {
        assertEquals(2, gridColumns(0f, 150f, 12f))
        assertEquals(2, gridColumns(100f, 150f, 12f))
    }
}
