package com.moody.player.ui.music

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.PlainTooltip
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.semantics.selected
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
    val openAlbum: (Long, String) -> Unit,
    val openArtist: (String) -> Unit,
    val openFolder: (String) -> Unit,
    val openPlaylist: (Long) -> Unit,
    val openSettings: () -> Unit,
    val requestAccess: () -> Unit,
    val openStats: () -> Unit = {},
)

const val FAVOURITES_ID = -1L

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen(vm: MusicViewModel, nav: MusicNav, hasAccess: Boolean, backgrounds: Boolean) {
    val s by vm.state.collectAsStateWithLifecycle()
    val tab by vm.tab.collectAsStateWithLifecycle()
    val playing by vm.music.state.collectAsStateWithLifecycle()
    var actionsFor by remember { mutableStateOf<AudioItem?>(null) }
    var newPlaylist by remember { mutableStateOf(false) }
    // Long-press a song to start selecting; tap the count to select every song.
    var selected by remember { mutableStateOf(setOf<String>()) }
    var batch by remember { mutableStateOf<SongBatchRequest?>(null) }
    var coverFor by remember { mutableStateOf<com.moody.player.domain.MoodMix<AudioItem>?>(null) }
    var sortSheet by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<com.moody.player.domain.MoodMix<AudioItem>?>(null) }
    val toaster = com.moody.player.design.components.LocalToaster.current
    androidx.activity.compose.BackHandler(selected.isNotEmpty()) { selected = emptySet() }
    androidx.compose.runtime.LaunchedEffect(tab) { selected = emptySet() }
    MusicContent(
        s, tab, playing.current?.uri?.toString(), hasAccess, backgrounds, nav,
        playing = playing.playing,
        selected = selected,
        onToggle = { song -> val k = song.uri.toString(); selected = if (k in selected) selected - k else selected + k },
        selectionBar = if (selected.isEmpty()) null else {
            {
                SongSelectionBar(selected.size, { selected = emptySet() }, { selected = s.songs.map { it.uri.toString() }.toSet() }) { a ->
                    batch = SongBatchRequest(a, s.songs.filter { it.uri.toString() in selected })
                }
            }
        },
        onTab = { vm.tab.value = it },
        onPlay = { list, i -> vm.music.playQueue(list, i) },
        onShuffle = { vm.music.playQueue(s.songs, 0, shuffle = true) },
        onSongMore = { actionsFor = it },
        onNewPlaylist = { newPlaylist = true },
        onMix = { m -> vm.music.playQueue(m.songs, 0, shuffle = m.shuffle) },
        onMixCover = { coverFor = it },
        onSort = { sortSheet = true },
        onBringBackMixes = vm::bringBackMixes,
    )
    if (sortSheet) {
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { sortSheet = false }, containerColor = MoodyTheme.colors.surface) {
            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
                Text("Sort songs", style = MoodyTheme.type.title, color = MoodyTheme.colors.text)
                Spacer(Modifier.height(20.dp))
                com.moody.player.ui.components.SortChoices(
                    listOf(com.moody.player.domain.SortKey.TITLE to "Title") + com.moody.player.ui.components.FileSorts,
                    s.songSort, vm::setSongSort,
                )
            }
        }
    }
    coverFor?.let { m ->
        MixSheet(
            m.title, hasCover = m.id in s.mixCovers, onDismiss = { coverFor = null }, onRemoveCover = { vm.clearMixCover(m.id) },
            onRename = { renaming = m },
            onRemoveMix = { vm.removeMix(m.id); toaster.show("Mix removed. Bring it back from Mood mixes") },
        ) { picked ->
            vm.setMixCover(m.id, picked) { ok -> if (!ok) toaster.show("That picture couldn't be opened") }
        }
    }
    renaming?.let { m ->
        NameDialog(
            "Rename mix", m.title, "Rename", onDismiss = { renaming = null },
            note = "Leave it empty to use Moody's name", allowBlank = true,
        ) { name ->
            // Saving the name as shown would pin it (time-of-day names would stop following the clock).
            if (name.trim() != m.title) vm.renameMix(m.id, name)
            renaming = null
        }
    }
    val writeCover = rememberCoverWriter(vm.music)
    actionsFor?.let { song -> SongActionsSheet(song, vm, onDismiss = { actionsFor = null }, onCoverSaved = writeCover) }
    SongBatchHost(vm, batch) { done -> batch = null; if (done) selected = emptySet() }
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
    onMix: (com.moody.player.domain.MoodMix<AudioItem>) -> Unit = {},
    onMixCover: ((com.moody.player.domain.MoodMix<AudioItem>) -> Unit)? = null,
    playing: Boolean = false,
    selected: Set<String> = emptySet(),
    onToggle: ((AudioItem) -> Unit)? = null,
    selectionBar: (@Composable () -> Unit)? = null,
    onSort: (() -> Unit)? = null,
    onBringBackMixes: (() -> Unit)? = null,
) {
    val c = MoodyTheme.colors
    val grid = tab == MusicTab.ALBUMS
    // Each sub-tab remembers where you were.
    val states = MusicTab.entries.associateWith { t -> rememberSaveable(t.name, saver = LazyGridState.Saver) { LazyGridState() } }
    val gridState = states.getValue(tab)
    val bar = rememberCollapsingBar(48.dp, "music")
    val topPad = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 49.dp
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
                    Spacer(Modifier.height(4.dp))
                    com.moody.player.ui.components.SectionTitle(
                        "Music",
                        if (s.loading) "Looking through your phone" else "${s.songs.size} ${if (s.songs.size == 1) "song" else "songs"}  ·  ${s.albums.size} ${if (s.albums.size == 1) "album" else "albums"}  ·  ${formatDuration(s.totalMs)}",
                    )
                    Spacer(Modifier.height(12.dp))
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
                    Spacer(Modifier.height(8.dp))
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
                tab == MusicTab.SONGS -> {
                    if (s.mixes.isNotEmpty() || s.hiddenMixes.isNotEmpty()) item(key = "mixes", span = { GridItemSpan(maxLineSpan) }) {
                        MoodMixesRow(s.mixes, onMix, s.mixCovers, onMixCover, hidden = s.hiddenMixes.size, onBringBack = onBringBackMixes)
                    }
                    // "Songs" with its sort button on the right, where Videos keeps sort beside the grid / list switch.
                    item(key = "songs-header", span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 16.dp, end = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Songs", style = MoodyTheme.type.title, color = c.text, modifier = Modifier.weight(1f))
                            if (onSort != null) com.moody.player.design.components.SortButton("Sort songs", onSort)
                        }
                    }
                    items(s.songs, key = { it.uri.toString() }, span = { GridItemSpan(maxLineSpan) }) { song ->
                        val current = song.uri.toString() == currentUri
                        val picking = selected.isNotEmpty()
                        SongRow(
                            song, current,
                            { if (picking && onToggle != null) onToggle(song) else onPlay(s.songs, s.songs.indexOf(song)) },
                            { onSongMore(song) }, spinning = current && playing,
                            selected = song.uri.toString() in selected, onLongClick = onToggle?.let { t -> { t(song) } },
                        )
                    }
                }
                tab == MusicTab.ALBUMS -> items(s.albums, key = { "${it.id}-${it.name}" }) { a ->
                    val current = a.songs.any { it.uri.toString() == currentUri }
                    Column(Modifier.clip(Radii.lg).clickable { nav.openAlbum(a.id, a.name) }) {
                        // The album's disc on a quiet sleeve; it turns when one of its songs is playing.
                        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f).clip(Radii.lg).background(c.surface), contentAlignment = Alignment.Center) {
                            Disc(a.songs.firstOrNull(), maxWidth * 0.86f, spinning = current && playing, behind = c.surface, current = current)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(a.name, style = MoodyTheme.type.bodyStrong, color = if (current) c.accent else c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${a.artist} · ${a.songs.size} ${if (a.songs.size == 1) "song" else "songs"}", style = MoodyTheme.type.caption, color = c.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                tab == MusicTab.ARTISTS -> items(s.artists, key = { it.name }, span = { GridItemSpan(maxLineSpan) }) { a ->
                    GroupRow(
                        leading = {
                            Box(Modifier.size(44.dp).clip(CircleShape).background(c.raised), contentAlignment = Alignment.Center) {
                                Text(a.name.take(1).uppercase(), style = MoodyTheme.type.title, color = c.text2)
                            }
                        },
                        title = a.name,
                        meta = "${a.songs.size} songs · ${a.albumCount} ${if (a.albumCount == 1) "album" else "albums"}",
                    ) { nav.openArtist(a.name) }
                }
                tab == MusicTab.FOLDERS -> items(s.folders, key = { it.path }, span = { GridItemSpan(maxLineSpan) }) { f ->
                    GroupRow(
                        leading = { Box(Modifier.size(44.dp).clip(Radii.md).background(c.raised), contentAlignment = Alignment.Center) { Icon(MoodyIcons.Folder, null, Modifier.size(20.dp), tint = c.text2) } },
                        title = f.name,
                        meta = "${f.songs.size} songs · ${f.path.ifEmpty { "/" }}",
                    ) { nav.openFolder(f.path) }
                }
                else -> {
                    full {
                        GroupRow(
                            leading = { Box(Modifier.size(44.dp).clip(Radii.md).background(c.accentWash), contentAlignment = Alignment.Center) { Icon(MoodyIcons.HeartFilled, null, Modifier.size(20.dp), tint = c.accent) } },
                            title = "Favourites",
                            meta = "${s.favorites.size} songs",
                        ) { nav.openPlaylist(FAVOURITES_ID) }
                    }
                    items(s.playlists, key = { it.playlist.id }, span = { GridItemSpan(maxLineSpan) }) { p ->
                        GroupRow(
                            leading = { Box(Modifier.size(44.dp).clip(Radii.md).background(c.raised), contentAlignment = Alignment.Center) { Icon(MoodyIcons.Playlist, null, Modifier.size(20.dp), tint = c.text2) } },
                            title = p.playlist.name,
                            meta = "${p.count} songs",
                        ) { nav.openPlaylist(p.playlist.id) }
                    }
                    full { Box(Modifier.padding(16.dp)) { SecondaryButton("New playlist", onNewPlaylist, icon = MoodyIcons.Plus) } }
                }
            }
            full { com.moody.player.ui.components.CreditFooter() }
        }
        CollapsingTopBar(bar, contentUnder = gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0, height = 48.dp) {
            if (selectionBar != null) selectionBar()
            else com.moody.player.ui.components.AppTopBar(48.dp) {
                com.moody.player.ui.update.UpdateBadgeAction()
                IconAction(MoodyIcons.Chart, "Your stats", nav.openStats, tint = c.text)
                IconAction(MoodyIcons.Settings, "Settings", nav.openSettings, tint = c.text)
            }
        }
    }
}

private fun LazyGridScope.full(content: @Composable () -> Unit) = item(span = { GridItemSpan(maxLineSpan) }) { content() }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: AudioItem, current: Boolean, onClick: () -> Unit, onMore: () -> Unit, leadingIndex: Int? = null, spinning: Boolean = false,
    selected: Boolean = false, onLongClick: (() -> Unit)? = null,
) {
    val c = MoodyTheme.colors
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    Row(
        Modifier.fillMaxWidth().height(60.dp)
            .background(if (selected) c.accentWash else androidx.compose.ui.graphics.Color.Transparent)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick?.let { l -> { haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); l() } },
            )
            .semantics { this.selected = selected }
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            // A selected song shows a check in place of its disc or number.
            Box(Modifier.size(if (leadingIndex != null) 24.dp else 44.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                Icon(MoodyIcons.Check, "Selected", Modifier.size(18.dp), tint = c.accentInk)
            }
            Spacer(Modifier.width(if (leadingIndex != null) 8.dp else 12.dp))
        } else if (leadingIndex != null) {
            Text("$leadingIndex", style = MoodyTheme.type.mono, color = if (current) c.accent else c.text3, modifier = Modifier.width(32.dp))
        } else {
            Disc(song, 44.dp, spinning = spinning, current = current)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MoodyTheme.type.bodyStrong, color = if (current) c.accent else c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${song.artist} · ${formatDuration(song.durationMs)}", style = MoodyTheme.type.caption, color = c.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconAction(MoodyIcons.More, "More for ${song.title}", onMore, tint = c.text3)
    }
}

@Composable
fun GroupRow(leading: @Composable () -> Unit, title: String, meta: String, onClick: () -> Unit) {
    val c = MoodyTheme.colors
    Row(Modifier.fillMaxWidth().height(60.dp).clickable(onClick = onClick).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        leading()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MoodyTheme.type.bodyStrong, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(meta, style = MoodyTheme.type.caption, color = c.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(MoodyIcons.ChevronRight, null, Modifier.size(18.dp), tint = c.text3)
    }
}

/** Pick a picture for a mood mix, or go back to its album discs. */
/** An info button whose tooltip says what removing a mix does (and doesn't). Its tap doesn't remove the mix. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun RemoveMixInfo() {
    val c = MoodyTheme.colors
    val tip = androidx.compose.material3.rememberTooltipState(isPersistent = true)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val text = "Your songs stay on your phone. Bring the mix back any time from Mood mixes"
    androidx.compose.material3.TooltipBox(
        positionProvider = androidx.compose.material3.TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip(containerColor = c.raised, contentColor = c.text) {
                Text(text, style = MoodyTheme.type.caption, color = c.text)
            }
        },
        state = tip,
    ) {
        IconAction(MoodyIcons.Info, "What removing does: $text", { scope.launch { tip.show() } }, tint = c.text3)
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun MixSheet(
    title: String, hasCover: Boolean, onDismiss: () -> Unit, onRemoveCover: () -> Unit,
    onRename: () -> Unit, onRemoveMix: () -> Unit, onPicked: (android.net.Uri) -> Unit,
) {
    val c = MoodyTheme.colors
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPicked(uri)
        onDismiss()
    }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surface) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(title, style = MoodyTheme.type.title, color = c.text, modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp))
            SheetRow(MoodyIcons.Image, if (hasCover) "Change cover" else "Choose a cover") {
                pick.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            if (hasCover) SheetRow(MoodyIcons.Close, "Use the album covers") { onRemoveCover(); onDismiss() }
            SheetRow(MoodyIcons.Rename, "Rename") { onDismiss(); onRename() }
            SheetRow(MoodyIcons.Delete, "Remove mix", trailing = { RemoveMixInfo() }) { onDismiss(); onRemoveMix() }
        }
    }
}
