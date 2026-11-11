package com.moody.player.ui.music

import android.graphics.drawable.BitmapDrawable
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import com.moody.player.design.components.ActionChip
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.palette.graphics.Palette
import com.moody.player.data.db.Favorite
import com.moody.player.data.db.FavoriteDao
import com.moody.player.design.MoodyTheme
import com.moody.player.design.Radii
import com.moody.player.design.components.ChoiceChip
import com.moody.player.design.components.IconAction
import com.moody.player.design.components.LocalToaster
import com.moody.player.design.components.Motion
import com.moody.player.design.icons.MoodyIcons
import com.moody.player.domain.formatDuration
import com.moody.player.player.music.MusicController
import com.moody.player.player.music.MusicState
import com.moody.player.player.music.RepeatMode
import com.moody.player.ui.player.SeekBar
import com.moody.player.ui.player.rateLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    val music: MusicController,
    private val favorites: FavoriteDao,
    private val media: com.moody.player.data.media.MediaRepository,
    private val lyricsStore: com.moody.player.data.media.LyricsStore,
    private val settingsStore: com.moody.player.data.prefs.SettingsStore,
) : ViewModel() {
    val settings = settingsStore.settings.stateIn(viewModelScope, SharingStarted.Eagerly, com.moody.player.data.prefs.Settings())
    fun updateViz(f: (com.moody.player.data.prefs.Settings) -> com.moody.player.data.prefs.Settings) = viewModelScope.launch { settingsStore.update(f) }
    fun originalSong(song: com.moody.player.data.media.AudioItem) = media.originalSong(song.uri) ?: song
    fun editSong(song: com.moody.player.data.media.AudioItem, edit: com.moody.player.data.media.SongEdit?) = viewModelScope.launch { media.editSong(song.uri, edit) }
    val state = music.state
    val favoriteUris = favorites.all().map { l -> l.map { it.audioUri }.toSet() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
    fun toggleFavorite(uri: String, on: Boolean) = viewModelScope.launch { if (on) favorites.add(Favorite(uri)) else favorites.remove(uri) }

    /** Lyrics of one song: null lyrics once loaded means the song has none yet. */
    data class LyricsUi(val uri: android.net.Uri? = null, val lyrics: com.moody.player.domain.Lyrics? = null, val loading: Boolean = true, val added: Boolean = false)
    private val _lyrics = kotlinx.coroutines.flow.MutableStateFlow(LyricsUi())
    val lyrics: kotlinx.coroutines.flow.StateFlow<LyricsUi> = _lyrics

    fun loadLyrics(song: android.net.Uri, force: Boolean = false) {
        if (!force && _lyrics.value.uri == song) return
        _lyrics.value = LyricsUi(song)
        viewModelScope.launch {
            val l = lyricsStore.load(song)
            if (_lyrics.value.uri == song) _lyrics.value = LyricsUi(song, l, loading = false, added = lyricsStore.hasAdded(song))
        }
    }

    fun importLyrics(song: android.net.Uri, picked: android.net.Uri, done: (Boolean) -> Unit) = viewModelScope.launch {
        val ok = lyricsStore.import(song, picked)
        if (ok) loadLyrics(song, force = true)
        done(ok)
    }

    fun removeLyrics(song: android.net.Uri) = viewModelScope.launch {
        lyricsStore.remove(song)
        loadLyrics(song, force = true)
    }
}

@Composable
fun NowPlayingScreen(vm: NowPlayingViewModel, onClose: () -> Unit, onEqualizer: () -> Unit, onFullscreenViz: () -> Unit = {}) {
    val s by vm.state.collectAsStateWithLifecycle()
    val favs by vm.favoriteUris.collectAsStateWithLifecycle()
    val c = MoodyTheme.colors
    val toaster = LocalToaster.current
    var queueOpen by remember { mutableStateOf(false) }
    var extrasOpen by remember { mutableStateOf<String?>(null) }
    var infoOpen by remember { mutableStateOf(false) }
    // 0 song (artwork), 1 lyrics, 2 visualizer.
    var mode by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }
    val showLyrics = mode == 1
    val settings by vm.settings.collectAsStateWithLifecycle()
    var tuneOpen by remember { mutableStateOf(false) }
    val item = s.current
    val lyr by vm.lyrics.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(showLyrics, item?.uri) { if (showLyrics && item != null) vm.loadLyrics(item.uri) }
    var lyricsTarget by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<android.net.Uri?>(null) }
    val pickLyrics = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { picked ->
        val song = lyricsTarget
        if (picked != null && song != null) vm.importLyrics(song, picked) { ok ->
            toaster.show(if (ok) "Lyrics added" else "That file has no lyrics Moody can read")
        }
    }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
            Row(Modifier.fillMaxWidth().height(48.dp).offset(x = (-12).dp), verticalAlignment = Alignment.CenterVertically) {
                IconAction(MoodyIcons.ChevronDown, "Close", onClose, tint = c.text)
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { ViewSwitch(mode) { mode = it } }
                Box(Modifier.offset(x = 24.dp)) { IconAction(MoodyIcons.Queue, "Queue", { queueOpen = true }, tint = c.text) }
            }
            // Song, lyrics and visualizer share the disc's place and crossfade into each other.
            androidx.compose.animation.Crossfade(if (item == null) 0 else mode, Modifier.weight(1f).fillMaxWidth(), animationSpec = Motion.base(), label = "art-lyrics") { shown ->
            if (shown == 2 && item != null) {
                VisualizerPanel(
                    settings, s.playing, c.accentHi,
                    onStyle = { v -> vm.updateViz { it.copy(vizStyle = v) } },
                    onTune = { tuneOpen = true },
                    onFullscreen = onFullscreenViz,
                    modifier = Modifier.fillMaxSize().padding(vertical = 12.dp),
                )
            } else if (shown == 1 && item != null) {
                LyricsPanel(
                    lyrics = lyr.lyrics?.takeIf { lyr.uri == item.uri && !it.isEmpty },
                    loading = lyr.uri != item.uri || lyr.loading,
                    positionMs = s.positionMs,
                    canRemove = lyr.added && lyr.uri == item.uri,
                    onSeek = vm.music::seekTo,
                    onAdd = { lyricsTarget = item.uri; pickLyrics.launch(arrayOf("text/*", "application/octet-stream")) },
                    onRemove = { vm.removeLyrics(item.uri); toaster.show("Lyrics removed") },
                    modifier = Modifier.fillMaxSize().padding(vertical = 12.dp),
                )
            } else
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                // The song's disc, as large as fits, turning while it plays.
                val side = minOf(maxWidth - 48.dp, maxHeight - 32.dp).coerceIn(120.dp, 320.dp)
                Disc(item, side, spinning = s.playing, sharedKey = NOW_PLAYING_DISC)
            }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item?.title ?: "Nothing playing", style = MoodyTheme.type.display, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    Text(listOfNotNull(item?.artist, item?.album?.takeIf { it.isNotBlank() }).joinToString(" · "), style = MoodyTheme.type.body, color = c.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (item != null) {
                    IconAction(MoodyIcons.Info, "Song info", { infoOpen = true }, tint = c.text2)
                    val fav = item.uri.toString() in favs
                    IconAction(if (fav) MoodyIcons.HeartFilled else MoodyIcons.Heart, if (fav) "Remove from favourites" else "Add to favourites", {
                        vm.toggleFavorite(item.uri.toString(), !fav)
                        toaster.show(if (fav) "Removed from favourites" else "Added to favourites")
                    }, tint = if (fav) c.accent else c.text2)
                }
            }
            Spacer(Modifier.height(12.dp))
            SeekBar(s.positionMs, s.durationMs, vm.music::seekTo, {}, rail = c.line2, tipBg = c.text, tipText = c.bg)
            Row(Modifier.fillMaxWidth()) {
                Text(formatDuration(s.positionMs).let { if (it == "--:--") "0:00" else it }, style = MoodyTheme.type.mono, color = c.text3)
                Spacer(Modifier.weight(1f))
                Text(formatDuration(s.durationMs), style = MoodyTheme.type.mono, color = c.text3)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconAction(MoodyIcons.Shuffle, if (s.shuffle) "Shuffle on" else "Shuffle off", { vm.music.setShuffle(!s.shuffle); toaster.show(if (s.shuffle) "Shuffle off" else "Shuffle on") }, tint = if (s.shuffle) c.accent else c.text2)
                IconAction(MoodyIcons.Prev, "Previous", vm.music::prev, tint = c.text, size = 28.dp)
                val playSrc = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                val playPressed by playSrc.collectIsPressedAsState()
                val playScale by animateFloatAsState(if (playPressed) 0.94f else 1f, Motion.fast(), label = "play")
                Box(
                    Modifier.size(64.dp).graphicsLayer { scaleX = playScale; scaleY = playScale }.clip(CircleShape).background(c.accent)
                        .clickable(playSrc, androidx.compose.material3.ripple(color = c.accentInk), role = Role.Button, onClick = vm.music::toggle)
                        .semantics { contentDescription = if (s.playing) "Pause" else "Play" },
                    contentAlignment = Alignment.Center,
                ) { Icon(if (s.playing) MoodyIcons.Pause else MoodyIcons.Play, null, Modifier.size(28.dp), tint = c.accentInk) }
                IconAction(MoodyIcons.Next, "Next", vm.music::next, tint = c.text, size = 28.dp)
                IconAction(
                    if (s.repeat == RepeatMode.ONE) MoodyIcons.RepeatOne else MoodyIcons.Repeat,
                    "Repeat: ${s.repeat.name.lowercase()}",
                    { vm.music.cycleRepeat(); toaster.show(when (s.repeat) { RepeatMode.OFF -> "Repeat all"; RepeatMode.ALL -> "Repeat this song"; RepeatMode.ONE -> "Repeat off" }) },
                    tint = if (s.repeat != RepeatMode.OFF) c.accent else c.text2,
                )
            }
            // Speed, sleep and equalizer sit under the controls: secondary, but one tap away.
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                ActionChip(if (s.speed == 1f) "Speed" else rateLabel(s.speed), { extrasOpen = "speed" }, icon = MoodyIcons.Speed, active = s.speed != 1f)
                ActionChip(s.sleepAt?.let { "${((it - System.currentTimeMillis()) / 60_000) + 1} min" } ?: "Sleep", { extrasOpen = "sleep" }, icon = MoodyIcons.Timer, active = s.sleepAt != null)
                ActionChip("Equalizer", onEqualizer, icon = MoodyIcons.Equalizer)
            }
        }
    }

    if (infoOpen && item != null) SongInfoDialog(item, vm.originalSong(item), onSave = { vm.editSong(item, it) }, onDismiss = { infoOpen = false })
    if (queueOpen) QueueSheet(s, vm.music, onDismiss = { queueOpen = false })
    extrasOpen?.let { which -> ExtrasSheet(which, s, vm.music, onDismiss = { extrasOpen = null }) }
    if (tuneOpen) VizTuneSheet(settings, { f -> vm.updateViz(f) }, onDismiss = { tuneOpen = false })
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ExtrasSheet(which: String, s: MusicState, music: MusicController, onDismiss: () -> Unit) {
    val c = MoodyTheme.colors
    val toaster = LocalToaster.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surface) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(if (which == "speed") "Speed" else "Sleep timer", style = MoodyTheme.type.title, color = c.text)
            Spacer(Modifier.height(16.dp))
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (which == "speed") {
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { r ->
                        ChoiceChip(if (r == 1f) "Normal" else rateLabel(r), s.speed == r, { music.setSpeed(r); onDismiss() })
                    }
                } else {
                    ChoiceChip("Off", s.sleepAt == null, { music.sleepIn(null); toaster.show("Sleep timer off"); onDismiss() })
                    listOf(15, 30, 45, 60, 90).forEach { m -> ChoiceChip("$m min", false, { music.sleepIn(m); toaster.show("Music stops in $m min"); onDismiss() }) }
                }
            }
        }
    }
}

/** Queue with long-press-drag to reorder (on the handle) and swipe to remove. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun QueueSheet(s: MusicState, music: MusicController, onDismiss: () -> Unit) {
    val c = MoodyTheme.colors
    val rowH = 64.dp
    val rowPx = with(LocalDensity.current) { rowH.toPx() }
    var dragIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (s.index - 2).coerceAtLeast(0))
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surface) {
        Column(Modifier.padding(bottom = 16.dp)) {
            Row(Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
                Text("Queue", style = MoodyTheme.type.title, color = c.text, modifier = Modifier.weight(1f))
                Text("${s.queue.size} songs", style = MoodyTheme.type.mono, color = c.text3)
            }
            LazyColumn(state = listState, modifier = Modifier.height(480.dp)) {
                itemsIndexed(s.queue, key = { i, it -> "$i-${it.uri}" }) { i, song ->
                    val dismiss = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
                        if (v != SwipeToDismissBoxValue.Settled && i != s.index) { music.removeAt(i); true } else false
                    })
                    val dragging = i == dragIndex
                    SwipeToDismissBox(
                        dismiss,
                        backgroundContent = {
                            Box(Modifier.fillMaxSize().background(c.danger.copy(alpha = 0.15f)).padding(horizontal = 24.dp), contentAlignment = Alignment.CenterEnd) {
                                Text("Remove", style = MoodyTheme.type.label, color = c.danger)
                            }
                        },
                        modifier = Modifier.zIndex(if (dragging) 1f else 0f).offset { androidx.compose.ui.unit.IntOffset(0, if (dragging) dragOffset.roundToInt() else 0) },
                    ) {
                        Row(
                            Modifier.fillMaxWidth().height(rowH).background(if (dragging) c.raised else c.surface).clickable { music.skipTo(i) }.padding(start = 24.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Disc(song, 44.dp, behind = if (dragging) c.raised else c.surface)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(song.title, style = MoodyTheme.type.bodyStrong, color = if (i == s.index) c.accent else c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(song.artist, style = MoodyTheme.type.caption, color = c.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Box(
                                Modifier.size(48.dp).pointerInput(i, s.queue.size) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { dragIndex = i; dragOffset = 0f },
                                        onDragEnd = {
                                            val target = (dragIndex + (dragOffset / rowPx).roundToInt()).coerceIn(0, s.queue.lastIndex)
                                            music.move(dragIndex, target)
                                            dragIndex = -1; dragOffset = 0f
                                        },
                                        onDragCancel = { dragIndex = -1; dragOffset = 0f },
                                    ) { ch, d -> ch.consume(); dragOffset += d.y }
                                }.semantics { contentDescription = "Hold and drag to reorder" },
                                contentAlignment = Alignment.Center,
                            ) { Icon(MoodyIcons.DragHandle, null, Modifier.size(20.dp), tint = c.text3) }
                        }
                    }
                }
            }
        }
    }
}

/** Song, Lyrics or Visual, as a small switch in the header. The lit part slides between them. */
@Composable
private fun ViewSwitch(mode: Int, onChange: (Int) -> Unit) {
    val c = MoodyTheme.colors
    val seg = 72.dp
    val x by androidx.compose.animation.core.animateDpAsState(seg * mode, Motion.base(), label = "switch")
    Box(
        Modifier.height(34.dp).clip(Radii.pill).background(c.surface).border(1.dp, c.line, Radii.pill).padding(3.dp),
    ) {
        Box(Modifier.offset(x = x).width(seg).fillMaxHeight().clip(Radii.pill).background(c.raised))
        Row {
            listOf("Song", "Lyrics", "Visual").forEachIndexed { v, label ->
                val on = v == mode
                val tc by animateColorAsState(if (on) c.text else c.text3, Motion.base(), label = "switch-text")
                Box(
                    Modifier.width(seg).fillMaxHeight().clip(Radii.pill)
                        .clickable(role = Role.Tab) { onChange(v) }
                        .semantics { contentDescription = "Show ${label.lowercase()}" },
                    contentAlignment = Alignment.Center,
                ) { Text(label, style = MoodyTheme.type.label, color = tc) }
            }
        }
    }
}

/** The visualizer in place of the artwork: the picture, style chips, tuning and fullscreen. */
@Composable
private fun VisualizerPanel(
    settings: com.moody.player.data.prefs.Settings, playing: Boolean, tint: Color,
    onStyle: (com.moody.player.domain.VizStyle) -> Unit, onTune: () -> Unit, onFullscreen: () -> Unit, modifier: Modifier = Modifier,
) {
    val c = MoodyTheme.colors
    val frames = rememberVizFrames(active = true, playing = playing, s = settings)
    val fallback by com.moody.player.player.music.AudioTap.onFallbackEngine.collectAsStateWithLifecycle()
    val sideFailed by com.moody.player.player.music.AudioTap.sidecarFailed.collectAsStateWithLifecycle()
    Column(modifier) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (fallback && sideFailed) VizUnavailable(Modifier.fillMaxSize())
            else VisualizerCanvas(settings.vizStyle, frames, c.accent, tint, settings.vizIntensity, Modifier.fillMaxSize())
            Row(Modifier.align(Alignment.TopEnd)) {
                IconAction(MoodyIcons.Equalizer, "Tune the visualizer", onTune, tint = c.text2)
                IconAction(MoodyIcons.Fullscreen, "Full screen visualizer", onFullscreen, tint = c.text2)
            }
        }
        VizStylePicker(settings.vizStyle, onStyle, Modifier.fillMaxWidth())
    }
}
