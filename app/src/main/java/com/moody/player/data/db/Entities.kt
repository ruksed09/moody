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
