package com.moody.player.domain

/** Kinds of play time Moody counts. */
object StatKind {
    const val VIDEO = "video"
    const val MUSIC = "music"
}

/** One hour bucket of play time for one file. [day] is days since 1970-01-01 in local time. */
data class StatRow(val day: Long, val hour: Int, val uri: String, val kind: String, val title: String, val ms: Long, val plays: Int)

data class DayTotal(val day: Long, val videoMs: Long, val musicMs: Long)

data class TopItem(val uri: String, val title: String, val ms: Long, val plays: Int)

data class StatsSummary(
    val videoMs: Long,
    val musicMs: Long,
    val days: List<DayTotal>,
    val topSongs: List<TopItem>,
    val topVideos: List<TopItem>,
    /** Hour of day (0..23) with the most play time, or null with no plays. */
    val busiestHour: Int?,
) {
    val isEmpty: Boolean get() = videoMs == 0L && musicMs == 0L
}

/**
 * Sums [rows] for the days [fromDay]..[toDay] (inclusive). Only uris in [visible] count toward the top
 * lists, so files that were deleted, hidden or moved to the vault never show up by name.
 */
fun summarize(rows: List<StatRow>, fromDay: Long, toDay: Long, visible: Set<String>, top: Int = 5): StatsSummary {
    val inRange = rows.filter { it.day in fromDay..toDay }
    val days = (fromDay..toDay).map { d ->
        val dayRows = inRange.filter { it.day == d }
        DayTotal(d, dayRows.filter { it.kind == StatKind.VIDEO }.sumOf { it.ms }, dayRows.filter { it.kind == StatKind.MUSIC }.sumOf { it.ms })
    }
    fun topOf(kind: String) = inRange.filter { it.kind == kind && it.uri in visible }
        .groupBy { it.uri }
        .map { (uri, r) -> TopItem(uri, r.maxByOrNull { it.day }?.title.orEmpty(), r.sumOf { it.ms }, r.sumOf { it.plays }) }
        .sortedWith(compareByDescending<TopItem> { it.ms }.thenByDescending { it.plays })
        .take(top)
    val byHour = inRange.groupBy { it.hour }.mapValues { (_, r) -> r.sumOf { it.ms } }
    return StatsSummary(
        videoMs = inRange.filter { it.kind == StatKind.VIDEO }.sumOf { it.ms },
        musicMs = inRange.filter { it.kind == StatKind.MUSIC }.sumOf { it.ms },
        days = days,
        topSongs = topOf(StatKind.MUSIC),
        topVideos = topOf(StatKind.VIDEO),
        busiestHour = byHour.filterValues { it > 0 }.maxByOrNull { it.value }?.key,
    )
}

/** "3 h 20 min", "45 min", "under a minute". */
fun formatSpent(ms: Long): String {
    val min = ms / 60_000
    return when {
        min <= 0 -> if (ms > 0) "under a minute" else "0 min"
        min < 60 -> "$min min"
        min % 60 == 0L -> "${min / 60} h"
        else -> "${min / 60} h ${min % 60} min"
    }
}

/** "9 PM", "12 AM". */
fun hourLabel(h: Int): String = when {
    h == 0 -> "12 AM"
    h < 12 -> "$h AM"
    h == 12 -> "12 PM"
    else -> "${h - 12} PM"
}
