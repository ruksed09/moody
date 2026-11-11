package com.moody.player.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moody.player.data.media.AudioItem
import com.moody.player.design.MoodyTheme
import com.moody.player.design.Radii
import com.moody.player.design.components.EmptyState
import com.moody.player.design.components.IconAction
import com.moody.player.design.components.LocalToaster
import com.moody.player.design.components.PrimaryButton
import com.moody.player.design.components.QuietButton
import com.moody.player.design.components.SecondaryButton
import com.moody.player.design.icons.MoodyIcons
import com.moody.player.design.illustrations.EmptyMusic
import com.moody.player.domain.formatDuration
import com.moody.player.ui.video.ConfirmDialog

enum class SongListKind { ALBUM, ARTIST, FOLDER, PLAYLIST }

/** Album, artist, folder or playlist: a header with play and shuffle, then the songs. */
@Composable
fun SongListScreen(vm: MusicViewModel, kind: SongListKind, key: String, onBack: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val playing by vm.music.state.collectAsStateWithLifecycle()
    val c = MoodyTheme.colors
    val toaster = LocalToaster.current
    var actionsFor by remember { mutableStateOf<AudioItem?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    val playlistId = if (kind == SongListKind.PLAYLIST) key.toLong() else null
    val playlistSongs by (if (playlistId != null && playlistId != FAVOURITES_ID) vm.playlistSongs(playlistId) else kotlinx.coroutines.flow.flowOf(emptyList()))
        .collectAsState(initial = emptyList())
    val (title, songs) = when (kind) {
        SongListKind.ALBUM -> s.albums.firstOrNull { it.id.toString() == key }.let { (it?.name ?: "Album") to (it?.songs ?: emptyList()) }
        SongListKind.ARTIST -> s.artists.firstOrNull { it.name == key }.let { (it?.name ?: key) to (it?.songs ?: emptyList()) }
        SongListKind.FOLDER -> s.folders.firstOrNull { it.path == key }.let { (it?.name ?: "Folder") to (it?.songs ?: emptyList()) }
        SongListKind.PLAYLIST -> if (playlistId == FAVOURITES_ID) "Favourites" to s.favorites
        else (s.playlists.firstOrNull { it.playlist.id == playlistId }?.playlist?.name ?: "Playlist") to playlistSongs
    }
    val editable = kind == SongListKind.PLAYLIST && playlistId != FAVOURITES_ID

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.statusBarsPadding().padding(horizontal = 16.dp)) {
                Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconAction(MoodyIcons.ArrowBack, "Back", onBack, tint = c.text)
                    Spacer(Modifier.weight(1f))
                    if (editable) {
                        IconAction(MoodyIcons.Rename, "Rename playlist", { renaming = true })
                        IconAction(MoodyIcons.Delete, "Delete playlist", { deleting = true })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (songs.isNotEmpty()) {
                        Disc(songs.first(), 96.dp)
                        Spacer(Modifier.width(16.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MoodyTheme.type.display, color = c.text, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        val sub = if (kind == SongListKind.ALBUM) songs.firstOrNull()?.artist else null
                        Text(listOfNotNull(sub, "${songs.size} songs", formatDuration(songs.sumOf { it.durationMs })).joinToString(" · "), style = MoodyTheme.type.mono, color = c.text3)
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (songs.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryButton("Play", { vm.music.playQueue(songs, 0) }, icon = MoodyIcons.Play)
                        SecondaryButton("Shuffle", { vm.music.playQueue(songs, 0, shuffle = true) }, icon = MoodyIcons.Shuffle)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        if (songs.isEmpty()) item {
            EmptyState({ EmptyMusic() }, if (kind == SongListKind.PLAYLIST) "Nothing here yet" else "No songs", if (playlistId == FAVOURITES_ID) "Tap the heart on a song to keep it here" else if (kind == SongListKind.PLAYLIST) "Add songs from their menu, under Add to playlist" else "These songs were moved or deleted")
        }
        itemsIndexed(songs, key = { i, it -> "$i-${it.uri}" }) { i, song ->
            val current = song.uri.toString() == playing.current?.uri?.toString()
            SongRow(song, current, { vm.music.playQueue(songs, i) }, { actionsFor = song }, spinning = current && playing.playing, leadingIndex = if (kind == SongListKind.ALBUM) (song.trackNo.takeIf { it > 0 } ?: (i + 1)) else null)
        }
    }

    actionsFor?.let { song ->
        SongActionsSheet(
            song, vm, onDismiss = { actionsFor = null },
            playlistActions = if (editable) {
                {
                    val idx = songs.indexOf(song)
                    if (idx > 0) SheetRow(MoodyIcons.ChevronUp, "Move up") { vm.reorderPlaylist(playlistId!!, songs.toMutableList().apply { add(idx - 1, removeAt(idx)) }); actionsFor = null }
                    if (idx < songs.lastIndex) SheetRow(MoodyIcons.ChevronDown, "Move down") { vm.reorderPlaylist(playlistId!!, songs.toMutableList().apply { add(idx + 1, removeAt(idx)) }); actionsFor = null }
                    SheetRow(MoodyIcons.Close, "Remove from this playlist") { vm.removeFromPlaylist(playlistId!!, song); toaster.show("Removed"); actionsFor = null }
                }
            } else null,
        )
    }
    if (renaming) NameDialog("Rename playlist", title, "Rename", onDismiss = { renaming = false }) { vm.renamePlaylist(playlistId!!, it); renaming = false }
    if (deleting) ConfirmDialog("Delete “$title”?", "The playlist goes away. The songs stay on your phone.", "Delete", onDismiss = { deleting = false }) {
        vm.deletePlaylist(playlistId!!); deleting = false; onBack()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongActionsSheet(song: AudioItem, vm: MusicViewModel, onDismiss: () -> Unit, playlistActions: (@Composable () -> Unit)? = null) {
    val c = MoodyTheme.colors
    val toaster = LocalToaster.current
    val s by vm.state.collectAsStateWithLifecycle()
    val favs by vm.favoriteSet.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    val openHealth = com.moody.player.ui.health.LocalOpenHealth.current
    var confirmDelete by remember { mutableStateOf(false) }
    val delete = rememberSongDelete { gone -> vm.forgetDeleted(gone); onDismiss() }
    val fav = song.uri.toString() in favs
    // Info is a centred modal, not part of the sheet: the sheet steps aside while it shows.
    if (showInfo) {
        val live = s.songs.firstOrNull { it.uri == song.uri } ?: song
        SongInfoDialog(live, vm.originalSong(live), onSave = { vm.editSong(live, it) }, onDismiss = onDismiss)
        return
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surface) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Row(Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Disc(song, 44.dp, behind = c.surface)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, style = MoodyTheme.type.heading, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, style = MoodyTheme.type.caption, color = c.text3, maxLines = 1)
                }
            }
            if (!picking) {
                SheetRow(MoodyIcons.Next, "Play next") { vm.music.playNext(listOf(song)); toaster.show("Plays next"); onDismiss() }
                SheetRow(MoodyIcons.Queue, "Add to queue") { vm.music.addToQueue(listOf(song)); toaster.show("Added to queue"); onDismiss() }
                SheetRow(MoodyIcons.Playlist, "Add to playlist") { picking = true }
                SheetRow(if (fav) MoodyIcons.HeartFilled else MoodyIcons.Heart, if (fav) "Remove from favourites" else "Add to favourites") {
                    vm.setFavorite(song, !fav); toaster.show(if (fav) "Removed from favourites" else "Added to favourites"); onDismiss()
                }
                playlistActions?.invoke()
                SheetRow(MoodyIcons.Info, "Info") { showInfo = true }
                SheetRow(MoodyIcons.Health, "Check file health") { onDismiss(); openHealth(song.uri) }
                SheetRow(MoodyIcons.Close, "Delete from phone") { confirmDelete = true }
            } else {
                SheetRow(MoodyIcons.Plus, "New playlist") { creating = true }
                s.playlists.forEach { p ->
                    SheetRow(MoodyIcons.Playlist, p.playlist.name, "${p.count}") { vm.addToPlaylist(p.playlist.id, listOf(song)); toaster.show("Added to ${p.playlist.name}"); onDismiss() }
                }
            }
        }
    }
    if (confirmDelete) com.moody.player.ui.video.ConfirmDialog(
        title = "Delete this song?",
        body = "“${song.title}” is removed from your phone, from Moody's playlists and from the queue. This can't be undone.",
        confirm = "Delete",
        onDismiss = { confirmDelete = false },
    ) { confirmDelete = false; delete(song) }
    if (creating) NameDialog("New playlist", "", "Create", onDismiss = { creating = false }) { name ->
        vm.createPlaylist(name, listOf(song)); toaster.show("Added to $name"); creating = false; onDismiss()
    }
}

@Composable
fun SheetRow(icon: ImageVector, label: String, meta: String? = null, onClick: () -> Unit) {
    val c = MoodyTheme.colors
    Row(Modifier.fillMaxWidth().height(52.dp).clickable(onClick = onClick).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(20.dp), tint = c.text2)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MoodyTheme.type.body, color = c.text, modifier = Modifier.weight(1f))
        if (meta != null) Text(meta, style = MoodyTheme.type.monoSmall, color = c.text3)
    }
}

@Composable
fun NameDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val c = MoodyTheme.colors
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().background(c.surface, Radii.xl).border(1.dp, c.line, Radii.xl).padding(24.dp)) {
            Text(title, style = MoodyTheme.type.title, color = c.text)
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(48.dp).background(c.bg, Radii.md).border(1.dp, c.accent.copy(alpha = 0.55f), Radii.md).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                if (text.isEmpty()) Text("Name", style = MoodyTheme.type.body, color = c.text3)
                BasicTextField(text, { text = it.take(60) }, singleLine = true, textStyle = MoodyTheme.type.body.copy(color = c.text), cursorBrush = SolidColor(c.accent), modifier = Modifier.fillMaxWidth().focusRequester(focus))
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                QuietButton("Cancel", onDismiss)
                Spacer(Modifier.width(8.dp))
                PrimaryButton(confirm, { onConfirm(text) }, enabled = text.isNotBlank())
            }
        }
    }
}
