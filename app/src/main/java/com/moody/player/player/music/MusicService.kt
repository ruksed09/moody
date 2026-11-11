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
            // A cover picked in Moody wins, so the notification and lock screen match the app.
            .setArtworkUri(cover?.let { android.net.Uri.fromFile(java.io.File(it)) } ?: MediaScanner.albumArtUri(albumId))
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
    @Inject lateinit var stats: com.moody.player.data.stats.PlayStats
    private var statsJob: Job? = null
    private val sitting = com.moody.player.data.stats.SittingCounter()
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var saver: Job? = null
    private var effects: AudioEffects? = null
    private lateinit var exo: ExoPlayer
    private var vlc: VlcAudioPlayer? = null

    /** Songs Media3 cut short this session (see [checkTruncated]); they always go to libVLC. */
    private val forceVlc = mutableSetOf<String>()

    /** Items sent from a controller may arrive without their uri; the media id is the uri. */
    private fun resolve(items: List<MediaItem>): List<MediaItem> = items.map {
        val withUri = if (it.localConfiguration != null) it else it.buildUpon().setUri(Uri.parse(it.mediaId)).build()
        if (withUri.mediaId in forceVlc) withUri.withVlc() else withUri
    }

    private fun MediaItem.withVlc(): MediaItem = buildUpon().setMediaMetadata(
        mediaMetadata.buildUpon().setExtras(Bundle(mediaMetadata.extras ?: Bundle.EMPTY).apply { putBoolean(EXTRA_VLC, true) }).build(),
    ).build()

    /**
     * Some MP3s (call recordings especially) carry a header that states a far shorter length than the
     * file has; Media3 believes it and ends the song early. When Media3's length is well under the
     * library's, the queue moves to libVLC at the same spot, and VLC plays the whole file.
     */
    private fun checkTruncated() {
        val s = session ?: return
        if (s.player !== exo) return
        val item = exo.currentMediaItem ?: return
        if (item.mediaId in forceVlc) return
        val d = exo.duration
        if (d <= 0) return
        val expected = media.audio.value?.firstOrNull { it.uri.toString() == item.mediaId }?.durationMs ?: return
        if (expected - d < 10_000 || d > expected * 0.9) return
        forceVlc += item.mediaId
        val items = (0 until exo.mediaItemCount).map { exo.getMediaItemAt(it) }.map { if (it.mediaId in forceVlc) it.withVlc() else it }
        val index = exo.currentMediaItemIndex
        val pos = exo.currentPosition
        val play = exo.playWhenReady
        pickPlayer(items)
        val target = s.player
        target.setMediaItems(items, index, pos)
        target.prepare()
        target.playWhenReady = play
    }

    /** Media3 plays most files; a queue holding anything it can't decode goes to libVLC as a whole. */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun pickPlayer(items: List<MediaItem>) {
        val s = session ?: return
        val wantVlc = items.any { it.mediaMetadata.extras?.getBoolean(EXTRA_VLC) == true }
        val target: Player = if (wantVlc) (vlc ?: VlcAudioPlayer(this).also { vlc = it }) else exo
        AudioTap.setFallback(target !== exo)
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
                        .add(SessionCommand(CMD_SHUTDOWN, Bundle.EMPTY))
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
            if (customCommand.customAction == CMD_SHUTDOWN) return shutDown()
            val fx = effects ?: return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
            if (customCommand.customAction == CMD_EQ_SET) fx.apply(args.getInt(ARG_PRESET, -1), args.getBoolean(ARG_BASS, false))
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, fx.describe()))
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Closing the mini-player stops the player; a stopped (idle) player must not keep a notification.
        setShowNotificationForIdlePlayer(SHOW_NOTIFICATION_FOR_IDLE_PLAYER_NEVER)
        // The audio sink carries a pass-through tap so the visualizer can see the music's own samples.
        val renderers = object : androidx.media3.exoplayer.DefaultRenderersFactory(this) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): androidx.media3.exoplayer.audio.AudioSink =
                androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(TapProcessor()))
                    .build()
        }
        // ADTS (.aac) files carry no length or seek table; constant-bitrate seeking gives them both.
        val extractors = androidx.media3.extractor.DefaultExtractorsFactory()
            .setAdtsExtractorFlags(androidx.media3.extractor.ts.AdtsExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING)
        val player = ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(this, extractors))
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
            override fun onEvents(p: Player, events: Player.Events) {
                if (events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_PLAYBACK_STATE_CHANGED)) scope.launch(Dispatchers.Main) { checkTruncated() }
            }
        })
        saver = scope.launch {
            while (isActive) { delay(10_000); if (player.isPlaying) save(player) }
        }
        statsJob = scope.launch {
            var last = android.os.SystemClock.elapsedRealtime()
            var pending = 0L
            var pendingId: String? = null
            var pendingTitle = ""
            while (isActive) {
                delay(1_000)
                val now = android.os.SystemClock.elapsedRealtime()
                val p = session?.player
                val item = p?.currentMediaItem
                val id = item?.mediaId
                if (pendingId != null && (id != pendingId || pending >= 15_000 || p?.isPlaying != true) && pending > 0) {
                    val counts = sitting.add(pendingId!!, pending)
                    val (uri, title, ms) = Triple(pendingId!!, pendingTitle, pending)
                    launch(Dispatchers.IO) { stats.record(uri, com.moody.player.domain.StatKind.MUSIC, title, ms, counts) }
                    pending = 0
                }
                if (p?.isPlaying == true && id != null) {
                    pendingId = id
                    pendingTitle = media.audio.value?.firstOrNull { it.uri.toString() == id }?.title ?: item?.mediaMetadata?.title?.toString().orEmpty()
                    pending += (now - last).coerceIn(0, 2_000)
                }
                last = now
            }
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
        // Something may have been queued while the saved queue was loading; that wins.
        if (target.mediaItemCount > 0 || exo.mediaItemCount > 0 || (vlc?.mediaItemCount ?: 0) > 0) return
        target.setMediaItems(items, index, prefs[KEY_POS] ?: 0L)
        target.prepare()
    }

    /**
     * Close from the app: save the queue, stop, and end this service so nothing stays alive in the background.
     * The reply waits for the save, so the controller unbinding can't cut it short.
     */
    private fun shutDown(): ListenableFuture<SessionResult> {
        val done = com.google.common.util.concurrent.SettableFuture.create<SessionResult>()
        val p = session?.player
        scope.launch {
            if (p != null) {
                p.pause()
                runCatching { save(p) }
                p.stop()
            }
            done.set(SessionResult(SessionResult.RESULT_SUCCESS))
            stopSelf()
        }
        return done
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
const val CMD_SHUTDOWN = "moody.shutdown"
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
