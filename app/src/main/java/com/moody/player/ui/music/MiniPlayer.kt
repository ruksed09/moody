package com.moody.player.ui.music

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moody.player.design.MoodyTheme
import com.moody.player.design.Radii
import com.moody.player.design.components.IconAction
import com.moody.player.design.components.Motion
import com.moody.player.design.icons.MoodyIcons
import com.moody.player.player.music.MusicState

/**
 * Docks above the tab bar while something is queued: the song's disc (turning while it plays) inside a
 * thin ring that fills with progress. Tap opens Now Playing; X stops the music and hides the bar.
 */
@Composable
fun MiniPlayer(s: MusicState, onOpen: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit, onClose: () -> Unit) {
    val c = MoodyTheme.colors
    AnimatedVisibility(s.current != null && !s.dismissed, enter = expandVertically(Motion.enter()) + fadeIn(Motion.enter()), exit = shrinkVertically(Motion.base()) + fadeOut(Motion.fast())) {
        val item = s.current ?: return@AnimatedVisibility
        val progress = if (s.durationMs > 0) (s.positionMs / s.durationMs.toFloat()).coerceIn(0f, 1f) else 0f
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .clip(Radii.lg)
                .background(c.surface)
                .border(1.dp, c.line, Radii.lg)
                .height(56.dp)
                .clickable(role = Role.Button, onClickLabel = "Open now playing", onClick = onOpen)
                .padding(start = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = 2.dp.toPx()
                    val inset = w / 2
                    val arc = Size(size.width - w, size.height - w)
                    drawArc(c.line2, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(w))
                    if (progress > 0f) drawArc(c.accent, -90f, 360f * progress, false, Offset(inset, inset), arc, style = Stroke(w, cap = StrokeCap.Round))
                }
                Disc(item, 36.dp, spinning = s.playing, behind = c.surface)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MoodyTheme.type.bodyStrong, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.artist, style = MoodyTheme.type.caption, color = c.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconAction(if (s.playing) MoodyIcons.Pause else MoodyIcons.Play, if (s.playing) "Pause" else "Play", onToggle, tint = c.text)
            IconAction(MoodyIcons.Next, "Next", onNext, tint = c.text2)
            // Close stops the music and hides this bar; the queue is kept.
            IconAction(MoodyIcons.Close, "Close player", onClose, tint = c.text3, size = 18.dp)
        }
    }
}
