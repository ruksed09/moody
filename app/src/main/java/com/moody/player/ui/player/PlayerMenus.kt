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
import androidx.compose.ui.unit.em
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import com.moody.player.domain.Aspect
import com.moody.player.domain.trackLabel
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
    AUDIO("Audio", MORE),
    SUBTITLES("Subtitles", MORE),
    SUB_STYLE("Subtitle style", SUBTITLES),
    SUB_DELAY("Subtitle timing", SUBTITLES),
    AUDIO_DELAY("Audio timing", AUDIO),
    SPEED("Speed", MORE),
    DECODER("Decoder", MORE),
    EQUALIZER("Equalizer", MORE),
    AB("A-B repeat", MORE),
    LOOP("Loop", MORE),
    SLEEP("Sleep timer", MORE),
    ASPECT("Aspect ratio", MORE),
}

private val SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 3f, 4f)
private val SUB_COLORS = listOf(0xFFFFFAF2 to "Warm white", 0xFFFFE58A to "Yellow", 0xFFBFE9F2 to "Ice", 0xFFF2B8A0 to "Peach")
private val SheetBg = Color(0xFA131118)
private val CardBg = Color(0x0AFFFFFF)

fun decoderLabel(d: Decoder) = when (d) { Decoder.HW -> "Hardware"; Decoder.HW_PLUS -> "Hardware+"; Decoder.SW -> "Software" }

/**
 * The one Playback sheet (owner's mockup): drag handle, header with a purple tile, grouped cards.
 * Sub-pages (Speed, Loop...) replace the content inside this same sheet; nothing stacks.
 */
@Composable
fun PlayerMenus(menu: PlayerMenu, vm: PlayerViewModel, st: PlayerState, host: PlayerHost, onNavigate: (PlayerMenu) -> Unit, onDismiss: () -> Unit) {
    val c = MoodyTheme.colors
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    ) {
        val narrow = maxWidth < 560.dp
        Column(
            Modifier
                .align(if (narrow) Alignment.BottomCenter else Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(if (narrow) 8.dp else 16.dp)
                .widthIn(max = if (narrow) 560.dp else 380.dp)
                .fillMaxWidth()
                .heightIn(max = maxHeight * 0.86f)
                .clip(Radii.xl)
                .background(SheetBg)
                .border(1.dp, c.line2, Radii.xl)
                .clickable(remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp).width(36.dp).height(4.dp).clip(Radii.pill).background(c.line3))
            Row(Modifier.fillMaxWidth().height(52.dp).padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (menu.parent != null) {
                    VideoIcon(MoodyIcons.ArrowBack, "Back", { onNavigate(menu.parent) }, size = 18.dp)
                } else {
                    Box(Modifier.size(32.dp).clip(Radii.md).background(c.accentWash).border(1.dp, c.accent.copy(alpha = 0.5f), Radii.md), contentAlignment = Alignment.Center) {
                        Icon(MoodyIcons.Play, null, Modifier.size(14.dp), tint = c.accent)
                    }
                    Spacer(Modifier.width(12.dp))
                }
                Text(menu.title, style = MoodyTheme.type.title, color = c.text, modifier = Modifier.weight(1f))
                VideoIcon(MoodyIcons.Close, "Close", onDismiss, size = 18.dp)
            }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
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
                    PlayerMenu.ASPECT -> AspectMenu(vm, st)
                }
            }
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text.uppercase(), style = MoodyTheme.type.caption.copy(letterSpacing = 0.08.em, fontWeight = FontWeight.SemiBold),
        color = MoodyTheme.colors.text3, modifier = Modifier.padding(start = 6.dp, top = 14.dp, bottom = 8.dp),
    )
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(Radii.lg).background(CardBg).border(1.dp, MoodyTheme.colors.line, Radii.lg), content = content)
}

@Composable
private fun CardDivider() = Box(Modifier.padding(start = 52.dp, end = 12.dp).fillMaxWidth().height(1.dp).background(MoodyTheme.colors.line))

/** A row in a card: purple icon, label, value, chevron, or a switch instead. */
@Composable
private fun SheetRow(
    icon: ImageVector?, label: String, value: String? = null, glyph: String? = null,
    switch: Boolean? = null, onClick: () -> Unit,
) {
    val c = MoodyTheme.colors
    Row(
        Modifier.fillMaxWidth().height(48.dp).clickable(role = if (switch != null) Role.Switch else Role.Button, onClick = onClick).padding(start = 14.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(26.dp), contentAlignment = Alignment.Center) {
            if (glyph != null) Text(glyph, style = MoodyTheme.type.label, color = c.accent)
            else if (icon != null) Icon(icon, null, Modifier.size(20.dp), tint = c.accent)
        }
        Spacer(Modifier.width(12.dp))
        Text(label, style = MoodyTheme.type.body, color = c.text, modifier = Modifier.weight(1f), maxLines = 1)
        if (switch != null) {
            Switch(
                switch, { onClick() }, modifier = Modifier.scale(0.72f),
                colors = SwitchDefaults.colors(checkedTrackColor = c.accentDeep, checkedThumbColor = Color.White, checkedBorderColor = c.accentDeep, uncheckedTrackColor = c.raised, uncheckedThumbColor = c.text3, uncheckedBorderColor = c.line2),
            )
        } else {
            if (value != null) Text(value, style = MoodyTheme.type.caption, color = c.text2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp))
            Spacer(Modifier.width(6.dp))
            Icon(MoodyIcons.ChevronRight, null, Modifier.size(16.dp), tint = c.text3)
        }
    }
}

@Composable
private fun OptionRow(label: String, checked: Boolean, meta: String? = null, onClick: () -> Unit) {
    val c = MoodyTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(Radii.md).clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            if (checked) Icon(MoodyIcons.Check, null, Modifier.size(18.dp), tint = c.accent)
        }
        Spacer(Modifier.width(12.dp))
        Text(label, style = MoodyTheme.type.body, color = if (checked) c.text else c.text2, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (meta != null) {
            Spacer(Modifier.width(8.dp))
            Text(meta, style = MoodyTheme.type.monoSmall, color = c.text2, modifier = Modifier.border(1.dp, c.line3, Radii.sm).padding(horizontal = 5.dp, vertical = 1.dp))
        }
    }
}

@Composable
private fun Note(text: String) = Text(text, style = MoodyTheme.type.caption, color = MoodyTheme.colors.text3, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))

@Composable
private fun Divider() = Box(Modifier.padding(vertical = 4.dp, horizontal = 4.dp).fillMaxWidth().height(1.dp).background(MoodyTheme.colors.line))

private fun speedValue(r: Float) = if (r == 1f) "Normal (1.0\u00d7)" else rateLabel(r)

@Composable
private fun MoreMenu(vm: PlayerViewModel, st: PlayerState, host: PlayerHost, go: (PlayerMenu) -> Unit, close: () -> Unit) {
    val loop by vm.loop.collectAsStateWithLifecycle()
    val ab by vm.ab.collectAsStateWithLifecycle()
    val sleep by vm.sleepAt.collectAsStateWithLifecycle()
    val audio = st.audioTracks.filter { it.id != -1 }
    val subs = st.subTracks.filter { it.id != -1 }
    val audioValue = audio.indexOfFirst { it.id == st.selectedAudio }.takeIf { it >= 0 }?.let { i ->
        val l = trackLabel(audio[i].name, i); if (l.lang != null) "${l.name} \u2013 ${l.lang}" else l.name
    } ?: "Default"
    val subValue = subs.indexOfFirst { it.id == st.selectedSub }.takeIf { it >= 0 }?.let { i -> trackLabel(subs[i].name, i).name } ?: "Off"

    GroupLabel("Audio & display")
    Card {
        SheetRow(MoodyIcons.Speed, "Speed", speedValue(st.rate)) { go(PlayerMenu.SPEED) }
        CardDivider()
        SheetRow(MoodyIcons.Audio, "Audio", audioValue) { go(PlayerMenu.AUDIO) }
        CardDivider()
        SheetRow(MoodyIcons.Cc, "Subtitles", subValue) { go(PlayerMenu.SUBTITLES) }
        CardDivider()
        SheetRow(MoodyIcons.Equalizer, "Equalizer") { go(PlayerMenu.EQUALIZER) }
    }
    GroupLabel("Playback")
    Card {
        SheetRow(null, "A-B repeat", glyph = "A-B", switch = ab.first != null) { vm.toggleAb() }
        CardDivider()
        SheetRow(MoodyIcons.Repeat, "Loop", loop.label) { go(PlayerMenu.LOOP) }
        CardDivider()
        SheetRow(MoodyIcons.Timer, "Sleep timer", when { sleep == null -> "Off"; sleep == -1L -> "End of video"; else -> "${((sleep!! - System.currentTimeMillis()) / 60_000) + 1} min" }) { go(PlayerMenu.SLEEP) }
    }
    GroupLabel("Video")
    Card {
        SheetRow(MoodyIcons.Aspect, "Aspect ratio", st.aspect.label) { go(PlayerMenu.ASPECT) }
        CardDivider()
        SheetRow(MoodyIcons.Rotate, "Rotate screen") { host.onRotate(); close() }
        if (host.onPip != null) {
            CardDivider()
            SheetRow(MoodyIcons.Pip, "Picture in picture", switch = false) { close(); host.onPip.invoke() }
        }
    }
    Spacer(Modifier.height(12.dp))
    Card {
        SheetRow(MoodyIcons.Decoder, "Decoder", decoderLabel(st.decoder)) { go(PlayerMenu.DECODER) }
    }
}

@Composable
private fun AudioMenu(vm: PlayerViewModel, st: PlayerState, go: (PlayerMenu) -> Unit) {
    val audio = st.audioTracks.filter { it.id != -1 }
    if (audio.isEmpty()) Note("This video has one audio track")
    audio.forEachIndexed { i, t ->
        val l = trackLabel(t.name, i)
        OptionRow(l.name, t.id == st.selectedAudio, l.lang) { vm.engine.selectAudio(t.id); vm.toast(l.name) }
    }
    Divider()
    SheetRow(MoodyIcons.Timer, "Audio timing", delayLabel(st.audioDelayMs)) { go(PlayerMenu.AUDIO_DELAY) }
}

@Composable
private fun SubtitlesMenu(vm: PlayerViewModel, st: PlayerState, go: (PlayerMenu) -> Unit) {
    val pick = rememberSubtitlePicker(vm)
    OptionRow("Off", st.selectedSub == -1) { vm.engine.selectSub(-1); vm.toast("Subtitles off") }
    st.subTracks.filter { it.id != -1 }.forEachIndexed { i, t ->
        val l = trackLabel(t.name, i)
        OptionRow(l.name, t.id == st.selectedSub, l.lang) { vm.engine.selectSub(t.id); vm.toast(l.name) }
    }
    Divider()
    SheetRow(MoodyIcons.Plus, "Load from file", "SRT, ASS, VTT") { pick() }
    SheetRow(MoodyIcons.Palette, "Style") { go(PlayerMenu.SUB_STYLE) }
    SheetRow(MoodyIcons.Timer, "Timing", delayLabel(st.subDelayMs)) { go(PlayerMenu.SUB_DELAY) }
}

@Composable
private fun AspectMenu(vm: PlayerViewModel, st: PlayerState) {
    Aspect.entries.forEach { a -> OptionRow(a.label, st.aspect == a) { vm.engine.setAspect(a); vm.toast(a.label) } }
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
        OptionRow(decoderLabel(d), st.decoder == d, null) { vm.reopenHere(d); vm.toast("${decoderLabel(d)} decoder"); close() }
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
