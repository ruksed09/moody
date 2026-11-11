package com.moody.player.player.music

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.moody.player.data.media.AudioItem
import com.moody.player.data.media.MediaRepository
import com.moody.player.player.PlaybackCoordinator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

enum class RepeatMode { OFF, ALL, ONE }

/** How long the sleep timer takes to fade the music out. */
private const val FADE_MS = 60_000L

data class MusicState(
    val connected: Boolean = false,
    val current: AudioItem? = null,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val queue: List<AudioItem> = emptyList(),
    val index: Int = -1,
    val repeat: RepeatMode = RepeatMode.OFF,
    val shuffle: Boolean = false,
    val speed: Float = 1f,
    /** Sleep timer end (epoch ms); null when off. */
    val sleepAt: Long? = null,
    val audioSessionId: Int = 0,
    /** The user closed the mini-player; it stays hidden until something plays again. */
    val dismissed: Boolean = false,
)

/** The app's handle on the music session. Every call runs on the main thread. */
@Singleton
class MusicController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val media: MediaRepository,
    private val coordinator: PlaybackCoordinator,
    private val settings: com.moody.player.data.prefs.SettingsStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controller: MediaController? = null
    private var connecting = false
    private val pendingActions = mutableListOf<(MediaController) -> Unit>()
    private var ticker: Job? = null
    private var sleepJob: Job? = null

    private val _state = MutableStateFlow(MusicState())
    val state: StateFlow<MusicState> = _state.asStateFlow()

    init {
        coordinator.pauseMusic = { scope.launch { controller?.pause() } }
        // Edited song details show up in the mini-player and Now Playing straight away.
        scope.launch { media.audio.collect { if (controller != null) { refreshSessionDetails(); sync() } } }
    }

    fun connect() {
        if (controller != null || connecting) return
        connecting = true
        val token = SessionToken(context, ComponentName(context, MusicService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            connecting = false
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(listener)
            _state.update { it.copy(connected = true) }
            sync()
            pendingActions.toList().forEach { it(c) }
            pendingActions.clear()
        }, ContextCompat.getMainExecutor(context))
    }

    private fun withController(action: (MediaController) -> Unit) {
        val c = controller
        if (c != null) action(c) else { pendingActions += action; connect() }
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            sync()
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED) && player.isPlaying) {
                coordinator.musicStarted()
                _state.update { it.copy(dismissed = false) }
            }
        }
    }

    private fun lookup(item: MediaItem): AudioItem {
        val byUri = media.audio.value?.firstOrNull { it.uri.toString() == item.mediaId }
        if (byUri != null) return byUri
        val m = item.mediaMetadata
        return AudioItem(
            uri = item.uriOrNull ?: Uri.EMPTY, id = 0, title = m.title?.toString() ?: "Unknown", artist = m.artist?.toString() ?: "Unknown artist",
            album = m.albumTitle?.toString() ?: "", albumId = 0, durationMs = 0, relativePath = "", mime = "audio/*", trackNo = 0, fileName = "", dateAdded = 0,
        )
    }

    /**
     * After a song is edited in Moody (title, artist, album or cover), the queued items still carry the old
     * details, so Android's media controls would show them. Swap in the new details; playback carries on.
     */
    private fun refreshSessionDetails() {
        val c = controller ?: return
        if (!c.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS)) return
        val songs = media.audio.value ?: return
        val byUri = songs.associateBy { it.uri.toString() }
        for (i in 0 until c.mediaItemCount) {
            val old = c.getMediaItemAt(i)
            val song = byUri[old.mediaId] ?: continue
            val fresh = song.toMediaItem()
            val a = old.mediaMetadata; val b = fresh.mediaMetadata
            if (a.title == b.title && a.artist == b.artist && a.albumTitle == b.albumTitle && a.artworkUri == b.artworkUri) continue
            runCatching { c.replaceMediaItem(i, fresh) }
        }
    }

    private fun sync() {
        val c = controller ?: return
        val queue = (0 until c.mediaItemCount).map { lookup(c.getMediaItemAt(it)) }
        _state.update {
            it.copy(
                current = c.currentMediaItem?.let(::lookup),
                playing = c.isPlaying,
                buffering = c.playbackState == Player.STATE_BUFFERING,
                positionMs = c.currentPosition.coerceAtLeast(0),
                durationMs = c.duration.takeIf { d -> d > 0 } ?: (c.currentMediaItem?.let(::lookup)?.durationMs ?: 0),
                queue = queue,
                index = c.currentMediaItemIndex,
                repeat = when (c.repeatMode) { Player.REPEAT_MODE_ALL -> RepeatMode.ALL; Player.REPEAT_MODE_ONE -> RepeatMode.ONE; else -> RepeatMode.OFF },
                shuffle = c.shuffleModeEnabled,
                speed = c.playbackParameters.speed,
            )
        }
        if (c.isPlaying && ticker?.isActive != true) {
            ticker = scope.launch {
                while (isActive && controller?.isPlaying == true) {
                    _state.update { s -> s.copy(positionMs = controller?.currentPosition ?: s.positionMs) }
                    delay(500)
                }
            }
        }
    }

    fun playQueue(items: List<AudioItem>, start: Int, shuffle: Boolean = false) = withController { c ->
        if (items.isEmpty()) return@withController
        c.shuffleModeEnabled = shuffle
        val index = if (shuffle && start == 0) items.indices.random() else start.coerceIn(0, items.lastIndex)
        c.setMediaItems(items.map { it.toMediaItem() }, index, 0L)
        c.prepare()
        c.play()
    }

    fun playNext(items: List<AudioItem>) = withController { c ->
        if (c.mediaItemCount == 0) { playQueue(items, 0); return@withController }
        c.addMediaItems(c.currentMediaItemIndex + 1, items.map { it.toMediaItem() })
    }

    fun addToQueue(items: List<AudioItem>) = withController { c ->
        if (c.mediaItemCount == 0) playQueue(items, 0) else c.addMediaItems(items.map { it.toMediaItem() })
    }

    fun toggle() = withController { c -> if (c.isPlaying) c.pause() else { restoreVolume(); if (c.playbackState == Player.STATE_IDLE) c.prepare(); c.play() } }
    fun pause() = withController { it.pause() }
    fun next() = withController { if (it.hasNextMediaItem()) it.seekToNextMediaItem() }
    fun prev() = withController { if (it.currentPosition > 3_000 || !it.hasPreviousMediaItem()) it.seekTo(0) else it.seekToPreviousMediaItem() }
    fun seekTo(ms: Long) = withController { it.seekTo(ms); _state.update { s -> s.copy(positionMs = ms) } }
    fun skipTo(index: Int) = withController { it.seekTo(index, 0); it.play() }

    fun cycleRepeat() = withController { c ->
        c.repeatMode = when (c.repeatMode) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF }
    }

    /**
     * Close from the mini-player: stop the sound, save the queue, and shut the music service down so
     * nothing keeps running in the background. The next play starts it again with the saved queue.
     */
    fun close() {
        _state.update { it.copy(dismissed = true, playing = false) }
        sleepIn(null)
        pendingActions.clear()
        val c = controller ?: return
        c.pause()
        val f = c.sendCustomCommand(androidx.media3.session.SessionCommand(CMD_SHUTDOWN, android.os.Bundle.EMPTY), android.os.Bundle.EMPTY)
        f.addListener({ disconnect() }, ContextCompat.getMainExecutor(context))
    }

    /** Lets go of the session; the service ends once nothing holds it. */
    private fun disconnect() {
        ticker?.cancel()
        controller?.let { it.removeListener(listener); it.release() }
        controller = null
        _state.update { it.copy(connected = false, playing = false, buffering = false) }
    }

    /** Removes every queue entry for this file (used after the file is deleted). */
    fun removeUri(uri: Uri) = withController { c ->
        val id = uri.toString()
        (c.mediaItemCount - 1 downTo 0).filter { c.getMediaItemAt(it).mediaId == id }.forEach { c.removeMediaItem(it) }
    }

    fun setShuffle(on: Boolean) = withController { it.shuffleModeEnabled = on }
    fun move(from: Int, to: Int) = withController { if (from != to) it.moveMediaItem(from, to) }
    fun removeAt(i: Int) = withController { it.removeMediaItem(i) }
    fun setSpeed(r: Float) = withController { it.playbackParameters = PlaybackParameters(r) }

    data class EqInfo(val presets: List<String>, val preset: Int, val bass: Boolean)

    /** Asks the service for its equalizer, optionally changing it first. Null when effects are unavailable. */
    fun equalizer(set: Pair<Int, Boolean>? = null, onResult: (EqInfo?) -> Unit) = withController { c ->
        val cmd = androidx.media3.session.SessionCommand(if (set == null) CMD_EQ_GET else CMD_EQ_SET, android.os.Bundle.EMPTY)
        val args = android.os.Bundle().apply { set?.let { putInt(ARG_PRESET, it.first); putBoolean(ARG_BASS, it.second) } }
        val f = c.sendCustomCommand(cmd, args)
        f.addListener({
            val r = runCatching { f.get() }.getOrNull()
            val b = r?.extras
            onResult(if (r == null || r.resultCode != androidx.media3.session.SessionResult.RESULT_SUCCESS || b == null) null
            else EqInfo(b.getStringArrayList(ARG_PRESETS).orEmpty(), b.getInt(ARG_PRESET, -1), b.getBoolean(ARG_BASS, false)))
        }, ContextCompat.getMainExecutor(context))
    }

    fun sleepIn(minutes: Int?) {
        sleepJob?.cancel()
        restoreVolume()
        if (minutes == null) { _state.update { it.copy(sleepAt = null) }; return }
        val total = minutes * 60_000L
        val at = System.currentTimeMillis() + total
        _state.update { it.copy(sleepAt = at) }
        sleepJob = scope.launch {
            val fade = settings.settings.first().sleepFade
            val fadeMs = if (fade) minOf(FADE_MS, total) else 0L
            delay(total - fadeMs)
            if (fadeMs > 0) {
                val steps = 40
                for (i in 1..steps) {
                    controller?.volume = (1f - i / steps.toFloat()).coerceAtLeast(0f)
                    delay(fadeMs / steps)
                }
            }
            controller?.pause()
            restoreVolume()
            _state.update { it.copy(sleepAt = null) }
        }
    }

    private fun restoreVolume() { controller?.let { if (it.volume < 1f) it.volume = 1f } }
}
