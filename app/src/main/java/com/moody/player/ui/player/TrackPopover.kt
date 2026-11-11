package com.moody.player.ui.player

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moody.player.design.MoodyTheme
import com.moody.player.design.Radii
import com.moody.player.design.icons.MoodyIcons
import com.moody.player.domain.trackLabel
import com.moody.player.player.video.PlayerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class TrackKind { AUDIO, SUBS }

/** Opens the system file picker for a subtitle file, copies it to the cache (libVLC reads slaves by path) and adds it. */
@Composable
fun rememberSubtitlePicker(vm: PlayerViewModel): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val raw = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "subtitle.srt"
            val name = raw.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(80)
            val file = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(context.cacheDir, "subs").apply { mkdirs() }
                    val out = File(dir, name.takeIf { '.' in it } ?: "$name.srt")
                    context.contentResolver.openInputStream(uri)!!.use { i -> out.outputStream().use { i.copyTo(it) } }
                    out
                }.getOrNull()
            }
            if (file == null) vm.toast("Couldn't read that file") else vm.importSubtitle(Uri.fromFile(file))
        }
    }
    return { picker.launch(arrayOf("*/*")) }
}

/** Compact list of audio or subtitle tracks, anchored top-right under the toolbar. */
@Composable
fun TrackPopover(kind: TrackKind, vm: PlayerViewModel, st: PlayerState, onDismiss: () -> Unit) {
    val c = MoodyTheme.colors
    val pickSubtitle = rememberSubtitlePicker(vm)
    // libVLC lists a "Disable" pseudo-track with id -1; it is shown as "Off" for subtitles and hidden for audio.
    val list = (if (kind == TrackKind.AUDIO) st.audioTracks else st.subTracks).filter { it.id != -1 }
    val studio by vm.studio.collectAsStateWithLifecycle()
    val studioOn by vm.studioOn.collectAsStateWithLifecycle()
    val selected = if (kind == TrackKind.AUDIO) st.selectedAudio else if (studioOn) -2 else st.selectedSub
    BoxWithConstraints(
        Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    ) {
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 52.dp, end = 8.dp)
                .widthIn(max = 300.dp)
                .fillMaxWidth(0.82f)
                .heightIn(max = maxHeight * 0.6f)
                .clip(Radii.lg)
                .background(Color(0xFA16141B))
                .border(1.dp, c.line2, Radii.lg)
                .clickable(remember { MutableInteractionSource() }, indication = null) {}
                .padding(vertical = 6.dp),
        ) {
            Text(
                if (kind == TrackKind.AUDIO) "Audio" else "Subtitles",
                style = MoodyTheme.type.label, color = c.text3,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (kind == TrackKind.SUBS) {
                    TrackRow("Off", null, selected == -1 && !studioOn) { vm.subsOff(); vm.toast("Subtitles off"); onDismiss() }
                    studio?.let { d -> TrackRow(d.name, "File · ${d.format.label}", studioOn) { vm.useStudio(); vm.toast(d.name); onDismiss() } }
                }
                if (list.isEmpty() && kind == TrackKind.AUDIO) {
                    Text("This video has one audio track", style = MoodyTheme.type.caption, color = c.text3, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                }
                list.forEachIndexed { i, t ->
                    val label = trackLabel(t.name, i)
                    TrackRow(label.name, label.lang, t.id == selected) {
                        if (kind == TrackKind.AUDIO) vm.engine.selectAudio(t.id) else vm.useVlcSub(t.id)
                        vm.toast(label.name)
                        onDismiss()
                    }
                }
                if (kind == TrackKind.SUBS) {
                    Box(Modifier.padding(vertical = 4.dp).fillMaxWidth().height(1.dp).background(c.line))
                    Row(
                        Modifier.fillMaxWidth().height(44.dp).clickable(role = Role.Button) { pickSubtitle(); onDismiss() }.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(MoodyIcons.Plus, null, Modifier.size(16.dp), tint = c.accent)
                        Spacer(Modifier.width(10.dp))
                        Text("Load from file", style = MoodyTheme.type.body, color = c.text)
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackRow(name: String, lang: String?, checked: Boolean, onClick: () -> Unit) {
    val c = MoodyTheme.colors
    Row(
        Modifier.fillMaxWidth().height(44.dp).clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            if (checked) Icon(MoodyIcons.Check, null, Modifier.size(16.dp), tint = c.accent)
        }
        Spacer(Modifier.width(10.dp))
        // The name gives way so the language tag always has room.
        Text(name, style = MoodyTheme.type.body, color = if (checked) c.text else c.text2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (lang != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                lang, style = MoodyTheme.type.monoSmall, color = c.text2,
                modifier = Modifier.border(1.dp, c.line3, Radii.sm).padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
    }
}
