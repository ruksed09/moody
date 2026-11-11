package com.moody.player

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.moody.player.data.db.MoodyDb
import com.moody.player.data.media.MediaRepository
import com.moody.player.data.media.VideoItem
import com.moody.player.data.prefs.SettingsStore
import com.moody.player.data.vault.MoveResult
import com.moody.player.data.vault.VaultManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Hide then unhide a real MediaStore file. The test creates the file itself, so the app owns it and
 * can delete it without the system consent dialog.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class VaultRoundTripTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, MoodyDb::class.java).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val settings = SettingsStore(context)
    private val manager = VaultManager(context, db.vault(), MediaRepository(context, settings, db.vault(), com.moody.player.data.media.SongEdits(context), scope))
    private val name = "moody-vault-test-${System.currentTimeMillis()}.mp4"
    private val bytes = ByteArray(256 * 1024) { (it % 251).toByte() }

    private fun insertTestVideo(): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/MoodyTest/")
        }
        val uri = context.contentResolver.insert(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)!!
        context.contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
        return uri
    }

    private fun findByName(): Uri? = context.contentResolver.query(
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
        arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.RELATIVE_PATH),
        "${MediaStore.MediaColumns.DISPLAY_NAME} = ?", arrayOf(name), null,
    )?.use { c -> if (c.moveToFirst()) Uri.withAppendedPath(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), c.getLong(0).toString()) else null }

    @Test fun hideThenUnhide_keepsTheBytesAndThePath() = runBlocking {
        val uri = insertTestVideo()
        val item = VideoItem(uri, 0, name, "Movies/MoodyTest/", bytes.size.toLong(), 1000, 0, 0, 0, "video/mp4")

        val (ids, failures) = manager.copyIn(listOf(item)) {}
        assertTrue("copy failures: $failures", failures.isEmpty())
        assertEquals(1, ids.size)
        assertEquals(null, manager.deleteDirect(ids))
        manager.finishHide(ids)

        assertEquals("original gone from MediaStore", null, findByName())
        val hidden = db.vault().done().first().single()
        assertTrue("vault copy exists", manager.fileFor(hidden).exists())
        assertEquals(bytes.size.toLong(), manager.fileFor(hidden).length())

        val results = manager.unhide(listOf(hidden)) {}
        val ok = results.single() as MoveResult.Ok
        assertEquals("Movies/MoodyTest/", ok.landedPath)
        val back = findByName()!!
        val restored = context.contentResolver.openInputStream(back)!!.use { it.readBytes() }
        assertTrue("bytes identical", restored.contentEquals(bytes))
        assertFalse("vault copy removed", manager.fileFor(hidden).exists())
        assertTrue(db.vault().done().first().isEmpty())
    }

    @After fun cleanUp() {
        findByName()?.let { runCatching { context.contentResolver.delete(it, null, null) } }
        db.close()
    }
}
