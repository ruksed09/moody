package com.moody.player.player.music

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger

/**
 * The music's own samples, for the visualizer. The audio thread only copies into a ring (and only while a
 * visualizer is on screen); the drawing loop reads from it and does the maths. No microphone permission:
 * this is the decoded PCM on its way to the speaker.
 */
object AudioTap {
    private const val CAP = 1 shl 15 // mono samples, about 0.7 s at 48 kHz
    private const val STALE_NS = 600_000_000L
    private val ring = FloatArray(CAP)
    private var written = 0L
    private val lock = Any()
    private val users = AtomicInteger(0)
    @Volatile private var lastWriteNs = 0L

    @Volatile var sampleRate: Int = 44_100
        private set

    /** True while some screen shows a visualizer; the tap does nothing otherwise. */
    val wanted: Boolean get() = users.get() > 0
    fun acquire() { users.incrementAndGet() }
    fun release() { users.updateAndGet { (it - 1).coerceAtLeast(0) } }

    private val _fallback = MutableStateFlow(false)
    /** The queue is playing through libVLC, which the tap can't hear. */
    val onFallbackEngine: StateFlow<Boolean> = _fallback.asStateFlow()
    fun setFallback(on: Boolean) { _fallback.value = on }

    // libVLC's output can't be tapped, so for its queues a VlcPcmSidecar decodes the song separately and the
    // visualizer reads that copy at libVLC's playback position.
    @Volatile private var side: PcmWindow? = null
    @Volatile private var sideRate = 0
    private val _sideFailed = MutableStateFlow(false)
    /** The sidecar couldn't decode the current song, so the visualizer has nothing to show for it. */
    val sidecarFailed: StateFlow<Boolean> = _sideFailed.asStateFlow()
    internal fun setSidecar(w: PcmWindow?, rate: Int, failed: Boolean) {
        side?.takeIf { it !== w }?.close()
        side = w; sideRate = rate
        _sideFailed.value = failed
    }

    @Volatile private var clockPos = 0L
    @Volatile private var clockAt = 0L
    @Volatile private var clockSpeed = 1f
    @Volatile private var clockPlaying = false
    /** libVLC's position: [posMs] at elapsed-realtime [atMs], moving at [speed] while [playing]. */
    fun setClock(posMs: Long, atMs: Long, speed: Float, playing: Boolean) {
        clockPos = posMs; clockAt = atMs; clockSpeed = speed; clockPlaying = playing
    }

    /** Mixes [input] (PCM 16-bit or float, interleaved) down to mono into the ring. Does not move [input]'s position. */
    fun write(input: ByteBuffer, encoding: Int, channels: Int, rate: Int) {
        if (channels <= 0) return
        sampleRate = rate
        val buf = input.duplicate().order(ByteOrder.nativeOrder())
        val bytesPer = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
        val frame = bytesPer * channels
        val start = buf.position()
        val frames = (buf.limit() - start) / frame
        synchronized(lock) {
            var w = written
            for (f in 0 until frames) {
                var sum = 0f
                val at = start + f * frame
                for (ch in 0 until channels) {
                    sum += if (bytesPer == 4) buf.getFloat(at + ch * 4) else buf.getShort(at + ch * 2) / 32768f
                }
                ring[(w and (CAP - 1).toLong()).toInt()] = sum / channels
                w++
            }
            written = w
        }
        lastWriteNs = System.nanoTime()
    }

    /**
     * Copies [out].size samples that end [delayMs] before the newest into [out]. The delay lines the picture
     * up with what is heard (samples reach the tap before the speaker's buffer plays them). False when there
     * isn't enough audio yet, or when none has arrived lately (so a stalled or switched engine never shows a
     * frozen picture).
     */
    fun read(out: FloatArray, delayMs: Int): Boolean {
        if (_fallback.value) {
            val w = side ?: return false
            if (!clockPlaying) return false
            val pos = clockPos + ((android.os.SystemClock.elapsedRealtime() - clockAt) * clockSpeed).toLong()
            sampleRate = sideRate
            return runCatching { w.read(out, pos) }.getOrDefault(false)
        }
        return readLive(out, delayMs)
    }

    private fun readLive(out: FloatArray, delayMs: Int): Boolean = synchronized(lock) {
        if (System.nanoTime() - lastWriteNs > STALE_NS) return false
        val end = written - sampleRate * delayMs / 1000L
        val begin = end - out.size
        if (begin < 0 || out.size > CAP / 2) return false
        for (i in out.indices) out[i] = ring[((begin + i) and (CAP - 1).toLong()).toInt()]
        true
    }

    /** How many samples have been written so far; used to tell whether audio is still arriving. */
    fun position(): Long = synchronized(lock) { written }
}

/** A pass-through audio processor that feeds [AudioTap]. Inactive for formats it can't read (e.g. passthrough). */
@OptIn(UnstableApi::class)
class TapProcessor : BaseAudioProcessor() {
    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) return AudioProcessor.AudioFormat.NOT_SET
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val n = inputBuffer.remaining()
        if (n == 0) return
        if (AudioTap.wanted) AudioTap.write(inputBuffer, inputAudioFormat.encoding, inputAudioFormat.channelCount, inputAudioFormat.sampleRate)
        replaceOutputBuffer(n).put(inputBuffer).flip()
    }
}
