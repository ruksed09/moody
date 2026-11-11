package com.moody.player.domain

/** Bookmark names: letters, digits, spaces, em dash, minus, slash, colon and semicolon; at most 40 characters. */
object BookmarkNames {
    const val MAX = 40
    private const val EXTRA = " \u2014-/:;"

    fun allowed(ch: Char): Boolean = ch.isLetterOrDigit() || ch in EXTRA

    /** What the name field keeps while typing: anything else is dropped, runs of spaces become one. */
    fun filter(raw: String): String {
        val sb = StringBuilder()
        for (ch in raw) {
            if (!allowed(ch)) continue
            if (ch == ' ' && (sb.isEmpty() || sb.last() == ' ')) continue
            sb.append(ch)
            if (sb.length == MAX) break
        }
        return sb.toString()
    }

    /** The name as saved: filtered and trimmed; blank means "no name". */
    fun clean(raw: String): String = filter(raw).trim()
}

/** A bookmark is a moment, or a range when it has an end (an A-B repeat that was saved). */
fun bookmarkLabel(startMs: Long, endMs: Long?): String =
    if (endMs != null) "${formatDuration(startMs)} \u2013 ${formatDuration(endMs)}" else formatDuration(startMs)
