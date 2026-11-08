package com.moody.player.data

import android.net.Uri
import com.moody.player.data.media.VideoItem
import com.moody.player.data.media.groupIntoFolders
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FoldersTest {
    private fun v(id: Long, path: String, size: Long, added: Long) = VideoItem(
        uri = Uri.parse("content://media/external/video/media/$id"), id = id, name = "v$id.mp4", relativePath = path,
        sizeBytes = size, durationMs = 60_000, dateAdded = added, width = 1920, height = 1080, mime = "video/mp4",
    )

    private val videos = listOf(v(1, "DCIM/Camera/", 100, 10), v(2, "DCIM/Camera/", 50, 30), v(3, "Movies/Anime/", 400, 20))

    @Test fun groupsByPath_withCountsAndTotals() {
        val folders = groupIntoFolders(videos, hidden = emptySet())
        val cam = folders.first { it.path == "DCIM/Camera/" }
        assertEquals(2, cam.count)
        assertEquals(150L, cam.totalBytes)
        assertEquals("Camera", cam.name)
        assertEquals(2L, cam.latest?.id)
    }

    @Test fun hiddenFolders_areExcluded() {
        val folders = groupIntoFolders(videos, hidden = setOf("Movies/Anime/"))
        assertEquals(listOf("DCIM/Camera/"), folders.map { it.path })
    }

    @Test fun orderedByNewestVideoFirst() {
        assertEquals(listOf("DCIM/Camera/", "Movies/Anime/"), groupIntoFolders(videos, emptySet()).map { it.path })
    }

    @Test fun rootFiles_getReadableName() {
        val f = groupIntoFolders(listOf(v(9, "", 1, 1)), emptySet()).single()
        assertEquals("Internal storage", f.name)
    }
}
