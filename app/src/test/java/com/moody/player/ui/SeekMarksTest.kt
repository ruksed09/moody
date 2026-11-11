package com.moody.player.ui

import com.moody.player.ui.player.SeekMark
import com.moody.player.ui.player.activeRange
import com.moody.player.ui.player.packLanes
import com.moody.player.ui.player.scrubLabel
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeekMarksTest {
    // The example from the question: A 1:12:23-1:13:45, B 1:12:56-1:15:34, C 1:12:56-1:13:23.
    private val a = SeekMark(4_343_000, 4_425_000, "A")
    private val b = SeekMark(4_376_000, 4_534_000, "B")
    private val c = SeekMark(4_376_000, 4_403_000, "C")

    @Test fun overlapping_getSeparateLanes() {
        assertArrayEquals(intArrayOf(0, 1), packLanes(listOf(a, b), lanes = 2, minSpanMs = 0))
    }

    @Test fun apart_shareALane() {
        val later = SeekMark(5_000_000, 5_100_000, "D")
        assertArrayEquals(intArrayOf(0, 0), packLanes(listOf(a, later), lanes = 2, minSpanMs = 0))
    }

    @Test fun tooShortToSeeApart_stillGetSeparateLanes() {
        // Drawn at least 2 minutes wide on a long video, so these two would touch.
        val x = SeekMark(1_000_000, 1_010_000, "x")
        val y = SeekMark(1_060_000, 1_070_000, "y")
        assertArrayEquals(intArrayOf(0, 1), packLanes(listOf(x, y), lanes = 2, minSpanMs = 120_000))
    }

    @Test fun moreThanLanes_overflow() {
        assertArrayEquals(intArrayOf(0, 1, -1), packLanes(listOf(a, b, c), lanes = 2, minSpanMs = 0))
    }

    @Test fun active_isTheOneEnteredLast_shorterOnATie() {
        assertEquals("A", activeRange(listOf(a, b, c), 4_360_000)?.label)
        assertEquals("C", activeRange(listOf(a, b, c), 4_380_000)?.label) // B and C start together; C is shorter
        assertEquals("B", activeRange(listOf(a, b, c), 4_450_000)?.label)
        assertNull(activeRange(listOf(a, b, c), 4_000_000))
    }

    @Test fun label_namesTheRangeAndCountsTheRest() {
        assertEquals("1:13:00 · C +2", scrubLabel("1:13:00", listOf(a, b, c), 4_380_000, null))
        assertEquals("1:14:00 · B", scrubLabel("1:14:00", listOf(a, b, c), 4_440_000, null))
        assertEquals("0:10", scrubLabel("0:10", listOf(a, b, c), 10_000, null))
    }

    @Test fun label_prefersAMomentUnderTheFinger_andCutsLongNames() {
        val moment = SeekMark(4_380_000, null, "A very long bookmark name that goes on")
        assertEquals("1:13:00 · A very long bookmark na…", scrubLabel("1:13:00", listOf(a, b), 4_380_000, moment))
    }
}
