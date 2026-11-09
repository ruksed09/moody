package com.moody.player.ui.video

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.moody.player.design.components.Motion
import com.moody.player.ui.components.CollapsingTopBar
import com.moody.player.ui.components.LocalBottomInset
import com.moody.player.ui.components.MoodyLogo
import com.moody.player.ui.components.rememberCollapsingBar
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moody.player.data.media.AccessLevel
import com.moody.player.data.media.VideoItem
import com.moody.player.design.MoodyTheme
import com.moody.player.design.Radii
import com.moody.player.design.components.ChoiceChip
import com.moody.player.design.components.EmptyState
import com.moody.player.design.components.IconAction
import com.moody.player.design.components.QuietButton
import com.moody.player.design.components.SectionLabel
import com.moody.player.design.components.VideoGridSkeleton
import com.moody.player.design.components.VideoRowSkeleton
import com.moody.player.design.icons.MoodyIcons
import com.moody.player.design.illustrations.BackdropKind
import com.moody.player.design.illustrations.EmptyFilm
import com.moody.player.design.illustrations.MoodBackdrop
import com.moody.player.design.illustrations.NoAccess
import com.moody.player.domain.LayoutMode
import com.moody.player.domain.SortKey
import com.moody.player.domain.SortSpec
import com.moody.player.domain.VideoView
import com.moody.player.domain.formatBytes

enum class SelectionAction { HIDE, SHARE, RENAME, DELETE, INFO }

/** Callbacks the Video tab needs from the app. */
data class VideoNav(
    val openFolder: (String) -> Unit,
    val play: (List<VideoItem>, Int) -> Unit,
    val openVault: () -> Unit,
    val openSettings: () -> Unit,
    val openStream: () -> Unit,
    val requestAccess: () -> Unit,
    val onSelection: (SelectionAction, List<VideoItem>, () -> Unit) -> Unit,
)

/** What the Video tab can ask of its state holder. */
interface VideoActions {
    fun setQuery(q: String)
    fun setView(v: VideoView)
    fun setLayout(l: LayoutMode)
    fun setSort(s: SortSpec)
    fun refresh()
    fun hideFolder(path: String)
}

@Composable
fun VideoLibraryScreen(vm: VideoViewModel, nav: VideoNav, modifier: Modifier = Modifier) {
    val s by vm.state.collectAsStateWithLifecycle()
    // One remembered scroll position per view, so switching Folders / All videos (or tabs) keeps your place.
    val folderState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }
    val allState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }
    VideoLibraryContent(s, vm, nav, modifier, if (s.view == VideoView.FOLDERS) folderState else allState)
}

private val BarHeight = 56.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoLibraryContent(
    s: VideoUiState,
    vm: VideoActions,
    nav: VideoNav,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
) {
    var searching by rememberSaveable { mutableStateOf(false) }
    var sortSheet by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var hidingFolder by remember { mutableStateOf<com.moody.player.data.media.Folder?>(null) }
    var actionsFor by remember { mutableStateOf<VideoItem?>(null) }
    val selecting = selected.isNotEmpty()
    val haptics = LocalHapticFeedback.current
    val bar = rememberCollapsingBar(BarHeight, "video")
    bar.pinned = selecting || searching
    LaunchedEffect(bar.pinned) { if (bar.pinned) bar.show() }

    BackHandler(selecting || searching) {
        if (selecting) selected = emptySet() else { searching = false; vm.setQuery("") }
    }

    fun toggle(v: VideoItem) {
        val k = v.uri.toString()
        selected = if (k in selected) selected - k else selected + k
    }
    val selectedItems = s.all.filter { it.uri.toString() in selected }
    val grid = s.layout == LayoutMode.GRID
    val side = if (grid) 16.dp else 0.dp
    val topPad = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + BarHeight + 1.dp
    val bottomPad = LocalBottomInset.current + 24.dp
    val ptr = rememberPullToRefreshState()

    Box(modifier.fillMaxSize().nestedScroll(bar.connection)) {
        PullToRefreshBox(
            isRefreshing = s.refreshing, onRefresh = { vm.refresh() }, state = ptr, modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = ptr, isRefreshing = s.refreshing, modifier = Modifier.align(Alignment.TopCenter).padding(top = topPad),
                    containerColor = MoodyTheme.colors.surface, color = MoodyTheme.colors.accent,
                )
            },
        ) {
            LazyVerticalGrid(
                state = gridState,
                columns = if (grid) GridCells.Adaptive(160.dp) else GridCells.Fixed(1),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = side, end = side, top = topPad, bottom = bottomPad),
                horizontalArrangement = Arrangement.spacedBy(if (grid) 12.dp else 0.dp),
                verticalArrangement = Arrangement.spacedBy(if (grid) 16.dp else 0.dp),
            ) {
                full {
                    Box {
                        if (s.backgrounds) MoodBackdrop(BackdropKind.VIDEO, Modifier.matchParentSize())
                        Column(Modifier.padding(horizontal = if (grid) 0.dp else 16.dp)) {
                            if (!searching) Header(s, onView = vm::setView, onVault = nav.openVault)
                            if (s.access == AccessLevel.PARTIAL) PartialBanner(nav.requestAccess)
                        }
                    }
                }
                when {
                    s.loading -> skeletons(grid)
                    s.access == AccessLevel.NONE -> full {
                        EmptyState({ NoAccess() }, "Moody can't see your videos yet", "Allow access to videos and music and your library builds itself", actionLabel = "Allow access", onAction = nav.requestAccess)
                    }
                    s.all.isEmpty() -> full {
                        EmptyState({ EmptyFilm() }, "No videos on this phone yet", "Videos you record, download or copy over show up here", actionLabel = "Scan again", onAction = { vm.refresh() })
                    }
                    searching && s.query.isNotBlank() && s.shown.isEmpty() -> full {
                        Text("Nothing called \u201c${s.query.trim()}\u201d", style = MoodyTheme.type.body, color = MoodyTheme.colors.text2, modifier = Modifier.padding(16.dp))
                    }
                    !searching && s.view == VideoView.FOLDERS -> {
                        continueRow(s, nav)
                        full { SectionHeader("Folders", s.layout, vm::setLayout, inset = if (grid) 0.dp else 16.dp) }
                        items(s.folders, key = { it.path }) { f ->
                            val long = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); hidingFolder = f }
                            if (grid) FolderGridCard(f, { nav.openFolder(f.path) }, long)
                            else FolderListRow(f, { nav.openFolder(f.path) }, long)
                        }
                    }
                    else -> {
                        if (!searching) continueRow(s, nav)
                        if (!searching) full { SectionHeader("All videos", s.layout, vm::setLayout, inset = if (grid) 0.dp else 16.dp) }
                        items(s.shown, key = { it.uri.toString() }) { v ->
                            val onClick = { if (selecting) toggle(v) else nav.play(s.shown, s.shown.indexOf(v)) }
                            val onLong = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); toggle(v) }
                            val sel = v.uri.toString() in selected
                            if (grid) VideoGridCard(v, s.positions[v.uri.toString()], sel, onClick, onLong)
                            else VideoListRow(v, s.positions[v.uri.toString()], sel, onClick, onLong, onMore = { actionsFor = v })
                        }
                    }
                }
            }
        }

        CollapsingTopBar(bar, contentUnder = gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0, height = BarHeight) {
            AnimatedContent(selecting to searching, transitionSpec = { fadeIn(Motion.fast()) togetherWith fadeOut(Motion.fast()) }, label = "bar") { (sel, srch) ->
                Box(Modifier.padding(horizontal = 8.dp)) {
                    when {
                        sel -> SelectionBar(selectedItems.size, onClose = { selected = emptySet() }, onAll = { selected = s.shown.map { it.uri.toString() }.toSet() }) { a ->
                            nav.onSelection(a, selectedItems) { selected = emptySet() }
                        }
                        srch -> SearchBar(s.query, vm::setQuery) { searching = false; vm.setQuery("") }
                        else -> TopRow(
                            onLogoLong = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); nav.openVault() },
                            onSearch = { searching = true },
                            onSort = { sortSheet = true },
                            onStream = nav.openStream,
                            onSettings = nav.openSettings,
                        )
                    }
                }
            }
        }
    }

    actionsFor?.let { v -> VideoActionsSheet(v, onDismiss = { actionsFor = null }) { a ->
        actionsFor = null
        if (a == null) nav.play(s.shown, s.shown.indexOf(v).coerceAtLeast(0)) else nav.onSelection(a, listOf(v)) {}
    } }

    hidingFolder?.let { f ->
        ConfirmDialog(
            "Hide \u201c${f.name}\u201d from Videos?",
            "Its videos stay on your phone. You can show the folder again in Settings, under Library.",
            "Hide folder", onDismiss = { hidingFolder = null }, destructive = false,
        ) { vm.hideFolder(f.path); hidingFolder = null }
    }

    if (sortSheet) {
        ModalBottomSheet(onDismissRequest = { sortSheet = false }, containerColor = MoodyTheme.colors.surface) {
            SortSheet(s.sort, s.layout, vm::setSort, vm::setLayout)
        }
    }
}

private fun LazyGridScope.full(content: @Composable () -> Unit) = item(span = { GridItemSpan(maxLineSpan) }) { content() }

private fun LazyGridScope.skeletons(grid: Boolean) {
    if (grid) items(6) { VideoGridSkeleton() } else items(8) { VideoRowSkeleton() }
}

private fun LazyGridScope.continueRow(s: VideoUiState, nav: VideoNav) {
    if (s.continueWatching.isEmpty()) return
    full {
        Column {
            SectionLabel("Continue watching", inset = if (s.layout == LayoutMode.GRID) 0.dp else 16.dp)
            LazyRow(contentPadding = PaddingValues(horizontal = if (s.layout == LayoutMode.GRID) 0.dp else 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(s.continueWatching, key = { it.video.uri.toString() }) { item ->
                    ContinueCard(item, onClick = {
                        val list = s.continueWatching.map { it.video }
                        nav.play(list, list.indexOf(item.video))
                    })
                }
            }
        }
    }
}

/** Logo and name, then search, play a link, sort, settings. Long-press the logo for the vault. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopRow(onLogoLong: () -> Unit, onSearch: () -> Unit, onSort: () -> Unit, onStream: () -> Unit, onSettings: () -> Unit) {
    val c = MoodyTheme.colors
    Row(Modifier.fillMaxWidth().height(BarHeight), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .clip(Radii.md)
                .combinedClickable(onClick = {}, onLongClick = onLogoLong)
                .padding(vertical = 8.dp, horizontal = 8.dp)
                .semantics { contentDescription = "Moody" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MoodyLogo(size = 32.dp)
            Spacer(Modifier.width(10.dp))
            Text("Moody", style = MoodyTheme.type.display, color = c.text)
        }
        Spacer(Modifier.weight(1f))
        IconAction(MoodyIcons.Search, "Search", onSearch, tint = c.text, size = 20.dp)
        IconAction(MoodyIcons.Link, "Play a link", onStream, tint = c.text, size = 20.dp)
        IconAction(MoodyIcons.Sort, "Sort and view", onSort, tint = c.text, size = 20.dp)
        IconAction(MoodyIcons.Settings, "Settings", onSettings, tint = c.text, size = 20.dp)
    }
}

@Composable
private fun Header(s: VideoUiState, onView: (VideoView) -> Unit, onVault: () -> Unit) {
    val c = MoodyTheme.colors
    Column(Modifier.padding(top = 12.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MoodyLogo(size = 48.dp)
            Spacer(Modifier.width(14.dp))
            Box(Modifier.width(1.dp).height(40.dp).background(c.line2))
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Videos", style = MoodyTheme.type.hero, color = c.text)
                Text(
                    if (s.loading) "Looking through your phone" else "${s.all.size} ${if (s.all.size == 1) "video" else "videos"}  \u00b7  ${formatBytes(s.totalBytes)}",
                    style = MoodyTheme.type.caption, color = c.text2,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        androidx.compose.foundation.layout.BoxWithConstraints {
        val narrow = maxWidth < 340.dp
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChoiceChip("Folders", s.view == VideoView.FOLDERS, { onView(VideoView.FOLDERS) }, icon = MoodyIcons.Folder)
            Spacer(Modifier.width(8.dp))
            ChoiceChip("All videos", s.view == VideoView.ALL, { onView(VideoView.ALL) }, icon = MoodyIcons.Film)
            Spacer(Modifier.weight(1f))
            if (!s.disguiseVault) ChoiceChip(if (narrow) "" else "Vault", false, onVault, Modifier.semantics { contentDescription = "Vault" }, icon = MoodyIcons.Vault)
        }
        }
    }
}

/** Section title with the grid / list switch on the right, as in the mockup. */
@Composable
private fun SectionHeader(title: String, layout: LayoutMode, onLayout: (LayoutMode) -> Unit, inset: androidx.compose.ui.unit.Dp) {
    val c = MoodyTheme.colors
    Row(Modifier.fillMaxWidth().padding(start = inset, end = inset, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MoodyTheme.type.heading, color = c.text, modifier = Modifier.weight(1f))
        Row(Modifier.height(30.dp).clip(Radii.pill).border(1.dp, c.line2, Radii.pill).padding(2.dp)) {
            listOf(LayoutMode.GRID to MoodyIcons.Grid, LayoutMode.LIST to MoodyIcons.ListView).forEach { (m, icon) ->
                val on = layout == m
                Box(
                    Modifier
                        .width(36.dp).fillMaxHeight()
                        .clip(Radii.pill)
                        .background(if (on) c.accentWash else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable(role = Role.RadioButton, onClickLabel = if (m == LayoutMode.GRID) "Grid" else "List") { onLayout(m) },
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, null, Modifier.size(16.dp), tint = if (on) c.accent else c.text2) }
            }
        }
    }
}

/** Per-video menu from a row's three dots. Picking null means Play. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoActionsSheet(v: VideoItem, onDismiss: () -> Unit, onPick: (SelectionAction?) -> Unit) {
    val c = MoodyTheme.colors
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surface) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(v.title, style = MoodyTheme.type.heading, color = c.text, maxLines = 2, modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp))
            listOf(
                Triple(null, MoodyIcons.Play, "Play"),
                Triple(SelectionAction.HIDE, MoodyIcons.EyeOff, "Hide in vault"),
                Triple(SelectionAction.SHARE, MoodyIcons.Share, "Share"),
                Triple(SelectionAction.RENAME, MoodyIcons.Rename, "Rename"),
                Triple(SelectionAction.INFO, MoodyIcons.Info, "Details"),
                Triple(SelectionAction.DELETE, MoodyIcons.Delete, "Delete"),
            ).forEach { (a, icon, label) ->
                Row(Modifier.fillMaxWidth().height(52.dp).clickable { onPick(a) }.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, Modifier.size(20.dp), tint = if (a == SelectionAction.DELETE) c.danger else c.text2)
                    Spacer(Modifier.width(16.dp))
                    Text(label, style = MoodyTheme.type.body, color = if (a == SelectionAction.DELETE) c.danger else c.text)
                }
            }
        }
    }
}

@Composable
private fun PartialBanner(onAllow: () -> Unit) {
    val c = MoodyTheme.colors
    Row(
        Modifier.padding(top = 16.dp).fillMaxWidth().clip(Radii.md).background(c.surface).border(1.dp, c.line, Radii.md).padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(MoodyIcons.Info, null, Modifier.size(18.dp), tint = c.text2)
        Spacer(Modifier.width(12.dp))
        Text("Moody can see only the videos you picked", style = MoodyTheme.type.caption, color = c.text2, modifier = Modifier.weight(1f))
        QuietButton("Allow all", onAllow, color = c.accent)
    }
}

@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    val c = MoodyTheme.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
        IconAction(MoodyIcons.ArrowBack, "Close search", onClose, tint = c.text)
        Box(Modifier.weight(1f).height(44.dp).clip(Radii.md).background(c.surface).border(1.dp, c.line2, Radii.md).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text("Search", style = MoodyTheme.type.body, color = c.text3)
            BasicTextField(
                query, onQuery, singleLine = true, textStyle = MoodyTheme.type.body.copy(color = c.text), cursorBrush = SolidColor(c.accent),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        if (query.isNotEmpty()) IconAction(MoodyIcons.Close, "Clear", { onQuery("") })
    }
}

@Composable
internal fun SelectionBar(count: Int, onClose: () -> Unit, onAll: () -> Unit, onAction: (SelectionAction) -> Unit) {
    val c = MoodyTheme.colors
    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
        IconAction(MoodyIcons.Close, "Clear selection", onClose, tint = c.text)
        Text("$count selected", style = MoodyTheme.type.heading, color = c.text, modifier = Modifier.weight(1f).clickable(role = Role.Button, onClick = onAll))
        IconAction(MoodyIcons.EyeOff, "Hide in vault", { onAction(SelectionAction.HIDE) })
        IconAction(MoodyIcons.Share, "Share", { onAction(SelectionAction.SHARE) })
        if (count == 1) IconAction(MoodyIcons.Rename, "Rename", { onAction(SelectionAction.RENAME) })
        if (count == 1) IconAction(MoodyIcons.Info, "Details", { onAction(SelectionAction.INFO) })
        IconAction(MoodyIcons.Delete, "Delete", { onAction(SelectionAction.DELETE) })
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SortSheet(sort: SortSpec, layout: LayoutMode, onSort: (SortSpec) -> Unit, onLayout: (LayoutMode) -> Unit) {
    val c = MoodyTheme.colors
    val t = MoodyTheme.type
    Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
        Text("Sort and view", style = t.title, color = c.text)
        Spacer(Modifier.height(20.dp))
        Text("Sort by", style = t.section, color = c.text3)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(SortKey.DATE to "Date added", SortKey.NAME to "Name", SortKey.SIZE to "Size", SortKey.DURATION to "Length").forEach { (k, label) ->
                ChoiceChip(label, sort.key == k, { onSort(SortSpec(k, if (k == SortKey.NAME) true else false)) })
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Order", style = t.section, color = c.text3)
        Spacer(Modifier.height(8.dp))
        val (asc, desc) = when (sort.key) {
            SortKey.NAME -> "A to Z" to "Z to A"
            SortKey.DATE -> "Oldest first" to "Newest first"
            SortKey.SIZE -> "Smallest first" to "Largest first"
            SortKey.DURATION -> "Shortest first" to "Longest first"
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip(desc, !sort.ascending, { onSort(sort.copy(ascending = false)) })
            ChoiceChip(asc, sort.ascending, { onSort(sort.copy(ascending = true)) })
        }
        Spacer(Modifier.height(16.dp))
        Text("Show as", style = t.section, color = c.text3)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip("Grid", layout == LayoutMode.GRID, { onLayout(LayoutMode.GRID) }, icon = MoodyIcons.Grid)
            ChoiceChip("List", layout == LayoutMode.LIST, { onLayout(LayoutMode.LIST) }, icon = MoodyIcons.ListView)
        }
    }
}
