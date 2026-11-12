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

/** A precise time for the A-B editor: "16:45.0" or "1:02:03.4" (tenths always shown). */
fun formatClock(ms: Long): String {
    val t = ms.coerceAtLeast(0) / 100
    val tenth = t % 10
    val total = t / 10
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d.%d".format(h, m, s, tenth) else "%d:%02d.%d".format(m, s, tenth)
}

/**
 * Reads "1:02:03.4", "16:45", "16:45.25" or "75.5" (seconds). Minutes and seconds after the first part must be
 * below 60. Null when it isn't a time.
 */
fun parseAbTime(text: String): Long? {
    val t = text.trim()
    if (t.isEmpty() || t.count { it == ':' } > 2) return null
    val parts = t.split(':')
    val last = parts.last()
    if (!Regex("""\d{1,2}(\.\d{1,3})?|\d+(\.\d{1,3})?""").matches(last)) return null
    val heads = parts.dropLast(1)
    if (heads.any { !Regex("""\d{1,3}""").matches(it) }) return null
    val nums = heads.map { it.toLong() }
    if (nums.size == 2 && nums[1] >= 60) return null
    val secPart = last.substringBefore('.')
    val frac = last.substringAfter('.', "")
    if (heads.isNotEmpty() && secPart.toLong() >= 60) return null
    var ms = secPart.toLong() * 1000 + (frac.padEnd(3, '0').take(3).ifEmpty { "0" }).toLong()
    var mult = 60_000L
    for (n in nums.reversed()) { ms += n * mult; mult *= 60 }
    return ms
}

/** Shortest A-B loop, and the closest A may sit to the end. */
const val AB_MIN_GAP_MS = 500L

/**
 * Why a new A (or B) can't be used, or null when it can. [a]/[b] are the loop's current points.
 */
fun abError(editingA: Boolean, ms: Long, a: Long?, b: Long?, durationMs: Long): String? {
    if (durationMs <= 0) return "The video's length isn't known yet"
    return if (editingA) when {
        ms < 0 -> "A can't be before 0:00"
        b != null && ms > b - AB_MIN_GAP_MS -> "A must be at least 0.5 s before B"
        b == null && ms > durationMs - AB_MIN_GAP_MS -> "A must be at least 0.5 s before the end"
        else -> null
    } else when {
        ms > durationMs -> "B can't be past the end (${formatDuration(durationMs)})"
        a != null && ms < a + AB_MIN_GAP_MS -> "B must be at least 0.5 s after A"
        ms < 0 -> "B can't be before 0:00"
        else -> null
    }
}
