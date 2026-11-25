package com.moody.player.player.music

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.moody.player.domain.boostSample
import com.moody.player.domain.clampBoost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer

/** How much songs are amplified (1 to 3). Not saved: it goes back to 1 when the music service ends. */
object MusicBoost {
    private val _gain = MutableStateFlow(1f)
    val gain: StateFlow<Float> = _gain.asStateFlow()
    fun set(g: Float) { _gain.value = clampBoost(g) }
}

/** Amplifies Media3's sound by [MusicBoost]; a plain copy at 100%. */
@OptIn(UnstableApi::class)
class BoostProcessor : BaseAudioProcessor() {
    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) return AudioProcessor.AudioFormat.NOT_SET
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val n = inputBuffer.remaining()
        if (n == 0) return
        val out = replaceOutputBuffer(n)
        val gain = MusicBoost.gain.value
        if (gain <= 1f) out.put(inputBuffer)
        else if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
            while (inputBuffer.remaining() >= 2) out.putShort((boostSample(inputBuffer.getShort() / 32768f, gain) * 32767f).toInt().toShort())
        } else {
            while (inputBuffer.remaining() >= 4) out.putFloat(boostSample(inputBuffer.getFloat(), gain))
        }
        out.flip()
    }
}
