package com.moody.player.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [PlaybackPosition::class, Playlist::class, PlaylistEntry::class, Favorite::class, VaultItem::class, RecentUrl::class, FileSettings::class],
    version = 1,
    exportSchema = true,
)
abstract class MoodyDb : RoomDatabase() {
    abstract fun positions(): PositionDao
    abstract fun playlists(): PlaylistDao
    abstract fun favorites(): FavoriteDao
    abstract fun vault(): VaultDao
    abstract fun recentUrls(): RecentUrlDao
    abstract fun fileSettings(): FileSettingsDao
}
