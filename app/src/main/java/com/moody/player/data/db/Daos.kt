package com.moody.player.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PositionDao {
    @Query("SELECT * FROM positions") fun all(): Flow<List<PlaybackPosition>>
    @Query("SELECT * FROM positions WHERE uri = :uri") suspend fun get(uri: String): PlaybackPosition?
    @Upsert suspend fun save(p: PlaybackPosition)
    @Query("DELETE FROM positions WHERE uri = :uri") suspend fun clear(uri: String)
    @Query("DELETE FROM positions") suspend fun clearAll()
    /** Started but not finished, newest first: the "Continue watching" row. */
    @Query("SELECT * FROM positions WHERE watched = 0 AND positionMs > 5000 ORDER BY updatedAt DESC LIMIT :limit")
    fun inProgress(limit: Int = 12): Flow<List<PlaybackPosition>>
}

@Dao
interface PlaylistDao {
    @Insert suspend fun create(p: Playlist): Long
    @Query("UPDATE playlists SET name = :name WHERE id = :id") suspend fun rename(id: Long, name: String)
    @Query("DELETE FROM playlists WHERE id = :id") suspend fun deletePlaylist(id: Long)
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC") fun all(): Flow<List<Playlist>>
    @Query("SELECT * FROM playlists WHERE id = :id") suspend fun get(id: Long): Playlist?
    @Query("SELECT * FROM playlist_entries WHERE playlistId = :id ORDER BY `order`") fun entries(id: Long): Flow<List<PlaylistEntry>>
    @Query("SELECT playlistId, COUNT(*) AS n FROM playlist_entries GROUP BY playlistId") fun counts(): Flow<List<PlaylistCount>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addEntries(e: List<PlaylistEntry>)
    @Query("SELECT COALESCE(MAX(`order`), -1) FROM playlist_entries WHERE playlistId = :id") suspend fun maxOrder(id: Long): Int
    @Query("DELETE FROM playlist_entries WHERE playlistId = :id AND audioUri = :uri") suspend fun removeEntry(id: Long, uri: String)
    @Query("DELETE FROM playlist_entries WHERE playlistId = :id") suspend fun clearEntries(id: Long)
    @Query("DELETE FROM playlist_entries WHERE audioUri = :uri") suspend fun removeEverywhere(uri: String)

    @Transaction suspend fun delete(id: Long) { clearEntries(id); deletePlaylist(id) }

    @Transaction suspend fun append(id: Long, uris: List<String>) {
        var next = maxOrder(id) + 1
        addEntries(uris.map { PlaylistEntry(id, it, next++) })
    }

    @Transaction suspend fun reorder(id: Long, uris: List<String>) {
        clearEntries(id)
        addEntries(uris.mapIndexed { i, u -> PlaylistEntry(id, u, i) })
    }
}

data class PlaylistCount(val playlistId: Long, val n: Int)

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC") fun all(): Flow<List<Favorite>>
    @Upsert suspend fun add(f: Favorite)
    @Query("DELETE FROM favorites WHERE audioUri = :uri") suspend fun remove(uri: String)
}

@Dao
interface VaultDao {
    @Upsert suspend fun upsert(v: VaultItem)
    @Query("SELECT * FROM vault_items WHERE state = 'DONE' ORDER BY addedAt DESC") fun done(): Flow<List<VaultItem>>
    @Query("SELECT * FROM vault_items WHERE state != 'DONE'") suspend fun unfinished(): List<VaultItem>
    @Query("SELECT * FROM vault_items WHERE id = :id") suspend fun get(id: String): VaultItem?
    @Query("SELECT sourceUri FROM vault_items") fun sourceUris(): Flow<List<String>>
    @Query("DELETE FROM vault_items WHERE id = :id") suspend fun delete(id: String)
}

@Dao
interface RecentUrlDao {
    @Query("SELECT * FROM recent_urls ORDER BY usedAt DESC LIMIT 10") fun recent(): Flow<List<RecentUrl>>
    @Upsert suspend fun upsert(u: RecentUrl)
    @Query("DELETE FROM recent_urls WHERE url NOT IN (SELECT url FROM recent_urls ORDER BY usedAt DESC LIMIT 10)") suspend fun trim()
    @Query("DELETE FROM recent_urls WHERE url = :url") suspend fun remove(url: String)

    @Transaction suspend fun add(u: RecentUrl) { upsert(u); trim() }
}

@Dao
interface FileSettingsDao {
    @Query("SELECT * FROM file_settings WHERE uri = :uri") suspend fun get(uri: String): FileSettings?
    @Upsert suspend fun save(s: FileSettings)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE uri = :uri ORDER BY positionMs") fun forUri(uri: String): Flow<List<Bookmark>>
    @Insert suspend fun add(b: Bookmark): Long
    @Query("DELETE FROM bookmarks WHERE id = :id") suspend fun remove(id: Long)
}

@Dao
interface StatsDao {
    @Query("INSERT OR IGNORE INTO play_stats(day, hour, uri, kind, title, ms, plays) VALUES (:day, :hour, :uri, :kind, :title, 0, 0)")
    suspend fun ensure(day: Long, hour: Int, uri: String, kind: String, title: String)

    @Query("UPDATE play_stats SET ms = ms + :ms, plays = plays + :plays, title = :title WHERE day = :day AND hour = :hour AND uri = :uri")
    suspend fun bump(day: Long, hour: Int, uri: String, title: String, ms: Long, plays: Int)

    @Transaction suspend fun add(day: Long, hour: Int, uri: String, kind: String, title: String, ms: Long, plays: Int) {
        ensure(day, hour, uri, kind, title)
        bump(day, hour, uri, title, ms, plays)
    }

    @Query("SELECT * FROM play_stats WHERE day >= :fromDay") fun since(fromDay: Long): Flow<List<PlayStat>>
    @Query("DELETE FROM play_stats") suspend fun clearAll()
}
