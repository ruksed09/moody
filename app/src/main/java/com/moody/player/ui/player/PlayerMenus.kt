package com.moody.player.ui.player

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moody.player.design.MoodyTheme
import com.moody.player.design.Radii
import com.moody.player.design.icons.MoodyIcons
import com.moody.player.domain.Decoder
import com.moody.player.domain.LoopMode
import com.moody.player.domain.SubBg
import com.moody.player.domain.SubSize
import com.moody.player.domain.formatDuration
import com.moody.player.player.video.PlayerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class PlayerMenu(val title: String, val parent: PlayerMenu? = null) {
    MORE("Playback"),
    AUDIO("Audio"),
    SUBTITLES("Subtitles"),
    SUB_STYLE("Subtitle style", SUBTITLES),
    SUB_DELAY("Subtitle timing", SUBTITLES),
    AUDIO_DELAY("Audio timing", AUDIO),
    SPEED("Speed"),
    DECODER("Decoder", MORE),
    EQUALIZER("Equalizer", MORE),
    AB("A-B repeat", MORE),
    LOOP("Loop", MORE),
    SLEEP("Sleep timer", MORE),
}

private val SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 3f, 4f)
private val SUB_COLORS = listOf(0xFFFFFAF2 to "Warm white", 0xFFFFE58A to "Yellow", 0xFFBFE9F2 to "Ice", 0xFFF2B8A0 to "Peach")

/** The menu panel from the mockup (`.pop`): anchored bottom-right on wide screens, a full sheet on narrow ones. */
@Composable
fun PlayerMenus(menu: PlayerMenu, vm: PlayerViewModel, st: PlayerState, host: PlayerHost, onNavigate: (PlayerMenu) -> Unit, onDismiss: () -> Unit) {
    val c = MoodyTheme.colors
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    ) {
        val narrow = maxWidth < 560.dp
        Column(
            Modifier
                .align(if (narrow) Alignment.BottomCenter else Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(if (narrow) 8.dp else 16.dp)
                .widthIn(max = if (narrow) 560.dp else 340.dp)
                .fillMaxWidth()
                .heightIn(max = maxHeight * 0.8f)
                .clip(Radii.lg)
                .background(Color(0xFA16141B))
                .border(1.dp, c.line2, Radii.lg)
                .clickable(remember { MutableInteractionSource() }, indication = null) {}
                .padding(8.dp),
        ) {
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                if (menu.parent != null) VideoIcon(MoodyIcons.ArrowBack, "Back", { onNavigate(menu.parent) }, size = 18.dp)
                else Spacer(Modifier.width(8.dp))
                Text(menu.title, style = MoodyTheme.type.heading, color = c.text, modifier = Modifier.weight(1f))
                VideoIcon(MoodyIcons.Close, "Close", onDismiss, size = 18.dp)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
            Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
                when (menu) {
                    PlayerMenu.MORE -> MoreMenu(vm, st, host, onNavigate, onDismiss)
                    PlayerMenu.AUDIO -> AudioMenu(vm, st, onNavigate)
                    PlayerMenu.SUBTITLES -> SubtitlesMenu(vm, st, onNavigate)
                    PlayerMenu.SUB_STYLE -> SubStyleMenu(vm)
                    PlayerMenu.SUB_DELAY -> DelayMenu(st.subDelayMs, "Show subtitles earlier or later", vm.engine::setSubDelayMs)
                    PlayerMenu.AUDIO_DELAY -> DelayMenu(st.audioDelayMs, "Move the sound earlier or later", vm.engine::setAudioDelayMs)
                    PlayerMenu.SPEED -> SpeedMenu(vm, st)
                    PlayerMenu.DECODER -> DecoderMenu(vm, st, onDismiss)
                    PlayerMenu.EQUALIZER -> EqualizerMenu(vm)
                    PlayerMenu.AB -> AbMenu(vm)
                    PlayerMenu.LOOP -> LoopMenu(vm)
                    PlayerMenu.SLEEP -> SleepMenu(vm, onDismiss)
                }
            }
        }
    }
}

@Composable
private fun NavRow(icon: ImageVector, label: String, value: String?, onClick: () -> Unit) {
    val c = MoodyTheme.colors
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(Radii.md).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = c.text2)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MoodyTheme.type.body, color = c.text, modifier = Modifier.weight(1f))
        if (value != null) Text(value, style = MoodyTheme.type.monoSmall, color = c.text3, maxLines = 1)
        Spacer(Modifier.width(4.dp))
        Icon(MoodyIcons.ChevronRight, null, Modifier.size(16.dp), tint = c.text3)
    }
}

@Composable
private fun OptionRow(label: String, checked: Boolean, meta: String? = null, onClick: () -> Unit) {
    val c = MoodyTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(Radii.md).clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            if (checked) Icon(MoodyIcons.Check, null, Modifier.size(18.dp), tint = c.accent)
        }
        Spacer(Modifier.width(12.dp))
        Text(label, style = MoodyTheme.type.body, color = if (checked) c.text else c.text2, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (meta != null) Text(meta, style = MoodyTheme.type.monoSmall, color = c.text3)
    }
}

@Composable
private fun Note(text: String) = Text(text, style = MoodyTheme.type.caption, color = MoodyTheme.colors.text3, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))

@Composable
private fun Divider() = Box(Modifier.padding(vertical = 4.dp, horizontal = 4.dp).fillMaxWidth().height(1.dp).background(MoodyTheme.colors.line))

@Composable
private fun MoreMenu(vm: PlayerViewModel, st: PlayerState, host: PlayerHost, go: (PlayerMenu) -> Unit, close: () -> Unit) {
    val loop by vm.loop.collectAsStateWithLifecycle()
    val ab by vm.ab.collectAsStateWithLifecycle()
    val sleep by vm.sleepAt.collectAsStateWithLifecycle()
    NavRow(MoodyIcons.Speed, "Speed", if (st.rate == 1f) "Normal" else rateLabel(st.rate)) { go(PlayerMenu.SPEED) }
    NavRow(MoodyIcons.Audio, "Audio", st.audioTracks.firstOrNull { it.id == st.selectedAudio }?.name) { go(PlayerMenu.AUDIO) }
    NavRow(MoodyIcons.Subtitles, "Subtitles", st.subTracks.firstOrNull { it.id == st.selectedSub }?.name ?: "Off") { go(PlayerMenu.SUBTITLES) }
    Divider()
    NavRow(MoodyIcons.Equalizer, "Equalizer", null) { go(PlayerMenu.EQUALIZER) }
    NavRow(MoodyIcons.Decoder, "Decoder", st.decoder.label) { go(PlayerMenu.DECODER) }
    NavRow(MoodyIcons.AbRepeat, "A-B repeat", if (ab.first != null) "On" else "Off") { go(PlayerMenu.AB) }
    NavRow(MoodyIcons.Repeat, "Loop", loop.label) { go(PlayerMenu.LOOP) }
    NavRow(MoodyIcons.Aspect, "Aspect ratio", st.aspect.label) { val a = st.aspect.next(); vm.engine.setAspect(a); vm.toast(a.label) }
    NavRow(MoodyIcons.Rotate, "Rotate screen", null) { host.onRotate(); close() }
    host.onPip?.let { pip -> NavRow(MoodyIcons.Pip, "Picture in picture", null) { close(); pip() } }
    NavRow(MoodyIcons.Timer, "Sleep timer", when { sleep == null -> "Off"; sleep == -1L -> "End of video"; else -> "${((sleep!! - System.currentTimeMillis()) / 60_000) + 1} min" }) { go(PlayerMenu.SLEEP) }
}

@Composable
private fun AudioMenu(vm: PlayerViewModel, st: PlayerState, go: (PlayerMenu) -> Unit) {
    if (st.audioTracks.isEmpty()) Note("This video has no separate audio tracks")
    st.audioTracks.forEach { t -> OptionRow(t.name, t.id == st.selectedAudio) { vm.engine.selectAudio(t.id); vm.toast(t.name) } }
    Divider()
    NavRow(MoodyIcons.Timer, "Audio timing", delayLabel(st.audioDelayMs)) { go(PlayerMenu.AUDIO_DELAY) }
}

@Composable
private fun SubtitlesMenu(vm: PlayerViewModel, st: PlayerState, go: (PlayerMenu) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            // libVLC reads subtitle slaves from paths, so the picked file is copied into the cache first.
            val name = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "subtitle.srt"
            val file = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(context.cacheDir, "subs").apply { mkdirs() }
                    val out = File(dir, name.takeIf { '.' in it } ?: "$name.srt")
                    context.contentResolver.openInputStream(uri)!!.use { i -> out.outputStream().use { i.copyTo(it) } }
                    out
                }.getOrNull()
            }
            if (file == null) vm.toast("Couldn't read that file") else { vm.engine.addSubtitle(Uri.fromFile(file)); vm.toast("Subtitles added") }
        }
    }
    OptionRow("Off", st.selectedSub == -1) { vm.engine.selectSub(-1); vm.toast("Subtitles off") }
    st.subTracks.filter { it.id != -1 }.forEach { t -> OptionRow(t.name, t.id == st.selectedSub) { vm.engine.selectSub(t.id); vm.toast(t.name) } }
    Divider()
    NavRow(MoodyIcons.Plus, "Load from file", "SRT, ASS, VTT") { picker.launch(arrayOf("*/*")) }
    NavRow(MoodyIcons.Palette, "Style", null) { go(PlayerMenu.SUB_STYLE) }
    NavRow(MoodyIcons.Timer, "Timing", delayLabel(st.subDelayMs)) { go(PlayerMenu.SUB_DELAY) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubStyleMenu(vm: PlayerViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    Note("Size")
    Segmented(SubSize.entries.map { it.label }, s.subSize.ordinal) { vm.updateSubStyle(size = SubSize.entries[it]) }
    Note("Background")
    Segmented(SubBg.entries.map { it.label }, s.subBg.ordinal) { vm.updateSubStyle(bg = SubBg.entries[it]) }
    Note("Colour")
    FlowRow(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SUB_COLORS.forEach { (argb, name) ->
            val on = s.subColor == argb
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .border(2.dp, if (on) MoodyTheme.colors.accent else Color.Transparent, CircleShape)
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(Color(argb))
                    .clickable(role = Role.RadioButton, onClickLabel = name) { vm.updateSubStyle(color = argb) },
            )
        }
    }
    Note("The new style shows after a short reload")
}

@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val c = MoodyTheme.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(Radii.md)
                    .background(if (on) c.accentWash else Color.Transparent)
                    .border(1.dp, if (on) c.accent.copy(alpha = 0.55f) else c.line2, Radii.md)
                    .clickable(role = Role.RadioButton) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) { Text(label, style = MoodyTheme.type.label, color = if (on) c.accent else c.text2) }
        }
    }
}

private fun delayLabel(ms: Long) = if (ms == 0L) "0 ms" else "%+d ms".format(ms)

@Composable
private fun DelayMenu(current: Long, note: String, set: (Long) -> Unit) {
    val c = MoodyTheme.colors
    Note(note)
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        StepButton("-250") { set(current - 250) }
        StepButton("-50") { set(current - 50) }
        Text(delayLabel(current), style = MoodyTheme.type.heading, color = c.text, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        StepButton("+50") { set(current + 50) }
        StepButton("+250") { set(current + 250) }
    }
    if (current != 0L) OptionRow("Reset to 0", false) { set(0) }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.padding(2.dp).size(48.dp, 40.dp).clip(Radii.md).border(1.dp, MoodyTheme.colors.line2, Radii.md).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = MoodyTheme.type.monoSmall, color = MoodyTheme.colors.text) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpeedMenu(vm: PlayerViewModel, st: PlayerState) {
    val c = MoodyTheme.colors
    FlowRow(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SPEEDS.forEach { r ->
            val on = st.rate == r
            Box(
                Modifier.size(60.dp, 40.dp).clip(Radii.md)
                    .background(if (on) c.accentWash else Color.Transparent)
                    .border(1.dp, if (on) c.accent.copy(alpha = 0.55f) else c.line2, Radii.md)
                    .clickable(role = Role.RadioButton) { vm.engine.setRate(r); vm.toast(if (r == 1f) "Normal speed" else "${rateLabel(r)} speed") },
                contentAlignment = Alignment.Center,
            ) { Text(if (r == 1f) "1×" else rateLabel(r), style = MoodyTheme.type.mono, color = if (on) c.accent else c.text2) }
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        StepButton("-0.05") { vm.engine.setRate(((st.rate - 0.05f) * 100).toInt() / 100f).also { } ; vm.toast("${rateLabel(vm.engine.state.value.rate)} speed") }
        Text(rateLabel(st.rate), style = MoodyTheme.type.heading, color = c.text, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        StepButton("+0.05") { vm.engine.setRate(((st.rate + 0.05f) * 100 + 0.5f).toInt() / 100f); vm.toast("${rateLabel(vm.engine.state.value.rate)} speed") }
    }
}

@Composable
private fun DecoderMenu(vm: PlayerViewModel, st: PlayerState, close: () -> Unit) {
    listOf(
        Decoder.HW to "Uses the phone's video chip. Smooth and light on battery",
        Decoder.HW_PLUS to "Hardware for everything, including colour conversion. Fastest when it works",
        Decoder.SW to "Decodes in software. Slower, but plays files the chip can't",
    ).forEach { (d, note) ->
        OptionRow(d.label, st.decoder == d, null) { vm.reopenHere(d); vm.toast("${d.label} decoder"); close() }
        Text(note, style = MoodyTheme.type.caption, color = MoodyTheme.colors.text3, modifier = Modifier.padding(start = 44.dp, end = 12.dp, bottom = 8.dp))
    }
}

@Composable
private fun EqualizerMenu(vm: PlayerViewModel) {
    var selected by remember { mutableStateOf(-1) }
    val presets = remember { vm.engine.equalizerPresets() }
    OptionRow("Off", selected == -1) { selected = -1; vm.engine.setEqualizer(null, null); vm.toast("Equalizer off") }
    presets.forEachIndexed { i, name -> OptionRow(name, selected == i) { selected = i; vm.engine.setEqualizer(i, null); vm.toast(name) } }
}

@Composable
private fun AbMenu(vm: PlayerViewModel) {
    val ab by vm.ab.collectAsStateWithLifecycle()
    Note("Mark the start and end of a part to loop it")
    OptionRow("Set A here", ab.first != null, ab.first?.let { formatDuration(it) }) { vm.markA() }
    OptionRow("Set B here", ab.second != null, ab.second?.let { formatDuration(it) }) { vm.markB() }
    if (ab.first != null) OptionRow("Turn off", false) { vm.clearAb() }
}

@Composable
private fun LoopMenu(vm: PlayerViewModel) {
    val loop by vm.loop.collectAsStateWithLifecycle()
    LoopMode.entries.forEach { m -> OptionRow(m.label, loop == m) { vm.setLoop(m) } }
}

@Composable
private fun SleepMenu(vm: PlayerViewModel, close: () -> Unit) {
    val at by vm.sleepAt.collectAsStateWithLifecycle()
    OptionRow("Off", at == null) { vm.sleepIn(null); close() }
    listOf(15, 30, 45, 60).forEach { m -> OptionRow("$m minutes", false) { vm.sleepIn(m); close() } }
    OptionRow("End of this video", at == -1L) { vm.sleepIn(-1); close() }
}
