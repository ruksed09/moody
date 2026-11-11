package com.moody.player.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "positions")
data class PlaybackPosition(
    @PrimaryKey val uri: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    val watched: Boolean,
)

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

@Entity(tableName = "playlist_entries", primaryKeys = ["playlistId", "audioUri"])
data class PlaylistEntry(
    val playlistId: Long,
    val audioUri: String,
    val order: Int,
)

@Entity(tableName = "favorites")
data class Favorite(@PrimaryKey val audioUri: String, val addedAt: Long = System.currentTimeMillis())

enum class VaultState { COPYING, DONE, RESTORING }

@Entity(tableName = "vault_items")
data class VaultItem(
    @PrimaryKey val id: String,
    /** File name inside filesDir/vault. */
    val fileName: String,
    val originalName: String,
    val originalRelativePath: String,
    val mime: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val state: VaultState,
    val addedAt: Long,
    /** MediaStore uri of the original, kept until the delete is confirmed. */
    val sourceUri: String = "",
)

@Entity(tableName = "recent_urls")
data class RecentUrl(@PrimaryKey val url: String, val usedAt: Long)

@Entity(tableName = "file_settings")
data class FileSettings(
    @PrimaryKey val uri: String,
    val speed: Float = 1f,
    val audioTrack: Int = -1,
    val subTrack: Int = -1,
)

/**
 * A moment the user marked in a video, or a range ([endMs] set) saved from A-B repeat. Never written for
 * vault videos. [uid] identifies it across phones (bookmarks travel inside MP4 files); a removed bookmark is
 * kept as [deleted] so a copy read back from a file doesn't bring it back.
 */
@Entity(tableName = "bookmarks", indices = [androidx.room.Index("uri")])
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val positionMs: Long,
    val createdAt: Long,
    @androidx.room.ColumnInfo(defaultValue = "") val name: String = "",
    val endMs: Long? = null,
    @androidx.room.ColumnInfo(defaultValue = "") val uid: String = "",
    @androidx.room.ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
    @androidx.room.ColumnInfo(defaultValue = "0") val deleted: Boolean = false,
)

/**
 * Play time for one file in one hour of one day (local time). Kept on the phone only, never for the
 * vault; the Stats screen and mood mixes read it.
 */
@Entity(tableName = "play_stats", primaryKeys = ["day", "hour", "uri"])
data class PlayStat(
    val day: Long,
    val hour: Int,
    val uri: String,
    val kind: String,
    val title: String,
    val ms: Long,
    val plays: Int,
)
