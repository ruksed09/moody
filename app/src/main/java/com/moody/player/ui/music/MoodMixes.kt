package com.moody.player.ui.music

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moody.player.data.media.AudioItem
import com.moody.player.design.MoodyTheme
import com.moody.player.design.Radii
import com.moody.player.design.components.Motion
import com.moody.player.design.icons.MoodyIcons
import com.moody.player.domain.MoodMix

private val CardSide = 132.dp
private val DiscSide = 116.dp

/**
 * Mixes Moody builds from what you play, when you play it and what you love. Everything is worked out
 * on the phone; each card says what it is built from.
 */
@Composable
fun MoodMixesRow(
    mixes: List<MoodMix<AudioItem>>, onPlay: (MoodMix<AudioItem>) -> Unit,
    covers: Map<String, String> = emptyMap(), onCover: ((MoodMix<AudioItem>) -> Unit)? = null,
    hidden: Int = 0, onBringBack: (() -> Unit)? = null,
) {
    val c = MoodyTheme.colors
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Mood mixes", style = MoodyTheme.type.title, color = c.text, modifier = Modifier.weight(1f))
            if (hidden > 0 && onBringBack != null) {
                Text(
                    "Bring back $hidden", style = MoodyTheme.type.label, color = c.accent,
                    modifier = Modifier.clip(Radii.sm).clickable(role = Role.Button, onClick = onBringBack).padding(horizontal = 6.dp, vertical = 6.dp)
                        .semantics { contentDescription = "Bring back $hidden removed ${if (hidden == 1) "mix" else "mixes"}" },
                )
            } else Text("Made on this phone", style = MoodyTheme.type.caption, color = c.text3)
        }
        if (mixes.isEmpty()) {
            Text("You removed every mix", style = MoodyTheme.type.caption, color = c.text3, modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(16.dp))
            return@Column
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(mixes, key = { it.id }) { m -> MixCard(m, covers[m.id], onCover?.let { f -> { f(m) } }) { onPlay(m) } }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun MixCard(m: MoodMix<AudioItem>, cover: String?, onCover: (() -> Unit)?, onClick: () -> Unit) {
    val c = MoodyTheme.colors
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, Motion.fast(), label = "mix-press")
    Column(
        Modifier
            .width(CardSide)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(Radii.lg)
            // Hold a card to rename it, give it a cover of your own or remove it.
            .combinedClickable(src, ripple(), role = Role.Button, onLongClickLabel = "Mix options", onLongClick = onCover, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "Play ${m.title}, ${m.songs.size} songs. ${m.basis}"
                if (onCover != null) customActions = listOf(androidx.compose.ui.semantics.CustomAccessibilityAction("Mix options") { onCover(); true })
            },
    ) {
        Box(Modifier.size(CardSide).clip(Radii.lg).background(c.surface)) {
            // A small stack of discs: the mix's first album in front, the next ones peeking out behind it.
            // A cover of your own goes on the front disc; the albums still peek out behind it.
            val covers = remember(m.songs, cover) {
                m.songs.distinctBy { it.albumId }.take(3).let { l -> if (cover != null && l.isNotEmpty()) listOf(l[0].copy(cover = cover)) + l.drop(1) else l }
            }
            covers.indices.reversed().forEach { i ->
                Disc(covers[i], DiscSide, Modifier.align(Alignment.CenterStart).offset(x = 4.dp + 6.dp * i), behind = c.surface)
            }
            if (covers.isEmpty()) Disc(null, DiscSide, Modifier.align(Alignment.CenterStart).offset(x = 4.dp), behind = c.surface)
            Box(
                Modifier.align(Alignment.BottomEnd).padding(6.dp).size(32.dp).clip(CircleShape).background(c.accent),
                contentAlignment = Alignment.Center,
            ) { Icon(if (m.shuffle) MoodyIcons.Shuffle else MoodyIcons.Play, null, Modifier.size(16.dp), tint = c.accentInk) }
        }
        Spacer(Modifier.height(8.dp))
        Text(m.title, style = MoodyTheme.type.bodyStrong, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            "${m.songs.size} songs · ${m.basis}", style = MoodyTheme.type.caption, color = c.text3,
            maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 4.dp),
        )
    }
}
