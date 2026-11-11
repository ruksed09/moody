package com.moody.player.di

import android.content.Context
import androidx.room.Room
import com.moody.player.data.db.MoodyDb
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class AppScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun db(@ApplicationContext ctx: Context): MoodyDb = Room.databaseBuilder(ctx, MoodyDb::class.java, "moody.db").build()

    @Provides fun positions(db: MoodyDb) = db.positions()
    @Provides fun playlists(db: MoodyDb) = db.playlists()
    @Provides fun favorites(db: MoodyDb) = db.favorites()
    @Provides fun vault(db: MoodyDb) = db.vault()
    @Provides fun recentUrls(db: MoodyDb) = db.recentUrls()
    @Provides fun fileSettings(db: MoodyDb) = db.fileSettings()
    @Provides fun bookmarks(db: MoodyDb) = db.bookmarks()
    @Provides fun stats(db: MoodyDb) = db.stats()

    /** Long-lived scope for work that must outlive a screen (scans, vault moves, downloads). */
    @Provides @Singleton @AppScope
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
