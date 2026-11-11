package com.moody.player.ui.video

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.moody.player.data.db.PlaybackPosition
import com.moody.player.data.media.Folder
import com.moody.player.data.media.VideoItem
import com.moody.player.design.MoodyTheme
import com.moody.player.design.OnVideo
import com.moody.player.design.Radii
import com.moody.player.design.components.ReelLine
import com.moody.player.design.icons.MoodyIcons
import com.moody.player.domain.formatBytes
import com.moody.player.domain.formatDuration

/** A video frame with its length, resume line and a dot for never-played. */
@Composable
fun Thumb(
    model: Any?,
    modifier: Modifier = Modifier,
    durationMs: Long? = null,
    position: PlaybackPosition? = null,
    isNew: Boolean = false,
    selected: Boolean = false,
    showBadge: Boolean = true,
    /** Shown in the top corner instead of the new-dot, e.g. a folder's video count. */
    count: Int? = null,
) {
    val c = MoodyTheme.colors
    Box(
        modifier
            .aspectRatio(16f / 9f)
            .clip(Radii.md)
            .background(c.raised)
            .then(if (selected) Modifier.border(2.dp, c.accent, Radii.md) else Modifier),
    ) {
        Icon(MoodyIcons.Film, null, Modifier.align(Alignment.Center).size(22.dp), tint = c.text3.copy(alpha = 0.5f))
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(model).videoFrameMillis(2_000).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (showBadge && durationMs != null && durationMs > 0) {
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(Radii.pill)
                    .background(OnVideo.ink)
                    .padding(start = 5.dp, end = 7.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(MoodyIcons.PlaySmall, null, Modifier.size(10.dp), tint = OnVideo.text)
                Spacer(Modifier.width(3.dp))
                Text(formatDuration(durationMs), style = MoodyTheme.type.monoSmall, color = OnVideo.text)
            }
        }
        if (count != null) {
            Row(
                Modifier.align(Alignment.TopEnd).padding(6.dp).clip(Radii.pill).background(OnVideo.ink).padding(horizontal = 7.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(MoodyIcons.Folder, null, Modifier.size(10.dp), tint = OnVideo.text)
                Spacer(Modifier.width(4.dp))
                Text("$count", style = MoodyTheme.type.monoSmall, color = OnVideo.text)
            }
        } else if (isNew && !selected) {
            Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(8.dp).clip(CircleShape).background(c.accent))
        }
        if (selected) {
            Box(
                Modifier.align(Alignment.TopStart).padding(8.dp).size(24.dp).clip(CircleShape).background(c.accent),
                contentAlignment = Alignment.Center,
            ) { Icon(MoodyIcons.Check, "Selected", Modifier.size(16.dp), tint = c.accentInk) }
        }
        if (position != null && durationMs != null && durationMs > 0 && !position.watched && position.positionMs > 5_000) {
            ReelLine(
                progress = position.positionMs / durationMs.toFloat(),
                modifier = Modifier.align(Alignment.BottomCenter),
                height = 3.dp,
                track = Color.Black.copy(alpha = 0.35f),
            )
        }
    }
}

private fun meta(v: VideoItem): String = listOfNotNull(v.resolutionLabel, v.extension.takeIf { it.isNotEmpty() }, formatBytes(v.sizeBytes)).joinToString("  ·  ")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoGridCard(v: VideoItem, position: PlaybackPosition?, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = MoodyTheme.colors
    Column(modifier.clip(Radii.md).combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(bottom = 8.dp)) {
        Thumb(v.uri, Modifier.fillMaxWidth(), v.durationMs, position, isNew = position == null, selected = selected)
        Spacer(Modifier.height(8.dp))
        Text(v.title, style = MoodyTheme.type.bodyStrong, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 2.dp))
        Spacer(Modifier.height(2.dp))
        Text(meta(v), style = MoodyTheme.type.caption, color = c.text3, maxLines = 1, modifier = Modifier.padding(horizontal = 2.dp))
    }
}

/** A 64 dp row: a 96 dp frame, two-line title, quiet meta and the menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoListRow(
    v: VideoItem, position: PlaybackPosition?, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit,
    modifier: Modifier = Modifier, onMore: (() -> Unit)? = null,
) {
    val c = MoodyTheme.colors
    Box(modifier.fillMaxWidth().background(if (selected) c.accentWash else Color.Transparent).combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumb(v.uri, Modifier.width(96.dp), v.durationMs, position, isNew = position == null, selected = selected)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(v.title, style = MoodyTheme.type.bodyStrong, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(meta(v), style = MoodyTheme.type.caption, color = c.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (onMore != null) com.moody.player.design.components.IconAction(MoodyIcons.More, "More for ${v.title}", onMore, tint = c.text3)
            else Spacer(Modifier.width(16.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderGridCard(f: Folder, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = MoodyTheme.colors
    Column(modifier.clip(Radii.md).combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(bottom = 8.dp)) {
        Thumb(f.latest?.uri, Modifier.fillMaxWidth(), count = f.count)
        Spacer(Modifier.height(8.dp))
        Text(f.name, style = MoodyTheme.type.bodyStrong, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 2.dp))
        Spacer(Modifier.height(2.dp))
        Text(folderMeta(f), style = MoodyTheme.type.caption, color = c.text3, maxLines = 1, modifier = Modifier.padding(horizontal = 2.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderListRow(f: Folder, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = MoodyTheme.colors
    Row(
        modifier.fillMaxWidth().height(56.dp).combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(Radii.md).background(c.raised), contentAlignment = Alignment.Center) {
            Icon(MoodyIcons.Folder, null, Modifier.size(20.dp), tint = c.text2)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(f.name, style = MoodyTheme.type.bodyStrong, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(folderMeta(f), style = MoodyTheme.type.caption, color = c.text3)
        }
        Icon(MoodyIcons.ChevronRight, null, Modifier.size(18.dp), tint = c.text3)
    }
}

private fun folderMeta(f: Folder) = "${f.count} ${if (f.count == 1) "video" else "videos"} · ${formatBytes(f.totalBytes)}"

@Composable
fun ContinueCard(item: ContinueItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = MoodyTheme.colors
    val left = (item.video.durationMs ?: 0) - item.position.positionMs
    Column(modifier.width(168.dp).clip(Radii.md).combinedClickableCompat(onClick).padding(bottom = 4.dp)) {
        Thumb(item.video.uri, Modifier.fillMaxWidth(), item.video.durationMs, item.position, isNew = false, showBadge = false)
        Spacer(Modifier.height(8.dp))
        Text(item.video.title, style = MoodyTheme.type.label, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${formatDuration(left)} left", style = MoodyTheme.type.monoSmall, color = c.text3)
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit) = combinedClickable(onClick = onClick)
