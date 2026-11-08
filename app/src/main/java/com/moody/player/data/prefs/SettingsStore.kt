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
    val backgrounds: Boolean = true,
    val videoView: VideoView = VideoView.FOLDERS,
    val videoLayout: LayoutMode = LayoutMode.GRID,
    val sort: SortSpec = SortSpec(SortKey.DATE, ascending = false),
    // Player
    val decoder: Decoder = Decoder.HW,
    val doubleTapSeekSec: Int = 10,
    val gestures: Boolean = true,
    val resume: Boolean = true,
    val perFileSettings: Boolean = false,
    val backgroundPlay: Boolean = true,
    val autoPip: Boolean = true,
    val subSize: SubSize = SubSize.M,
    val subBg: SubBg = SubBg.SHADOW,
    val subColor: Long = 0xFFFFFAF2,
    // Music
    val gapless: Boolean = true,
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
    val resume = booleanPreferencesKey("resume")
    val perFile = booleanPreferencesKey("per_file")
    val bgPlay = booleanPreferencesKey("bg_play")
    val autoPip = booleanPreferencesKey("auto_pip")
    val subSize = stringPreferencesKey("sub_size")
    val subBg = stringPreferencesKey("sub_bg")
    val subColor = longPreferencesKey("sub_color")
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
        resume = this[K.resume] ?: d.resume,
        perFileSettings = this[K.perFile] ?: d.perFileSettings,
        backgroundPlay = this[K.bgPlay] ?: d.backgroundPlay,
        autoPip = this[K.autoPip] ?: d.autoPip,
        subSize = enum(K.subSize, d.subSize),
        subBg = enum(K.subBg, d.subBg),
        subColor = this[K.subColor] ?: d.subColor,
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
    this[K.resume] = s.resume
    this[K.perFile] = s.perFileSettings
    this[K.bgPlay] = s.backgroundPlay
    this[K.autoPip] = s.autoPip
    this[K.subSize] = s.subSize.name
    this[K.subBg] = s.subBg.name
    this[K.subColor] = s.subColor
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
