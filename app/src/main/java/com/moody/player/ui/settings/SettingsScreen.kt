package com.moody.player.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.moody.player.BuildConfig
import com.moody.player.data.media.MediaRepository
import com.moody.player.data.media.folderName
import com.moody.player.data.prefs.Settings
import com.moody.player.data.prefs.SettingsStore
import com.moody.player.data.vault.VaultLock
import com.moody.player.design.FontMode
import com.moody.player.design.MoodyTheme
import com.moody.player.design.Radii
import com.moody.player.design.ThemeMode
import com.moody.player.design.components.ChoiceChip
import com.moody.player.design.components.IconAction
import com.moody.player.design.components.LocalToaster
import com.moody.player.design.components.PrimaryButton
import com.moody.player.design.components.QuietButton
import com.moody.player.design.icons.MoodyIcons
import com.moody.player.domain.Decoder
import com.moody.player.domain.LayoutMode
import com.moody.player.domain.PinHasher
import com.moody.player.domain.SubBg
import com.moody.player.domain.SubSize
import com.moody.player.ui.vault.Field
import com.moody.player.ui.vault.RECOVERY_QUESTIONS
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: SettingsStore,
    val lock: VaultLock,
    private val media: MediaRepository,
) : ViewModel() {
    val settings = store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val vaultInfo = lock.info.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    fun update(t: (Settings) -> Settings) = viewModelScope.launch { store.update(t) }
    fun rescan() = viewModelScope.launch { media.rescan() }
}

/** Slot for the Updates group, filled by the update feature. */
typealias UpdatesRow = @Composable () -> Unit

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit, onOpenVault: () -> Unit, updates: UpdatesRow, onShortcuts: () -> Unit = {}) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val info by vm.vaultInfo.collectAsStateWithLifecycle()
    val unlocked by vm.lock.unlocked.collectAsStateWithLifecycle()
    val c = MoodyTheme.colors
    val toaster = LocalToaster.current
    var dialog by remember { mutableStateOf<String?>(null) }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.statusBarsPadding().fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconAction(MoodyIcons.ArrowBack, "Back", onBack, tint = c.text, size = 22.dp)
                Spacer(Modifier.width(4.dp))
                Text("Settings", style = MoodyTheme.type.display, color = c.text)
            }
        }

        item { Group("Appearance") }
        item {
            Choice("Theme", MoodyIcons.Moon) {
                listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark", ThemeMode.MIDNIGHT to "Midnight").forEach { (m, l) ->
                    ChoiceChip(l, s.themeMode == m, { vm.update { it.copy(themeMode = m) } })
                }
            }
        }
        item {
            Choice("Font", null, "App font is Geist. System font follows your phone.", glyph = "Aa") {
                ChoiceChip("App font", s.fontMode == FontMode.APP, { vm.update { it.copy(fontMode = FontMode.APP) } })
                ChoiceChip("System font", s.fontMode == FontMode.SYSTEM, { vm.update { it.copy(fontMode = FontMode.SYSTEM) } })
            }
        }
        item { Toggle("Background drawings", "The moon and horizon behind tab titles.", s.backgrounds, MoodyIcons.Image) { v -> vm.update { it.copy(backgrounds = v) } } }
        item {
            Choice("Videos show as", MoodyIcons.Grid) {
                ChoiceChip("Grid", s.videoLayout == LayoutMode.GRID, { vm.update { it.copy(videoLayout = LayoutMode.GRID) } })
                ChoiceChip("List", s.videoLayout == LayoutMode.LIST, { vm.update { it.copy(videoLayout = LayoutMode.LIST) } })
            }
        }

        item { Group("Player") }
        item { Action("Customize player shortcuts", "${s.playerShortcuts.size} of 4 quick buttons above the video", MoodyIcons.Layers, onShortcuts) }
        item {
            Choice("Decoder", MoodyIcons.Decoder, "HW is smooth and light on battery. SW plays files the phone's chip can't.") {
                Decoder.entries.forEach { d -> ChoiceChip(d.label, s.decoder == d, { vm.update { it.copy(decoder = d) } }) }
            }
        }
        item {
            Choice("Double-tap jumps", MoodyIcons.Rotate) {
                listOf(5, 10, 15, 30).forEach { n -> ChoiceChip("$n s", s.doubleTapSeekSec == n, { vm.update { it.copy(doubleTapSeekSec = n) } }) }
            }
        }
        item { Toggle("Swipe gestures", "Control volume and brightness with vertical swipes.", s.gestures, MoodyIcons.Hand) { v -> vm.update { it.copy(gestures = v) } } }
        item { Toggle("Resume where you left off", "Videos pick up from the last position.", s.resume, MoodyIcons.Replay) { v -> vm.update { it.copy(resume = v) } } }
        item { Toggle("Remember speed and tracks per video", null, s.perFileSettings, MoodyIcons.Speed) { v -> vm.update { it.copy(perFileSettings = v) } } }
        item { Toggle("Keep playing in the background", "Sound continues when you leave the player.", s.backgroundPlay, MoodyIcons.Audio) { v -> vm.update { it.copy(backgroundPlay = v) } } }
        item { Toggle("Picture in picture on leave", "The video shrinks to a small window when you go home.", s.autoPip, MoodyIcons.Pip) { v -> vm.update { it.copy(autoPip = v) } } }
        item {
            Choice("Subtitle size", MoodyIcons.Subtitles) {
                SubSize.entries.forEach { z -> ChoiceChip(z.label, s.subSize == z, { vm.update { it.copy(subSize = z) } }) }
            }
        }
        item {
            Choice("Subtitle background", null) {
                SubBg.entries.forEach { b -> ChoiceChip(b.label, s.subBg == b, { vm.update { it.copy(subBg = b) } }) }
            }
        }

        item { Group("Music") }
        item {
            Choice("Leave out clips shorter than", MoodyIcons.Music, "Hides ringtones and voice notes from Music") {
                listOf(0 to "Keep all", 30 to "30 s", 60 to "1 min").forEach { (n, l) -> ChoiceChip(l, s.minSongSec == n, { vm.update { it.copy(minSongSec = n) } }) }
            }
        }

        item { Group("Library") }
        item { Action("Scan again", "Look for new videos and music now", MoodyIcons.Refresh) { vm.rescan(); toaster.show("Scanning") } }
        if (s.hiddenFolders.isEmpty()) {
            item { Note("Hidden folders show here. Hide one from its menu in Videos") }
        } else {
            item { Note("Hidden folders") }
            s.hiddenFolders.sorted().forEach { path ->
                item(key = path) {
                    Row(Modifier.fillMaxWidth().height(52.dp).padding(start = 56.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(folderName(path), style = MoodyTheme.type.body, color = c.text, modifier = Modifier.weight(1f))
                        QuietButton("Show again", { vm.update { it.copy(hiddenFolders = it.hiddenFolders - path) } }, color = c.accent)
                    }
                }
            }
        }

        item { Group("Vault") }
        item { Toggle("Disguise the vault", "Hides the Vault button. Open it by pressing and holding the Moody logo on Videos.", s.disguiseVault, MoodyIcons.EyeOff) { v -> vm.update { it.copy(disguiseVault = v) } } }
        item {
            Choice("Lock again after", MoodyIcons.Lock, "It also locks whenever you leave Moody") {
                listOf(60 to "1 min", 120 to "2 min", 300 to "5 min", 600 to "10 min").forEach { (n, l) -> ChoiceChip(l, s.vaultAutoLockSec == n, { vm.update { it.copy(vaultAutoLockSec = n) } }) }
            }
        }
        if (info?.setUp == true) {
            if (unlocked) {
                item { Action("Change PIN", null, MoodyIcons.Key) { dialog = "pin" } }
                item { Action("Change recovery question", info?.question, MoodyIcons.Shield) { dialog = "recovery" } }
            } else {
                item { Action("Unlock the vault to change its PIN", null, MoodyIcons.Key, onOpenVault) }
            }
        } else {
            item { Action("Set up the vault", "Choose a PIN for hidden videos", MoodyIcons.Vault, onOpenVault) }
        }

        item { Group("Updates") }
        item { updates() }

        item { Group("About") }
        item { Note("Moody ${BuildConfig.VERSION_NAME}") }
        item {
            Note("Video by libVLC (LGPL 2.1). Music by AndroidX Media3 (Apache 2.0). Type: Geist and Geist Mono (SIL OFL 1.1). Icons drawn after Lucide (ISC).")
        }
        item { Spacer(Modifier.height(48.dp)) }
    }

    when (dialog) {
        "pin" -> ChangePinDialog(vm.lock, onDone = { dialog = null; if (it) toaster.show("PIN changed") })
        "recovery" -> ChangeRecoveryDialog(vm.lock, info?.question.orEmpty(), onDone = { dialog = null; if (it) toaster.show("Recovery question saved") })
    }
}

@Composable
private fun Group(title: String) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(title, style = MoodyTheme.type.heading, color = MoodyTheme.colors.accent, modifier = Modifier.padding(top = 18.dp, bottom = 10.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(MoodyTheme.colors.line))
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MoodyTheme.type.caption, color = MoodyTheme.colors.text3, modifier = Modifier.padding(start = 56.dp, end = 16.dp, top = 8.dp, bottom = 8.dp))
}

@Composable
private fun RowShell(icon: ImageVector?, onClick: (() -> Unit)?, glyph: String? = null, top: Boolean = false, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val c = MoodyTheme.colors
    Column(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = if (top) Alignment.Top else Alignment.CenterVertically,
        ) {
            Box(Modifier.size(32.dp).background(c.surface, Radii.sm), contentAlignment = Alignment.Center) {
                when {
                    glyph != null -> Text(glyph, style = MoodyTheme.type.label, color = c.text)
                    icon != null -> Icon(icon, null, Modifier.size(18.dp), tint = c.text)
                }
            }
            Spacer(Modifier.width(14.dp))
            content()
        }
        Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(c.line))
    }
}

@Composable
private fun Toggle(title: String, body: String?, checked: Boolean, icon: ImageVector? = null, onChange: (Boolean) -> Unit) {
    val c = MoodyTheme.colors
    RowShell(icon, { onChange(!checked) }) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MoodyTheme.type.heading, color = c.text)
            if (body != null) Text(body, style = MoodyTheme.type.caption, color = c.text2)
        }
        Switch(
            checked, onChange, modifier = Modifier.scale(0.78f),
            colors = SwitchDefaults.colors(checkedTrackColor = c.accentDeep, checkedThumbColor = androidx.compose.ui.graphics.Color.White, checkedBorderColor = c.accentDeep, uncheckedTrackColor = c.raised, uncheckedThumbColor = c.text3, uncheckedBorderColor = c.line2),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Choice(title: String, icon: ImageVector?, body: String? = null, glyph: String? = null, chips: @Composable () -> Unit) {
    val c = MoodyTheme.colors
    RowShell(icon, null, glyph, top = true) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MoodyTheme.type.heading, color = c.text)
            if (body != null) Text(body, style = MoodyTheme.type.caption, color = c.text2)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
        }
    }
}

@Composable
private fun Action(title: String, body: String?, icon: ImageVector, onClick: () -> Unit) {
    val c = MoodyTheme.colors
    RowShell(icon, onClick) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MoodyTheme.type.body, color = c.text)
            if (!body.isNullOrBlank()) Text(body, style = MoodyTheme.type.caption, color = c.text3)
        }
        Icon(MoodyIcons.ChevronRight, null, Modifier.size(18.dp), tint = c.text3)
    }
}

@Composable
private fun DialogFrame(title: String, content: @Composable () -> Unit) {
    val c = MoodyTheme.colors
    Column(Modifier.fillMaxWidth().background(c.surface, Radii.xl).border(1.dp, c.line, Radii.xl).padding(24.dp)) {
        Text(title, style = MoodyTheme.type.title, color = c.text)
        Spacer(Modifier.height(16.dp))
        content()
    }
}

@Composable
private fun ChangePinDialog(lock: VaultLock, onDone: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    val valid = PinHasher.isValidPin(a) && a == b
    Dialog(onDismissRequest = { onDone(false) }) {
        DialogFrame("Change PIN") {
            Field("New PIN, 4 to 6 digits", a, digits = true) { v -> a = v.filter(Char::isDigit).take(6) }
            Spacer(Modifier.height(12.dp))
            Field("Enter it again", b, digits = true) { v -> b = v.filter(Char::isDigit).take(6) }
            if (b.length >= 4 && a != b) Text("Those don't match", style = MoodyTheme.type.caption, color = MoodyTheme.colors.danger, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                QuietButton("Cancel", { onDone(false) })
                Spacer(Modifier.width(8.dp))
                PrimaryButton("Save", { scope.launch { lock.setPin(a); onDone(true) } }, enabled = valid)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChangeRecoveryDialog(lock: VaultLock, current: String, onDone: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf(current.ifBlank { RECOVERY_QUESTIONS.first() }) }
    var answer by remember { mutableStateOf("") }
    Dialog(onDismissRequest = { onDone(false) }) {
        DialogFrame("Recovery question") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RECOVERY_QUESTIONS.forEach { r -> ChoiceChip(r, q == r, { q = r }) }
            }
            Spacer(Modifier.height(12.dp))
            Field("Question", q) { q = it }
            Spacer(Modifier.height(12.dp))
            Field("Answer", answer) { answer = it }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                QuietButton("Cancel", { onDone(false) })
                Spacer(Modifier.width(8.dp))
                PrimaryButton("Save", { scope.launch { lock.setRecovery(q, answer); onDone(true) } }, enabled = q.isNotBlank() && answer.trim().length >= 2)
            }
        }
    }
}
