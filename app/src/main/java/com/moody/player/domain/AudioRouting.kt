package com.moody.player.domain

private val VLC_EXTENSIONS = setOf("wma", "ape", "wv", "mpc", "ac3", "dts", "amr", "ra", "rm", "tta", "tak", "dsf", "dff", "aiff", "aif", "mka")
// Whole tokens only: a plain substring test let "dts" match "audio/aac-adts", sending ordinary AAC to libVLC.
private val VLC_MIME_HINTS = Regex("(?<![a-z0-9])(x-ms-wma|ape|wavpack|musepack|eac3|ac3|dts|amr|realaudio|x-tta|aiff|matroska)(?![a-z0-9])")

/** Audio Media3 can't decode on its own, so the Music tab hands it to libVLC instead. */
fun needsVlc(mime: String?, fileName: String): Boolean {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    if (ext in VLC_EXTENSIONS) return true
    val m = mime?.lowercase() ?: return false
    return VLC_MIME_HINTS.containsMatchIn(m)
}
