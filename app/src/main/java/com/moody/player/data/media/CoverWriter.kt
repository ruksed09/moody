package com.moody.player.data.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.moody.player.data.health.HealthProber
import com.moody.player.domain.MediaKind
import com.moody.player.domain.diagnose
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.images.AndroidArtwork
import java.io.File
import java.io.InputStream
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.math.abs

/** How writing a cover into a song file ended. */
sealed interface CoverWrite {
    data object Done : CoverWrite
    /** The file's format can't carry a cover Moody can write; the cover stays in Moody only. */
    data object Unsupported : CoverWrite
    /** Something didn't check out; the file is exactly as it was. */
    data class Failed(val why: String) : CoverWrite
}

/**
 * Writes a cover picture into a song file, so every app and Android's media controls show it.
 *
 * The file is never edited in place. Moody copies it twice into its own storage (an untouched backup and a
 * working copy), puts the cover into the working copy, and checks that the audio still reads the same:
 * same length, and the same health verdict as the original. Only then is the file replaced. Afterwards it
 * is read back and compared byte for byte; on any mismatch the backup is written back.
 *
 * Call from a background thread, after the user allowed Moody to change the file.
 */
object CoverWriter {
    private val formats = setOf("mp3", "m4a", "mp4", "aac", "flac", "ogg")

    /**
     * Throws [SecurityException] (on Android 10 a RecoverableSecurityException with a consent prompt) when
     * Moody may not change [uri]. Opens read-write without truncating, so nothing changes.
     */
    fun checkWritable(context: Context, uri: Uri) {
        context.contentResolver.openFileDescriptor(uri, "rw")?.close() ?: throw SecurityException("No access")
    }

    /**
     * [releasePlayer] runs just before the file is replaced (stop playback of it if it is playing) and
     * [resumePlayer] right after, whatever the outcome.
     */
    suspend fun embed(
        context: Context,
        song: AudioItem,
        coverPath: String,
        releasePlayer: suspend () -> Unit,
        resumePlayer: suspend () -> Unit,
    ): CoverWrite {
        val ext = song.fileName.substringAfterLast('.', "").lowercase()
        if (ext !in formats) return CoverWrite.Unsupported
        val cover = File(coverPath)
        if (!cover.isFile) return CoverWrite.Failed("The picture is missing")
        val resolver = context.contentResolver
        checkWritable(context, song.uri)

        val size = resolver.openFileDescriptor(song.uri, "r")?.use { it.statSize } ?: return CoverWrite.Failed("Couldn't open the song")
        val dir = File(context.cacheDir, "cover-write").apply { deleteRecursively(); mkdirs() }
        if (dir.usableSpace < size * 2 + cover.length() + (16L shl 20)) return CoverWrite.Failed("Not enough free space")
        val backup = File(dir, "original.$ext")
        val work = File(dir, "tagged.$ext")
        try {
            resolver.openInputStream(song.uri)?.use { input -> backup.outputStream().use { input.copyTo(it) } }
                ?: return CoverWrite.Failed("Couldn't read the song")
            if (backup.length() != size) return CoverWrite.Failed("Couldn't read the whole song")
            backup.copyTo(work, overwrite = true)

            // Tag the working copy. A file the library can't read (odd or mislabelled format) stays as it is.
            quietLibrary()
            try {
                val af = AudioFileIO.read(work)
                val tag = af.tagOrCreateAndSetDefault
                tag.deleteArtworkField()
                tag.setField(AndroidArtwork.createArtworkFromFile(cover).apply { pictureType = 3 }) // 3 = front cover
                af.commit()
            } catch (e: Exception) {
                return CoverWrite.Unsupported
            }

            // The audio must have come through untouched.
            val art = runCatching { AudioFileIO.read(work).tag?.firstArtwork?.binaryData }.getOrNull()
            if (art == null || art.isEmpty()) return CoverWrite.Failed("The cover didn't stick")
            val before = durationMs(backup); val after = durationMs(work)
            if (before == null || after == null || abs(before - after) > 1_000) return CoverWrite.Failed("The song's length changed")
            if (!sameHealth(context, song, backup, work)) return CoverWrite.Failed("The tagged copy didn't play the same")

            // Replace the file, then make sure it reads back exactly as the tagged copy.
            releasePlayer()
            try {
                val ok = runCatching {
                    resolver.openOutputStream(song.uri, "wt")?.use { out -> work.inputStream().use { it.copyTo(out) } } ?: error("no stream")
                    sameBytes(resolver.openInputStream(song.uri), work)
                }.getOrDefault(false)
                if (!ok) {
                    val restored = runCatching {
                        resolver.openOutputStream(song.uri, "wt")?.use { out -> backup.inputStream().use { it.copyTo(out) } } ?: error("no stream")
                        sameBytes(resolver.openInputStream(song.uri), backup)
                    }.getOrDefault(false)
                    // If even the restore failed, keep the backup where it is and say so.
                    if (!restored) {
                        val keep = File(context.filesDir, "cover-backup").apply { mkdirs() }
                        backup.copyTo(File(keep, song.fileName.ifBlank { "song.$ext" }), overwrite = true)
                        return CoverWrite.Failed("Writing failed. A copy of the original is kept in Moody's storage")
                    }
                    return CoverWrite.Failed("Writing failed, so the song was put back as it was")
                }
            } finally {
                resumePlayer()
            }
            return CoverWrite.Done
        } finally {
            if (dir.exists()) dir.deleteRecursively()
        }
    }

    private fun quietLibrary() {
        TagOptionSingleton.getInstance().isAndroid = true
        Logger.getLogger("org.jaudiotagger").level = Level.OFF
    }

    private fun durationMs(f: File): Long? = runCatching {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(f.absolutePath)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } finally {
            r.release()
        }
    }.getOrNull()

    private fun sameHealth(context: Context, song: AudioItem, a: File, b: File): Boolean {
        val prober = HealthProber(context)
        return try {
            val ra = diagnose(prober.probe(Uri.fromFile(a), MediaKind.AUDIO, song.fileName, song.durationMs), "a")
            val rb = diagnose(prober.probe(Uri.fromFile(b), MediaKind.AUDIO, song.fileName, song.durationMs), "b")
            ra.severity == rb.severity && ra.findings.map { it.code }.toSet() == rb.findings.map { it.code }.toSet()
        } finally {
            prober.close()
        }
    }

    private fun sameBytes(stream: InputStream?, file: File): Boolean {
        if (stream == null) return false
        stream.buffered().use { x ->
            file.inputStream().buffered().use { y ->
                val bx = ByteArray(64 * 1024); val by = ByteArray(64 * 1024)
                while (true) {
                    val nx = x.readNBytesCompat(bx); val ny = y.readNBytesCompat(by)
                    if (nx != ny) return false
                    if (nx <= 0) return true
                    for (i in 0 until nx) if (bx[i] != by[i]) return false
                }
            }
        }
    }

    /** Fills [buf] as far as the stream allows; returns the count (0 at the end). */
    private fun InputStream.readNBytesCompat(buf: ByteArray): Int {
        var n = 0
        while (n < buf.size) {
            val r = read(buf, n, buf.size - n)
            if (r < 0) break
            n += r
        }
        return n
    }
}
