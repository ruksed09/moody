package com.moody.player.ui

import android.net.Uri
import com.moody.player.data.media.AudioItem
import com.moody.player.domain.SortKey
import com.moody.player.domain.SortSpec
import com.moody.player.ui.music.sortSongs
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SongSortTest {
    private fun song(id: Long, title: String, file: String, ms: Long, size: Long, added: Long, modified: Long) = AudioItem(
        Uri.parse("content://media/external/audio/media/$id"), id, title, "A", "B", 1, ms, "Music/", "audio/mpeg", 0, file, added,
        sizeBytes = size, dateModified = modified,
    )

    private val a = song(1, "Zebra", "track10.mp3", 200_000, 5_000, added = 100, modified = 300)
    private val b = song(2, "Apple", "track2.mp3", 100_000, 9_000, added = 200, modified = 0) // no modified date: falls back to added
    private val c = song(3, "Mango", "Track1.mp3", 300_000, 1_000, added = 50, modified = 400)
    private val all = listOf(a, b, c)

    private fun ids(k: SortKey, asc: Boolean) = sortSongs(all, SortSpec(k, asc)).map { it.id }

    @Test fun title() = assertEquals(listOf(2L, 3L, 1L), ids(SortKey.TITLE, true))
    @Test fun fileName_naturalOrder() = assertEquals(listOf(3L, 2L, 1L), ids(SortKey.NAME, true))
    @Test fun dateModified_newestFirst() = assertEquals(listOf(3L, 1L, 2L), ids(SortKey.DATE, false))
    @Test fun size_largestFirst() = assertEquals(listOf(2L, 1L, 3L), ids(SortKey.SIZE, false))
    @Test fun duration_shortestFirst() = assertEquals(listOf(2L, 1L, 3L), ids(SortKey.DURATION, true))
}
