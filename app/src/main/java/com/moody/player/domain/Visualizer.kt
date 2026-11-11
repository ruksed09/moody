package com.moody.player.domain

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class VizStyle(val label: String) {
    BARS("Bars"), CIRCLE("Circle"), WAVE("Wave"), RINGS("Rings"), MINIMAL("Minimal")
}

/** Per-region boosts the user sets, each 0..2 (1 = as heard). */
data class BandGains(val bass: Float = 1f, val mids: Float = 1f, val treble: Float = 1f, val sensitivity: Float = 1f)

/** In-place radix-2 FFT. [re] and [im] must be the same power-of-two length. */
fun fft(re: FloatArray, im: FloatArray) {
    val n = re.size
    require(n == im.size && n > 0 && n and (n - 1) == 0) { "FFT size must be a power of two" }
    var j = 0
    for (i in 1 until n) {
        var bit = n shr 1
        while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
        j = j xor bit
        if (i < j) {
            val tr = re[i]; re[i] = re[j]; re[j] = tr
            val ti = im[i]; im[i] = im[j]; im[j] = ti
        }
    }
    var len = 2
    while (len <= n) {
        val ang = -2.0 * PI / len
        val wr = cos(ang).toFloat(); val wi = sin(ang).toFloat()
        var i = 0
        while (i < n) {
            var cr = 1f; var ci = 0f
            for (k in 0 until len / 2) {
                val a = i + k; val b = a + len / 2
                val xr = re[b] * cr - im[b] * ci
                val xi = re[b] * ci + im[b] * cr
                re[b] = re[a] - xr; im[b] = im[a] - xi
                re[a] += xr; im[a] += xi
                val ncr = cr * wr - ci * wi
                ci = cr * wi + ci * wr
                cr = ncr
            }
            i += len
        }
        len = len shl 1
    }
}

/** Band edges in Hz, spaced evenly on a log scale between [lowHz] and [highHz]. */
fun bandEdges(bands: Int, lowHz: Float = 40f, highHz: Float = 16_000f): FloatArray =
    FloatArray(bands + 1) { i -> lowHz * (highHz / lowHz).pow(i / bands.toFloat()) }

/** Which boost applies at [hz]: bass below 250 Hz, treble above 4 kHz, mids between. */
fun gainAt(hz: Float, g: BandGains): Float = when {
    hz < 250f -> g.bass
    hz > 4_000f -> g.treble
    else -> g.mids
} * g.sensitivity

/**
 * Reusable analyser: windowed FFT of the newest samples into [bands] log-spaced levels (0..1), plus the
 * overall level. Not thread-safe; one per drawing loop.
 */
class SpectrumAnalyzer(val size: Int = 1024, val bands: Int = 48) {
    private val re = FloatArray(size)
    private val im = FloatArray(size)
    private val window = FloatArray(size) { i -> (0.5 - 0.5 * cos(2.0 * PI * i / (size - 1))).toFloat() }
    val levels = FloatArray(bands)
    var level = 0f
        private set

    /** Fills [levels] and [level] from [samples] (mono, -1..1, at least [size] long; the newest are used). */
    fun analyze(samples: FloatArray, sampleRate: Int, gains: BandGains) {
        val off = samples.size - size
        var sum = 0f
        for (i in 0 until size) {
            val s = samples[off + i]
            sum += s * s
            re[i] = s * window[i]; im[i] = 0f
        }
        level = (sqrt(sum / size) * 3.2f * gains.sensitivity).coerceIn(0f, 1f)
        fft(re, im)
        val edges = bandEdges(bands, highHz = minOf(16_000f, sampleRate / 2f - 1f))
        val binHz = sampleRate / size.toFloat()
        for (b in 0 until bands) {
            val lo = (edges[b] / binHz).toInt().coerceIn(1, size / 2 - 1)
            val hi = max(lo + 1, (edges[b + 1] / binHz).toInt()).coerceAtMost(size / 2)
            var peak = 0f
            for (k in lo until hi) peak = max(peak, hypot(re[k], im[k]))
            // Magnitude to dB, then -64..0 dB onto 0..1. A sine at full scale peaks near size/4 after the window.
            val db = 20f * log10(peak / (size / 4f) + 1e-9f)
            val centre = sqrt(edges[b] * edges[b + 1])
            levels[b] = (((db + 64f) / 64f) * gainAt(centre, gains)).coerceIn(0f, 1f)
        }
    }
}

/** Fast rise, slower fall. [intensity] 0..2: higher keeps more motion (slower fall, bigger jumps). */
fun smoothInto(shown: FloatArray, target: FloatArray, intensity: Float) {
    val fall = 0.80f + 0.08f * intensity.coerceIn(0f, 2f) // 0.80..0.96 per frame
    for (i in shown.indices) {
        val t = target[i]
        shown[i] = if (t > shown[i]) shown[i] + (t - shown[i]) * 0.6f else max(t, shown[i] * fall)
    }
}
