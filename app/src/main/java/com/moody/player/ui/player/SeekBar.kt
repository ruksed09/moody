package com.moody.player.ui.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.moody.player.design.MoodyTheme
import com.moody.player.design.OnVideo
import com.moody.player.design.Radii
import com.moody.player.design.components.Motion
import com.moody.player.domain.formatDuration

/** A bookmark as the seek bar shows it: a moment ([endMs] null) or a saved range, with its name. */
data class SeekMark(val startMs: Long, val endMs: Long?, val label: String) {
    val isRange get() = endMs != null && endMs > startMs
}

/** Most bookmarks one video keeps. More would only be noise on a seek bar. */
const val MAX_BOOKMARKS = 100

/**
 * Puts saved ranges into lanes above the rail so overlapping ones never share a line. [ranges] are sorted by
 * start; a range is at least [minSpanMs] long here (how wide it is drawn, plus a gap). Gives each range its
 * lane, or -1 when every lane is taken there (those are drawn together as one dotted line in the top lane).
 */
internal fun packLanes(ranges: List<SeekMark>, lanes: Int, minSpanMs: Long): IntArray {
    val laneEnd = LongArray(lanes) { Long.MIN_VALUE }
    return IntArray(ranges.size) { i ->
        val r = ranges[i]
        val end = maxOf(r.endMs ?: r.startMs, r.startMs + minSpanMs)
        val lane = (0 until lanes).firstOrNull { laneEnd[it] < r.startMs }
        if (lane != null) { laneEnd[lane] = end; lane } else -1
    }
}

/**
 * The one range that counts as "here" at [atMs]: of the ranges around it, the one that started last (the
 * one just entered); if two start together, the shorter. Used by the seek bar and its time tip alike.
 */
internal fun activeRange(marks: List<SeekMark>, atMs: Long): SeekMark? =
    marks.filter { it.isRange && atMs >= it.startMs && atMs <= it.endMs!! }
        .maxWithOrNull(compareBy<SeekMark> { it.startMs }.thenByDescending { it.endMs!! - it.startMs })

/** "1:13:00 · Chorus", with "+1" when more ranges cover that moment. Long names are cut short. */
internal fun scrubLabel(time: String, marks: List<SeekMark>, atMs: Long, nearMoment: SeekMark?): String {
    val name = nearMoment?.label ?: activeRange(marks, atMs)?.label ?: return time
    val more = if (nearMoment != null) 0 else marks.count { it.isRange && atMs >= it.startMs && atMs <= it.endMs!! } - 1
    val short = if (name.length > 24) name.take(23).trimEnd() + "\u2026" else name
    return "$time \u00b7 $short" + if (more > 0) " +$more" else ""
}

/**
 * The reel line, grown into a seek bar: thin rail, amber fill, a knob and a time tip while dragging.
 * [abA]/[abB] mark an A-B loop on the rail. [marks] are bookmarks: moments are small dots on the rail (dots
 * that would touch merge into one), saved ranges are thin lines in lanes just above it, so overlapping ranges
 * sit on separate lines. The range playing now is drawn brighter. While dragging, the time tip names the
 * bookmark under the finger.
 */
@Composable
fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    onScrubbing: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    abA: Long? = null,
    abB: Long? = null,
    marks: List<SeekMark> = emptyList(),
    rail: androidx.compose.ui.graphics.Color = OnVideo.rail,
    tipBg: androidx.compose.ui.graphics.Color = OnVideo.ink,
    tipText: androidx.compose.ui.graphics.Color = OnVideo.text,
) {
    val accent = MoodyTheme.colors.accent
    var width by remember { mutableStateOf(1f) }
    var dragging by remember { mutableStateOf(false) }
    var dragFrac by remember { mutableStateOf(0f) }
    val frac = if (dragging) dragFrac else if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val railH by animateDpAsState(if (dragging) 6.dp else 4.dp, Motion.fast(), label = "rail")
    val density = LocalDensity.current
    val moments = remember(marks) { marks.filter { !it.isRange }.sortedBy { it.startMs } }
    val ranges = remember(marks) { marks.filter { it.isRange }.sortedBy { it.startMs } }

    BoxWithConstraints(modifier.fillMaxWidth().height(36.dp)) {
        // Two lanes in portrait, three on wider screens: the knob and the time tip need the rest.
        val laneCount = if (maxWidth < 600.dp) 2 else 3
        if (dragging) {
            val at = (dragFrac * durationMs).toLong()
            val near = with(density) { 8.dp.toPx() }
            val nearMoment = if (durationMs > 0) moments.minByOrNull { kotlin.math.abs(it.startMs - at) }
                ?.takeIf { kotlin.math.abs(it.startMs - at) / durationMs.toFloat() * width <= near } else null
            val time = formatDuration(at).let { if (it == "--:--") "0:00" else it }
            val tipCenter = frac * width
            Text(
                scrubLabel(time, ranges, at, nearMoment),
                style = MoodyTheme.type.mono,
                color = tipText,
                maxLines = 1,
                modifier = Modifier
                    .offset(y = (-34).dp)
                    // Centred over the finger, kept on screen whatever its width.
                    .layout { m, c ->
                        val p = m.measure(c.copy(minWidth = 0))
                        layout(c.maxWidth, p.height) {
                            p.place((tipCenter - p.width / 2f).roundToInt().coerceIn(0, (c.maxWidth - p.width).coerceAtLeast(0)), 0)
                        }
                    }
                    .clip(Radii.sm)
                    .background(tipBg)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .align(Alignment.Center)
                .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
                .semantics {
                    contentDescription = if (marks.isEmpty()) "Seek" else "Seek, ${marks.size} ${if (marks.size == 1) "bookmark" else "bookmarks"}"
                    progressBarRangeInfo = ProgressBarRangeInfo(frac, 0f..1f)
                    setProgress { v -> onSeek((v * durationMs).toLong()); true }
                }
                .pointerInput(durationMs) {
                    detectTapGestures { o -> if (durationMs > 0) onSeek((o.x / size.width * durationMs).toLong()) }
                }
                .pointerInput(durationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { o -> dragging = true; dragFrac = (o.x / size.width).coerceIn(0f, 1f); onScrubbing(true) },
                        onDragEnd = { dragging = false; onSeek((dragFrac * durationMs).toLong()); onScrubbing(false) },
                        onDragCancel = { dragging = false; onScrubbing(false) },
                    ) { change, _ -> change.consume(); dragFrac = (change.position.x / size.width).coerceIn(0f, 1f) }
                },
        ) {
            val h = railH.toPx()
            val y = size.height / 2 - h / 2
            val r = CornerRadius(h / 2)
            drawRoundRect(rail, Offset(0f, y), Size(size.width, h), r)
            if (abA != null && durationMs > 0) {
                val ax = abA / durationMs.toFloat() * size.width
                val bx = (abB ?: positionMs) / durationMs.toFloat() * size.width
                drawRect(accent.copy(alpha = 0.3f), Offset(ax, y - 3.dp.toPx()), Size((bx - ax).coerceAtLeast(2f), h + 6.dp.toPx()))
            }
            drawRoundRect(accent, Offset(0f, y), Size(size.width * frac, h), r)
            if (durationMs > 0 && marks.isNotEmpty()) {
                fun xOf(ms: Long) = (ms / durationMs.toFloat()).coerceIn(0f, 1f) * size.width
                val msPerPx = durationMs / size.width
                // While an A-B loop is being set, its band leads and the saved ranges step back.
                val dim = if (abA != null) 0.3f else 1f
                val edge = Color.Black.copy(alpha = 0.25f * dim)

                // Saved ranges: thin lines in lanes above the rail.
                val line = 2.dp.toPx()
                val minW = 4.dp.toPx()
                val lanes = packLanes(ranges, laneCount, ((minW + 2.dp.toPx()) * msPerPx).toLong())
                val active = activeRange(ranges, positionMs)
                // Lanes start just above the knob's halo, so the range playing now is never hidden under it.
                fun laneY(lane: Int) = size.height / 2 - 12.5.dp.toPx() - lane * (line + 1.5.dp.toPx())
                fun drawRange(m: SeekMark, lane: Int, isActive: Boolean, dashed: Boolean) {
                    val sx = xOf(m.startMs)
                    val ex = maxOf(xOf(m.endMs!!), sx + minW)
                    val ly = laneY(lane)
                    val w = if (isActive) 3.dp.toPx() else line
                    val color = Color.White.copy(alpha = (if (isActive) 0.85f else 0.38f) * dim)
                    val dash = if (dashed) androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx())) else null
                    drawLine(edge, Offset(sx, ly), Offset(ex, ly), w + 1.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round, dash)
                    drawLine(color, Offset(sx, ly), Offset(ex, ly), w, androidx.compose.ui.graphics.StrokeCap.Round, dash)
                    // End caps once the line is long enough to show both ends apart.
                    if (!dashed && ex - sx >= 12.dp.toPx()) {
                        val cap = 2.dp.toPx()
                        drawLine(color, Offset(sx, ly - cap), Offset(sx, ly + cap), 1.dp.toPx())
                        drawLine(color, Offset(ex, ly - cap), Offset(ex, ly + cap), 1.dp.toPx())
                    }
                }
                // Ranges with no lane left share the top one as a dim dotted line; the range playing now is
                // drawn last, solid and brighter, in its own lane (the top one if it had none).
                ranges.forEachIndexed { i, m ->
                    if (m != active) drawRange(m, if (lanes[i] < 0) laneCount - 1 else lanes[i], isActive = false, dashed = lanes[i] < 0)
                }
                active?.let { m -> val l = lanes[ranges.indexOf(m)]; drawRange(m, if (l < 0) laneCount - 1 else l, isActive = true, dashed = false) }

                // Moments: dots on the rail; dots closer than 6 dp become one slightly bigger dot.
                val gap = 6.dp.toPx()
                var i = 0
                while (i < moments.size) {
                    var j = i
                    while (j + 1 < moments.size && xOf(moments[j + 1].startMs) - xOf(moments[j].startMs) < gap) j++
                    val cx = (xOf(moments[i].startMs) + xOf(moments[j].startMs)) / 2
                    val dot = if (j > i) 3.dp.toPx() else 2.dp.toPx()
                    val c = Offset(cx, size.height / 2)
                    drawCircle(Color.Black.copy(alpha = 0.3f * dim), dot + 1.dp.toPx(), c)
                    drawCircle(Color.White.copy(alpha = 0.7f * dim), dot, c)
                    i = j + 1
                }
            }
            val knob = if (dragging) 9.dp.toPx() else 7.dp.toPx()
            drawCircle(accent.copy(alpha = 0.25f), knob + 4.dp.toPx(), Offset(size.width * frac, size.height / 2))
            drawCircle(accent, knob, Offset(size.width * frac, size.height / 2))
        }
    }
}
