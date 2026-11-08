package com.moody.player.domain

private val VLC_EXTENSIONS = setOf("wma", "ape", "wv", "mpc", "ac3", "dts", "amr", "ra", "rm", "tta", "tak", "dsf", "dff", "aiff", "aif", "mka")
private val VLC_MIME_HINTS = listOf("x-ms-wma", "ape", "wavpack", "musepack", "ac3", "dts", "amr", "realaudio", "x-tta", "aiff", "matroska")

/** Audio Media3 can't decode on its own, so the Music tab hands it to libVLC instead. */
fun needsVlc(mime: String?, fileName: String): Boolean {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    if (ext in VLC_EXTENSIONS) return true
    val m = mime?.lowercase() ?: return false
    return VLC_MIME_HINTS.any { it in m }
}
