package com.moody.player.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.moody.player.design.FontMode
import com.moody.player.design.ThemeMode
import com.moody.player.domain.Decoder
import com.moody.player.domain.LayoutMode
import com.moody.player.domain.SortKey
import com.moody.player.domain.SortSpec
import com.moody.player.domain.SubBg
import com.moody.player.domain.SubSize
import com.moody.player.domain.VideoView
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class Settings(
    // Appearance
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val fontMode: FontMode = FontMode.APP,
    val backgrounds: Boolean = false,
    val videoView: VideoView = VideoView.FOLDERS,
    val videoLayout: LayoutMode = LayoutMode.GRID,
    val sort: SortSpec = SortSpec(SortKey.DATE, ascending = false),
    // Player
    val decoder: Decoder = Decoder.HW,
    val doubleTapSeekSec: Int = 10,
    val gestures: Boolean = true,
    val disabledGestures: Set<com.moody.player.domain.PlayerGesture> = emptySet(),
    val resume: Boolean = true,
    /** Seconds to step back when resuming a video (0 = off). */
    val smartResumeSec: Int = 5,
    val perFileSettings: Boolean = false,
    val backgroundPlay: Boolean = true,
    val autoPip: Boolean = true,
    /** A thin, untouchable progress line stays on screen while the video controls are locked. */
    val seekBarWhenLocked: Boolean = false,
    /** Bookmarks show on the video seek bar, and double-tap seeking offers a jump to one. */
    val showBookmarks: Boolean = true,
    val playerShortcuts: List<com.moody.player.domain.PlayerShortcut> = com.moody.player.domain.DEFAULT_SHORTCUTS,
    /** With more than three shortcuts, whether the player shows them all (kept across videos and restarts). */
    val shortcutsExpanded: Boolean = false,
    val subSize: SubSize = SubSize.M,
    val subBg: SubBg = SubBg.SHADOW,
    val subColor: Long = 0xFFFFFAF2,
    /** Look of subtitle files Moody draws itself (Subtitle Studio). */
    val subFont: com.moody.player.domain.SubFont = com.moody.player.domain.SubFont.DEFAULT,
    val subOutline: com.moody.player.domain.SubOutline = com.moody.player.domain.SubOutline.THIN,
    val subAlign: com.moody.player.domain.SubAlign = com.moody.player.domain.SubAlign.CENTER,
    /** Subtitles sit at the top of the picture instead of the bottom. */
    val subTop: Boolean = false,
    /** Distance from the picture's edge, in percent of its height. */
    val subLift: Int = 6,
    /** Draw subtitle files with Subtitle Studio (tap-to-seek transcript, full styling) instead of libVLC. */
    val subStudio: Boolean = true,
    // Visualizer
    val vizStyle: com.moody.player.domain.VizStyle = com.moody.player.domain.VizStyle.BARS,
    val vizSensitivity: Float = 1f,
    val vizBass: Float = 1f,
    val vizMids: Float = 1f,
    val vizTreble: Float = 1f,
    val vizIntensity: Float = 1f,
    // Media Health: files taken out of Moody's library (the files themselves are untouched).
    val removedMedia: Set<String> = emptySet(),
    // Music
    val gapless: Boolean = true,
    /** The music sleep timer fades the sound out over its last minute. */
    val sleepFade: Boolean = true,
    /** Count play time for the Stats screen and mood mixes. */
    val keepStats: Boolean = true,
    val minSongSec: Int = 30,
    // Library
    val hiddenFolders: Set<String> = emptySet(),
    val showNomedia: Boolean = false,
    // Vault
    val disguiseVault: Boolean = false,
    val vaultAutoLockSec: Int = 120,
    // App state
    val onboarded: Boolean = false,
    val lastUpdateCheck: Long = 0,
    val snoozedVersion: String = "",
    val snoozedUntil: Long = 0,
)

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore("settings")

private object K {
    val theme = stringPreferencesKey("theme")
    val font = stringPreferencesKey("font")
    val backgrounds = booleanPreferencesKey("backgrounds")
    val videoView = stringPreferencesKey("video_view")
    val videoLayout = stringPreferencesKey("video_layout")
    val sortKey = stringPreferencesKey("sort_key")
    val sortAsc = booleanPreferencesKey("sort_asc")
    val decoder = stringPreferencesKey("decoder")
    val doubleTap = intPreferencesKey("double_tap_sec")
    val gestures = booleanPreferencesKey("gestures")
    val gesturesOff = stringSetPreferencesKey("gestures_off")
    val resume = booleanPreferencesKey("resume")
    val smartResume = intPreferencesKey("smart_resume_sec")
    val sleepFade = booleanPreferencesKey("sleep_fade")
    val keepStats = booleanPreferencesKey("keep_stats")
    val perFile = booleanPreferencesKey("per_file")
    val bgPlay = booleanPreferencesKey("bg_play")
    val autoPip = booleanPreferencesKey("auto_pip")
    val seekLocked = booleanPreferencesKey("seek_locked")
    val showMarks = booleanPreferencesKey("show_marks")
    val shortcuts = stringPreferencesKey("player_shortcuts")
    val shortcutsOpen = booleanPreferencesKey("player_shortcuts_open")
    val subSize = stringPreferencesKey("sub_size")
    val subBg = stringPreferencesKey("sub_bg")
    val subColor = longPreferencesKey("sub_color")
    val subFont = stringPreferencesKey("sub_font")
    val subOutline = stringPreferencesKey("sub_outline")
    val subAlign = stringPreferencesKey("sub_align")
    val subTop = booleanPreferencesKey("sub_top")
    val subLift = intPreferencesKey("sub_lift")
    val subStudio = booleanPreferencesKey("sub_studio")
    val vizStyle = stringPreferencesKey("viz_style")
    val vizSens = androidx.datastore.preferences.core.floatPreferencesKey("viz_sens")
    val vizBass = androidx.datastore.preferences.core.floatPreferencesKey("viz_bass")
    val vizMids = androidx.datastore.preferences.core.floatPreferencesKey("viz_mids")
    val vizTreble = androidx.datastore.preferences.core.floatPreferencesKey("viz_treble")
    val vizIntensity = androidx.datastore.preferences.core.floatPreferencesKey("viz_intensity")
    val removed = stringSetPreferencesKey("removed_media")
    val gapless = booleanPreferencesKey("gapless")
    val minSong = intPreferencesKey("min_song_sec")
    val hidden = stringSetPreferencesKey("hidden_folders")
    val nomedia = booleanPreferencesKey("show_nomedia")
    val disguise = booleanPreferencesKey("disguise_vault")
    val autoLock = intPreferencesKey("vault_auto_lock")
    val onboarded = booleanPreferencesKey("onboarded")
    val lastCheck = longPreferencesKey("last_update_check")
    val snoozedVer = stringPreferencesKey("snoozed_version")
    val snoozedUntil = longPreferencesKey("snoozed_until")
}

private inline fun <reified E : Enum<E>> Preferences.enum(key: Preferences.Key<String>, default: E): E =
    this[key]?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: default

internal fun Preferences.toSettings(): Settings {
    val d = Settings()
    return Settings(
        themeMode = enum(K.theme, d.themeMode),
        fontMode = enum(K.font, d.fontMode),
        backgrounds = this[K.backgrounds] ?: d.backgrounds,
        videoView = enum(K.videoView, d.videoView),
        videoLayout = enum(K.videoLayout, d.videoLayout),
        sort = SortSpec(enum(K.sortKey, d.sort.key), this[K.sortAsc] ?: d.sort.ascending),
        decoder = enum(K.decoder, d.decoder),
        doubleTapSeekSec = this[K.doubleTap] ?: d.doubleTapSeekSec,
        gestures = this[K.gestures] ?: d.gestures,
        disabledGestures = this[K.gesturesOff]?.let { com.moody.player.domain.decodeGestures(it) } ?: d.disabledGestures,
        resume = this[K.resume] ?: d.resume,
        smartResumeSec = this[K.smartResume] ?: d.smartResumeSec,
        sleepFade = this[K.sleepFade] ?: d.sleepFade,
        keepStats = this[K.keepStats] ?: d.keepStats,
        perFileSettings = this[K.perFile] ?: d.perFileSettings,
        backgroundPlay = this[K.bgPlay] ?: d.backgroundPlay,
        autoPip = this[K.autoPip] ?: d.autoPip,
        seekBarWhenLocked = this[K.seekLocked] ?: d.seekBarWhenLocked,
        showBookmarks = this[K.showMarks] ?: d.showBookmarks,
        playerShortcuts = this[K.shortcuts]?.let { com.moody.player.domain.decodeShortcuts(it) } ?: d.playerShortcuts,
        shortcutsExpanded = this[K.shortcutsOpen] ?: d.shortcutsExpanded,
        subSize = enum(K.subSize, d.subSize),
        subBg = enum(K.subBg, d.subBg),
        subColor = this[K.subColor] ?: d.subColor,
        subFont = enum(K.subFont, d.subFont),
        subOutline = enum(K.subOutline, d.subOutline),
        subAlign = enum(K.subAlign, d.subAlign),
        subTop = this[K.subTop] ?: d.subTop,
        subLift = this[K.subLift] ?: d.subLift,
        subStudio = this[K.subStudio] ?: d.subStudio,
        vizStyle = enum(K.vizStyle, d.vizStyle),
        vizSensitivity = this[K.vizSens] ?: d.vizSensitivity,
        vizBass = this[K.vizBass] ?: d.vizBass,
        vizMids = this[K.vizMids] ?: d.vizMids,
        vizTreble = this[K.vizTreble] ?: d.vizTreble,
        vizIntensity = this[K.vizIntensity] ?: d.vizIntensity,
        removedMedia = this[K.removed] ?: d.removedMedia,
        gapless = this[K.gapless] ?: d.gapless,
        minSongSec = this[K.minSong] ?: d.minSongSec,
        hiddenFolders = this[K.hidden] ?: d.hiddenFolders,
        showNomedia = this[K.nomedia] ?: d.showNomedia,
        disguiseVault = this[K.disguise] ?: d.disguiseVault,
        vaultAutoLockSec = this[K.autoLock] ?: d.vaultAutoLockSec,
        onboarded = this[K.onboarded] ?: d.onboarded,
        lastUpdateCheck = this[K.lastCheck] ?: d.lastUpdateCheck,
        snoozedVersion = this[K.snoozedVer] ?: d.snoozedVersion,
        snoozedUntil = this[K.snoozedUntil] ?: d.snoozedUntil,
    )
}

internal fun MutablePreferences.write(s: Settings) {
    this[K.theme] = s.themeMode.name
    this[K.font] = s.fontMode.name
    this[K.backgrounds] = s.backgrounds
    this[K.videoView] = s.videoView.name
    this[K.videoLayout] = s.videoLayout.name
    this[K.sortKey] = s.sort.key.name
    this[K.sortAsc] = s.sort.ascending
    this[K.decoder] = s.decoder.name
    this[K.doubleTap] = s.doubleTapSeekSec
    this[K.gestures] = s.gestures
    this[K.gesturesOff] = com.moody.player.domain.encodeGestures(s.disabledGestures)
    this[K.resume] = s.resume
    this[K.smartResume] = s.smartResumeSec
    this[K.sleepFade] = s.sleepFade
    this[K.keepStats] = s.keepStats
    this[K.perFile] = s.perFileSettings
    this[K.bgPlay] = s.backgroundPlay
    this[K.autoPip] = s.autoPip
    this[K.seekLocked] = s.seekBarWhenLocked
    this[K.showMarks] = s.showBookmarks
    this[K.shortcuts] = com.moody.player.domain.encodeShortcuts(s.playerShortcuts)
    this[K.shortcutsOpen] = s.shortcutsExpanded
    this[K.subSize] = s.subSize.name
    this[K.subBg] = s.subBg.name
    this[K.subColor] = s.subColor
    this[K.subFont] = s.subFont.name
    this[K.subOutline] = s.subOutline.name
    this[K.subAlign] = s.subAlign.name
    this[K.subTop] = s.subTop
    this[K.subLift] = s.subLift
    this[K.subStudio] = s.subStudio
    this[K.vizStyle] = s.vizStyle.name
    this[K.vizSens] = s.vizSensitivity
    this[K.vizBass] = s.vizBass
    this[K.vizMids] = s.vizMids
    this[K.vizTreble] = s.vizTreble
    this[K.vizIntensity] = s.vizIntensity
    this[K.removed] = s.removedMedia
    this[K.gapless] = s.gapless
    this[K.minSong] = s.minSongSec
    this[K.hidden] = s.hiddenFolders
    this[K.nomedia] = s.showNomedia
    this[K.disguise] = s.disguiseVault
    this[K.autoLock] = s.vaultAutoLockSec
    this[K.onboarded] = s.onboarded
    this[K.lastCheck] = s.lastUpdateCheck
    this[K.snoozedVer] = s.snoozedVersion
    this[K.snoozedUntil] = s.snoozedUntil
}

@Singleton
class SettingsStore @Inject constructor(@ApplicationContext private val context: Context) {
    val settings: Flow<Settings> = context.settingsStore.data.map { it.toSettings() }

    suspend fun update(transform: (Settings) -> Settings) {
        context.settingsStore.edit { prefs -> prefs.write(transform(prefs.toSettings())) }
    }
}
