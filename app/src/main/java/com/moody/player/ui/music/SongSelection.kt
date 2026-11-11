package com.moody.player.ui.music

import android.app.RecoverableSecurityException
import android.content.ClipData
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moody.player.data.media.AudioItem
import com.moody.player.design.MoodyTheme
import com.moody.player.design.components.IconAction
import com.moody.player.design.components.LocalToaster
import com.moody.player.design.icons.MoodyIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What can be done with several songs at once. */
enum class SongBatch { PLAYLIST, FAVOURITE, SHARE, DELETE }

data class SongBatchRequest(val action: SongBatch, val songs: List<AudioItem>)

private fun songs(n: Int) = if (n == 1) "1 song" else "$n songs"

/** Replaces the top bar while songs are selected. Tap the count to select every song in view. */
@Composable
fun SongSelectionBar(count: Int, onClose: () -> Unit, onAll: () -> Unit, onAction: (SongBatch) -> Unit) {
    val c = MoodyTheme.colors
    Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconAction(MoodyIcons.Close, "Clear selection", onClose, tint = c.text)
        Text(
            "$count selected", style = MoodyTheme.type.heading, color = c.text,
            modifier = Modifier.weight(1f).clickable(role = Role.Button, onClickLabel = "Select all", onClick = onAll),
        )
        IconAction(MoodyIcons.Playlist, "Add to playlist", { onAction(SongBatch.PLAYLIST) }, tint = c.text)
        IconAction(MoodyIcons.Heart, "Add to favourites", { onAction(SongBatch.FAVOURITE) }, tint = c.text)
        IconAction(MoodyIcons.Share, "Share", { onAction(SongBatch.SHARE) }, tint = c.text)
        IconAction(MoodyIcons.Delete, "Delete from phone", { onAction(SongBatch.DELETE) }, tint = c.text)
    }
}

/**
 * Carries out [request] on its songs, showing the playlist picker or the delete confirmation when the
 * action needs one. [onFinish] gets true when the action went through (the selection can clear).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongBatchHost(vm: MusicViewModel, request: SongBatchRequest?, onFinish: (done: Boolean) -> Unit) {
    val c = MoodyTheme.colors
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val delete = rememberSongsDelete(onFailed = { onFinish(false) }) { gone -> vm.forgetDeleted(gone); onFinish(true) }
    var confirmDelete by remember(request) { mutableStateOf(request?.action == SongBatch.DELETE) }
    var creating by remember(request) { mutableStateOf(false) }
    val r = request ?: return
    when (r.action) {
        SongBatch.FAVOURITE -> LaunchedEffect(r) {
            r.songs.forEach { vm.setFavorite(it, true) }
            toaster.show("Added ${songs(r.songs.size)} to favourites")
            onFinish(true)
        }
        SongBatch.SHARE -> LaunchedEffect(r) {
            val uris = r.songs.map { it.uri }
            val send = if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
            else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            send.type = "audio/*"
            send.clipData = ClipData.newRawUri(null, uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(send, null))
            onFinish(true)
        }
        SongBatch.PLAYLIST -> {
            val s by vm.state.collectAsStateWithLifecycle()
            if (!creating) ModalBottomSheet(onDismissRequest = { onFinish(false) }, containerColor = c.surface) {
                Column(Modifier.padding(bottom = 24.dp)) {
                    Text("Add ${songs(r.songs.size)} to", style = MoodyTheme.type.title, color = c.text, modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp))
                    SheetRow(MoodyIcons.Plus, "New playlist") { creating = true }
                    s.playlists.forEach { p ->
                        SheetRow(MoodyIcons.Playlist, p.playlist.name, "${p.count}") {
                            vm.addToPlaylist(p.playlist.id, r.songs)
                            toaster.show("Added to ${p.playlist.name}")
                            onFinish(true)
                        }
                    }
                }
            }
            if (creating) NameDialog("New playlist", "", "Create", onDismiss = { onFinish(false) }) { name ->
                vm.createPlaylist(name, r.songs)
                toaster.show("Added to $name")
                onFinish(true)
            }
        }
        SongBatch.DELETE -> if (confirmDelete) com.moody.player.ui.video.ConfirmDialog(
            title = "Delete ${songs(r.songs.size)}?",
            body = "They are removed from your phone, from Moody's playlists and from the queue. This can't be undone.",
            confirm = "Delete",
            onDismiss = { onFinish(false) },
        ) { confirmDelete = false; delete(r.songs) }
    }
}

/**
 * Deletes several song files with one system confirmation on Android 11 and later. On older versions
 * each file is deleted directly; files Moody may not delete are counted and reported. [onFailed] runs
 * when nothing was deleted (the prompt was cancelled), so the caller can let the user try again.
 */
@Composable
fun rememberSongsDelete(onFailed: () -> Unit = {}, onDeleted: (List<AudioItem>) -> Unit): (List<AudioItem>) -> Unit {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<List<AudioItem>>(emptyList()) }
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        val list = pending
        pending = emptyList()
        if (list.isEmpty()) return@rememberLauncherForActivityResult
        if (res.resultCode != android.app.Activity.RESULT_OK) { toaster.show("Not deleted"); onFailed(); return@rememberLauncherForActivityResult }
        toaster.show("Deleted ${songs(list.size)}")
        onDeleted(list)
    }
    return { list ->
        if (list.isNotEmpty()) {
            if (Build.VERSION.SDK_INT >= 30) {
                pending = list
                consent.launch(IntentSenderRequest.Builder(MediaStore.createDeleteRequest(context.contentResolver, list.map { it.uri }).intentSender).build())
            } else scope.launch {
                val gone = withContext(Dispatchers.IO) {
                    list.filter { song ->
                        try { context.contentResolver.delete(song.uri, null, null) > 0 } catch (e: RecoverableSecurityException) { false } catch (e: SecurityException) { false }
                    }
                }
                val failed = list.size - gone.size
                toaster.show(if (failed == 0) "Deleted ${songs(gone.size)}" else "Deleted ${songs(gone.size)}; ${songs(failed)} couldn't be deleted")
                if (gone.isNotEmpty()) onDeleted(gone) else onFailed()
            }
        }
    }
}
