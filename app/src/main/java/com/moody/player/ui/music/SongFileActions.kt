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
import androidx.compose.ui.unit.dp
import com.moody.player.data.media.AudioItem
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
fun SongInfo(song: AudioItem) {
    val context = LocalContext.current
    val c = MoodyTheme.colors
    var size by remember { mutableStateOf<Long?>(null) }
    var sizeRead by remember { mutableStateOf(false) }
    LaunchedEffect(song.uri) { size = withContext(Dispatchers.IO) { sizeOf(context, song) }; sizeRead = true }
    val unknown = "Unknown"
    val ext = song.fileName.substringAfterLast('.', "").uppercase()
    val rows = listOf(
        "Title" to song.title.ifBlank { unknown },
        "Artist" to song.artist.takeUnless { it.isBlank() || it == "<unknown>" } ,
        "Album" to song.album.takeUnless { it.isBlank() || it == "<unknown>" },
        "Length" to (if (song.durationMs > 0) formatDuration(song.durationMs) else null),
        "Format" to (ext.ifEmpty { null } ?: song.mime.substringAfter('/').uppercase().takeUnless { it.isBlank() || it == "*" }),
        "Size" to (if (!sizeRead) "Reading…" else size?.let(::formatBytes)),
        "Location" to (song.relativePath.ifEmpty { "/" } + song.fileName).takeUnless { song.fileName.isBlank() },
    )
    Column(Modifier.padding(horizontal = 24.dp)) {
        rows.forEach { (k, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                Text(k, style = MoodyTheme.type.body, color = c.text3, modifier = Modifier.width(92.dp))
                Text(v ?: unknown, style = MoodyTheme.type.body, color = if (v == null) c.text3 else c.text, modifier = Modifier.weight(1f))
            }
        }
    }
}
