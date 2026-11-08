package com.moody.player.domain

/** "m:ss" under an hour, "h:mm:ss" from an hour; "--:--" when the length is unknown. */
fun formatDuration(ms: Long?): String {
    if (ms == null || ms <= 0) return "--:--"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Signed delta for seek previews: "+1:30" / "-0:10". */
fun formatDelta(ms: Long): String {
    val abs = kotlin.math.abs(ms) / 1000
    return (if (ms < 0) "-" else "+") + "%d:%02d".format(abs / 60, abs % 60)
}

/** "1.2 GB", "340 MB", "12 KB". */
fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return if (i <= 1) "%.0f %s".format(v, units[i]) else "%.1f %s".format(v, units[i])
}
