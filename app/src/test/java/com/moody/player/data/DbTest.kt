package com.moody.player.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.moody.player.data.db.MoodyDb
import com.moody.player.data.db.Playlist
import com.moody.player.data.db.PlaylistEntry
import com.moody.player.data.db.RecentUrl
import com.moody.player.data.db.VaultItem
import com.moody.player.data.db.VaultState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DbTest {
    private lateinit var db: MoodyDb

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MoodyDb::class.java).allowMainThreadQueries().build()
    }

    @After fun close() = db.close()

    @Test fun recentUrls_keepsTenNewestFirst() = runTest {
        repeat(12) { db.recentUrls().add(RecentUrl("https://example.com/$it.m3u8", usedAt = it.toLong())) }
        val recent = db.recentUrls().recent().first()
        assertEquals(10, recent.size)
        assertEquals("https://example.com/11.m3u8", recent.first().url)
        assertEquals("https://example.com/2.m3u8", recent.last().url)
    }

    @Test fun playlistEntries_keepTheirOrder() = runTest {
        val id = db.playlists().create(Playlist(name = "Night drive", createdAt = 1))
        db.playlists().addEntries(listOf(PlaylistEntry(id, "content://a/2", 1), PlaylistEntry(id, "content://a/1", 0), PlaylistEntry(id, "content://a/3", 2)))
        assertEquals(listOf("content://a/1", "content://a/2", "content://a/3"), db.playlists().entries(id).first().map { it.audioUri })
    }

    @Test fun vault_unfinishedReturnsInFlightMoves() = runTest {
        db.vault().upsert(VaultItem("a", "a.bin", "Trip.mp4", "DCIM/Camera/", "video/mp4", 10, 1000, VaultState.COPYING, 1))
        db.vault().upsert(VaultItem("b", "b.bin", "Clip.mp4", "Movies/", "video/mp4", 10, 1000, VaultState.DONE, 2))
        db.vault().upsert(VaultItem("c", "c.bin", "Old.mkv", "Download/", "video/x-matroska", 10, 1000, VaultState.RESTORING, 3))
        assertEquals(setOf("a", "c"), db.vault().unfinished().map { it.id }.toSet())
        assertEquals(listOf("b"), db.vault().done().first().map { it.id })
    }
}
