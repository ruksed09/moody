package com.moody.player.ui.music

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moody.player.data.media.AudioItem
import com.moody.player.design.MoodyTheme
import com.moody.player.design.Radii
import com.moody.player.design.components.ChoiceChip
import com.moody.player.design.components.ActionChip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.moody.player.design.components.EmptyState
import com.moody.player.design.components.IconAction
import com.moody.player.design.components.PrimaryButton
import com.moody.player.design.components.SecondaryButton
import com.moody.player.design.components.SongRowSkeleton
import com.moody.player.design.icons.MoodyIcons
import com.moody.player.design.illustrations.BackdropKind
import com.moody.player.design.illustrations.EmptyMusic
import com.moody.player.design.illustrations.MoodBackdrop
import com.moody.player.domain.formatDuration
import com.moody.player.ui.components.MoodyLogo
import com.moody.player.ui.components.CollapsingTopBar
import com.moody.player.ui.components.LocalBottomInset
import com.moody.player.ui.components.rememberCollapsingBar
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.input.nestedscroll.nestedScroll

/** Where the Music tab can go. */
data class MusicNav(
    val openAlbum: (Long) -> Unit,
    val openArtist: (String) -> Unit,
    val openFolder: (String) -> Unit,
    val openPlaylist: (Long) -> Unit,
    val openSettings: () -> Unit,
    val requestAccess: () -> Unit,
)

const val FAVOURITES_ID = -1L

@Composable
fun MusicScreen(vm: MusicViewModel, nav: MusicNav, hasAccess: Boolean, backgrounds: Boolean) {
    val s by vm.state.collectAsStateWithLifecycle()
    val tab by vm.tab.collectAsStateWithLifecycle()
    val playing by vm.music.state.collectAsStateWithLifecycle()
    var actionsFor by remember { mutableStateOf<AudioItem?>(null) }
    var newPlaylist by remember { mutableStateOf(false) }
    MusicContent(
        s, tab, playing.current?.uri?.toString(), hasAccess, backgrounds, nav,
        onTab = { vm.tab.value = it },
        onPlay = { list, i -> vm.music.playQueue(list, i) },
        onShuffle = { vm.music.playQueue(s.songs, 0, shuffle = true) },
        onSongMore = { actionsFor = it },
        onNewPlaylist = { newPlaylist = true },
    )
    actionsFor?.let { song -> SongActionsSheet(song, vm, onDismiss = { actionsFor = null }) }
    if (newPlaylist) NameDialog("New playlist", "", "Create", onDismiss = { newPlaylist = false }) { vm.createPlaylist(it); newPlaylist = false }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MusicContent(
    s: MusicUiState,
    tab: MusicTab,
    currentUri: String?,
    hasAccess: Boolean,
    backgrounds: Boolean,
    nav: MusicNav,
    onTab: (MusicTab) -> Unit,
    onPlay: (List<AudioItem>, Int) -> Unit,
    onShuffle: () -> Unit,
    onSongMore: (AudioItem) -> Unit,
    onNewPlaylist: () -> Unit,
) {
    val c = MoodyTheme.colors
    val grid = tab == MusicTab.ALBUMS
    // Each sub-tab remembers where you were.
    val states = MusicTab.entries.associateWith { t -> rememberSaveable(t.name, saver = LazyGridState.Saver) { LazyGridState() } }
    val gridState = states.getValue(tab)
    val bar = rememberCollapsingBar(56.dp, "music")
    val topPad = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 57.dp
    Box(Modifier.fillMaxSize().nestedScroll(bar.connection)) {
        LazyVerticalGrid(
            state = gridState,
            columns = if (grid) com.moody.player.ui.components.MediaGridCells() else GridCells.Fixed(1),
            contentPadding = PaddingValues(top = topPad, bottom = LocalBottomInset.current + 24.dp, start = if (grid) 16.dp else 0.dp, end = if (grid) 16.dp else 0.dp),
            horizontalArrangement = Arrangement.spacedBy(if (grid) 12.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(if (grid) 16.dp else 0.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            full {
                Box {
                if (backgrounds) MoodBackdrop(BackdropKind.MUSIC, Modifier.matchParentSize())
                Column(Modifier.padding(horizontal = if (grid) 0.dp else 16.dp)) {
                    Spacer(Modifier.height(12.dp))
                    com.moody.player.ui.components.SectionTitle(
                        MoodyIcons.Music, "Music",
                        if (s.loading) "Looking through your phone" else "${s.songs.size} ${if (s.songs.size == 1) "song" else "songs"}  ·  ${s.albums.size} ${if (s.albums.size == 1) "album" else "albums"}  ·  ${formatDuration(s.totalMs)}",
                    )
                    Spacer(Modifier.height(16.dp))
                    // Shuffle leads the chip row as an action (outlined, with its icon, like Folders on the Videos tab);
                    // a hairline separates it from the tabs, which are choices.
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (s.songs.isNotEmpty()) {
                            item {
                                ActionChip("Shuffle", onShuffle, Modifier.semantics { contentDescription = "Shuffle all songs" }, icon = MoodyIcons.Shuffle)
                            }
                            item { Box(Modifier.width(1.dp).height(20.dp).background(c.line2)) }
                        }
                        items(MusicTab.entries) { t -> ChoiceChip(t.label, t == tab, { onTab(t) }) }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                }
            }
            when {
                !hasAccess -> full {
                    EmptyState({ EmptyMusic() }, "Moody can't see your music yet", "Allow access to music and your songs, albums and artists show up here", actionLabel = "Allow access", onAction = nav.requestAccess)
                }
                s.loading -> items(8, span = { GridItemSpan(maxLineSpan) }) { SongRowSkeleton() }
                s.songs.isEmpty() && tab != MusicTab.PLAYLISTS -> full {
                    EmptyState({ EmptyMusic() }, "No music on this phone yet", "Songs you download or copy into Music show up here. Clips under 30 seconds are left out")
                }
                tab == MusicTab.SONGS -> items(s.songs, key = { it.uri.toString() }, span = { GridItemSpan(maxLineSpan) }) { song ->
                    SongRow(song, song.uri.toString() == currentUri, { onPlay(s.songs, s.songs.indexOf(song)) }, { onSongMore(song) })
                }
                tab == MusicTab.ALBUMS -> items(s.albums, key = { "${it.id}-${it.name}" }) { a ->
                    Column(Modifier.clip(Radii.md).clickable { nav.openAlbum(a.id) }) {
                        Artwork(a.songs.firstOrNull(), Modifier.fillMaxWidth().aspectRatio(1f), shape = Radii.lg, iconSize = 32.dp)
                        Spacer(Modifier.height(8.dp))
                        Text(a.name, style = MoodyTheme.type.bodyStrong, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${a.artist} · ${a.songs.size} ${if (a.songs.size == 1) "song" else "songs"}", style = MoodyTheme.type.caption, color = c.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                tab == MusicTab.ARTISTS -> items(s.artists, key = { it.name }, span = { GridItemSpan(maxLineSpan) }) { a ->
                    GroupRow(
                        leading = {
                            Box(Modifier.size(48.dp).clip(CircleShape).background(c.raised), contentAlignment = Alignment.Center) {
                                Text(a.name.take(1).uppercase(), style = MoodyTheme.type.title, color = c.text2)
                            }
                        },
                        title = a.name,
                        meta = "${a.songs.size} songs · ${a.albumCount} ${if (a.albumCount == 1) "album" else "albums"}",
                    ) { nav.openArtist(a.name) }
                }
                tab == MusicTab.FOLDERS -> items(s.folders, key = { it.path }, span = { GridItemSpan(maxLineSpan) }) { f ->
                    GroupRow(
                        leading = { Box(Modifier.size(48.dp).clip(Radii.md).background(c.raised), contentAlignment = Alignment.Center) { Icon(MoodyIcons.Folder, null, Modifier.size(20.dp), tint = c.text2) } },
                        title = f.name,
                        meta = "${f.songs.size} songs · ${f.path.ifEmpty { "/" }}",
                    ) { nav.openFolder(f.path) }
                }
                else -> {
                    full {
                        GroupRow(
                            leading = { Box(Modifier.size(48.dp).clip(Radii.md).background(c.accentWash), contentAlignment = Alignment.Center) { Icon(MoodyIcons.HeartFilled, null, Modifier.size(20.dp), tint = c.accent) } },
                            title = "Favourites",
                            meta = "${s.favorites.size} songs",
                        ) { nav.openPlaylist(FAVOURITES_ID) }
                    }
                    items(s.playlists, key = { it.playlist.id }, span = { GridItemSpan(maxLineSpan) }) { p ->
                        GroupRow(
                            leading = { Box(Modifier.size(48.dp).clip(Radii.md).background(c.raised), contentAlignment = Alignment.Center) { Icon(MoodyIcons.Playlist, null, Modifier.size(20.dp), tint = c.text2) } },
                            title = p.playlist.name,
                            meta = "${p.count} songs",
                        ) { nav.openPlaylist(p.playlist.id) }
                    }
                    full { Box(Modifier.padding(16.dp)) { SecondaryButton("New playlist", onNewPlaylist, icon = MoodyIcons.Plus) } }
                }
            }
            full { com.moody.player.ui.components.CreditFooter() }
        }
        CollapsingTopBar(bar, contentUnder = gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0, height = 56.dp) {
            com.moody.player.ui.components.AppTopBar(56.dp) {
                IconAction(MoodyIcons.Settings, "Settings", nav.openSettings, tint = c.text, size = 20.dp)
            }
        }
    }
}

private fun LazyGridScope.full(content: @Composable () -> Unit) = item(span = { GridItemSpan(maxLineSpan) }) { content() }

@Composable
fun SongRow(song: AudioItem, current: Boolean, onClick: () -> Unit, onMore: () -> Unit, leadingIndex: Int? = null) {
    val c = MoodyTheme.colors
    Row(
        Modifier.fillMaxWidth().height(64.dp).clickable(onClick = onClick).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIndex != null) {
            Text("$leadingIndex", style = MoodyTheme.type.mono, color = if (current) c.accent else c.text3, modifier = Modifier.width(32.dp))
        } else {
            ArtworkSquare(song, 48.dp)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MoodyTheme.type.bodyStrong, color = if (current) c.accent else c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${song.artist} · ${formatDuration(song.durationMs)}", style = MoodyTheme.type.caption, color = c.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconAction(MoodyIcons.More, "More for ${song.title}", onMore, tint = c.text3, size = 20.dp)
    }
}

@Composable
fun GroupRow(leading: @Composable () -> Unit, title: String, meta: String, onClick: () -> Unit) {
    val c = MoodyTheme.colors
    Row(Modifier.fillMaxWidth().height(72.dp).clickable(onClick = onClick).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        leading()
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MoodyTheme.type.bodyStrong, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(meta, style = MoodyTheme.type.caption, color = c.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(MoodyIcons.ChevronRight, null, Modifier.size(18.dp), tint = c.text3)
    }
}
