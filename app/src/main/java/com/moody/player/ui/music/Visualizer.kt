package com.moody.player.ui.music

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.moody.player.data.prefs.Settings
import com.moody.player.design.MoodyTheme
import com.moody.player.design.components.ChoiceChip
import com.moody.player.design.components.QuietButton
import com.moody.player.domain.BandGains
import com.moody.player.domain.SpectrumAnalyzer
import com.moody.player.domain.VizStyle
import com.moody.player.domain.smoothInto
import com.moody.player.player.music.AudioTap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** How far behind the tap the picture runs: roughly the audio output buffer, so motion matches the sound. */
private const val DELAY_MS = 180
private const val BANDS = 48
private const val WAVE = 256

/** Smoothed levels for one visualizer, updated once per display frame (capped near 60 fps). */
@Stable
class VizFrames {
    val levels = FloatArray(BANDS)
    val wave = FloatArray(WAVE)
    var level = 0f
    /** Read inside drawing only, so a new frame redraws without recomposing. */
    var tick by mutableLongStateOf(0L)
}

/**
 * Runs the analysis loop while [active] and [playing]. When playback pauses the bars settle to rest and the
 * loop stops, so a paused visualizer costs nothing.
 */
@Composable
fun rememberVizFrames(active: Boolean, playing: Boolean, s: Settings): VizFrames {
    val f = remember { VizFrames() }
    DisposableEffect(active) {
        if (active) AudioTap.acquire()
        onDispose { if (active) AudioTap.release() }
    }
    val gains = BandGains(s.vizBass, s.vizMids, s.vizTreble, s.vizSensitivity)
    val intensity = s.vizIntensity
    LaunchedEffect(active, playing, gains, intensity) {
        if (!active) return@LaunchedEffect
        val analyzer = SpectrumAnalyzer(1024, BANDS)
        val buf = FloatArray(1024)
        val target = FloatArray(BANDS)
        var last = 0L
        while (true) {
            val now = withFrameNanos { it }
            if (now - last < 15_000_000L) continue
            last = now
            val ok = playing && AudioTap.read(buf, DELAY_MS)
            if (ok) {
                analyzer.analyze(buf, AudioTap.sampleRate, gains)
                analyzer.levels.copyInto(target)
                val amp = (0.9f * gains.sensitivity).coerceIn(0.2f, 2f)
                for (i in 0 until WAVE) f.wave[i] = (buf[i * 4] * amp * 2.2f).coerceIn(-1f, 1f)
            } else {
                target.fill(0f)
                for (i in 0 until WAVE) f.wave[i] *= 0.82f
            }
            smoothInto(f.levels, target, intensity)
            f.level += ((if (ok) analyzer.level else 0f) - f.level) * 0.3f
            f.tick++
            if (!playing && f.level < 0.002f && f.levels.all { it < 0.002f }) break
        }
    }
    return f
}

/** Draws [frames] in [style]. Colours come from the theme accent and the artwork's own tint. */
@Composable
fun VisualizerCanvas(style: VizStyle, frames: VizFrames, a: Color, b: Color, intensity: Float, modifier: Modifier = Modifier) {
    Canvas(modifier.semantics { contentDescription = "Music visualizer, ${style.label}" }) {
        frames.tick // redraw every frame
        when (style) {
            VizStyle.BARS -> bars(frames, a, b)
            VizStyle.CIRCLE -> circle(frames, a, b, intensity)
            VizStyle.WAVE -> wave(frames, a, b)
            VizStyle.RINGS -> rings(frames, a, b, intensity)
            VizStyle.MINIMAL -> minimal(frames, a, b)
        }
    }
}

private fun DrawScope.bars(f: VizFrames, a: Color, b: Color) {
    val n = BANDS
    val gap = 2.dp.toPx()
    val w = (size.width - gap * (n - 1)) / n
    val floor = size.height * 0.78f
    val maxH = floor * 0.94f
    val brush = Brush.verticalGradient(listOf(b, a), startY = floor - maxH, endY = floor)
    val r = CornerRadius(w / 2, w / 2)
    for (i in 0 until n) {
        val h = (f.levels[i] * maxH).coerceAtLeast(2.dp.toPx())
        val x = i * (w + gap)
        drawRoundRect(brush, Offset(x, floor - h), Size(w, h), r)
        // A faint reflection under the floor.
        drawRoundRect(brush, Offset(x, floor + gap), Size(w, h * 0.28f), r, alpha = 0.16f)
    }
}

private fun DrawScope.circle(f: VizFrames, a: Color, b: Color, intensity: Float) {
    val c = center
    val m = min(size.width, size.height)
    // Kept inside the panel: the longest spoke stops short of the edge at any level and motion setting.
    val base = m * 0.22f * (1f + f.level * 0.08f * intensity.coerceAtMost(2f))
    val reach = (m * 0.46f - base - 3.dp.toPx()).coerceAtLeast(0f)
    drawCircle(a, base * 0.92f, c, alpha = 0.08f + f.level * 0.12f)
    val spokes = BANDS * 2
    val stroke = (2 * PI * base / spokes * 0.55f).toFloat().coerceIn(1.5f, 6.dp.toPx())
    for (k in 0 until spokes) {
        // Mirror the bands so bass meets bass at the top and treble at the bottom.
        val band = if (k < BANDS) k else spokes - 1 - k
        val v = f.levels[band]
        val ang = (k / spokes.toFloat()) * 2 * PI - PI / 2
        val dx = cos(ang).toFloat(); val dy = sin(ang).toFloat()
        val start = Offset(c.x + dx * base, c.y + dy * base)
        val end = Offset(c.x + dx * (base + 3.dp.toPx() + v * reach), c.y + dy * (base + 3.dp.toPx() + v * reach))
        drawLine(lerpColor(a, b, band / BANDS.toFloat()), start, end, stroke, StrokeCap.Round)
    }
}

private fun DrawScope.wave(f: VizFrames, a: Color, b: Color) {
    val mid = size.height / 2
    val amp = size.height * 0.36f
    fun path(scale: Float, shift: Float): Path = Path().apply {
        for (i in 0 until WAVE) {
            val x = i / (WAVE - 1f) * size.width
            val y = mid + shift + f.wave[i] * amp * scale
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
    }
    val brush = Brush.horizontalGradient(listOf(a, b, a))
    drawLine(a.copy(alpha = 0.12f), Offset(0f, mid), Offset(size.width, mid), 1.dp.toPx())
    drawPath(path(0.55f, 0f), brush, alpha = 0.28f, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
    drawPath(path(1f, 0f), brush, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
}

private fun DrawScope.rings(f: VizFrames, a: Color, b: Color, intensity: Float) {
    val c = center
    val m = min(size.width, size.height) / 2
    val groups = 6
    val per = BANDS / groups
    for (g in 0 until groups) {
        var v = 0f
        for (i in g * per until (g + 1) * per) v = maxOf(v, f.levels[i])
        // Rest radii leave room to swell; nothing passes the panel's edge.
        val r = (m * (0.16f + 0.11f * g) * (1f + v * 0.15f * intensity.coerceAtMost(2f))).coerceAtMost(m * 0.94f)
        drawCircle(lerpColor(a, b, g / (groups - 1f)), r, c, alpha = (0.85f - g * 0.11f) * (0.35f + v * 0.65f), style = Stroke((1.5f + v * 5f).dp.toPx()))
    }
    drawCircle(a, m * 0.09f * (1f + f.levels[2] * 0.6f * intensity.coerceAtMost(2f)), c, alpha = 0.55f)
}

private fun DrawScope.minimal(f: VizFrames, a: Color, b: Color) {
    val pills = 9
    val w = 8.dp.toPx()
    val gap = 8.dp.toPx()
    val total = pills * w + (pills - 1) * gap
    val maxH = size.height * 0.42f
    for (p in 0 until pills) {
        var v = 0f
        for (i in p * BANDS / pills until (p + 1) * BANDS / pills) v = maxOf(v, f.levels[i])
        val h = (w + v * maxH)
        val x = center.x - total / 2 + p * (w + gap)
        drawRoundRect(lerpColor(a, b, p / (pills - 1f)), Offset(x, center.y - h / 2), Size(w, h), CornerRadius(w / 2, w / 2))
    }
}

private fun lerpColor(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t.coerceIn(0f, 1f))

/** Style chips: switching is instant, the next frame draws the new style. */
@Composable
fun VizStylePicker(style: VizStyle, onPick: (VizStyle) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
        items(VizStyle.entries) { v -> ChoiceChip(v.label, v == style, { onPick(v) }) }
    }
}

/** Sensitivity, bass, mids, treble and motion. Changes apply while the music plays. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VizTuneSheet(s: Settings, onChange: ((Settings) -> Settings) -> Unit, onDismiss: () -> Unit) {
    val c = MoodyTheme.colors
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.raised) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Visualizer", style = MoodyTheme.type.title, color = c.text, modifier = Modifier.weight(1f))
                QuietButton("Reset", { onChange { it.copy(vizSensitivity = 1f, vizBass = 1f, vizMids = 1f, vizTreble = 1f, vizIntensity = 1f) } })
            }
            Text("Only changes the picture, never the sound.", style = MoodyTheme.type.caption, color = c.text3)
            Spacer(Modifier.height(8.dp))
            TuneSlider("Sensitivity", s.vizSensitivity) { v -> onChange { it.copy(vizSensitivity = v) } }
            TuneSlider("Bass", s.vizBass) { v -> onChange { it.copy(vizBass = v) } }
            TuneSlider("Mids", s.vizMids) { v -> onChange { it.copy(vizMids = v) } }
            TuneSlider("Treble", s.vizTreble) { v -> onChange { it.copy(vizTreble = v) } }
            TuneSlider("Motion", s.vizIntensity) { v -> onChange { it.copy(vizIntensity = v) } }
        }
    }
}

@Composable
private fun TuneSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    val c = MoodyTheme.colors
    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(label, style = MoodyTheme.type.body, color = c.text, modifier = Modifier.weight(1f))
        Text("${(value * 100).toInt()}%", style = MoodyTheme.type.mono, color = c.text2)
    }
    Slider(
        value = value, onValueChange = { onChange((it * 20).toInt() / 20f) }, valueRange = 0f..2f,
        modifier = Modifier.semantics { contentDescription = "$label ${(value * 100).toInt()} percent" },
        colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.line2),
    )
}

/** Shown in place of the picture when the song plays through the fallback engine, which the visualizer can't hear. */
@Composable
fun VizUnavailable(modifier: Modifier = Modifier) {
    val c = MoodyTheme.colors
    Box(modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
        Text(
            "The visualizer couldn't read this song's audio.",
            style = MoodyTheme.type.caption, color = c.text2, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp).background(c.surface.copy(alpha = 0.7f), com.moody.player.design.Radii.md).padding(12.dp),
        )
    }
}

