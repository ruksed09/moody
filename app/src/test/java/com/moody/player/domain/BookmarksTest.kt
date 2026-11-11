package com.moody.player.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarksTest {
    @Test fun keepsAllowedCharacters() {
        assertEquals("Intro \u2014 part-2/3: a;b", BookmarkNames.clean("Intro \u2014 part-2/3: a;b"))
    }

    @Test fun dropsEverythingElse() {
        assertEquals("abc12", BookmarkNames.clean("a<b>c\"1'2{}|\\%&*!?.,_=+#@"))
        assertEquals("", BookmarkNames.clean("\n\t<>"))
        assertFalse(BookmarkNames.allowed('\u2013')) // en dash is not the em dash
        assertFalse(BookmarkNames.allowed('.'))
        assertTrue(BookmarkNames.allowed('\u00e9'))
    }

    @Test fun collapsesAndTrimsSpaces() {
        assertEquals("a b", BookmarkNames.clean("   a    b   "))
        assertEquals("a ", BookmarkNames.filter("  a  "))
    }

    @Test fun capsAt40() {
        assertEquals(40, BookmarkNames.clean("x".repeat(100)).length)
    }

    @Test fun labels() {
        assertEquals("1:05", bookmarkLabel(65_000, null))
        assertEquals("1:05 \u2013 1:30", bookmarkLabel(65_000, 90_000))
    }
}
