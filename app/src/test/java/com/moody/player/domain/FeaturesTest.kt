package com.moody.player.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsTest {
    @Test fun parsesTimedLines_sorted_withMultipleTags() {
        val l = parseLrc("[ti:Song]\n[00:12.30][01:40.00]Chorus\n[00:05.5]Intro\n\n[00:20.123]Verse")
        assertTrue(l.synced)
        assertEquals(listOf(5_500L, 12_300L, 20_123L, 100_000L), l.lines.map { it.timeMs })
        assertEquals(listOf("Intro", "Chorus", "Verse", "Chorus"), l.lines.map { it.text })
    }

    @Test fun offset_movesLinesEarlier() {
        val l = parseLrc("[offset:+500]\n[00:10.00]A")
        assertEquals(9_500L, l.lines.single().timeMs)
    }

    @Test fun plainText_isUnsynced() {
        val l = parseLrc("First line\nSecond line")
        assertFalse(l.synced)
        assertEquals(2, l.lines.size)
        assertEquals(-1, l.indexAt(5_000))
    }

    @Test fun indexAt_findsCurrentLine() {
        val l = parseLrc("[00:01.00]a\n[00:05.00]b\n[00:09.00]c")
        assertEquals(-1, l.indexAt(500))
        assertEquals(0, l.indexAt(1_000))
        assertEquals(1, l.indexAt(8_999))
        assertEquals(2, l.indexAt(60_000))
    }
}

class SmartResumeTest {
    @Test fun rewinds_butNotBelowZero() {
        assertEquals(55_000L, smartResume(60_000, 5))
        assertEquals(0L, smartResume(3_000, 5))
    }

    @Test fun startOrOff_unchanged() {
        assertEquals(0L, smartResume(0, 5))
        assertEquals(60_000L, smartResume(60_000, 0))
    }
}

class StatsTest {
    private fun row(day: Long, hour: Int, uri: String, kind: String, ms: Long, plays: Int = 1) = StatRow(day, hour, uri, kind, uri.uppercase(), ms, plays)

    @Test fun summarize_totalsDaysTopAndBusiestHour() {
        val rows = listOf(
            row(10, 21, "a", StatKind.MUSIC, 600_000), row(10, 21, "b", StatKind.MUSIC, 120_000),
            row(11, 9, "v", StatKind.VIDEO, 1_800_000), row(11, 22, "a", StatKind.MUSIC, 300_000),
            row(3, 9, "old", StatKind.VIDEO, 9_000_000),
        )
        val s = summarize(rows, 5, 11, visible = setOf("a", "b", "v"))
        assertEquals(1_800_000L, s.videoMs)
        assertEquals(1_020_000L, s.musicMs)
        assertEquals(7, s.days.size)
        assertEquals("a", s.topSongs.first().uri)
        assertEquals(2, s.topSongs.first().plays)
        assertEquals(9, s.busiestHour)
    }

    @Test fun summarize_hidesFilesNoLongerVisible() {
        val s = summarize(listOf(row(1, 1, "secret", StatKind.VIDEO, 60_000)), 1, 1, visible = emptySet())
        assertTrue(s.topVideos.isEmpty())
        assertEquals(60_000L, s.videoMs)
    }

    @Test fun formatting() {
        assertEquals("0 min", formatSpent(0))
        assertEquals("45 min", formatSpent(45 * 60_000L))
        assertEquals("2 h", formatSpent(120 * 60_000L))
        assertEquals("3 h 20 min", formatSpent(200 * 60_000L))
        assertEquals("12 AM", hourLabel(0))
        assertEquals("9 PM", hourLabel(21))
    }
}

class MixesTest {
    private data class S(val id: String, val ms: Long)
    private val songs = (1..8).map { S("s$it", if (it <= 3) 6 * 60_000L else 3 * 60_000L) }
    private fun mixes(rows: List<StatRow>, fav: Set<String> = emptySet(), hour: Int = 22) =
        buildMixes(songs, { it.id }, { it.ms }, rows, fav, hour, today = 100)

    @Test fun noHistory_onlyFocusAndLoved() {
        val m = mixes(emptyList(), fav = setOf("s4", "s5", "s6"))
        assertEquals(listOf("focus", "loved"), m.map { it.id })
        assertTrue(m.first { it.id == "loved" }.shuffle)
    }

    @Test fun history_buildsNightRepeatAndNow() {
        val rows = listOf("s4", "s5", "s6").flatMap { u ->
            listOf(StatRow(99, 23, u, StatKind.MUSIC, "t", 200_000, 2), StatRow(98, 22, u, StatKind.MUSIC, "t", 100_000, 1))
        }
        val ids = mixes(rows).map { it.id }
        assertTrue("now" in ids)
        assertTrue("repeat" in ids)
        assertTrue("night" in ids)
        assertTrue("rediscover" in ids)
        val rediscover = mixes(rows).first { it.id == "rediscover" }.songs.map { it.id }
        assertFalse("s4" in rediscover)
    }

    @Test fun oldHistory_isIgnored() {
        val rows = listOf("s4", "s5", "s6").map { StatRow(10, 23, it, StatKind.MUSIC, "t", 200_000, 3) }
        assertNull(mixes(rows).firstOrNull { it.id == "repeat" })
    }
}
