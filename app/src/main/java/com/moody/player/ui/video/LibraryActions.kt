package com.moody.player.ui.video

import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.moody.player.data.media.VideoItem
import com.moody.player.design.MoodyTheme
import com.moody.player.design.Radii
import com.moody.player.design.components.LocalToaster
import com.moody.player.design.components.PrimaryButton
import com.moody.player.design.components.QuietButton
import com.moody.player.domain.formatBytes
import com.moody.player.domain.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Handles the selection bar's actions. [onHide] is supplied by the vault. */
class LibraryActions(
    val handle: (SelectionAction, List<VideoItem>, () -> Unit) -> Unit,
)

private sealed interface Pending {
    data class Delete(val items: List<VideoItem>, val done: () -> Unit) : Pending
    data class Rename(val item: VideoItem, val newName: String, val done: () -> Unit) : Pending
}

@Composable
fun rememberLibraryActions(onHide: (List<VideoItem>, () -> Unit) -> Unit, onChanged: () -> Unit): LibraryActions {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Pending?>(null) }
    var renaming by remember { mutableStateOf<Pair<VideoItem, () -> Unit>?>(null) }
    var confirmDelete by remember { mutableStateOf<Pair<List<VideoItem>, () -> Unit>?>(null) }
    var info by remember { mutableStateOf<VideoItem?>(null) }

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        val p = pending
        pending = null
        if (r.resultCode != android.app.Activity.RESULT_OK || p == null) return@rememberLauncherForActivityResult
        scope.launch {
            when (p) {
                is Pending.Delete -> {
                    // On Android 11+ the system already deleted them; on 10 we retry now that we have consent.
                    if (Build.VERSION.SDK_INT == 29) withContext(Dispatchers.IO) { p.items.forEach { runCatching { context.contentResolver.delete(it.uri, null, null) } } }
                    toaster.show(if (p.items.size == 1) "Video deleted" else "${p.items.size} videos deleted")
                    p.done(); onChanged()
                }
                is Pending.Rename -> {
                    val ok = withContext(Dispatchers.IO) { renameNow(context, p.item, p.newName) }
                    toaster.show(if (ok) "Renamed" else "Couldn't rename this video")
                    p.done(); onChanged()
                }
            }
        }
    }

    fun delete(items: List<VideoItem>, done: () -> Unit) {
        if (Build.VERSION.SDK_INT >= 30) {
            pending = Pending.Delete(items, done)
            val sender = MediaStore.createDeleteRequest(context.contentResolver, items.map { it.uri }).intentSender
            consent.launch(IntentSenderRequest.Builder(sender).build())
        } else {
            confirmDelete = items to done
        }
    }

    fun rename(item: VideoItem, newName: String, done: () -> Unit) {
        val clean = newName.trim().replace(Regex("[\\\\/:*?\"<>|]"), "")
        if (clean.isEmpty()) { toaster.show("Name can't be empty"); return }
        val full = if (item.extension.isNotEmpty()) "$clean.${item.name.substringAfterLast('.')}" else clean
        if (Build.VERSION.SDK_INT >= 30) {
            pending = Pending.Rename(item, full, done)
            val sender = MediaStore.createWriteRequest(context.contentResolver, listOf(item.uri)).intentSender
            consent.launch(IntentSenderRequest.Builder(sender).build())
        } else scope.launch {
            try {
                val ok = withContext(Dispatchers.IO) { renameNow(context, item, full) }
                toaster.show(if (ok) "Renamed" else "Couldn't rename this video")
                done(); onChanged()
            } catch (e: RecoverableSecurityException) {
                pending = Pending.Rename(item, full, done)
                consent.launch(IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build())
            }
        }
    }

    renaming?.let { (item, done) ->
        RenameDialog(item.title, onDismiss = { renaming = null }) { name -> renaming = null; rename(item, name, done) }
    }
    confirmDelete?.let { (items, done) ->
        ConfirmDialog(
            title = if (items.size == 1) "Delete this video?" else "Delete ${items.size} videos?",
            body = "They are removed from your phone, not only from Moody.",
            confirm = "Delete",
            onDismiss = { confirmDelete = null },
        ) {
            confirmDelete = null
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { items.forEach { context.contentResolver.delete(it.uri, null, null) } }
                    toaster.show(if (items.size == 1) "Video deleted" else "${items.size} videos deleted")
                    done(); onChanged()
                } catch (e: RecoverableSecurityException) {
                    pending = Pending.Delete(items, done)
                    consent.launch(IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build())
                }
            }
        }
    }
    info?.let { v -> InfoSheet(v) { info = null } }

    return remember(onHide) {
        LibraryActions { action, items, done ->
            if (items.isEmpty()) return@LibraryActions
            when (action) {
                SelectionAction.SHARE -> {
                    val send = if (items.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, items[0].uri)
                    else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(items.map { it.uri }))
                    send.type = "video/*"
                    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(Intent.createChooser(send, null))
                    done()
                }
                SelectionAction.DELETE -> delete(items, done)
                SelectionAction.RENAME -> renaming = items.first() to done
                SelectionAction.INFO -> { info = items.first(); done() }
                SelectionAction.HIDE -> onHide(items, done)
            }
        }
    }
}

private fun renameNow(context: Context, item: VideoItem, newName: String): Boolean {
    if (Build.VERSION.SDK_INT >= 29) {
        val v = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, newName) }
        return context.contentResolver.update(item.uri, v, null, null) > 0
    }
    @Suppress("DEPRECATION")
    val path = context.contentResolver.query(item.uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: return false
    val src = File(path)
    val dst = File(src.parentFile, newName)
    if (dst.exists() || !src.renameTo(dst)) return false
    MediaScannerConnection.scanFile(context, arrayOf(src.absolutePath, dst.absolutePath), null, null)
    return true
}

@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    val c = MoodyTheme.colors
    var value by remember { mutableStateOf(TextFieldValue(current, TextRange(0, current.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().background(c.surface, Radii.xl).border(1.dp, c.line, Radii.xl).padding(24.dp)) {
            Text("Rename video", style = MoodyTheme.type.title, color = c.text)
            Spacer(Modifier.height(16.dp))
            Text("Name", style = MoodyTheme.type.section, color = c.text3)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(48.dp).background(c.bg, Radii.md).border(1.dp, c.accent.copy(alpha = 0.55f), Radii.md).padding(horizontal = 12.dp), contentAlignment = androidx.compose.ui.Alignment.CenterStart) {
                BasicTextField(value, { value = it }, singleLine = true, textStyle = MoodyTheme.type.body.copy(color = c.text), cursorBrush = SolidColor(c.accent), modifier = Modifier.fillMaxWidth().focusRequester(focus))
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                QuietButton("Cancel", onDismiss)
                Spacer(Modifier.width(8.dp))
                PrimaryButton("Rename", { onRename(value.text) }, enabled = value.text.isNotBlank() && value.text != current)
            }
        }
    }
}

@Composable
fun ConfirmDialog(title: String, body: String, confirm: String, onDismiss: () -> Unit, destructive: Boolean = true, onConfirm: () -> Unit) {
    val c = MoodyTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().background(c.surface, Radii.xl).border(1.dp, c.line, Radii.xl).padding(24.dp)) {
            Text(title, style = MoodyTheme.type.title, color = c.text)
            Spacer(Modifier.height(8.dp))
            Text(body, style = MoodyTheme.type.body, color = c.text2)
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                QuietButton("Cancel", onDismiss)
                Spacer(Modifier.width(8.dp))
                QuietButton(confirm, onConfirm, color = if (destructive) c.danger else c.accent)
            }
        }
    }
}

private data class VideoFacts(val rows: List<Pair<String, String>>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoSheet(v: VideoItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val c = MoodyTheme.colors
    var facts by remember { mutableStateOf<VideoFacts?>(null) }
    LaunchedEffect(v.uri) {
        facts = withContext(Dispatchers.IO) {
            val r = MediaMetadataRetriever()
            val rows = mutableListOf<Pair<String, String>>()
            runCatching {
                r.setDataSource(context, v.uri)
                fun k(key: Int) = r.extractMetadata(key)
                val w = k(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                val h = k(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                if (w != null && h != null) rows += "Resolution" to "$w × $h"
                k(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()?.let { rows += "Bitrate" to "%.1f Mbit/s".format(it / 1_000_000.0) }
                k(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull()?.let { rows += "Frame rate" to "%.2f fps".format(it) }
                k(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)?.let { rows += "Container" to it }
                if (k(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes") rows += "Audio" to "Yes"
            }
            runCatching { r.release() }
            VideoFacts(rows)
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surface) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(v.title, style = MoodyTheme.type.title, color = c.text)
            Spacer(Modifier.height(16.dp))
            val base = listOf(
                "Length" to formatDuration(v.durationMs),
                "Size" to formatBytes(v.sizeBytes),
                "Format" to v.extension.ifEmpty { v.mime },
                "Folder" to v.relativePath.ifEmpty { "/" },
                "File" to v.name,
            )
            (base + facts?.rows.orEmpty()).forEach { (k, value) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(k, style = MoodyTheme.type.body, color = c.text3, modifier = Modifier.width(112.dp))
                    Text(value, style = MoodyTheme.type.mono, color = c.text, modifier = Modifier.weight(1f))
                }
            }
            if (facts == null) Text("Reading details", style = MoodyTheme.type.caption, color = c.text3, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/** For callers that need a Uri list from items. */
fun List<VideoItem>.uris(): List<Uri> = map { it.uri }
