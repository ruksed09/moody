package com.moody.player.data.media

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import java.io.File

/** Reads the Video and Audio collections. Pure queries, no caching. */
class MediaScanner(private val context: Context) {

    private val videoCollection: Uri =
        if (Build.VERSION.SDK_INT >= 29) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    private val audioCollection: Uri =
        if (Build.VERSION.SDK_INT >= 29) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

    fun videos(): List<VideoItem> {
        val cols = buildList {
            add(MediaStore.Video.Media._ID); add(MediaStore.Video.Media.DISPLAY_NAME); add(MediaStore.Video.Media.SIZE)
            add(MediaStore.Video.Media.DURATION); add(MediaStore.Video.Media.DATE_ADDED); add(MediaStore.Video.Media.WIDTH)
            add(MediaStore.Video.Media.HEIGHT); add(MediaStore.Video.Media.MIME_TYPE); add(MediaStore.Video.Media.DATE_MODIFIED)
            if (Build.VERSION.SDK_INT >= 29) add(MediaStore.Video.Media.RELATIVE_PATH) else add(MediaStore.Video.Media.DATA)
        }.toTypedArray()
        val out = ArrayList<VideoItem>()
        query(videoCollection, cols, null)?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val name = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val size = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val dur = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val added = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val w = c.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val h = c.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val mime = c.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val modified = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
            while (c.moveToNext()) {
                val vid = c.getLong(id)
                out += VideoItem(
                    uri = ContentUris.withAppendedId(videoCollection, vid),
                    id = vid,
                    name = c.getString(name) ?: "Untitled",
                    relativePath = relativePath(c),
                    sizeBytes = c.getLong(size),
                    durationMs = c.getLong(dur).takeIf { it > 0 },
                    dateAdded = c.getLong(added),
                    width = c.getInt(w),
                    height = c.getInt(h),
                    mime = c.getString(mime) ?: "video/*",
                    dateModified = c.getLong(modified),
                )
            }
        }
        return out
    }

    fun audio(minDurationMs: Long): List<AudioItem> {
        val cols = buildList {
            add(MediaStore.Audio.Media._ID); add(MediaStore.Audio.Media.TITLE); add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM); add(MediaStore.Audio.Media.ALBUM_ID); add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.MIME_TYPE); add(MediaStore.Audio.Media.TRACK); add(MediaStore.Audio.Media.DISPLAY_NAME)
            add(MediaStore.Audio.Media.DATE_ADDED); add(MediaStore.Audio.Media.DATE_MODIFIED); add(MediaStore.Audio.Media.SIZE)
            if (Build.VERSION.SDK_INT >= 29) add(MediaStore.Audio.Media.RELATIVE_PATH) else add(MediaStore.Audio.Media.DATA)
        }.toTypedArray()
        val out = ArrayList<AudioItem>()
        val durationFilter = if (minDurationMs <= 0) "(${MediaStore.Audio.Media.DURATION} >= 0 OR ${MediaStore.Audio.Media.DURATION} IS NULL)"
        else "${MediaStore.Audio.Media.DURATION} >= $minDurationMs"
        query(audioCollection, cols, durationFilter)?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val title = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val album = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val dur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val mime = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val track = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val file = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val added = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val modified = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            val size = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            while (c.moveToNext()) {
                val aid = c.getLong(id)
                val fileName = c.getString(file) ?: ""
                out += AudioItem(
                    uri = ContentUris.withAppendedId(audioCollection, aid),
                    id = aid,
                    title = c.getString(title)?.takeIf { it.isNotBlank() } ?: fileName.substringBeforeLast('.'),
                    artist = c.getString(artist)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Unknown artist",
                    album = c.getString(album)?.takeIf { it.isNotBlank() } ?: "Unknown album",
                    albumId = c.getLong(albumId),
                    durationMs = c.getLong(dur),
                    relativePath = relativePath(c),
                    mime = c.getString(mime) ?: "audio/*",
                    trackNo = c.getInt(track) % 1000,
                    fileName = fileName,
                    dateAdded = c.getLong(added),
                    sizeBytes = c.getLong(size),
                    dateModified = c.getLong(modified),
                )
            }
        }
        return out
    }

    private fun query(uri: Uri, cols: Array<String>, selection: String?): Cursor? =
        try { context.contentResolver.query(uri, cols, selection, null, null) } catch (e: SecurityException) { null }

    @Suppress("DEPRECATION")
    private fun relativePath(c: Cursor): String {
        if (Build.VERSION.SDK_INT >= 29) {
            return c.getString(c.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)) ?: ""
        }
        val data = c.getString(c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)) ?: return ""
        val root = android.os.Environment.getExternalStorageDirectory().absolutePath
        val parent = File(data).parent ?: return ""
        return parent.removePrefix(root).trimStart('/').let { if (it.isEmpty()) "" else "$it/" }
    }

    companion object {
        fun albumArtUri(albumId: Long): Uri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
    }
}

/** What Moody may read: everything, a user-picked subset (Android 14+), or nothing. */
fun accessLevel(context: Context): AccessLevel {
    fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    return when {
        Build.VERSION.SDK_INT >= 33 && granted(Manifest.permission.READ_MEDIA_VIDEO) -> AccessLevel.FULL
        Build.VERSION.SDK_INT >= 34 && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> AccessLevel.PARTIAL
        Build.VERSION.SDK_INT < 33 && granted(Manifest.permission.READ_EXTERNAL_STORAGE) -> AccessLevel.FULL
        else -> AccessLevel.NONE
    }
}

fun hasAudioAccess(context: Context): Boolean {
    val p = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    return ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
}

/** Permissions to request for media. */
fun mediaPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
    Build.VERSION.SDK_INT >= 29 -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    // Android 9 and below also need write access to delete originals when hiding and to restore files.
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
}
