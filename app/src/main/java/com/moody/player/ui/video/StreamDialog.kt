package com.moody.player.ui.video

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.moody.player.data.db.RecentUrl
import com.moody.player.data.db.RecentUrlDao
import com.moody.player.design.MoodyTheme
import com.moody.player.design.Radii
import com.moody.player.design.components.PrimaryButton
import com.moody.player.design.components.QuietButton
import com.moody.player.design.icons.MoodyIcons
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private val SCHEMES = setOf("http", "https", "rtsp", "rtmp", "rtp", "udp", "mms", "mmsh")

/** Returns a playable stream uri, or null with nothing to say when the text is not a link Moody can open. */
fun parseStreamUrl(text: String): Uri? {
    val t = text.trim()
    if (t.isEmpty() || t.contains(' ')) return null
    val u = Uri.parse(if ("://" in t) t else "https://$t")
    return u.takeIf { it.scheme?.lowercase() in SCHEMES && !it.host.isNullOrBlank() }
}

@HiltViewModel
class StreamViewModel @Inject constructor(private val dao: RecentUrlDao) : ViewModel() {
    val recent = dao.recent().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun remember(url: String) = viewModelScope.launch { dao.add(RecentUrl(url, System.currentTimeMillis())) }
    fun forget(url: String) = viewModelScope.launch { dao.remove(url) }
}

@Composable
fun StreamDialog(vm: StreamViewModel, onDismiss: () -> Unit, onPlay: (Uri) -> Unit) {
    val c = MoodyTheme.colors
    val recent by vm.recent.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }
    var touched by remember { mutableStateOf(false) }
    val parsed = parseStreamUrl(text)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    fun go(u: Uri) { vm.remember(u.toString()); onPlay(u) }

    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().background(c.surface, Radii.xl).border(1.dp, c.line, Radii.xl).padding(24.dp)) {
            Text("Play a link", style = MoodyTheme.type.title, color = c.text)
            Spacer(Modifier.height(4.dp))
            Text("HTTP, HLS (.m3u8), RTSP or RTMP", style = MoodyTheme.type.caption, color = c.text3)
            Spacer(Modifier.height(16.dp))
            val showError = touched && text.isNotBlank() && parsed == null
            Box(
                Modifier.fillMaxWidth().height(48.dp).background(c.bg, Radii.md)
                    .border(1.dp, if (showError) c.danger else c.line2, Radii.md).padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (text.isEmpty()) Text("https://", style = MoodyTheme.type.mono, color = c.text3)
                BasicTextField(
                    text, { text = it; touched = false }, singleLine = true,
                    textStyle = MoodyTheme.type.mono.copy(color = c.text), cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { touched = true; parsed?.let(::go) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            if (showError) Text("That doesn't look like a stream link", style = MoodyTheme.type.caption, color = c.danger, modifier = Modifier.padding(top = 6.dp))
            if (recent.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text("Recent", style = MoodyTheme.type.section, color = c.text3)
                Spacer(Modifier.height(4.dp))
                recent.forEach { r ->
                    Row(Modifier.fillMaxWidth().height(44.dp).clickable { parseStreamUrl(r.url)?.let(::go) }, verticalAlignment = Alignment.CenterVertically) {
                        Icon(MoodyIcons.Link, null, Modifier.size(16.dp), tint = c.text3)
                        Spacer(Modifier.width(10.dp))
                        Text(r.url, style = MoodyTheme.type.monoSmall, color = c.text2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Box(Modifier.size(40.dp).clickable { vm.forget(r.url) }, contentAlignment = Alignment.Center) {
                            Icon(MoodyIcons.Close, "Remove", Modifier.size(14.dp), tint = c.text3)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                QuietButton("Cancel", onDismiss)
                Spacer(Modifier.width(8.dp))
                PrimaryButton("Play", { touched = true; parsed?.let(::go) }, icon = MoodyIcons.Play, enabled = text.isNotBlank())
            }
        }
    }
}
