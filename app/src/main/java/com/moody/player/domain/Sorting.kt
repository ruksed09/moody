package com.moody.player.domain

/**
 * NAME is the file name; TITLE is a song's title tag (Music only); DATE is when the file was last modified;
 * COUNT is how many items a folder holds (folder lists only).
 */
enum class SortKey { NAME, DATE, SIZE, DURATION, TITLE, COUNT }

data class SortSpec(val key: SortKey, val ascending: Boolean)

/** Natural, case-insensitive name order: "ep2" before "ep10". */
val NaturalOrder: Comparator<String> = Comparator { a, b ->
    val ta = tokens(a.lowercase())
    val tb = tokens(b.lowercase())
    for (i in 0 until minOf(ta.size, tb.size)) {
        val x = ta[i]
        val y = tb[i]
        val c = if (x[0].isDigit() && y[0].isDigit()) {
            val nx = x.trimStart('0').ifEmpty { "0" }
            val ny = y.trimStart('0').ifEmpty { "0" }
            if (nx.length != ny.length) nx.length - ny.length else nx.compareTo(ny)
        } else x.compareTo(y)
        if (c != 0) return@Comparator c
    }
    ta.size - tb.size
}

private fun tokens(s: String): List<String> {
    val out = ArrayList<String>()
    var i = 0
    while (i < s.length) {
        val digit = s[i].isDigit()
        val start = i
        while (i < s.length && s[i].isDigit() == digit) i++
        out += s.substring(start, i)
    }
    return out
}

fun <T> List<T>.sortedBy(
    spec: SortSpec,
    name: (T) -> String,
    date: (T) -> Long,
    size: (T) -> Long,
    duration: (T) -> Long,
    title: (T) -> String = name,
    count: (T) -> Long = { 0L },
): List<T> {
    val cmp: Comparator<T> = when (spec.key) {
        SortKey.NAME -> Comparator { a, b -> NaturalOrder.compare(name(a), name(b)) }
        SortKey.TITLE -> Comparator { a, b -> NaturalOrder.compare(title(a), title(b)) }
        SortKey.DATE -> compareBy(date)
        SortKey.SIZE -> compareBy(size)
        SortKey.DURATION -> compareBy(duration)
        SortKey.COUNT -> compareBy(count)
    }
    return sortedWith(if (spec.ascending) cmp else cmp.reversed())
}
