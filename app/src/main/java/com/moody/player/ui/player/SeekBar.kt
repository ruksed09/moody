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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import com.moody.player.design.MoodyTheme
import com.moody.player.design.OnVideo
import com.moody.player.design.Radii
import com.moody.player.design.components.Motion
import com.moody.player.domain.formatDuration

/**
 * The reel line, grown into a seek bar: thin rail, amber fill, a knob and a time tip while dragging.
 * [abA]/[abB] mark an A-B loop on the rail.
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
) {
    val accent = MoodyTheme.colors.accent
    var width by remember { mutableStateOf(1f) }
    var dragging by remember { mutableStateOf(false) }
    var dragFrac by remember { mutableStateOf(0f) }
    val frac = if (dragging) dragFrac else if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val rail by animateDpAsState(if (dragging) 6.dp else 4.dp, Motion.fast(), label = "rail")
    val density = LocalDensity.current

    BoxWithConstraints(modifier.fillMaxWidth().height(36.dp)) {
        if (dragging) {
            val tipX = with(density) { (frac * width).toDp() } - 28.dp
            Text(
                formatDuration((dragFrac * durationMs).toLong()).let { if (it == "--:--") "0:00" else it },
                style = MoodyTheme.type.mono,
                color = OnVideo.text,
                modifier = Modifier
                    .offset(x = tipX.coerceIn(0.dp, maxWidth - 56.dp), y = (-34).dp)
                    .clip(Radii.sm)
                    .background(OnVideo.ink)
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
                    contentDescription = "Seek"
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
            val h = rail.toPx()
            val y = size.height / 2 - h / 2
            val r = CornerRadius(h / 2)
            drawRoundRect(OnVideo.rail, Offset(0f, y), Size(size.width, h), r)
            if (abA != null && durationMs > 0) {
                val ax = abA / durationMs.toFloat() * size.width
                val bx = (abB ?: positionMs) / durationMs.toFloat() * size.width
                drawRect(accent.copy(alpha = 0.3f), Offset(ax, y - 3.dp.toPx()), Size((bx - ax).coerceAtLeast(2f), h + 6.dp.toPx()))
            }
            drawRoundRect(accent, Offset(0f, y), Size(size.width * frac, h), r)
            val knob = if (dragging) 9.dp.toPx() else 7.dp.toPx()
            drawCircle(accent.copy(alpha = 0.25f), knob + 4.dp.toPx(), Offset(size.width * frac, size.height / 2))
            drawCircle(accent, knob, Offset(size.width * frac, size.height / 2))
        }
    }
}
