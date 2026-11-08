package com.moody.player.ui.video

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moody.player.data.media.VideoItem
import com.moody.player.data.media.folderName
import com.moody.player.design.MoodyTheme
import com.moody.player.design.components.EmptyState
import com.moody.player.design.components.IconAction
import com.moody.player.design.icons.MoodyIcons
import com.moody.player.design.illustrations.EmptyFilm
import com.moody.player.domain.LayoutMode
import com.moody.player.domain.formatBytes
import com.moody.player.domain.sortedBy

@Composable
fun FolderScreen(path: String, vm: VideoViewModel, nav: VideoNav, onBack: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val videos = remember(s.all, s.sort, path) {
        s.all.filter { it.relativePath == path }.sortedBy(s.sort, { it.name }, { it.dateAdded }, { it.sizeBytes }, { it.durationMs ?: 0 })
    }
    var selected by remember { mutableStateOf(setOf<String>()) }
    val selecting = selected.isNotEmpty()
    val haptics = LocalHapticFeedback.current
    val c = MoodyTheme.colors
    BackHandler(selecting) { selected = emptySet() }
    fun toggle(v: VideoItem) {
        val k = v.uri.toString()
        selected = if (k in selected) selected - k else selected + k
    }
    val grid = s.layout == LayoutMode.GRID

    LazyVerticalGrid(
        columns = if (grid) GridCells.Adaptive(160.dp) else GridCells.Fixed(1),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = if (grid) 16.dp else 0.dp, vertical = 0.dp),
        horizontalArrangement = Arrangement.spacedBy(if (grid) 12.dp else 0.dp),
        verticalArrangement = Arrangement.spacedBy(if (grid) 12.dp else 0.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.statusBarsPadding().padding(horizontal = if (grid) 0.dp else 16.dp).padding(bottom = 12.dp)) {
                if (selecting) {
                    SelectionBar(selected.size, { selected = emptySet() }, { selected = videos.map { it.uri.toString() }.toSet() }) { a ->
                        nav.onSelection(a, videos.filter { it.uri.toString() in selected }) { selected = emptySet() }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconAction(MoodyIcons.ArrowBack, "Back", onBack, tint = c.text)
                    }
                }
                Text(folderName(path), style = MoodyTheme.type.display, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${videos.size} videos · ${formatBytes(videos.sumOf { it.sizeBytes })} · ${path.ifEmpty { "/" }}",
                    style = MoodyTheme.type.mono, color = c.text3, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        if (videos.isEmpty() && !s.loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState({ EmptyFilm() }, "This folder is empty now", "Its videos were moved, hidden or deleted")
            }
        }
        items(videos, key = { it.uri.toString() }) { v ->
            val onClick = { if (selecting) toggle(v) else nav.play(videos, videos.indexOf(v)) }
            val onLong = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); toggle(v) }
            val sel = v.uri.toString() in selected
            if (grid) VideoGridCard(v, s.positions[v.uri.toString()], sel, onClick, onLong)
            else VideoListRow(v, s.positions[v.uri.toString()], sel, onClick, onLong)
        }
        item(span = { GridItemSpan(maxLineSpan) }) { Column(Modifier.padding(bottom = 24.dp)) {} }
    }
}
