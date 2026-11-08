package com.moody.player.ui.nav

import android.net.Uri

object Routes {
    const val ONBOARDING = "onboarding"
    const val MAIN = "main"
    const val FOLDER = "folder/{path}"
    const val VAULT = "vault"
    const val SETTINGS = "settings"
    const val PLAYLIST = "playlist/{id}"
    const val NOW_PLAYING = "now-playing"
    const val ALBUM = "album/{id}"
    const val ARTIST = "artist/{name}"

    fun folder(path: String) = "folder/${Uri.encode(path.ifEmpty { "" })}"
    fun playlist(id: Long) = "playlist/$id"
    fun album(id: Long) = "album/$id"
    fun artist(name: String) = "artist/${Uri.encode(name)}"
}
