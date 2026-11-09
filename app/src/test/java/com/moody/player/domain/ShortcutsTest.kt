package com.moody.player.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ShortcutsTest {
    private val sel = listOf(PlayerShortcut.LOOP, PlayerShortcut.MUTE)

    @Test fun add_appends() = assertEquals(listOf(PlayerShortcut.LOOP, PlayerShortcut.MUTE, PlayerShortcut.SCREENSHOT), addShortcut(sel, PlayerShortcut.SCREENSHOT))
    @Test fun add_ignoresDuplicates() = assertEquals(sel, addShortcut(sel, PlayerShortcut.LOOP))
    @Test fun add_respectsLimit() {
        val full = PlayerShortcut.entries.take(MAX_SHORTCUTS)
        assertEquals(full, addShortcut(full, PlayerShortcut.entries.last()))
    }
    @Test fun remove_drops() = assertEquals(listOf(PlayerShortcut.MUTE), removeShortcut(sel, PlayerShortcut.LOOP))
    @Test fun move_reorders() = assertEquals(listOf(PlayerShortcut.MUTE, PlayerShortcut.LOOP), moveShortcut(sel, 0, 1))
    @Test fun move_outOfRange_isIgnored() = assertEquals(sel, moveShortcut(sel, 0, 5))

    @Test fun storage_roundTrip_skipsUnknownAndDuplicates() {
        assertEquals("LOOP,MUTE", encodeShortcuts(sel))
        assertEquals(sel, decodeShortcuts("LOOP,MUTE,LOOP,BOGUS"))
        assertEquals(emptyList<PlayerShortcut>(), decodeShortcuts(""))
    }

    @Test fun savedBackgroundPlayFromOlderVersionIsDropped() {
        // 1.1.0 could save BACKGROUND_PLAY; it is now a Settings toggle, not a shortcut.
        assertEquals(listOf(PlayerShortcut.LOOP, PlayerShortcut.ROTATE), decodeShortcuts("LOOP,BACKGROUND_PLAY,ROTATE"))
    }
}
