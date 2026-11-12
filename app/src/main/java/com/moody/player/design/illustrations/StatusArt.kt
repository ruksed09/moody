package com.moody.player.design.illustrations

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.DrawResult
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moody.player.design.MoodyColors
import com.moody.player.design.MoodyTheme
import com.moody.player.design.components.Motion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/*
 * Small animated drawings for the Media health and Your stats empty states. Same language as
 * Illustrations.kt: real objects in a few flat tones, one teal for the part that matters, no glows.
 * Each one plays once when it appears and then stays still; only the scan shield loops, slowly.
 * They are decorative (the screen says the words), so they carry no semantics.
 *
 * Every drawing is a function of one clock in milliseconds, so the last frame of the animation and
 * the still frame (reduced motion, or `still = true`) are the same picture.
 */

/** True when Android's animations are switched off (animator duration scale 0). */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
}

/** "No problems found": a folder, a teal badge whose check draws itself, then two stars twinkle once. */
@Composable
fun HealthClearArt(modifier: Modifier = Modifier, size: Dp = 140.dp, still: Boolean = false) {
    val pal = StatusPal(MoodyTheme.colors)
    val clock = rememberClock(CLEAR_MS, still)
    Spacer(modifier.size(size).clearAndSetSemantics {}.drawWithCache { healthClear(pal, clock) })
}

/** "Check your library": a shield with a heartbeat line; a teal pulse crosses it every 2.4 s. */
@Composable
fun HealthScanArt(modifier: Modifier = Modifier, size: Dp = 64.dp, still: Boolean = false) {
    val pal = StatusPal(MoodyTheme.colors)
    val frozen = still || rememberReducedMotion()
    val loop: State<Float>? = if (frozen) null else rememberInfiniteTransition(label = "scan")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(SCAN_MS, easing = LinearEasing), RepeatMode.Restart), label = "scan")
    Spacer(modifier.size(size).clearAndSetSemantics {}.drawWithCache { healthScan(pal, loop) })
}

/** "Stats are off": a clock that has stopped, with a pause badge; the minute hand settles once. */
@Composable
fun StatsOffArt(modifier: Modifier = Modifier, size: Dp = 64.dp, still: Boolean = false) {
    val pal = StatusPal(MoodyTheme.colors)
    val clock = rememberClock(OFF_MS, still)
    Spacer(modifier.size(size).clearAndSetSemantics {}.drawWithCache { statsOff(pal, clock) })
}

/** "Nothing counted yet": watch and listen bars grow from a baseline, then a small clock appears. */
@Composable
fun StatsEmptyArt(modifier: Modifier = Modifier, size: Dp = 140.dp, still: Boolean = false) {
    val pal = StatusPal(MoodyTheme.colors)
    val clock = rememberClock(EMPTY_MS, still)
    Spacer(modifier.size(size).clearAndSetSemantics {}.drawWithCache { statsEmpty(pal, clock) })
}

/* ---------- palette (same values as the private one in Illustrations.kt) ---------- */

private class StatusPal(c: MoodyColors) {
    val frame = c.raised
    val back = if (c.isDark) Color(0xFF1D2226) else Color(0xFFD0D8DB)
    val moon = c.accent
    val moonInk = c.accentInk
    val line = c.line2
    val ink = c.text3
    val ink2 = c.text2
    val listen = if (c.isDark) Color(0xFFF08A6B) else Color(0xFFB5522F)
}

/* ---------- clock ---------- */

/** Milliseconds since the drawing appeared, stopping at [total]. Without an animation it reads [total]. */
private class ArtClock(private val total: Float, private val anim: Animatable<Float, AnimationVector1D>?) {
    val ms: Float get() = anim?.value ?: total

    /** 0..1 progress of a step that starts at [start] and lasts [dur], eased to a soft landing. */
    fun step(start: Int, dur: Int): Float = Motion.Ease.transform(linear(start, dur))
    fun linear(start: Int, dur: Int): Float = ((ms - start) / dur).coerceIn(0f, 1f)
}

@Composable
private fun rememberClock(totalMs: Int, still: Boolean): ArtClock {
    val frozen = still || rememberReducedMotion()
    val anim = remember { Animatable(0f) }
    LaunchedEffect(frozen) {
        if (!frozen) anim.animateTo(totalMs.toFloat(), tween(totalMs, easing = LinearEasing))
    }
    return remember(frozen) { ArtClock(totalMs.toFloat(), if (frozen) null else anim) }
}

private const val CLEAR_MS = 1300
private const val SCAN_MS = 2400
private const val OFF_MS = 800
private const val EMPTY_MS = 1050

/* ---------- drawings ---------- */

private fun CacheDrawScope.healthClear(p: StatusPal, clock: ArtClock): DrawResult {
    val s = size.minDimension
    val hair = Stroke(max(1.dp.toPx(), s * 0.008f))
    val body = Rect(s * 0.14f, s * 0.32f, s * 0.86f, s * 0.80f)
    val round = s * 0.07f
    // Back of the folder: the tab and the sheet behind, one outline.
    val tabTop = body.top - s * 0.085f
    val back = Path().apply {
        moveTo(body.left, body.bottom - round)
        lineTo(body.left, tabTop + round * 0.7f)
        quadraticTo(body.left, tabTop, body.left + round * 0.7f, tabTop)
        lineTo(body.left + s * 0.24f, tabTop)
        lineTo(body.left + s * 0.31f, body.top)
        lineTo(body.right - round, body.top)
        quadraticTo(body.right, body.top, body.right, body.top + round)
        lineTo(body.right, body.bottom - round)
        close()
    }
    val front = Path().apply {
        addRoundRect(RoundRect(Rect(body.left, body.top + s * 0.075f, body.right, body.bottom), CornerRadius(round)))
    }
    val badgeAt = Offset(s * 0.75f, s * 0.73f)
    val badgeR = s * 0.15f
    val gap = s * 0.032f
    val check = Path().apply {
        moveTo(badgeAt.x - badgeR * 0.42f, badgeAt.y + badgeR * 0.04f)
        lineTo(badgeAt.x - badgeR * 0.12f, badgeAt.y + badgeR * 0.34f)
        lineTo(badgeAt.x + badgeR * 0.44f, badgeAt.y - badgeR * 0.28f)
    }
    val measure = PathMeasure().apply { setPath(check, false) }
    val checkLen = measure.length
    val checkStroke = Stroke(badgeR * 0.22f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val drawn = Path()
    val cut = Path()
    val bigStar = star(Offset(s * 0.80f, s * 0.16f), s * 0.058f)
    val smallStar = star(Offset(s * 0.62f, s * 0.13f), s * 0.032f)

    return onDrawBehind {
        val enter = clock.step(0, 320)
        val badge = clock.step(200, 320)
        val tick = clock.step(480, 380)
        translate(top = s * 0.035f * (1f - enter)) {
            // The folder, with room cut out for the badge so it reads on any background.
            cut.rewind()
            if (badge > 0f) cut.addOval(Rect(badgeAt, (badgeR + gap) * (0.6f + 0.4f * badge)))
            clipPath(cut, ClipOp.Difference) {
                drawPath(back, p.back.copy(alpha = enter))
                drawPath(back, p.line.copy(alpha = p.line.alpha * enter), style = hair)
                drawPath(front, p.frame.copy(alpha = enter))
                drawPath(front, p.line.copy(alpha = p.line.alpha * enter), style = hair)
            }
            if (badge > 0f) {
                scale(0.6f + 0.4f * badge, badgeAt) { drawCircle(p.moon.copy(alpha = badge), badgeR, badgeAt) }
            }
            if (tick > 0f) {
                drawn.rewind()
                measure.getSegment(0f, checkLen * tick, drawn, true)
                drawPath(drawn, p.moonInk, style = checkStroke)
            }
        }
        twinkle(bigStar, Offset(s * 0.80f, s * 0.16f), p.moon, clock.linear(820, 380))
        twinkle(smallStar, Offset(s * 0.62f, s * 0.13f), p.ink, clock.linear(920, 380))
    }
}

private fun CacheDrawScope.healthScan(p: StatusPal, loop: State<Float>?): DrawResult {
    val s = size.minDimension
    val hair = Stroke(max(1.dp.toPx(), s * 0.012f))
    val shield = shieldPath(Rect(s * 0.16f, s * 0.07f, s * 0.84f, s * 0.94f))
    val y = s * 0.47f
    val amp = s * 0.115f
    // The pulse of the Health icon, scaled to the shield.
    val pulse = Path().apply {
        moveTo(s * 0.27f, y)
        lineTo(s * 0.385f, y)
        lineTo(s * 0.452f, y - amp)
        lineTo(s * 0.548f, y + amp)
        lineTo(s * 0.615f, y)
        lineTo(s * 0.73f, y)
    }
    val measure = PathMeasure().apply { setPath(pulse, false) }
    val len = measure.length
    val window = len * 0.55f
    val stroke = Stroke(max(1.5.dp.toPx(), s * 0.045f), cap = StrokeCap.Round, join = StrokeJoin.Round)
    val lit = Path()
    val rest = p.ink.copy(alpha = 0.45f)

    return onDrawBehind {
        drawPath(shield, p.frame)
        drawPath(shield, p.line, style = hair)
        if (loop == null) {
            drawPath(pulse, p.moon, style = stroke)
        } else {
            drawPath(pulse, rest, style = stroke)
            // The lit part enters from before the line and leaves past its end, then the line rests.
            val travel = FastOutSlowInEasing.transform((loop.value / 0.78f).coerceIn(0f, 1f))
            val head = travel * (len + window)
            val from = (head - window).coerceIn(0f, len)
            val to = head.coerceIn(0f, len)
            if (to - from > 0.5f) {
                lit.rewind()
                measure.getSegment(from, to, lit, true)
                drawPath(lit, p.moon, style = stroke)
            }
        }
    }
}

private fun CacheDrawScope.statsOff(p: StatusPal, clock: ArtClock): DrawResult {
    val s = size.minDimension
    val hair = Stroke(max(1.dp.toPx(), s * 0.012f))
    val c = Offset(s * 0.45f, s * 0.45f)
    val r = s * 0.36f
    val badgeAt = Offset(s * 0.75f, s * 0.75f)
    val badgeR = s * 0.17f
    val cut = Path().apply { addOval(Rect(badgeAt, badgeR + s * 0.04f)) }
    // Pause badge with the two bars cut out of it, so whatever is behind shows through.
    val barW = badgeR * 0.24f
    val barH = badgeR * 0.84f
    val bars = Path().apply {
        for (side in intArrayOf(-1, 1)) {
            val x = badgeAt.x + side * badgeR * 0.26f - barW / 2f
            addRoundRect(RoundRect(Rect(x, badgeAt.y - barH / 2f, x + barW, badgeAt.y + barH / 2f), CornerRadius(barW * 0.35f)))
        }
    }
    val badge = Path.combine(PathOperation.Difference, Path().apply { addOval(Rect(badgeAt, badgeR)) }, bars)
    val hand = max(1.5.dp.toPx(), s * 0.05f)
    val tick = max(1.dp.toPx(), s * 0.028f)

    return onDrawBehind {
        clipPath(cut, ClipOp.Difference) {
            drawCircle(p.frame, r, c)
            drawCircle(p.line, r, c, style = hair)
            for (i in 0 until 4) {
                val a = i * (PI.toFloat() / 2f)
                drawLine(p.ink.copy(alpha = 0.55f), c + polar(r * 0.74f, a), c + polar(r * 0.86f, a), tick, StrokeCap.Round)
            }
            // Hour hand at ten; the minute hand coasts to a stop at two.
            drawLine(p.ink2, c, c + polar(r * 0.44f, HOUR_AT), hand, StrokeCap.Round)
            val minute = MINUTE_AT - MINUTE_COAST * (1f - clock.step(120, 620))
            drawLine(p.moon, c, c + polar(r * 0.66f, minute), hand, StrokeCap.Round)
            drawCircle(p.ink2, hand * 0.85f, c)
        }
        drawPath(badge, p.ink)
    }
}

private fun CacheDrawScope.statsEmpty(p: StatusPal, clock: ArtClock): DrawResult {
    val s = size.minDimension
    val base = s * 0.80f
    val foot = base - s * 0.04f
    val colW = s * 0.094f
    val pitch = s * 0.14f
    val left = (s - (pitch * (COLS.size - 1) + colW)) / 2f
    val split = s * 0.014f
    val round = s * 0.022f
    val corner = CornerRadius(round)
    val baseStroke = max(1.dp.toPx(), s * 0.016f)
    val glyphAt = Offset(left + pitch * 0.5f + colW / 2f, s * 0.33f)
    val glyphR = s * 0.085f
    val glyphStroke = Stroke(max(1.dp.toPx(), s * 0.018f), cap = StrokeCap.Round)

    return onDrawBehind {
        drawLine(p.ink.copy(alpha = 0.5f), Offset(s * 0.13f, base), Offset(s * 0.87f, base), baseStroke, StrokeCap.Round)
        for (i in COLS.indices) {
            val grow = clock.step(80 + i * 70, 420)
            if (grow <= 0f) continue
            val h = s * COLS[i] * grow
            val watch = h * WATCH[i]
            val x = left + i * pitch
            // Watching at the bottom, listening stacked on top, as in the real chart.
            // A bar that has barely started is flatter than its corners, so the corners shrink with it.
            drawRoundRect(p.moon, Offset(x, foot - watch), Size(colW, watch), if (watch < round * 2f) CornerRadius(watch / 2f) else corner)
            val listen = h - watch - split
            if (WATCH[i] < 1f && listen > 0f) drawRoundRect(p.listen, Offset(x, foot - h), Size(colW, listen), if (listen < round * 2f) CornerRadius(listen / 2f) else corner)
        }
        val g = clock.step(720, 320)
        if (g > 0f) {
            val ink = p.ink.copy(alpha = g)
            scale(0.7f + 0.3f * g, glyphAt) {
                drawCircle(ink, glyphR, glyphAt, style = glyphStroke)
                drawLine(ink, glyphAt, glyphAt + polar(glyphR * 0.52f, 0f), glyphStroke.width, StrokeCap.Round)
                drawLine(ink, glyphAt, glyphAt + polar(glyphR * 0.42f, PI.toFloat() * 0.62f), glyphStroke.width, StrokeCap.Round)
            }
        }
    }
}

/** Column heights (share of the canvas) and how much of each is watching. */
private val COLS = floatArrayOf(0.15f, 0.25f, 0.19f, 0.37f, 0.50f)
private val WATCH = floatArrayOf(1f, 0.58f, 1f, 0.62f, 0.56f)

private const val HOUR_AT = PI.toFloat() * 2f * 10f / 12f
private const val MINUTE_AT = PI.toFloat() * 2f * 2f / 12f
private const val MINUTE_COAST = 0.9f

/* ---------- helpers ---------- */

/** A point [r] from the origin at clock angle [a] (radians, 0 = twelve o'clock, clockwise). */
private fun polar(r: Float, a: Float) = Offset(r * sin(a), -r * cos(a))

/** A star swells past its size and settles at it, fading in on the way. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.twinkle(star: Path, at: Offset, color: Color, t: Float) {
    if (t <= 0f) return
    val rise = Motion.Ease.transform((t * 2f).coerceAtMost(1f))
    scale(rise * (1f + 0.45f * sin(PI.toFloat() * t)), at) { drawPath(star, color.copy(alpha = rise)) }
}

/** Four-point star with pinched sides. */
private fun star(c: Offset, r: Float): Path = Path().apply {
    val k = r * 0.16f
    moveTo(c.x, c.y - r)
    quadraticTo(c.x + k, c.y - k, c.x + r, c.y)
    quadraticTo(c.x + k, c.y + k, c.x, c.y + r)
    quadraticTo(c.x - k, c.y + k, c.x - r, c.y)
    quadraticTo(c.x - k, c.y - k, c.x, c.y - r)
    close()
}

private fun shieldPath(r: Rect): Path = Path().apply {
    moveTo(r.center.x, r.top)
    lineTo(r.right, r.top + r.height * 0.14f)
    lineTo(r.right, r.top + r.height * 0.46f)
    cubicTo(r.right, r.top + r.height * 0.76f, r.center.x + r.width * 0.18f, r.bottom - r.height * 0.06f, r.center.x, r.bottom)
    cubicTo(r.center.x - r.width * 0.18f, r.bottom - r.height * 0.06f, r.left, r.top + r.height * 0.76f, r.left, r.top + r.height * 0.46f)
    lineTo(r.left, r.top + r.height * 0.14f)
    close()
}
