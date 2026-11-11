package com.moody.player.data.stats

import com.moody.player.data.db.PlayStat
import com.moody.player.data.db.StatsDao
import com.moody.player.data.prefs.SettingsStore
import com.moody.player.domain.StatRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Counts play time per file and hour, on the phone only. Callers must never pass vault playback.
 * A play counts once a file has played for [PLAY_AFTER_MS] in one sitting.
 */
@Singleton
class PlayStats @Inject constructor(private val dao: StatsDao, private val settings: SettingsStore) {

    suspend fun record(uri: String, kind: String, title: String, ms: Long, countPlay: Boolean) {
        if (ms <= 0 && !countPlay) return
        if (!settings.settings.first().keepStats) return
        val now = LocalDateTime.now()
        runCatching { dao.add(now.toLocalDate().toEpochDay(), now.hour, uri, kind, title, ms.coerceAtLeast(0), if (countPlay) 1 else 0) }
    }

    /** Rows from [days] days ago up to today. */
    fun recent(days: Int): Flow<List<StatRow>> =
        dao.since(today() - days).map { list -> list.map { it.toRow() } }

    suspend fun clear() = dao.clearAll()

    companion object {
        const val PLAY_AFTER_MS = 10_000L
        fun today(): Long = LocalDate.now().toEpochDay()
    }
}

private fun PlayStat.toRow() = StatRow(day, hour, uri, kind, title, ms, plays)

/**
 * Tracks one sitting of one file: feed it play time as it passes, and it hands back how much to
 * record and whether this is the moment the sitting first counts as a play.
 */
class SittingCounter {
    private var key: String? = null
    private var played = 0L
    private var counted = false

    /** Returns true when this addition makes the sitting count as a play. */
    fun add(forKey: String, ms: Long): Boolean {
        if (forKey != key) { key = forKey; played = 0; counted = false }
        played += ms
        if (!counted && played >= PlayStats.PLAY_AFTER_MS) { counted = true; return true }
        return false
    }

    fun reset() { key = null; played = 0; counted = false }
}
