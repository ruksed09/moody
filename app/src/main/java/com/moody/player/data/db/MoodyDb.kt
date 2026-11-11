package com.moody.player.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        PlaybackPosition::class, Playlist::class, PlaylistEntry::class, Favorite::class, VaultItem::class, RecentUrl::class, FileSettings::class,
        Bookmark::class, PlayStat::class,
    ],
    version = 2,
    exportSchema = true,
    // Version 2 only adds the bookmarks and play_stats tables; everything already stored is kept.
    autoMigrations = [AutoMigration(from = 1, to = 2)],
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
