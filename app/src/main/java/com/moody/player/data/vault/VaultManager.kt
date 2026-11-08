package com.moody.player.data.vault

import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.util.Size
import com.moody.player.data.db.VaultDao
import com.moody.player.data.db.VaultItem
import com.moody.player.data.db.VaultState
import com.moody.player.data.media.MediaRepository
import com.moody.player.data.media.VideoItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

sealed interface RestoreTarget {
    val relativePath: String
    data class Video(override val relativePath: String) : RestoreTarget
    data class Downloads(override val relativePath: String) : RestoreTarget
}

/** Where an unhidden video goes: its own folder when Android lets apps write there, otherwise Movies/Moody. */
fun restoreTarget(originalRelativePath: String): RestoreTarget {
    val p = originalRelativePath.trim().let { if (it.isEmpty() || it.endsWith("/")) it else "$it/" }
    val top = p.substringBefore('/')
    return when {
        top == "DCIM" || top == "Movies" || top == "Pictures" -> RestoreTarget.Video(p)
        top == "Download" || top == "Downloads" -> RestoreTarget.Downloads(p)
        else -> RestoreTarget.Video("Movies/Moody/")
    }
}

data class MoveProgress(val index: Int, val total: Int, val currentName: String, val bytesDone: Long, val bytesTotal: Long)

sealed interface MoveResult {
    data class Ok(val landedPath: String) : MoveResult
    data class NoSpace(val name: String, val needBytes: Long) : MoveResult
    data object Declined : MoveResult
    data class Failed(val name: String, val msg: String) : MoveResult
}

/** After a crash mid-move: half-copied files go away (originals were never touched); half-restored ones stay in the vault. */
suspend fun recoverMoves(dao: VaultDao, fileFor: (VaultItem) -> File) {
    dao.unfinished().forEach { row ->
        when (row.state) {
            VaultState.COPYING -> { fileFor(row).delete(); dao.delete(row.id) }
            VaultState.RESTORING -> if (fileFor(row).exists()) dao.upsert(row.copy(state = VaultState.DONE)) else dao.delete(row.id)
            VaultState.DONE -> Unit
        }
    }
}

private const val SPACE_MARGIN = 50L * 1024 * 1024

/**
 * Moves videos into and out of app-private storage. A move never deletes before the copy is verified:
 * hide = copy in, verify, then the original is deleted (with the user's consent); unhide = write out, verify, then delete the vault copy.
 */
@Singleton
class VaultManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: VaultDao,
    private val media: MediaRepository,
) {
    private val dir = File(context.filesDir, "vault").apply { mkdirs() }
    private val thumbs = File(dir, "thumbs").apply { mkdirs() }

    val items: Flow<List<VaultItem>> = dao.done()

    fun fileFor(item: VaultItem) = File(dir, item.fileName)
    fun thumbFor(item: VaultItem) = File(thumbs, "${item.id}.jpg")

    /** Step 1 of hiding: copy each video in. Returns the ids copied plus any per-file failures. */
    suspend fun copyIn(videos: List<VideoItem>, onProgress: (MoveProgress) -> Unit): Pair<List<String>, List<MoveResult>> = withContext(Dispatchers.IO) {
        val copied = mutableListOf<String>()
        val failures = mutableListOf<MoveResult>()
        videos.forEachIndexed { i, v ->
            coroutineContext.ensureActive()
            val free = StatFs(dir.absolutePath).availableBytes
            if (free < v.sizeBytes + SPACE_MARGIN) { failures += MoveResult.NoSpace(v.name, v.sizeBytes + SPACE_MARGIN); return@forEachIndexed }
            val id = UUID.randomUUID().toString()
            val file = File(dir, "$id.bin")
            val row = VaultItem(id, file.name, v.name, v.relativePath, v.mime, v.sizeBytes, v.durationMs ?: 0, VaultState.COPYING, System.currentTimeMillis(), v.uri.toString())
            dao.upsert(row)
            try {
                var done = 0L
                context.contentResolver.openInputStream(v.uri)!!.use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(1 shl 20)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            onProgress(MoveProgress(i, videos.size, v.name, done, v.sizeBytes))
                        }
                        out.fd.sync()
                    }
                }
                if (v.sizeBytes > 0 && file.length() != v.sizeBytes) error("The copy came out a different size")
                saveThumb(v.uri, thumbFor(row))
                copied += id
            } catch (e: Exception) {
                file.delete()
                dao.delete(id)
                failures += MoveResult.Failed(v.name, e.message ?: "Couldn't copy")
            }
        }
        copied to failures
    }

    /** Android 11+: one system dialog to delete the originals of [ids]. */
    suspend fun deleteRequest(ids: List<String>): IntentSender? {
        if (Build.VERSION.SDK_INT < 30) return null
        val uris = ids.mapNotNull { dao.get(it)?.sourceUri?.let(Uri::parse) }
        if (uris.isEmpty()) return null
        return MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender
    }

    /** Android 10 and below: delete directly. Returns a consent intent if Android 10 asks for one. */
    suspend fun deleteDirect(ids: List<String>): IntentSender? = withContext(Dispatchers.IO) {
        for (id in ids) {
            val uri = dao.get(id)?.sourceUri?.let(Uri::parse) ?: continue
            try { context.contentResolver.delete(uri, null, null) }
            catch (e: SecurityException) {
                if (Build.VERSION.SDK_INT >= 29 && e is android.app.RecoverableSecurityException) return@withContext e.userAction.actionIntent.intentSender
                throw e
            }
        }
        null
    }

    /** Step 2 of hiding, after the originals are gone. */
    suspend fun finishHide(ids: List<String>) {
        ids.forEach { id -> dao.get(id)?.let { dao.upsert(it.copy(state = VaultState.DONE)) } }
        media.rescan()
    }

    /** The user said no to deleting the originals: drop the copies, nothing else changes. */
    suspend fun rollback(ids: List<String>) = withContext(Dispatchers.IO) {
        ids.forEach { id -> dao.get(id)?.let { fileFor(it).delete(); thumbFor(it).delete() }; dao.delete(id) }
    }

    suspend fun unhide(list: List<VaultItem>, onProgress: (MoveProgress) -> Unit): List<MoveResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<MoveResult>()
        list.forEachIndexed { i, item ->
            coroutineContext.ensureActive()
            val src = fileFor(item)
            if (!src.exists()) { dao.delete(item.id); results += MoveResult.Failed(item.originalName, "The vault copy is missing"); return@forEachIndexed }
            dao.upsert(item.copy(state = VaultState.RESTORING))
            try {
                val landed = writeOut(item, src) { done -> onProgress(MoveProgress(i, list.size, item.originalName, done, src.length())) }
                src.delete()
                thumbFor(item).delete()
                dao.delete(item.id)
                results += MoveResult.Ok(landed)
            } catch (e: Exception) {
                dao.upsert(item.copy(state = VaultState.DONE))
                results += MoveResult.Failed(item.originalName, e.message ?: "Couldn't move it back")
            }
        }
        media.rescan()
        results
    }

    private fun writeOut(item: VaultItem, src: File, progress: (Long) -> Unit): String {
        if (Build.VERSION.SDK_INT < 29) {
            @Suppress("DEPRECATION")
            val root = Environment.getExternalStorageDirectory()
            val target = File(root, restoreTarget(item.originalRelativePath).relativePath.ifEmpty { "Movies/Moody/" }).apply { mkdirs() }
            var out = File(target, item.originalName)
            var n = 1
            while (out.exists()) out = File(target, item.originalName.substringBeforeLast('.') + " ($n)." + item.originalName.substringAfterLast('.')).also { n++ }
            copyWithProgress(src, out.outputStream(), progress)
            if (out.length() != src.length()) { out.delete(); error("The restored file came out a different size") }
            MediaScannerConnection.scanFile(context, arrayOf(out.absolutePath), arrayOf(item.mime), null)
            return out.parentFile!!.absolutePath.removePrefix(root.absolutePath).trim('/') + "/"
        }
        val target = restoreTarget(item.originalRelativePath)
        val attempts = listOf(target, RestoreTarget.Video("Movies/Moody/")).distinct()
        var lastError: Exception? = null
        for (t in attempts) {
            val collection = when (t) {
                is RestoreTarget.Video -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                is RestoreTarget.Downloads -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, item.originalName)
                put(MediaStore.MediaColumns.MIME_TYPE, item.mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, t.relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = try { context.contentResolver.insert(collection, values) } catch (e: Exception) { lastError = e; null } ?: continue
            try {
                context.contentResolver.openOutputStream(uri)!!.use { copyWithProgress(src, it, progress) }
                val written = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1
                if (written != src.length()) error("The restored file came out a different size")
                context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                return t.relativePath
            } catch (e: Exception) {
                runCatching { context.contentResolver.delete(uri, null, null) }
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("No folder accepted the file")
    }

    private fun copyWithProgress(src: File, out: java.io.OutputStream, progress: (Long) -> Unit) {
        out.use { o ->
            src.inputStream().use { input ->
                val buf = ByteArray(1 shl 20)
                var done = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    o.write(buf, 0, n)
                    done += n
                    progress(done)
                }
            }
        }
    }

    suspend fun deleteForever(list: List<VaultItem>) = withContext(Dispatchers.IO) {
        list.forEach { fileFor(it).delete(); thumbFor(it).delete(); dao.delete(it.id) }
    }

    suspend fun recoverInterruptedMoves() = withContext(Dispatchers.IO) {
        recoverMoves(dao) { fileFor(it) }
    }

    private fun saveThumb(uri: Uri, out: File) {
        runCatching {
            val bmp: Bitmap? = if (Build.VERSION.SDK_INT >= 29) context.contentResolver.loadThumbnail(uri, Size(512, 288), null)
            else MediaMetadataRetriever().run { try { setDataSource(context, uri); getFrameAtTime(2_000_000) } finally { release() } }
            bmp?.let { b -> out.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 82, it) } }
        }
    }
}
