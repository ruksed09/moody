package com.moody.player.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainTest {
    @Test fun formatDuration_unknownOrZero_showsDashes() {
        assertEquals("--:--", formatDuration(null))
        assertEquals("--:--", formatDuration(0))
        assertEquals("--:--", formatDuration(-5))
    }

    @Test fun formatDuration_underAnHour_isMinutesSeconds() {
        assertEquals("1:05", formatDuration(65_000))
        assertEquals("0:09", formatDuration(9_400))
    }

    @Test fun formatDuration_overAnHour_hasHours() {
        assertEquals("1:02:05", formatDuration(3_725_000))
    }

    private data class F(val name: String, val date: Long = 0, val size: Long = 0, val dur: Long = 0)

    private fun List<F>.sorted(spec: SortSpec) = sortedBy(spec, { it.name }, { it.date }, { it.size }, { it.dur })

    @Test fun sort_byName_isNaturalAndCaseInsensitive() {
        val out = listOf(F("ep10"), F("ep2"), F("Ep1")).sorted(SortSpec(SortKey.NAME, true)).map { it.name }
        assertEquals(listOf("Ep1", "ep2", "ep10"), out)
    }

    @Test fun sort_bySizeDescending() {
        val out = listOf(F("a", size = 1), F("b", size = 3), F("c", size = 2)).sorted(SortSpec(SortKey.SIZE, false)).map { it.name }
        assertEquals(listOf("b", "c", "a"), out)
    }

    @Test fun seekDelta_halfWidthOnLongVideo_isHalfOfThreeMinutes() {
        assertEquals(90_000L, seekDeltaMs(540f, 1080f, 7_200_000))
    }

    @Test fun seekDelta_shortVideo_scalesToDuration() {
        assertEquals(-30_000L, seekDeltaMs(-540f, 1080f, 60_000))
    }

    @Test fun levelDelta_swipeUpIsPositive() {
        assertEquals(0.5f, levelDelta(-500f, 1000f), 0.0001f)
        assertEquals(-0.25f, levelDelta(250f, 1000f), 0.0001f)
    }

    @Test fun resumePosition_rules() {
        assertEquals(0L, resumePosition(3_000, 600_000))
        assertEquals(0L, resumePosition(590_000, 600_000))
        assertEquals(120_000L, resumePosition(120_000, 600_000))
        assertEquals(0L, resumePosition(120_000, 0))
    }

    @Test fun isWatched_past95Percent() {
        assertTrue(isWatched(571_000, 600_000))
        assertFalse(isWatched(500_000, 600_000))
        assertFalse(isWatched(10, 0))
    }

    @Test fun formatDelta_signed() {
        assertEquals("+1:30", formatDelta(90_000))
        assertEquals("-0:10", formatDelta(-10_400))
        assertEquals("+0:00", formatDelta(0))
    }

    @Test fun formatBytes_units() {
        assertEquals("12 KB", formatBytes(12 * 1024))
        assertEquals("1.5 GB", formatBytes(1536L * 1024 * 1024))
    }
}
