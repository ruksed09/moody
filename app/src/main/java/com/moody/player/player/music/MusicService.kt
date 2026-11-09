package com.moody.player.player.music

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
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
            .setExtras(Bundle().apply { putBoolean(EXTRA_VLC, com.moody.player.domain.needsVlc(mime, fileName)) })
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
    private var effects: AudioEffects? = null
    private lateinit var exo: ExoPlayer
    private var vlc: VlcAudioPlayer? = null

    /** Items sent from a controller may arrive without their uri; the media id is the uri. */
    private fun resolve(items: List<MediaItem>): List<MediaItem> = items.map {
        if (it.localConfiguration != null) it else it.buildUpon().setUri(Uri.parse(it.mediaId)).build()
    }

    /** Media3 plays most files; a queue holding anything it can't decode goes to libVLC as a whole. */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun pickPlayer(items: List<MediaItem>) {
        val s = session ?: return
        val wantVlc = items.any { it.mediaMetadata.extras?.getBoolean(EXTRA_VLC) == true }
        val target: Player = if (wantVlc) (vlc ?: VlcAudioPlayer(this).also { vlc = it }) else exo
        if (s.player !== target) {
            s.player.pause()
            s.player.stop()
            target.repeatMode = s.player.repeatMode
            target.shuffleModeEnabled = s.player.shuffleModeEnabled
            s.player = target
        }
    }

    private val callback = object : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult =
            MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(SessionCommand(CMD_EQ_GET, Bundle.EMPTY))
                        .add(SessionCommand(CMD_EQ_SET, Bundle.EMPTY))
                        .build(),
                )
                .build()

        override fun onAddMediaItems(mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(resolve(mediaItems).toMutableList())

        override fun onSetMediaItems(
            mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val resolved = resolve(mediaItems)
            pickPlayer(resolved)
            return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(resolved, startIndex, startPositionMs))
        }

        override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
            val fx = effects ?: return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
            if (customCommand.customAction == CMD_EQ_SET) fx.apply(args.getInt(ARG_PRESET, -1), args.getBoolean(ARG_BASS, false))
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, fx.describe()))
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Closing the mini-player stops the player; a stopped (idle) player must not keep a notification.
        setShowNotificationForIdlePlayer(SHOW_NOTIFICATION_FOR_IDLE_PLAYER_NEVER)
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        exo = player
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_NOW_PLAYING, true), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        player.audioSessionId = (getSystemService(AUDIO_SERVICE) as android.media.AudioManager).generateAudioSessionId()
        effects = AudioEffects(player.audioSessionId)
        session = MediaSession.Builder(this, player).setSessionActivity(open).setCallback(callback).build()
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
        pickPlayer(items)
        val target = session?.player ?: p
        target.setMediaItems(items, index, prefs[KEY_POS] ?: 0L)
        target.prepare()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        effects?.release()
        scope.cancel()
        session?.release()
        exo.release()
        vlc?.release()
        session = null
        super.onDestroy()
    }
}

/** Uri key for a queue entry. */
val MediaItem.uriOrNull: Uri? get() = localConfiguration?.uri ?: runCatching { Uri.parse(mediaId) }.getOrNull()

const val CMD_EQ_GET = "moody.eq.get"
const val CMD_EQ_SET = "moody.eq.set"
const val ARG_PRESET = "preset"
const val ARG_BASS = "bass"
const val ARG_PRESETS = "presets"

/** Equalizer and bass boost on the music session. Some phones refuse effects; then everything is a no-op. */
private class AudioEffects(sessionId: Int) {
    private val eq = runCatching { android.media.audiofx.Equalizer(0, sessionId) }.getOrNull()
    private val bass = runCatching { android.media.audiofx.BassBoost(0, sessionId) }.getOrNull()
    private var preset = -1
    private var bassOn = false

    fun apply(p: Int, b: Boolean) {
        preset = p
        bassOn = b
        eq?.let { e ->
            runCatching {
                if (p >= 0 && p < e.numberOfPresets) { e.usePreset(p.toShort()); e.enabled = true } else e.enabled = false
            }
        }
        bass?.let { runCatching { if (it.strengthSupported) it.setStrength(if (b) 700 else 0); it.enabled = b } }
    }

    fun describe(): Bundle = Bundle().apply {
        val names = eq?.let { e -> runCatching { (0 until e.numberOfPresets).map { e.getPresetName(it.toShort()) } }.getOrNull() } ?: emptyList()
        putStringArrayList(ARG_PRESETS, ArrayList(names))
        putInt(ARG_PRESET, preset)
        putBoolean(ARG_BASS, bassOn)
    }

    fun release() { runCatching { eq?.release() }; runCatching { bass?.release() } }
}

const val EXTRA_VLC = "moody.vlc"
