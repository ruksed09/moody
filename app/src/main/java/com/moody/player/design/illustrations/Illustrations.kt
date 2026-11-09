package com.moody.player.design.illustrations

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.moody.player.design.MoodyColors
import com.moody.player.design.MoodyTheme
import kotlin.math.PI
import kotlin.math.sin

/*
 * Moody's illustration language: a late-night screening room drawn in few tones.
 * Header backdrops stay hairline and quiet; the empty-state and welcome scenes are the only "painted" moments.
 * No glows, grain or blobs. Every shape is a real object from the world: moon, hills, film, screen, record, shield.
 */

enum class BackdropKind { VIDEO, MUSIC, PLAIN }

private fun crescent(center: Offset, r: Float, cut: Offset): Path {
    val moon = Path().apply { addOval(Rect(center, r)) }
    val bite = Path().apply { addOval(Rect(center + cut, r * 0.86f)) }
    return Path.combine(PathOperation.Difference, moon, bite)
}

private fun hills(w: Float, top: Float, bottom: Float, phase: Float, amp: Float): Path = Path().apply {
    moveTo(0f, bottom)
    lineTo(0f, top + amp * sin(phase))
    val steps = 24
    for (i in 1..steps) {
        val x = w * i / steps
        val y = top + amp * sin(phase + i / steps.toFloat() * PI.toFloat() * 1.6f) * (0.6f + 0.4f * sin(i * 0.7f))
        lineTo(x, y)
    }
    lineTo(w, bottom)
    close()
}

private fun ridge(w: Float, top: Float, phase: Float, amp: Float): Path = Path().apply {
    val steps = 24
    for (i in 0..steps) {
        val x = w * i / steps
        val y = if (i == 0) top + amp * sin(phase)
        else top + amp * sin(phase + i / steps.toFloat() * PI.toFloat() * 1.6f) * (0.6f + 0.4f * sin(i * 0.7f))
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
}

/** Quiet header drawing behind a tab title. Fades into the page background at the bottom. */
@Composable
fun MoodBackdrop(kind: BackdropKind, modifier: Modifier = Modifier) {
    val c = MoodyTheme.colors
    Canvas(modifier.fillMaxWidth().height(200.dp)) {
        val w = size.width
        val h = size.height
        val hair = Stroke(width = 1.dp.toPx())
        val line = c.text.copy(alpha = if (c.isDark) 0.08f else 0.10f)

        // Moon outline, right of the title, clear of the top bar.
        drawPath(crescent(Offset(w * 0.88f, h * 0.20f), h * 0.10f, Offset(-h * 0.045f, -h * 0.028f)), c.accent.copy(alpha = 0.42f), style = hair)

        // Two horizon lines (open strokes, so no edge is drawn along the bottom).
        drawPath(hills(w, h * 0.70f, h, 0.4f, h * 0.05f), c.text.copy(alpha = 0.018f))
        drawPath(ridge(w, h * 0.70f, 0.4f, h * 0.05f), line, style = hair)
        drawPath(ridge(w, h * 0.80f, 2.1f, h * 0.035f), line, style = hair)

        when (kind) {
            BackdropKind.VIDEO -> filmRibbon(w, h, line)
            BackdropKind.MUSIC -> soundLines(w, h, line)
            BackdropKind.PLAIN -> Unit
        }
        // Fade into the page.
        drawRect(Brush.verticalGradient(0.55f to Color.Transparent, 1f to c.bg))
    }
}

private fun DrawScope.filmRibbon(w: Float, h: Float, line: Color) {
    val top = Path()
    val bottom = Path()
    val gap = h * 0.075f
    val steps = 40
    for (i in 0..steps) {
        val x = w * i / steps
        val y = h * 0.50f - h * 0.14f * sin(i / steps.toFloat() * PI.toFloat() * 0.9f + 0.3f)
        if (i == 0) { top.moveTo(x, y); bottom.moveTo(x, y + gap) } else { top.lineTo(x, y); bottom.lineTo(x, y + gap) }
        if (i % 2 == 0) {
            drawRoundRect(line, Offset(x - 2.dp.toPx(), y + gap * 0.32f), Size(4.dp.toPx(), gap * 0.36f), CornerRadius(1.dp.toPx()))
        }
    }
    drawPath(top, line, style = Stroke(1.dp.toPx()))
    drawPath(bottom, line, style = Stroke(1.dp.toPx()))
}

private fun DrawScope.soundLines(w: Float, h: Float, line: Color) {
    for (k in 0 until 3) {
        val p = Path()
        val steps = 60
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            val env = sin(t * PI.toFloat())
            val y = h * (0.46f + k * 0.06f) + h * 0.07f * env * sin(t * PI.toFloat() * (5f + k) + k)
            if (i == 0) p.moveTo(w * t, y) else p.lineTo(w * t, y)
        }
        drawPath(p, line, style = Stroke(1.dp.toPx()))
    }
}

/* ---------- painted scenes ---------- */

private class Pal(c: MoodyColors) {
    val frame = c.raised
    val deep = if (c.isDark) Color(0xFF08070B) else Color(0xFFE6DACB)
    val hillA = if (c.isDark) Color(0xFF221F2A) else Color(0xFFDCCDBB)
    val hillB = if (c.isDark) Color(0xFF1A1820) else Color(0xFFE7DBCC)
    val moon = c.accent
    val line = c.line2
    val ink = c.text3
}

@Composable
private fun Scene(modifier: Modifier, draw: DrawScope.(Pal) -> Unit) {
    val pal = Pal(MoodyTheme.colors)
    Canvas(modifier.size(220.dp, 160.dp)) { draw(pal) }
}

/** A film frame at night: moon over hills, sprocket holes either side. */
@Composable
fun EmptyFilm(modifier: Modifier = Modifier) = Scene(modifier) { p ->
    val w = size.width; val h = size.height
    val frame = Rect(w * 0.14f, h * 0.10f, w * 0.86f, h * 0.90f)
    drawRoundRect(p.frame, frame.topLeft, frame.size, CornerRadius(14.dp.toPx()))
    val screen = Rect(frame.left + w * 0.10f, frame.top + h * 0.10f, frame.right - w * 0.10f, frame.bottom - h * 0.10f)
    drawRoundRect(p.deep, screen.topLeft, screen.size, CornerRadius(6.dp.toPx()))
    clipScreen(screen) {
        drawPath(hills(screen.width, screen.height * 0.62f, screen.height, 0.8f, screen.height * 0.07f).shift(screen.topLeft), p.hillA)
        drawPath(hills(screen.width, screen.height * 0.78f, screen.height, 2.6f, screen.height * 0.05f).shift(screen.topLeft), p.hillB)
        drawPath(crescent(Offset(screen.left + screen.width * 0.70f, screen.top + screen.height * 0.32f), screen.height * 0.14f, Offset(-screen.height * 0.06f, -screen.height * 0.04f)), p.moon)
        drawCircle(p.ink, 1.4.dp.toPx(), Offset(screen.left + screen.width * 0.25f, screen.top + screen.height * 0.22f))
        drawCircle(p.ink, 1.dp.toPx(), Offset(screen.left + screen.width * 0.42f, screen.top + screen.height * 0.14f))
    }
    for (i in 0 until 5) {
        val y = frame.top + h * 0.12f + i * (frame.height - h * 0.24f) / 4f - 3.dp.toPx()
        drawRoundRect(p.deep, Offset(frame.left + w * 0.035f, y), Size(w * 0.03f, 6.dp.toPx()), CornerRadius(1.5.dp.toPx()))
        drawRoundRect(p.deep, Offset(frame.right - w * 0.065f, y), Size(w * 0.03f, 6.dp.toPx()), CornerRadius(1.5.dp.toPx()))
    }
}

/** A record with a crescent on its label and a resting tonearm. */
@Composable
fun EmptyMusic(modifier: Modifier = Modifier) = Scene(modifier) { p ->
    val w = size.width; val h = size.height
    val c = Offset(w * 0.46f, h * 0.52f)
    val r = h * 0.40f
    drawCircle(p.deep, r, c)
    for (k in 1..5) drawCircle(p.line, r * (0.45f + k * 0.1f), c, style = Stroke(0.8.dp.toPx()))
    drawCircle(p.frame, r * 0.34f, c)
    drawPath(crescent(c, r * 0.20f, Offset(-r * 0.08f, -r * 0.05f)), p.moon)
    drawCircle(p.deep, 2.dp.toPx(), c)
    // Tonearm.
    val pivot = Offset(w * 0.86f, h * 0.16f)
    drawCircle(p.frame, 7.dp.toPx(), pivot)
    drawLine(p.ink, pivot, Offset(w * 0.78f, h * 0.70f), 3.dp.toPx(), StrokeCap.Round)
    drawLine(p.ink, Offset(w * 0.78f, h * 0.70f), Offset(w * 0.70f, h * 0.76f), 5.dp.toPx(), StrokeCap.Round)
}

/** A shield with a keyhole; the moon rises behind it. */
@Composable
fun EmptyVault(modifier: Modifier = Modifier) = Scene(modifier) { p ->
    val w = size.width; val h = size.height
    drawPath(crescent(Offset(w * 0.68f, h * 0.28f), h * 0.16f, Offset(-h * 0.07f, -h * 0.04f)), p.moon)
    val s = shield(Rect(w * 0.30f, h * 0.12f, w * 0.70f, h * 0.92f))
    drawPath(s, p.frame)
    drawPath(s, p.line, style = Stroke(1.dp.toPx()))
    val kc = Offset(w * 0.5f, h * 0.46f)
    drawCircle(p.deep, h * 0.07f, kc)
    drawRoundRect(p.deep, Offset(kc.x - h * 0.025f, kc.y), Size(h * 0.05f, h * 0.16f), CornerRadius(h * 0.02f))
}

/** A folder held shut by a small padlock. */
@Composable
fun NoAccess(modifier: Modifier = Modifier) = Scene(modifier) { p ->
    val w = size.width; val h = size.height
    val body = Rect(w * 0.20f, h * 0.26f, w * 0.80f, h * 0.84f)
    val tab = Path().apply {
        moveTo(body.left, body.top + 8.dp.toPx())
        lineTo(body.left, body.top - h * 0.08f + 6.dp.toPx())
        quadraticTo(body.left, body.top - h * 0.08f, body.left + 6.dp.toPx(), body.top - h * 0.08f)
        lineTo(body.left + w * 0.20f, body.top - h * 0.08f)
        lineTo(body.left + w * 0.25f, body.top)
        close()
    }
    drawPath(tab, p.hillA)
    drawRoundRect(p.frame, body.topLeft, body.size, CornerRadius(10.dp.toPx()))
    val lc = Offset(w * 0.5f, h * 0.60f)
    drawArc(p.ink, 180f, 180f, false, Offset(lc.x - h * 0.07f, lc.y - h * 0.15f), Size(h * 0.14f, h * 0.16f), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
    drawRoundRect(p.moon, Offset(lc.x - h * 0.11f, lc.y - h * 0.06f), Size(h * 0.22f, h * 0.17f), CornerRadius(4.dp.toPx()))
    drawCircle(p.deep, 2.5.dp.toPx(), Offset(lc.x, lc.y + h * 0.02f))
}

/** Three welcome scenes: the screen, the vault, the record. */
@Composable
fun WelcomeArt(page: Int, modifier: Modifier = Modifier) {
    val pal = Pal(MoodyTheme.colors)
    Canvas(modifier.fillMaxWidth().aspectRatio(1.25f)) {
        when (page) {
            0 -> screeningRoom(pal)
            1 -> vaultScene(pal)
            else -> recordScene(pal)
        }
    }
}

private fun DrawScope.screeningRoom(p: Pal) {
    val w = size.width; val h = size.height
    val screen = Rect(w * 0.22f, h * 0.14f, w * 0.90f, h * 0.62f)
    // Projector beam: one hue, fading with distance, as light does.
    val beam = Path().apply {
        moveTo(w * 0.08f, h * 0.80f)
        lineTo(screen.left, screen.top)
        lineTo(screen.left, screen.bottom)
        close()
    }
    drawPath(beam, Brush.linearGradient(listOf(p.moon.copy(alpha = 0.22f), p.moon.copy(alpha = 0.03f)), Offset(w * 0.08f, h * 0.8f), Offset(screen.left, screen.center.y)))
    drawRoundRect(p.deep, screen.topLeft, screen.size, CornerRadius(8.dp.toPx()))
    clipScreen(screen) {
        drawPath(hills(screen.width, screen.height * 0.66f, screen.height, 1.1f, screen.height * 0.06f).shift(screen.topLeft), p.hillA)
        drawPath(crescent(Offset(screen.left + screen.width * 0.78f, screen.top + screen.height * 0.30f), screen.height * 0.12f, Offset(-screen.height * 0.05f, -screen.height * 0.03f)), p.moon)
    }
    // Play mark centred on the screen.
    val c = screen.center
    val r = screen.height * 0.13f
    drawCircle(p.frame, r, c)
    val tri = Path().apply {
        moveTo(c.x - r * 0.30f, c.y - r * 0.42f); lineTo(c.x + r * 0.46f, c.y)
        lineTo(c.x - r * 0.30f, c.y + r * 0.42f); quadraticTo(c.x - r * 0.05f, c.y, c.x - r * 0.30f, c.y - r * 0.42f); close()
    }
    drawPath(tri, p.moon)
    // Seat backs.
    for (i in 0 until 5) {
        val x = w * (0.18f + i * 0.17f)
        drawRoundRect(p.frame, Offset(x, h * 0.78f), Size(w * 0.13f, h * 0.22f), CornerRadius(10.dp.toPx()))
    }
}

private fun DrawScope.vaultScene(p: Pal) {
    val w = size.width; val h = size.height
    drawPath(crescent(Offset(w * 0.70f, h * 0.24f), h * 0.12f, Offset(-h * 0.05f, -h * 0.03f)), p.moon)
    val s = shield(Rect(w * 0.32f, h * 0.10f, w * 0.68f, h * 0.84f))
    drawPath(s, p.frame)
    drawPath(s, p.line, style = Stroke(1.dp.toPx()))
    val dots = 4
    for (i in 0 until dots) {
        val x = w * 0.5f + (i - (dots - 1) / 2f) * w * 0.06f
        drawCircle(if (i < 3) p.moon else p.deep, w * 0.016f, Offset(x, h * 0.44f))
    }
    drawRoundRect(p.deep, Offset(w * 0.41f, h * 0.56f), Size(w * 0.18f, h * 0.06f), CornerRadius(h * 0.03f))
    drawPath(hills(w, h * 0.88f, h, 0.5f, h * 0.02f), p.hillB)
}

private fun DrawScope.recordScene(p: Pal) {
    val w = size.width; val h = size.height
    val c = Offset(w * 0.42f, h * 0.52f)
    val r = h * 0.36f
    drawCircle(p.deep, r, c)
    for (k in 1..6) drawCircle(p.line, r * (0.40f + k * 0.09f), c, style = Stroke(0.8.dp.toPx()))
    drawCircle(p.frame, r * 0.32f, c)
    drawPath(crescent(c, r * 0.18f, Offset(-r * 0.07f, -r * 0.05f)), p.moon)
    // Headphone band arcing over the record.
    drawArc(p.ink, 200f, 140f, false, Offset(c.x - r * 1.15f, c.y - r * 1.25f), Size(r * 2.3f, r * 2.3f), style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
    // Sound lines to the right.
    for (k in 0 until 3) {
        val x = w * (0.80f + k * 0.05f)
        drawLine(p.moon.copy(alpha = 0.8f - k * 0.22f), Offset(x, h * (0.40f - k * 0.04f)), Offset(x, h * (0.64f + k * 0.04f)), 3.dp.toPx(), StrokeCap.Round)
    }
}

/* ---------- helpers ---------- */

private fun shield(r: Rect): Path = Path().apply {
    moveTo(r.center.x, r.top)
    lineTo(r.right, r.top + r.height * 0.14f)
    lineTo(r.right, r.top + r.height * 0.46f)
    cubicTo(r.right, r.top + r.height * 0.76f, r.center.x + r.width * 0.18f, r.bottom - r.height * 0.06f, r.center.x, r.bottom)
    cubicTo(r.center.x - r.width * 0.18f, r.bottom - r.height * 0.06f, r.left, r.top + r.height * 0.76f, r.left, r.top + r.height * 0.46f)
    lineTo(r.left, r.top + r.height * 0.14f)
    close()
}

private fun Path.shift(o: Offset): Path = apply { translate(o) }

private inline fun DrawScope.clipScreen(r: Rect, block: DrawScope.() -> Unit) {
    val clip = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(r, CornerRadius(6.dp.toPx()))) }
    drawContext.canvas.save()
    drawContext.canvas.clipPath(clip)
    block()
    drawContext.canvas.restore()
}

/** Rotation helper kept for the logo animation's crescent. */
fun DrawScope.drawCrescent(center: Offset, radius: Float, color: Color, angle: Float = 0f) {
    rotate(angle, center) { drawPath(crescent(center, radius, Offset(-radius * 0.42f, -radius * 0.26f)), color) }
}
