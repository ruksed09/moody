package com.moody.player.ui

import com.moody.player.data.db.Bookmark
import com.moody.player.ui.player.nearestBookmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NearestBookmarkTest {
    private fun mark(at: Long, end: Long? = null) = Bookmark(uri = "content://v/1", positionMs = at, createdAt = 0, endMs = end)
    private val marks = listOf(mark(60_000), mark(10_000, 20_000), mark(120_000))

    @Test fun forward_takesTheNextOne() {
        assertEquals(60_000L, nearestBookmark(marks, 30_000, right = true)?.positionMs)
    }

    @Test fun back_takesTheOneJustPassed() {
        assertEquals(60_000L, nearestBookmark(marks, 90_000, right = false)?.positionMs)
    }

    @Test fun back_skipsOneWeAreSittingOn() {
        // Just landed on 60 s: going back should offer the one before it.
        assertEquals(10_000L, nearestBookmark(marks, 60_400, right = false)?.positionMs)
    }

    @Test fun noneThatWay_fallsBackToTheOtherSide() {
        assertEquals(120_000L, nearestBookmark(marks, 200_000, right = true)?.positionMs)
        assertEquals(10_000L, nearestBookmark(marks, 0, right = false)?.positionMs)
    }

    @Test fun noBookmarks_noTip() {
        assertNull(nearestBookmark(emptyList(), 5_000, right = true))
    }
}
