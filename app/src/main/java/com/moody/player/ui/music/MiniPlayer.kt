package com.moody.player.ui.music

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moody.player.design.MoodyTheme
import com.moody.player.design.Radii
import com.moody.player.design.components.IconAction
import com.moody.player.design.components.Motion
import com.moody.player.design.components.ReelLine
import com.moody.player.design.icons.MoodyIcons
import com.moody.player.player.music.MusicState

/** Docks above the bottom bar while something is queued. Tap opens Now Playing. */
@Composable
fun MiniPlayer(s: MusicState, onOpen: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit) {
    val c = MoodyTheme.colors
    AnimatedVisibility(s.current != null, enter = expandVertically(Motion.enter()) + fadeIn(Motion.enter()), exit = shrinkVertically(Motion.base()) + fadeOut(Motion.fast())) {
        val item = s.current ?: return@AnimatedVisibility
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).clip(Radii.lg).background(c.surface).border(1.dp, c.line, Radii.lg)) {
            Row(
                Modifier.fillMaxWidth().height(64.dp).clickable(role = Role.Button, onClickLabel = "Open now playing", onClick = onOpen).padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArtworkSquare(item, 48.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MoodyTheme.type.bodyStrong, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(item.artist, style = MoodyTheme.type.caption, color = c.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconAction(if (s.playing) MoodyIcons.Pause else MoodyIcons.Play, if (s.playing) "Pause" else "Play", onToggle, tint = c.text)
                IconAction(MoodyIcons.Next, "Next", onNext, tint = c.text2)
            }
            Box(Modifier.padding(horizontal = 12.dp)) {
                ReelLine(if (s.durationMs > 0) s.positionMs / s.durationMs.toFloat() else 0f, height = 2.dp, track = c.line)
            }
        }
    }
}
