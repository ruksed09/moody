package com.moody.player.ui.music

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.moody.player.design.MoodyTheme
import com.moody.player.design.Radii
import com.moody.player.design.components.Motion
import com.moody.player.design.components.QuietButton
import com.moody.player.design.components.SecondaryButton
import com.moody.player.design.icons.MoodyIcons
import com.moody.player.domain.Lyrics

/**
 * Lyrics in place of the artwork. Synced lyrics follow the song: the current line is bright and kept
 * near the middle, and tapping a line jumps there. Plain lyrics simply scroll.
 */
@Composable
fun LyricsPanel(
    lyrics: Lyrics?,
    loading: Boolean,
    positionMs: Long,
    canRemove: Boolean,
    onSeek: (Long) -> Unit,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MoodyTheme.colors
    if (loading) { Box(modifier); return }
    if (lyrics == null) {
        Column(modifier.padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(MoodyIcons.Lyrics, null, Modifier.size(32.dp), tint = c.text3)
            Spacer(Modifier.height(12.dp))
            Text("No lyrics for this song", style = MoodyTheme.type.title, color = c.text)
            Spacer(Modifier.height(6.dp))
            Text(
                "Add an .lrc file to see lines move with the music, or a plain text file. Moody keeps its own copy.",
                style = MoodyTheme.type.caption, color = c.text2, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            SecondaryButton("Add lyrics file", onAdd, icon = MoodyIcons.Plus)
        }
        return
    }
    val current = lyrics.indexAt(positionMs)
    val list = rememberLazyListState()
    LaunchedEffect(current) {
        // Keep the current line about a third of the way down.
        if (current >= 0) list.animateScrollToItem((current - 2).coerceAtLeast(0))
    }
    Column(modifier) {
        LazyColumn(
            state = list,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    // Soft fade at the top and bottom edges.
                    drawRect(Brush.verticalGradient(0f to Color.Transparent, 0.12f to Color.Black, 0.88f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
                },
            contentPadding = PaddingValues(vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(if (lyrics.synced) 14.dp else 6.dp),
        ) {
            itemsIndexed(lyrics.lines) { i, line ->
                val on = !lyrics.synced || i == current
                val color by animateColorAsState(if (on) c.text else c.text3, Motion.base(), label = "lyric")
                val scale by animateFloatAsState(if (lyrics.synced && i == current) 1.04f else 1f, Motion.base(), label = "lyric-scale")
                Text(
                    line.text.ifBlank { "♪" },
                    style = if (lyrics.synced) MoodyTheme.type.display else MoodyTheme.type.body,
                    color = color,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(Radii.md)
                        .then(if (line.timeMs != null) Modifier.clickable { onSeek(line.timeMs) } else Modifier)
                        .graphicsLayer { scaleX = scale; scaleY = scale; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f) }
                        .padding(vertical = 2.dp),
                )
            }
        }
        if (canRemove) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                QuietButton("Replace", onAdd, color = c.text2)
                QuietButton("Remove", onRemove, color = c.text2)
            }
        }
    }
}
