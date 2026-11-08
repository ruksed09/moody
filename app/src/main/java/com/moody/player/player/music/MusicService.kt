package com.moody.player.player.music

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.moody.player.MainActivity
import com.moody.player.R
import com.moody.player.data.media.AudioItem
import com.moody.player.data.media.MediaScanner
import com.moody.player.data.media.MediaRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

internal val Context.musicState by preferencesDataStore("music_state")
private val KEY_QUEUE = stringPreferencesKey("queue")
private val KEY_INDEX = intPreferencesKey("index")
private val KEY_POS = longPreferencesKey("position")

fun AudioItem.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(uri.toString())
    .setUri(uri)
    .setMimeType(mime)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(MediaScanner.albumArtUri(albumId))
            .setTrackNumber(trackNo.takeIf { it > 0 })
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .build(),
    )
    .build()

/** Music playback: one ExoPlayer behind a MediaSession, which gives notification, lock screen and headset controls. */
@AndroidEntryPoint
class MusicService : MediaSessionService() {
    @Inject lateinit var media: MediaRepository
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var saver: Job? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_NOW_PLAYING, true), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        session = MediaSession.Builder(this, player).setSessionActivity(open).build()
        setMediaNotificationProvider(DefaultMediaNotificationProvider(this).apply { setSmallIcon(R.drawable.ic_notification) })

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { scope.launch { save(player) } }
            override fun onIsPlayingChanged(isPlaying: Boolean) { scope.launch { save(player) } }
        })
        saver = scope.launch {
            while (isActive) { delay(10_000); if (player.isPlaying) save(player) }
        }
        scope.launch { restore(player) }
    }

    private suspend fun save(p: Player) {
        if (p.mediaItemCount == 0) return
        val ids = (0 until p.mediaItemCount).joinToString("\n") { p.getMediaItemAt(it).mediaId }
        musicState.edit {
            it[KEY_QUEUE] = ids
            it[KEY_INDEX] = p.currentMediaItemIndex
            it[KEY_POS] = p.currentPosition
        }
    }

    /** Brings back the last queue (paused) so the mini player and headset button work right away. */
    private suspend fun restore(p: Player) {
        if (p.mediaItemCount > 0) return
        val prefs = musicState.data.first()
        val ids = prefs[KEY_QUEUE]?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
        if (ids.isEmpty()) return
        val library = media.audio.value ?: run { media.rescan(); media.audio.value }.orEmpty()
        val byUri = library.associateBy { it.uri.toString() }
        val items = ids.mapNotNull { byUri[it]?.toMediaItem() }
        if (items.isEmpty()) return
        val index = (prefs[KEY_INDEX] ?: 0).coerceIn(0, items.lastIndex)
        p.setMediaItems(items, index, prefs[KEY_POS] ?: 0L)
        p.prepare()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}

/** Uri key for a queue entry. */
val MediaItem.uriOrNull: Uri? get() = localConfiguration?.uri ?: runCatching { Uri.parse(mediaId) }.getOrNull()
