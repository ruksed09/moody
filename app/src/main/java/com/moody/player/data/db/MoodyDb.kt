package com.moody.player.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        PlaybackPosition::class, Playlist::class, PlaylistEntry::class, Favorite::class, VaultItem::class, RecentUrl::class, FileSettings::class,
        Bookmark::class, PlayStat::class,
    ],
    version = 3,
    exportSchema = true,
    // Version 2 only adds the bookmarks and play_stats tables; version 3 only adds columns to bookmarks
    // (name, range end, id, update time, removed flag). Everything already stored is kept.
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class MoodyDb : RoomDatabase() {
    abstract fun positions(): PositionDao
    abstract fun playlists(): PlaylistDao
    abstract fun favorites(): FavoriteDao
    abstract fun vault(): VaultDao
    abstract fun recentUrls(): RecentUrlDao
    abstract fun fileSettings(): FileSettingsDao
    abstract fun bookmarks(): BookmarkDao
    abstract fun stats(): StatsDao
}
