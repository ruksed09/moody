package com.moody.player.ui

import android.net.Uri
import com.moody.player.data.media.AudioItem
import com.moody.player.data.prefs.Settings
import com.moody.player.domain.SortKey
import com.moody.player.domain.SortSpec
import com.moody.player.ui.music.groupMusic
import com.moody.player.ui.music.sortSongFolders
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MusicGroupingTest {
    private fun song(id: Long, title: String, artist: String, album: String, albumId: Long, track: Int, path: String = "Music/") = AudioItem(
        Uri.parse("content://media/external/audio/media/$id"), id, title, artist, album, albumId, 200_000, path, "audio/mpeg", track, "$title.mp3", id,
    )

    private val songs = listOf(
        song(1, "Track 10", "Nils Frahm", "Spaces", 7, 10),
        song(2, "Says", "Nils Frahm", "Spaces", 7, 2),
        song(3, "Ambre", "Nils Frahm", "Wintermusik", 8, 1, "Music/Frahm/"),
        song(4, "Avril 14th", "Aphex Twin", "Drukqs", 9, 4),
    )

    @Test fun albums_sortTracksByNumber() {
        val spaces = groupMusic(songs).first.first { it.name == "Spaces" }
        assertEquals(listOf("Says", "Track 10"), spaces.songs.map { it.title })
        assertEquals("Nils Frahm", spaces.artist)
    }

    @Test fun artists_countDistinctAlbums_sortedByName() {
        val artists = groupMusic(songs).second
        assertEquals(listOf("Aphex Twin", "Nils Frahm"), artists.map { it.name })
        assertEquals(2, artists.last().albumCount)
        assertEquals(3, artists.last().songs.size)
    }

    @Test fun folders_groupByPath() {
        val folders = groupMusic(songs).third
        assertEquals(listOf("Frahm", "Music"), folders.map { it.name })
    }

    @Test fun folders_defaultSortKeepsNameOrder() {
        val folders = groupMusic(songs).third
        assertEquals(folders, sortSongFolders(folders, Settings().songFolderSort))
    }

    @Test fun folders_sortByCountAndNewestSong() {
        val folders = groupMusic(songs).third
        assertEquals(listOf("Music", "Frahm"), sortSongFolders(folders, SortSpec(SortKey.COUNT, false)).map { it.name })
        assertEquals(listOf("Music", "Frahm"), sortSongFolders(folders, SortSpec(SortKey.DATE, false)).map { it.name })
        assertEquals(listOf("Frahm", "Music"), sortSongFolders(folders, SortSpec(SortKey.DATE, true)).map { it.name })
    }
}
