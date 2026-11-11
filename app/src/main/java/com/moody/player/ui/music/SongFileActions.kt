package com.moody.player.ui.music

import android.app.RecoverableSecurityException
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.ui.unit.dp
import com.moody.player.data.media.AudioItem
import com.moody.player.data.media.deleteCover
import com.moody.player.data.media.storeCover
import com.moody.player.data.media.SongEdit
import com.moody.player.data.media.editFor
import com.moody.player.design.Radii
import com.moody.player.design.components.PrimaryButton
import com.moody.player.design.components.QuietButton
import com.moody.player.design.components.SecondaryButton
import com.moody.player.design.icons.MoodyIcons
import com.moody.player.design.MoodyTheme
import com.moody.player.design.components.LocalToaster
import com.moody.player.domain.formatBytes
import com.moody.player.domain.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Deletes a song's actual file. Called only after the user confirmed in Moody's own dialog; Android 10+
 * then shows its system consent for files Moody didn't create. [onDeleted] runs only once the file is gone.
 */
@Composable
fun rememberSongDelete(onDeleted: (AudioItem) -> Unit): (AudioItem) -> Unit {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<AudioItem?>(null) }
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        val song = pending
        pending = null
        if (song == null) return@rememberLauncherForActivityResult
        if (r.resultCode != android.app.Activity.RESULT_OK) { toaster.show("Not deleted"); return@rememberLauncherForActivityResult }
        scope.launch {
            // Android 11+ already deleted it; on 10 the consent lets us delete now.
            if (Build.VERSION.SDK_INT == 29) withContext(Dispatchers.IO) { runCatching { context.contentResolver.delete(song.uri, null, null) } }
            toaster.show("Song deleted")
            onDeleted(song)
        }
    }
    return { song ->
        if (Build.VERSION.SDK_INT >= 30) {
            pending = song
            consent.launch(IntentSenderRequest.Builder(MediaStore.createDeleteRequest(context.contentResolver, listOf(song.uri)).intentSender).build())
        } else scope.launch {
            try {
                val n = withContext(Dispatchers.IO) { context.contentResolver.delete(song.uri, null, null) }
                if (n > 0) { toaster.show("Song deleted"); onDeleted(song) } else toaster.show("Couldn't delete this song")
            } catch (e: RecoverableSecurityException) {
                pending = song
                consent.launch(IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build())
            } catch (e: SecurityException) {
                toaster.show("Moody isn't allowed to delete this file")
            }
        }
    }
}

private fun sizeOf(context: Context, song: AudioItem): Long? = runCatching {
    context.contentResolver.query(song.uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
        if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
    }
}.getOrNull()

/** Everything Moody knows about a song's file. Missing values read "Unknown" instead of leaving gaps. */
@Composable
fun SongInfo(song: AudioItem, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val c = MoodyTheme.colors
    var size by remember { mutableStateOf<Long?>(null) }
    var sizeRead by remember { mutableStateOf(false) }
    LaunchedEffect(song.uri) { size = withContext(Dispatchers.IO) { sizeOf(context, song) }; sizeRead = true }
    val unknown = "Unknown"
    val ext = song.fileName.substringAfterLast('.', "").uppercase()
    val rows = listOf(
        "Title" to song.title.ifBlank { unknown },
        "Artist" to song.artist.takeUnless { it.isBlank() || it == "<unknown>" || it == "Unknown artist" },
        "Album" to song.album.takeUnless { it.isBlank() || it == "<unknown>" || it == "Unknown album" },
        "Length" to (if (song.durationMs > 0) formatDuration(song.durationMs) else null),
        "Format" to (ext.ifEmpty { null } ?: song.mime.substringAfter('/').uppercase().takeUnless { it.isBlank() || it == "*" }),
        "Size" to (if (!sizeRead) "Reading…" else size?.let(::formatBytes)),
        "Location" to (song.relativePath.ifEmpty { "/" } + song.fileName).takeUnless { song.fileName.isBlank() },
    )
    Column(modifier) {
        rows.forEach { (k, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                Text(k, style = MoodyTheme.type.body, color = c.text3, modifier = Modifier.width(84.dp))
                Text(v ?: unknown, style = MoodyTheme.type.body, color = if (v == null) c.text3 else c.text, modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * Song info as a centred modal. Edit turns the title, artist and album into fields; saved values
 * live in Moody only and the file stays as it is. [original] is the song as its file describes it.
 */
@Composable
fun SongInfoDialog(song: AudioItem, original: AudioItem, onSave: (SongEdit?) -> Unit, onDismiss: () -> Unit) {
    val c = MoodyTheme.colors
    val toaster = LocalToaster.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val edited = song.title != original.title || song.artist != original.artist || song.album != original.album || song.cover != null
    var editing by remember { mutableStateOf(false) }
    var title by remember(song) { mutableStateOf(song.title) }
    var artist by remember(song) { mutableStateOf(song.artist) }
    var album by remember(song) { mutableStateOf(song.album) }
    // The cover being edited: the saved one, a newly picked copy, or null for the file's own art.
    var cover by remember(song) { mutableStateOf(song.cover) }
    // A copy picked in this edit that hasn't been saved yet; removed if the edit is dropped.
    var unsaved by remember { mutableStateOf<String?>(null) }
    fun dropUnsaved() { unsaved?.let { p -> scope.launch(Dispatchers.IO) { deleteCover(context, p) } }; unsaved = null }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val stored = withContext(Dispatchers.IO) { storeCover(context, uri) }
            if (stored == null) { toaster.show("Couldn't use that picture"); return@launch }
            dropUnsaved()
            unsaved = stored
            cover = stored
        }
    }
    val close = { dropUnsaved(); onDismiss() }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .imePadding()
                .background(c.surface, Radii.xl)
                .border(1.dp, c.line, Radii.xl)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ArtworkSquare(song.copy(cover = cover), 56.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (editing) "Edit details" else "Song info", style = MoodyTheme.type.title, color = c.text)
                    Text(
                        if (editing) "Shown in Moody only. The file isn't changed." else if (edited) "Edited in Moody" else song.fileName.ifBlank { song.title },
                        style = MoodyTheme.type.caption, color = c.text3, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            if (editing) {
                Text("Cover", style = MoodyTheme.type.caption, color = c.text3)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ArtworkSquare(song.copy(cover = cover), 64.dp)
                    Spacer(Modifier.width(12.dp))
                    SecondaryButton(if (cover == null) "Choose picture" else "Change", {
                        pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }, icon = MoodyIcons.Image)
                    if (cover != null) {
                        Spacer(Modifier.width(4.dp))
                        QuietButton("Remove", { dropUnsaved(); cover = null })
                    }
                }
                Spacer(Modifier.height(16.dp))
                EditField("Title", title) { title = it }
                Spacer(Modifier.height(12.dp))
                EditField("Artist", artist) { artist = it }
                Spacer(Modifier.height(12.dp))
                EditField("Album", album) { album = it }
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    QuietButton("Cancel", { dropUnsaved(); editing = false; title = song.title; artist = song.artist; album = song.album; cover = song.cover })
                    Spacer(Modifier.width(8.dp))
                    PrimaryButton("Save", {
                        onSave(editFor(original, title, artist, album, cover).takeUnless { it.isEmpty })
                        unsaved = null
                        toaster.show("Details saved")
                        editing = false
                    }, enabled = title.isNotBlank())
                }
            } else {
                SongInfo(song)
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (edited) QuietButton("Restore original", { onSave(null); toaster.show("Original details restored") }, color = c.text2)
                    Spacer(Modifier.weight(1f))
                    QuietButton("Close", close)
                    Spacer(Modifier.width(8.dp))
                    SecondaryButton("Edit", { editing = true }, icon = MoodyIcons.Rename)
                }
            }
        }
    }
}

@Composable
private fun EditField(label: String, value: String, onChange: (String) -> Unit) {
    val c = MoodyTheme.colors
    var focused by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MoodyTheme.type.caption, color = c.text3)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.fillMaxWidth().height(48.dp).background(c.bg, Radii.md)
                .border(1.dp, if (focused) c.accent.copy(alpha = 0.7f) else c.line2, Radii.md).padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value, { onChange(it.take(120)) }, singleLine = true,
                textStyle = MoodyTheme.type.body.copy(color = c.text), cursorBrush = SolidColor(c.accent),
                modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            )
        }
    }
}
