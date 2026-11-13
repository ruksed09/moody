package com.moody.player.ui.music

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.DrawResult
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.moody.player.design.illustrations.rememberReducedMotion
import com.moody.player.domain.VizBackdrop
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The quiet picture behind the full-screen visualizer: dim and low in contrast, so the visual stays in front.
 * [glow] colours the soft light (the disc colour picked in Settings), [line] the fine lines and dots (the
 * visualizer's own colour). Brushes, paths and dot positions are worked out once per size; while [moving], one
 * clock value drifts them, read only when drawing. With animations off in Android's settings it stays still.
 */
@Composable
fun VizBackdropLayer(kind: VizBackdrop, glow: Color, line: Color, moving: Boolean, modifier: Modifier = Modifier) {
    if (kind == VizBackdrop.NONE) return
    val still = rememberReducedMotion()
    val clock = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(moving, still) {
        if (!moving || still) return@LaunchedEffect
        var last = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (last != 0L) clock.floatValue += (now - last) / 1_000_000_000f
                last = now
            }
        }
    }
    Spacer(
        // Its own layer, so the visualizer redrawing in front does not redraw the backdrop too.
        modifier.graphicsLayer().drawWithCache {
            val t = { clock.floatValue }
            when (kind) {
                VizBackdrop.NONE -> onDrawBehind {}
                VizBackdrop.GLOW -> glow(glow, t)
                VizBackdrop.FLOOR -> floor(glow, line, t)
                VizBackdrop.GRID -> grid(line, t)
                VizBackdrop.HALO -> halo(glow, t)
                VizBackdrop.STARS -> specks(Color.White, 70, 0.9f, upward = true, t)
                VizBackdrop.ORBITS -> orbits(line, t)
                VizBackdrop.HORIZON -> horizon(glow, line, t)
                VizBackdrop.TIDE -> tide(line, t)
                VizBackdrop.DUST -> specks(glow, 34, 2.2f, upward = false, t)
                VizBackdrop.RIPPLES -> ripples(line, t)
                VizBackdrop.VIGNETTE -> vignette(glow)
                VizBackdrop.GRAIN -> grain()
            }
        },
    )
}

/** A slow breath between 0.8 and 1, for light that should not sit dead still. */
private fun breath(t: Float, speed: Float = 0.35f) = 0.9f + 0.1f * sin(t * speed)

/** One soft light in the middle. */
private fun CacheDrawScope.glow(color: Color, t: () -> Float): DrawResult {
    val mid = Offset(size.width / 2f, size.height / 2f)
    val r = max(size.width, size.height) * 0.62f
    val brush = Brush.radialGradient(0f to color.copy(alpha = 0.13f), 0.55f to color.copy(alpha = 0.04f), 1f to Color.Transparent, center = mid, radius = r)
    return onDrawBehind { drawCircle(brush, r, mid, alpha = breath(t())) }
}

/** Light coming up from the ground the bars stand on, and the ground's own thin line. */
private fun CacheDrawScope.floor(color: Color, line: Color, t: () -> Float): DrawResult {
    val top = size.height * 0.52f
    val rise = Brush.verticalGradient(0f to Color.Transparent, 1f to color.copy(alpha = 0.14f), startY = top, endY = size.height)
    val at = size.height * 0.80f
    val edge = Brush.horizontalGradient(0f to Color.Transparent, 0.5f to line.copy(alpha = 0.16f), 1f to Color.Transparent)
    val px = 1.dp.toPx()
    return onDrawBehind {
        drawRect(rise, Offset(0f, top), Size(size.width, size.height - top), alpha = breath(t()))
        drawRect(edge, Offset(0f, at), Size(size.width, px))
    }
}

/** A faint grid drifting downward, fading out toward the edges. */
private fun CacheDrawScope.grid(line: Color, t: () -> Float): DrawResult {
    val cell = 44.dp.toPx()
    val px = 1.dp.toPx()
    val tint = line.copy(alpha = 0.07f)
    val mid = Offset(size.width / 2f, size.height / 2f)
    val r = max(size.width, size.height) * 0.6f
    // Black over the lines near the edges: cheaper than fading every line by itself.
    val fade = Brush.radialGradient(0.25f to Color.Transparent, 1f to Backdrop, center = mid, radius = r)
    val columns = (size.width / cell).toInt() + 1
    val rows = (size.height / cell).toInt() + 2
    val left = (size.width - (columns - 1) * cell) / 2f
    return onDrawBehind {
        val shift = (t() * 5.dp.toPx()) % cell
        for (i in 0 until columns) drawLine(tint, Offset(left + i * cell, 0f), Offset(left + i * cell, size.height), px)
        for (j in 0 until rows) drawLine(tint, Offset(0f, j * cell + shift - cell), Offset(size.width, j * cell + shift - cell), px)
        drawRect(fade)
    }
}

/** A wide soft ring of light around where the circle sits. */
private fun CacheDrawScope.halo(color: Color, t: () -> Float): DrawResult {
    val mid = Offset(size.width / 2f, size.height / 2f)
    val r = min(size.width, size.height) * 0.92f
    val brush = Brush.radialGradient(
        0.30f to Color.Transparent, 0.48f to color.copy(alpha = 0.13f), 0.62f to color.copy(alpha = 0.05f), 1f to Color.Transparent,
        center = mid, radius = r,
    )
    return onDrawBehind { drawCircle(brush, r, mid, alpha = breath(t())) }
}

/** Small dots that drift (up, or sideways) and twinkle: stars when fine and white, dust when larger and coloured. */
private fun CacheDrawScope.specks(color: Color, count: Int, dp: Float, upward: Boolean, t: () -> Float): DrawResult {
    val rnd = Random(count)
    val xs = FloatArray(count) { rnd.nextFloat() * size.width }
    val ys = FloatArray(count) { rnd.nextFloat() * size.height }
    val radii = FloatArray(count) { (0.5f + rnd.nextFloat() * 0.8f) * dp.dp.toPx() / 2f }
    val phase = FloatArray(count) { rnd.nextFloat() * 6.28f }
    val depth = FloatArray(count) { 0.4f + rnd.nextFloat() * 0.6f }
    val speed = (if (upward) 3f else 5f).dp.toPx()
    return onDrawBehind {
        val now = t()
        for (i in 0 until count) {
            val move = now * speed * depth[i]
            val x = if (upward) xs[i] else (xs[i] + move) % size.width
            val y = if (upward) ((ys[i] - move) % size.height + size.height) % size.height else ys[i]
            val a = (if (upward) 0.20f else 0.11f) * depth[i] * (0.65f + 0.35f * sin(now * 0.8f + phase[i]))
            drawCircle(color.copy(alpha = a), radii[i], Offset(x, y))
        }
    }
}

/** Three thin orbits around the circle, each with one slow dot. */
private fun CacheDrawScope.orbits(line: Color, t: () -> Float): DrawResult {
    val mid = Offset(size.width / 2f, size.height / 2f)
    val base = min(size.width, size.height) / 2f
    val stroke = Stroke(width = 1.dp.toPx())
    val dot = 1.75.dp.toPx()
    val ring = line.copy(alpha = 0.07f)
    val body = line.copy(alpha = 0.26f)
    return onDrawBehind {
        val now = t()
        for (k in 0 until 3) {
            val r = base * (1.05f + 0.34f * k)
            drawCircle(ring, r, mid, style = stroke)
            val at = now * (0.10f - 0.025f * k) + k * 2.1f
            drawCircle(body, dot, Offset(mid.x + r * cos(at), mid.y + r * sin(at)))
        }
    }
}

/** A band of light lying along the middle, where the wave runs, with a hairline through it. */
private fun CacheDrawScope.horizon(color: Color, line: Color, t: () -> Float): DrawResult {
    val half = size.height * 0.24f
    val top = size.height / 2f - half
    val band = Brush.verticalGradient(0f to Color.Transparent, 0.5f to color.copy(alpha = 0.12f), 1f to Color.Transparent, startY = top, endY = top + half * 2)
    val edge = Brush.horizontalGradient(0f to Color.Transparent, 0.5f to line.copy(alpha = 0.14f), 1f to Color.Transparent)
    val px = 1.dp.toPx()
    return onDrawBehind {
        drawRect(band, Offset(0f, top), Size(size.width, half * 2), alpha = breath(t()))
        drawRect(edge, Offset(0f, size.height / 2f - px / 2f), Size(size.width, px))
    }
}

/** Three long, slow swells sliding sideways. Each is one path built once and only moved. */
private fun CacheDrawScope.tide(line: Color, t: () -> Float): DrawResult {
    val length = size.width * 0.9f
    val stroke = Stroke(width = 1.25.dp.toPx(), cap = StrokeCap.Round)
    val paths = List(3) { k ->
        val y = size.height * (0.34f + 0.16f * k)
        val height = size.height * (0.030f - 0.006f * k)
        Path().apply {
            val steps = 96
            for (i in 0..steps) {
                // Two whole waves wider than the screen, so sliding by one wave never shows an end.
                val x = -length + (size.width + 2 * length) * i / steps
                val py = y + height * sin((x / length) * 2f * PI.toFloat() + k * 1.3f)
                if (i == 0) moveTo(x, py) else lineTo(x, py)
            }
        }
    }
    val tints = List(3) { k -> line.copy(alpha = 0.10f - 0.025f * k) }
    return onDrawBehind {
        val now = t()
        for (k in 0 until 3) {
            val slide = (now * (7f + 3f * k).dp.toPx()) % length
            translate(left = if (k % 2 == 0) slide else -slide) { drawPath(paths[k], tints[k], style = stroke) }
        }
    }
}

/** Faint circles widening from the middle and fading as they go, like the rings they sit behind. */
private fun CacheDrawScope.ripples(line: Color, t: () -> Float): DrawResult {
    val mid = Offset(size.width / 2f, size.height / 2f)
    val reach = max(size.width, size.height) * 0.62f
    val stroke = Stroke(width = 1.dp.toPx())
    return onDrawBehind {
        val now = t()
        for (k in 0 until 5) {
            val part = (k / 5f + now * 0.025f) % 1f
            drawCircle(line.copy(alpha = 0.11f * (1f - part)), reach * (0.12f + 0.88f * part), mid, style = stroke)
        }
    }
}

/** The middle lifted a touch and the corners let go: nothing moves. */
private fun CacheDrawScope.vignette(color: Color): DrawResult {
    val mid = Offset(size.width / 2f, size.height / 2f)
    val r = max(size.width, size.height) * 0.72f
    val brush = Brush.radialGradient(0f to color.copy(alpha = 0.09f), 0.6f to color.copy(alpha = 0.025f), 1f to Color.Transparent, center = mid, radius = r)
    return onDrawBehind { drawCircle(brush, r, mid) }
}

/** Fine still grain, like film: a few thousand faint points placed once. */
private fun CacheDrawScope.grain(): DrawResult {
    val rnd = Random(11)
    val count = ((size.width * size.height) / (9.dp.toPx() * 9.dp.toPx())).toInt().coerceIn(200, 4_000)
    val light = List(count) { Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height) }
    val px = 1.dp.toPx()
    val tint = Color.White.copy(alpha = 0.055f)
    return onDrawBehind { drawPoints(light, PointMode.Points, tint, strokeWidth = px, cap = StrokeCap.Round) }
}

/** The black the full-screen visualizer sits on. */
internal val Backdrop = Color(0xFF040506)
