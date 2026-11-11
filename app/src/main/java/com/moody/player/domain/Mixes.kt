package com.moody.player.domain

/**
 * A playlist Moody builds by itself. [basis] says in plain words what it is built from, so the mix
 * never feels random. [shuffle] mixes play shuffled; the others play in the listed order.
 */
data class MoodMix<T>(val id: String, val title: String, val basis: String, val songs: List<T>, val shuffle: Boolean = false)

private const val MIN_SONGS = 3
private const val MAX_SONGS = 50

private fun isNight(h: Int) = h >= 21 || h < 5
private fun hourGap(a: Int, b: Int) = minOf((a - b + 24) % 24, (b - a + 24) % 24)

/** The name for "what you play at this time of day". */
fun partOfDay(hour: Int): String = when (hour) {
    in 5..11 -> "Morning"
    in 12..16 -> "Afternoon"
    in 17..20 -> "Evening"
    else -> "Late night"
}

/**
 * Builds the mood mixes from the library, play history (last [historyDays] days of [rows]), favourites
 * and the current hour. Mixes with fewer than three songs are left out. With no history yet, only the
 * mixes that don't need it (Focus, Loved) can appear.
 */
fun <T> buildMixes(
    songs: List<T>,
    key: (T) -> String,
    durationMs: (T) -> Long,
    rows: List<StatRow>,
    favorites: Set<String>,
    nowHour: Int,
    today: Long,
    historyDays: Int = 30,
): List<MoodMix<T>> {
    val byKey = songs.associateBy(key)
    val recent = rows.filter { it.kind == StatKind.MUSIC && it.day > today - historyDays && it.uri in byKey }
    val perSong = recent.groupBy { it.uri }
    fun msWhere(uri: String, pred: (Int) -> Boolean) = perSong[uri].orEmpty().filter { pred(it.hour) }.sumOf { it.ms }
    fun total(uri: String) = perSong[uri].orEmpty().sumOf { it.ms }
    fun plays(uri: String) = perSong[uri].orEmpty().sumOf { it.plays }

    val mixes = mutableListOf<MoodMix<T>>()

    // Right now: songs you mostly play within two hours of this time.
    val nowSongs = perSong.keys
        .filter { u -> total(u) > 0 && msWhere(u) { hourGap(it, nowHour) <= 2 } >= total(u) * 0.4 }
        .sortedByDescending { u -> msWhere(u) { hourGap(it, nowHour) <= 2 } }
    mixes += MoodMix("now", "${partOfDay(nowHour)} picks", "What you usually play around this time", nowSongs.mapNotNull(byKey::get))

    // On repeat: the most played this month.
    val repeat = perSong.keys.filter { plays(it) >= 2 }.sortedWith(compareByDescending<String> { plays(it) }.thenByDescending { total(it) })
    mixes += MoodMix("repeat", "On repeat", "Your most played this month", repeat.mapNotNull(byKey::get))

    // After dark: songs mostly played between 9 PM and 5 AM.
    val night = perSong.keys.filter { u -> total(u) > 0 && msWhere(u, ::isNight) >= total(u) * 0.5 }.sortedByDescending { msWhere(it, ::isNight) }
    mixes += MoodMix("night", "After dark", "Songs you play late at night", night.mapNotNull(byKey::get))

    // Focus: long tracks to stay in the zone, the ones you return to first.
    val focus = songs.filter { durationMs(it) >= 5 * 60_000L }.sortedByDescending { plays(key(it)) }
    mixes += MoodMix("focus", "Focus", "Long tracks, 5 minutes and up", focus)

    // Loved: favourites, shuffled.
    mixes += MoodMix("loved", "Loved", "Your favourites, shuffled", songs.filter { key(it) in favorites }, shuffle = true)

    // Rediscover: once there is some history, the songs you haven't played this month.
    if (recent.isNotEmpty()) {
        mixes += MoodMix("rediscover", "Rediscover", "Not played in the last $historyDays days", songs.filter { key(it) !in perSong }, shuffle = true)
    }

    return mixes.filter { it.songs.size >= MIN_SONGS }.map { it.copy(songs = it.songs.take(MAX_SONGS)) }
}
