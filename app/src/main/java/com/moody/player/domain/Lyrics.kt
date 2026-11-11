package com.moody.player.domain

/** One lyric line. [timeMs] is null for plain (unsynced) lyrics. */
data class LyricLine(val timeMs: Long?, val text: String)

data class Lyrics(val lines: List<LyricLine>) {
    val synced: Boolean get() = lines.isNotEmpty() && lines.all { it.timeMs != null }
    val isEmpty: Boolean get() = lines.isEmpty()

    /** Index of the line playing at [positionMs]; -1 before the first line or for unsynced lyrics. */
    fun indexAt(positionMs: Long): Int {
        if (!synced) return -1
        var lo = 0
        var hi = lines.lastIndex
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (lines[mid].timeMs!! <= positionMs) { found = mid; lo = mid + 1 } else hi = mid - 1
        }
        return found
    }
}

private val TIME_TAG = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
private val META_TAG = Regex("""^\[(ar|ti|al|au|by|re|ve|length|#):.*]$""", RegexOption.IGNORE_CASE)
private val OFFSET_TAG = Regex("""^\[offset:\s*([+-]?\d+)\s*]$""", RegexOption.IGNORE_CASE)

/**
 * Reads an .lrc file. Lines may carry several time tags (`[00:12.30][01:40.00]Chorus`); `[offset:+500]`
 * shifts every line (positive = earlier, as in the format). Text without any time tags comes back as
 * plain lyrics, one line per text line.
 */
fun parseLrc(text: String): Lyrics {
    var offset = 0L
    val timed = mutableListOf<LyricLine>()
    val plain = mutableListOf<LyricLine>()
    text.lineSequence().map { it.trim().removePrefix("﻿") }.forEach { raw ->
        if (raw.isEmpty()) return@forEach
        OFFSET_TAG.matchEntire(raw)?.let { offset = it.groupValues[1].toLongOrNull() ?: 0L; return@forEach }
        if (META_TAG.matches(raw)) return@forEach
        val tags = TIME_TAG.findAll(raw).toList()
        if (tags.isEmpty()) { plain += LyricLine(null, raw); return@forEach }
        val words = raw.substring(tags.last().range.last + 1).trim()
        tags.forEach { m ->
            val min = m.groupValues[1].toLong()
            val sec = m.groupValues[2].toLong()
            val frac = m.groupValues[3]
            val ms = when (frac.length) { 0 -> 0L; 1 -> frac.toLong() * 100; 2 -> frac.toLong() * 10; else -> frac.take(3).toLong() }
            timed += LyricLine(min * 60_000 + sec * 1_000 + ms, words)
        }
    }
    if (timed.isNotEmpty()) {
        return Lyrics(timed.map { it.copy(timeMs = (it.timeMs!! - offset).coerceAtLeast(0)) }.sortedBy { it.timeMs })
    }
    return Lyrics(plain)
}
