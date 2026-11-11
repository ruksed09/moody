package com.moody.player.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings as SysSettings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import com.moody.player.BuildConfig
import com.moody.player.data.db.PositionDao
import com.moody.player.data.media.AccessLevel
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
import com.moody.player.domain.PlayerGesture
import com.moody.player.domain.SEEK_STEPS
import com.moody.player.domain.SubBg
import com.moody.player.domain.SubSize
import com.moody.player.domain.formatBytes
import com.moody.player.ui.vault.Field
import com.moody.player.ui.vault.RECOVERY_QUESTIONS
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: SettingsStore,
    val lock: VaultLock,
    private val media: MediaRepository,
    private val positions: PositionDao,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val settings = store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val vaultInfo = lock.info.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val access = media.access
    private val _cacheBytes = MutableStateFlow<Long?>(null)
    val cacheBytes = _cacheBytes.asStateFlow()

    fun update(t: (Settings) -> Settings) = viewModelScope.launch { store.update(t) }
    fun rescan() = viewModelScope.launch { media.rescan() }
    fun refreshAccess() = media.refreshAccess()
    fun clearHistory() = viewModelScope.launch { positions.clearAll() }

    fun measureCache() = viewModelScope.launch {
        _cacheBytes.value = withContext(Dispatchers.IO) { context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }
    }

    /** Thumbnails, copied subtitle files and downloaded updates. All of it is rebuilt when needed. */
    fun clearCache() = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            context.imageLoader.diskCache?.clear()
            listOf("subs", "updates").forEach { File(context.cacheDir, it).deleteRecursively() }
        }
        context.imageLoader.memoryCache?.clear()
        measureCache()
    }
}

/** Slot for the Updates row, filled by the update feature. */
typealias UpdatesRow = @Composable () -> Unit

/** Top-level categories. Each opens its own page; nothing opens as an overlapping dialog. */
enum class SettingsSection(val id: String, val title: String, val summary: String, val icon: ImageVector) {
    APPEARANCE("appearance", "Appearance", "Theme, font, library layout", MoodyIcons.Moon),
    VIDEO("video", "Video player", "Shortcuts, gestures, picture-in-picture, subtitles", MoodyIcons.VideoTab),
    MUSIC("music", "Music player", "Which audio counts as music, sleep timer", MoodyIcons.Music),
    LIBRARY("library", "Library", "Scanning and hidden folders", MoodyIcons.Folder),
    PLAYBACK("playback", "Playback", "Resume, watch history and stats", MoodyIcons.Replay),
    VAULT("vault", "Vault", "PIN, disguise and auto-lock", MoodyIcons.Lock),
    STORAGE("storage", "Storage & permissions", "Media access and cache", MoodyIcons.Shield),
    ADVANCED("advanced", "Advanced", "Decoder", MoodyIcons.Decoder),
    ABOUT("about", "About", "Version, updates, privacy, licenses", MoodyIcons.Info),
    ;

    companion object { fun of(id: String?) = entries.firstOrNull { it.id == id } }
}

/**
 * Shared frame for every Settings page: the header stays put, only the list below scrolls, and a hairline
 * appears under the header once content has moved beneath it. The list state lives in this destination,
 * so its scroll position survives opening a sub-page and coming back.
 */
@Composable
fun SettingsPage(title: String, onBack: () -> Unit, content: LazyListScope.() -> Unit) {
    val c = MoodyTheme.colors
    val list = rememberLazyListState()
    val lineAlpha by animateFloatAsState(if (list.canScrollBackward) 1f else 0f, label = "header-line")
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.statusBarsPadding().fillMaxWidth().height(48.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconAction(MoodyIcons.ArrowBack, "Back", onBack, tint = c.text, size = 22.dp)
            Spacer(Modifier.width(4.dp))
            Text(title, style = MoodyTheme.type.title, color = c.text, maxLines = 1)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).alpha(lineAlpha).background(c.line))
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = list,
            contentPadding = PaddingValues(bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            content = content,
        )
    }
}

/** Settings home: one row per category. */
@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit, onSection: (SettingsSection) -> Unit, onStats: () -> Unit = {}, onHealth: () -> Unit = {}) {
    SettingsPage("Settings", onBack) {
        item(key = "stats") { Action("Your stats", "Time spent watching and listening, top songs and videos", MoodyIcons.Chart, onStats) }
        item(key = "health") { Action("Media health", "Find damaged, incomplete or unsupported files", MoodyIcons.Health, onHealth) }
        SettingsSection.entries.forEach { sec ->
            item(key = sec.id) { Action(sec.title, sec.summary, sec.icon) { onSection(sec) } }
        }
        item { com.moody.player.ui.components.CreditFooter() }
    }
}

/** One category page. Changes apply as soon as they are made and are saved for the next launch. */
@Composable
fun SettingsSectionScreen(
    section: SettingsSection,
    vm: SettingsViewModel,
    onBack: () -> Unit,
    onOpenVault: () -> Unit,
    onShortcuts: () -> Unit,
    onGestures: () -> Unit,
    onPermissions: () -> Unit,
    updates: UpdatesRow,
) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val info by vm.vaultInfo.collectAsStateWithLifecycle()
    val unlocked by vm.lock.unlocked.collectAsStateWithLifecycle()
    val access by vm.access.collectAsStateWithLifecycle()
    val cache by vm.cacheBytes.collectAsStateWithLifecycle()
    val c = MoodyTheme.colors
    val toaster = LocalToaster.current
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<String?>(null) }
    if (section == SettingsSection.STORAGE) LaunchedEffect(Unit) { vm.refreshAccess(); vm.measureCache() }

    SettingsPage(section.title, onBack) {
        when (section) {
            SettingsSection.APPEARANCE -> {
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
            }
            SettingsSection.VIDEO -> {
                item { Label("Controls") }
                item { Action("Customize player shortcuts", "${s.playerShortcuts.size} quick ${if (s.playerShortcuts.size == 1) "button" else "buttons"} under the title bar", MoodyIcons.Layers, onShortcuts) }
                item {
                    val offCount = s.disabledGestures.size
                    Action("Gestures", if (!s.gestures) "Off" else if (offCount == 0) "All on · double-tap jumps ${s.doubleTapSeekSec} s" else "${PlayerGesture.entries.size - offCount} of ${PlayerGesture.entries.size} on", MoodyIcons.Hand, onGestures)
                }
                item { Label("Bookmarks") }
                item {
                    Toggle(
                        "Show bookmarks",
                        "Dots on the seek bar mark saved moments and a soft line spans saved ranges. Double-tap seeking offers a jump to one.",
                        s.showBookmarks, MoodyIcons.Bookmark,
                    ) { v -> vm.update { it.copy(showBookmarks = v) } }
                }
                if (android.os.Build.VERSION.SDK_INT >= 31) item {
                    var allowed by remember { mutableStateOf(android.provider.MediaStore.canManageMedia(context)) }
                    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { allowed = android.provider.MediaStore.canManageMedia(context); onPauseOrDispose {} }
                    Action(
                        "Save bookmarks without asking",
                        if (allowed) "Allowed. Bookmarks go into MP4 videos straight away"
                        else "Bookmarks are saved inside MP4 videos so they travel when shared. Android asks once per video; allow Media management to skip that.",
                        MoodyIcons.Bookmark,
                    ) {
                        runCatching {
                            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_REQUEST_MANAGE_MEDIA, android.net.Uri.parse("package:" + context.packageName)))
                        }
                    }
                }
                item { Toggle("Seek bar while locked", "A thin progress line stays visible when the controls are locked. In the player, hold the lock button to change this.", s.seekBarWhenLocked, MoodyIcons.Lock) { v -> vm.update { it.copy(seekBarWhenLocked = v) } } }
                item { Label("Leaving the player") }
                item { Toggle("Picture-in-picture on Home", "Going Home while a video plays shrinks it to a small window.", s.autoPip, MoodyIcons.Pip) { v -> vm.update { it.copy(autoPip = v) } } }
                item { Toggle("Keep playing in the background", "Sound continues when picture-in-picture is off or unavailable.", s.backgroundPlay, MoodyIcons.Audio) { v -> vm.update { it.copy(backgroundPlay = v) } } }
                item { Label("Subtitles") }
                item {
                    Choice("Subtitle size", MoodyIcons.Subtitles) {
                        SubSize.entries.forEach { z -> ChoiceChip(z.label, s.subSize == z, { vm.update { it.copy(subSize = z) } }) }
                    }
                }
                item {
                    Choice("Subtitle background", null, glyph = "Bg") {
                        SubBg.entries.forEach { b -> ChoiceChip(b.label, s.subBg == b, { vm.update { it.copy(subBg = b) } }) }
                    }
                }
                item { Toggle("Subtitle Studio", "Moody draws subtitle files itself: a searchable transcript, tap a line to jump, more styles. Font, outline and position are set in the player.", s.subStudio, MoodyIcons.Lyrics) { v -> vm.update { it.copy(subStudio = v) } } }
            }
            SettingsSection.MUSIC -> {
                item {
                    Choice("Leave out clips shorter than", MoodyIcons.Music, "Hides ringtones and voice notes from Music.") {
                        listOf(0 to "Keep all", 30 to "30 s", 60 to "1 min").forEach { (n, l) -> ChoiceChip(l, s.minSongSec == n, { vm.update { it.copy(minSongSec = n) } }) }
                    }
                }
                item { Toggle("Fade out with the sleep timer", "The music gets quieter over the last minute instead of stopping all at once.", s.sleepFade, MoodyIcons.Timer) { v -> vm.update { it.copy(sleepFade = v) } } }
                item { Note("The mini-player has a close button: it stops the music and hides the bar. Your queue stays.") }
            }
            SettingsSection.LIBRARY -> {
                item { Action("Scan again", "Look for new videos and music now", MoodyIcons.Refresh) { vm.rescan(); toaster.show("Scanning") } }
                item { Label("Hidden folders") }
                if (s.hiddenFolders.isEmpty()) {
                    item { Note("None. Hide a folder from its menu in Videos.") }
                } else {
                    s.hiddenFolders.sorted().forEach { path ->
                        item(key = path) {
                            Row(Modifier.fillMaxWidth().height(48.dp).padding(start = 60.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(folderName(path), style = MoodyTheme.type.body, color = c.text, modifier = Modifier.weight(1f), maxLines = 1)
                                QuietButton("Show again", { vm.update { it.copy(hiddenFolders = it.hiddenFolders - path) } }, color = c.accent)
                            }
                        }
                    }
                }
            }
            SettingsSection.PLAYBACK -> {
                item { Toggle("Resume where you left off", "Videos pick up from the last position.", s.resume, MoodyIcons.Replay) { v -> vm.update { it.copy(resume = v) } } }
                item {
                    Choice("Smart resume", MoodyIcons.SeekBack, "Steps back a little when a video resumes, so you catch the line you stopped on.") {
                        listOf(0 to "Off", 3 to "3 s", 5 to "5 s", 10 to "10 s").forEach { (n, l) -> ChoiceChip(l, s.smartResumeSec == n, { vm.update { it.copy(smartResumeSec = n) } }) }
                    }
                }
                item { Toggle("Remember speed and tracks per video", null, s.perFileSettings, MoodyIcons.Speed) { v -> vm.update { it.copy(perFileSettings = v) } } }
                item { Action("Clear watch history", "Forget saved positions and empty Continue watching", MoodyIcons.Timer) { dialog = "history" } }
                item { Label("Stats") }
                item { Toggle("Keep play stats", "Counts watching and listening time on this phone only. Vault videos are never counted.", s.keepStats, MoodyIcons.Chart) { v -> vm.update { it.copy(keepStats = v) } } }
            }
            SettingsSection.VAULT -> {
                item { Toggle("Disguise the vault", "Hides the Vault button. Open it by pressing and holding the Moody logo on Videos.", s.disguiseVault, MoodyIcons.EyeOff) { v -> vm.update { it.copy(disguiseVault = v) } } }
                item {
                    Choice("Lock again after", MoodyIcons.Lock, "It also locks whenever you leave Moody.") {
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
            }
            SettingsSection.STORAGE -> {
                item {
                    Action(
                        "Media access",
                        when (access) { AccessLevel.FULL -> "All videos and music"; AccessLevel.PARTIAL -> "Only the items you picked"; AccessLevel.NONE -> "Not allowed yet" },
                        MoodyIcons.Folder, onPermissions,
                    )
                }
                item { Action("App permissions", "Open Android settings for Moody", MoodyIcons.Shield) { context.openAppDetails() } }
                item { Action("Clear cache", cache?.let { "${formatBytes(it)} of thumbnails and temporary files" } ?: "Measuring…", MoodyIcons.Refresh) { vm.clearCache(); toaster.show("Cache cleared") } }
            }
            SettingsSection.ADVANCED -> {
                item {
                    Choice("Decoder", MoodyIcons.Decoder, "Hardware is smooth and light on battery. Software plays files the phone's chip can't.") {
                        Decoder.entries.forEach { d -> ChoiceChip(d.label, s.decoder == d, { vm.update { it.copy(decoder = d) } }) }
                    }
                }
                item { Note("If a video stalls, Moody switches it to software decoding on its own.") }
            }
            SettingsSection.ABOUT -> {
                item { Action("Moody ${BuildConfig.VERSION_NAME}", com.moody.player.ui.components.TAGLINE, MoodyIcons.Info, null) }
                item { updates() }
                item { Label("Privacy") }
                item { Note("Moody plays files on this phone. The only network request it makes on its own is the update check to GitHub.") }
                item { Label("Developer") }
                item {
                    Action("Developed by ${com.moody.player.ui.components.DEVELOPER_NAME}", "Telegram · @TheMaverickR", MoodyIcons.Captain) {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(com.moody.player.ui.components.DEVELOPER_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                }
                item { Label("Licenses") }
                item { Note("Video by libVLC (LGPL 2.1). Music by AndroidX Media3 (Apache 2.0). Type: Geist and Geist Mono (SIL OFL 1.1). Icons drawn after Lucide (ISC).") }
            }
        }
    }

    when (dialog) {
        "pin" -> ChangePinDialog(vm.lock, onDone = { dialog = null; if (it) toaster.show("PIN changed") })
        "recovery" -> ChangeRecoveryDialog(vm.lock, info?.question.orEmpty(), onDone = { dialog = null; if (it) toaster.show("Recovery question saved") })
        "history" -> ConfirmDialog("Clear watch history?", "Saved positions are forgotten and Continue watching is emptied. Your files are not touched.", "Clear") { ok ->
            dialog = null
            if (ok) { vm.clearHistory(); toaster.show("Watch history cleared") }
        }
    }
}

/** Settings > Video player > Gestures: each gesture on its own, plus how far a double-tap jumps. */
@Composable
fun GesturesScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    SettingsPage("Gestures", onBack) {
        item { Toggle("Gestures on the video", "Turn all of them off at once.", s.gestures, MoodyIcons.Hand) { v -> vm.update { it.copy(gestures = v) } } }
        item {
            Choice("Double-tap jumps", MoodyIcons.Replay) {
                SEEK_STEPS.forEach { n -> ChoiceChip("$n s", s.doubleTapSeekSec == n, { vm.update { it.copy(doubleTapSeekSec = n) } }) }
            }
        }
        item { Label("Gestures") }
        PlayerGesture.entries.forEach { g ->
            item(key = g.name) {
                Box(Modifier.alpha(if (s.gestures) 1f else 0.45f)) {
                    Toggle(g.label, g.detail, s.gestures && g !in s.disabledGestures, null) { v ->
                        if (s.gestures) vm.update { it.copy(disabledGestures = if (v) it.disabledGestures - g else it.disabledGestures + g) }
                    }
                }
            }
        }
        item { Note("A single tap shows or hides the controls. Gestures pause while the screen is locked, and swipes that start at the screen edges are left to Android.") }
    }
}

private fun Context.openAppDetails() {
    runCatching {
        startActivity(Intent(SysSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MoodyTheme.type.label, color = MoodyTheme.colors.accent, modifier = Modifier.padding(start = 60.dp, end = 16.dp, top = 16.dp, bottom = 4.dp))
}

@Composable
private fun Note(text: String) {
    Text(text, style = MoodyTheme.type.caption, color = MoodyTheme.colors.text3, modifier = Modifier.padding(start = 60.dp, end = 16.dp, top = 8.dp, bottom = 8.dp))
}

/** A compact row: plain icon, content, hairline divider aligned with the text. */
@Composable
private fun RowShell(icon: ImageVector?, onClick: (() -> Unit)?, glyph: String? = null, top: Boolean = false, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val c = MoodyTheme.colors
    Column(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 8.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = if (top) Alignment.Top else Alignment.CenterVertically,
        ) {
            Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
                when {
                    glyph != null -> Text(glyph, style = MoodyTheme.type.label, color = c.text2)
                    icon != null -> Icon(icon, null, Modifier.size(20.dp), tint = c.text2)
                }
            }
            Spacer(Modifier.width(12.dp))
            content()
        }
        Box(Modifier.padding(start = 60.dp).fillMaxWidth().height(1.dp).background(c.line))
    }
}

@Composable
private fun Toggle(title: String, body: String?, checked: Boolean, icon: ImageVector? = null, onChange: (Boolean) -> Unit) {
    val c = MoodyTheme.colors
    RowShell(icon, { onChange(!checked) }) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MoodyTheme.type.body, color = c.text)
            if (body != null) Text(body, style = MoodyTheme.type.caption, color = c.text2)
        }
        Switch(
            checked, onChange, modifier = Modifier.scale(0.74f),
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
            Text(title, style = MoodyTheme.type.body, color = c.text)
            if (body != null) Text(body, style = MoodyTheme.type.caption, color = c.text2)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
        }
    }
}

@Composable
private fun Action(title: String, body: String?, icon: ImageVector, onClick: (() -> Unit)?) {
    val c = MoodyTheme.colors
    RowShell(icon, onClick) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MoodyTheme.type.body, color = c.text)
            if (!body.isNullOrBlank()) Text(body, style = MoodyTheme.type.caption, color = c.text3)
        }
        if (onClick != null) Icon(MoodyIcons.ChevronRight, null, Modifier.size(16.dp), tint = c.text3)
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

@Composable
private fun ConfirmDialog(title: String, body: String, confirm: String, onDone: (Boolean) -> Unit) {
    Dialog(onDismissRequest = { onDone(false) }) {
        DialogFrame(title) {
            Text(body, style = MoodyTheme.type.body, color = MoodyTheme.colors.text2)
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                QuietButton("Cancel", { onDone(false) })
                Spacer(Modifier.width(8.dp))
                PrimaryButton(confirm, { onDone(true) })
            }
        }
    }
}
