package com.moody.player.ui.nav

import android.net.Uri

object Routes {
    const val ONBOARDING = "onboarding"
    const val MAIN = "main"
    const val PERMISSIONS = "permissions"
    const val FOLDER = "folder/{path}"
    const val VAULT = "vault"
    const val SETTINGS = "settings"
    const val SHORTCUTS = "settings/shortcuts"
    const val GESTURES = "settings/gestures"
    const val SETTINGS_SECTION = "settings/section/{id}"
    const val PLAYLIST = "playlist/{key}"
    const val NOW_PLAYING = "now-playing"
    const val ALBUM = "album/{key}"
    const val ARTIST = "artist/{key}"
    const val MUSIC_FOLDER = "music-folder/{key}"

    /** Empty paths (files at the storage root) travel as "~" so the route still matches. */
    fun folder(path: String) = "folder/${Uri.encode(path.ifEmpty { "~" })}"
    fun decodePath(arg: String?): String = Uri.decode(arg.orEmpty()).let { if (it == "~") "" else it }
    fun settingsSection(id: String) = "settings/section/$id"
    fun playlist(id: Long) = "playlist/$id"
    fun album(id: Long) = "album/$id"
    fun artist(name: String) = "artist/${Uri.encode(name)}"
    fun musicFolder(path: String) = "music-folder/${Uri.encode(path.ifEmpty { "~" })}"
}
