package com.moody.player.data.media

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.moody.player.domain.Lyrics
import com.moody.player.domain.parseLrc
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lyrics for a song: a file the user added in Moody (kept in Moody's own storage), else an .lrc or .txt
 * with the same name next to the song when Android lets Moody read it. The song file is never changed.
 */
@Singleton
class LyricsStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val dir get() = File(context.filesDir, "lyrics").apply { mkdirs() }

    private fun keyFor(song: Uri): String =
        MessageDigest.getInstance("SHA-256").digest(song.toString().toByteArray()).take(12).joinToString("") { "%02x".format(it) }

    private fun added(song: Uri) = File(dir, keyFor(song) + ".lrc")

    /** True when the lyrics shown come from a file the user added in Moody. */
    fun hasAdded(song: Uri): Boolean = added(song).isFile

    suspend fun load(song: Uri): Lyrics? = withContext(Dispatchers.IO) {
        val text = added(song).takeIf { it.isFile }?.readText() ?: sidecar(song)?.readText()
        text?.let(::parseLrc)?.takeIf { !it.isEmpty }
    }

    /** Copies a picked lyrics file in. Returns false when it isn't readable or has no lines. */
    suspend fun import(song: Uri, picked: Uri): Boolean = withContext(Dispatchers.IO) {
        val text = runCatching {
            context.contentResolver.openInputStream(picked)?.use { s -> s.readBytes().takeIf { it.size <= 512 * 1024 }?.toString(Charsets.UTF_8) }
        }.getOrNull() ?: return@withContext false
        if (parseLrc(text).isEmpty) return@withContext false
        added(song).writeText(text)
        true
    }

    suspend fun remove(song: Uri) = withContext(Dispatchers.IO) { added(song).delete() }

    private fun sidecar(song: Uri): File? {
        val path = when (song.scheme) {
            "file" -> song.path
            "content" -> runCatching {
                context.contentResolver.query(song, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            }.getOrNull()
            else -> null
        } ?: return null
        val base = path.substringBeforeLast('.')
        return listOf("lrc", "LRC", "txt").map { File("$base.$it") }.firstOrNull { runCatching { it.isFile && it.canRead() }.getOrDefault(false) }
    }
}
