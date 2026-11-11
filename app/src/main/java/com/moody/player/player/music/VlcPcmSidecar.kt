package com.moody.player.player.music

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import java.io.File
import java.io.RandomAccessFile

/**
 * Lets the visualizer see songs that play through libVLC, whose output can't be tapped. A second, silent libVLC
 * player decodes a window of the same file to low-rate mono PCM in the cache; [AudioTap] reads it back at the
 * playback position. Nothing is heard from it and the real playback path is untouched. It only runs while a
 * visualizer is on screen, and keeps one window (a few MB) at a time.
 */
internal class VlcPcmSidecar(context: Context, private val lib: LibVLC) {
    private val resolver = context.contentResolver
    private val dir = File(context.cacheDir, "viz").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
    private var mp: MediaPlayer? = null
    private var fd: ParcelFileDescriptor? = null
    private var uri: Uri? = null
    private var file: File? = null
    private var winStartMs = 0L
    private var serial = 0

    /** Keeps a decoded window around [posMs] of [song] while a visualizer wants one; stops otherwise. Main thread. */
    fun follow(song: Uri?, posMs: Long) {
        if (song == null || !AudioTap.wanted) { stop(); return }
        val inWindow = song == uri && posMs >= winStartMs && posMs < winStartMs + WINDOW_MS - 5_000
        if (mp != null && inWindow) return
        start(song, (posMs - 1_000).coerceAtLeast(0))
    }

    private fun start(song: Uri, fromMs: Long) {
        stop()
        val out = File(dir, "w${serial++}.wav")
        val media = if (song.scheme == "content") {
            val pfd = runCatching { resolver.openFileDescriptor(song, "r") }.getOrNull() ?: run { AudioTap.setSidecar(null, 0, false); return }
            fd = pfd
            Media(lib, pfd.fileDescriptor)
        } else Media(lib, song)
        media.addOption(":sout=#transcode{vcodec=none,scodec=none,acodec=s16l,channels=1,samplerate=$RATE}:std{access=file,mux=wav,dst=${out.absolutePath}}")
        media.addOption(":no-sout-video")
        media.addOption(":no-sout-spu")
        if (fromMs > 0) media.addOption(":start-time=${fromMs / 1000.0}")
        media.addOption(":stop-time=${(fromMs + WINDOW_MS) / 1000.0}")
        val player = MediaPlayer(lib)
        player.setEventListener { e -> if (e.type == MediaPlayer.Event.EncounteredError) AudioTap.setSidecar(null, 0, true) }
        player.media = media
        media.release()
        player.play()
        mp = player; uri = song; file = out; winStartMs = fromMs
        AudioTap.setSidecar(PcmWindow(out, fromMs, RATE), RATE, false)
    }

    fun stop() {
        AudioTap.setSidecar(null, 0, false)
        mp?.let { it.setEventListener(null); it.stop(); it.release() }
        mp = null
        fd?.close(); fd = null
        file?.delete(); file = null
        uri = null
    }

    companion object {
        const val RATE = 22_050
        const val WINDOW_MS = 10 * 60_000L // about 26 MB of cache at most
    }
}

/** Reads the growing WAV a [VlcPcmSidecar] writes. */
internal class PcmWindow(private val file: File, private val startMs: Long, private val rate: Int) {
    private var raf: RandomAccessFile? = null
    private var dataAt = -1L
    private var bytes = ByteArray(0)

    /** Fills [out] with the samples that end at [posMs]. False when that part isn't decoded yet. */
    fun read(out: FloatArray, posMs: Long): Boolean {
        val r = raf ?: runCatching { RandomAccessFile(file, "r") }.getOrNull()?.also { raf = it } ?: return false
        if (dataAt < 0) dataAt = findData(r) ?: return false
        val end = (posMs - startMs) * rate / 1000
        val begin = end - out.size
        if (begin < 0) return false
        val need = out.size * 2
        val at = dataAt + begin * 2
        if (at + need > r.length()) return false
        if (bytes.size != need) bytes = ByteArray(need)
        r.seek(at)
        r.readFully(bytes)
        for (i in out.indices) {
            val lo = bytes[i * 2].toInt() and 0xFF
            val hi = bytes[i * 2 + 1].toInt()
            out[i] = ((hi shl 8) or lo).toShort() / 32768f
        }
        return true
    }

    private fun findData(r: RandomAccessFile): Long? {
        if (r.length() < 64) return null
        val head = ByteArray(minOf(256L, r.length()).toInt())
        r.seek(0); r.readFully(head)
        for (i in 12 until head.size - 8) {
            if (head[i] == 'd'.code.toByte() && head[i + 1] == 'a'.code.toByte() && head[i + 2] == 't'.code.toByte() && head[i + 3] == 'a'.code.toByte()) return (i + 8).toLong()
        }
        return null
    }

    fun close() { runCatching { raf?.close() }; raf = null }
}
