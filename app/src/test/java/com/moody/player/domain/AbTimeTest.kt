package com.moody.player.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AbTimeTest {
    @Test fun format_showsTenths() {
        assertEquals("0:00.0", formatClock(0))
        assertEquals("16:45.0", formatClock(1_005_000))
        assertEquals("1:02:03.4", formatClock(3_723_450))
    }

    @Test fun parse_acceptsCommonShapes() {
        assertEquals(1_005_000L, parseAbTime("16:45"))
        assertEquals(1_005_300L, parseAbTime("16:45.3"))
        assertEquals(1_005_250L, parseAbTime(" 16:45.25 "))
        assertEquals(3_723_400L, parseAbTime("1:02:03.4"))
        assertEquals(75_500L, parseAbTime("75.5"))
    }

    @Test fun parse_roundTripsFormat() {
        listOf(0L, 100L, 59_900L, 1_005_000L, 3_723_400L).forEach { assertEquals(it, parseAbTime(formatClock(it))) }
    }

    @Test fun parse_rejectsNonsense() {
        listOf("", "abc", "1:60", "1:61:00", "1::2", "1:2:3:4", "16:45.", ":30", "-5").forEach { assertNull(it, parseAbTime(it)) }
    }

    @Test fun rules_forA() {
        assertNull(abError(true, 0, null, null, 60_000))
        assertEquals("A can't be before 0:00", abError(true, -1, null, null, 60_000))
        assertEquals("A must be at least 0.5 s before B", abError(true, 9_600, 5_000, 10_000, 60_000))
        assertEquals("A must be at least 0.5 s before the end", abError(true, 59_800, null, null, 60_000))
    }

    @Test fun rules_forB() {
        assertNull(abError(false, 60_000, 5_000, null, 60_000))
        assertEquals("B can't be past the end (1:00)", abError(false, 60_001, 5_000, null, 60_000))
        assertEquals("B must be at least 0.5 s after A", abError(false, 5_400, 5_000, null, 60_000))
        assertEquals("The video's length isn't known yet", abError(false, 5_400, 5_000, null, 0))
    }
}
