package com.moody.player.player.music

import android.content.Context
import android.os.Looper
import android.os.ParcelFileDescriptor
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

/**
 * A Media3 [Player] backed by libVLC, for audio Media3 can't decode (WMA, APE, AC3...).
 * The music session swaps to it for queues that contain such files, so notification and headset controls keep working.
 */
@UnstableApi
class VlcAudioPlayer(private val context: Context) : SimpleBasePlayer(Looper.getMainLooper()) {
    private val lib = LibVLC(context, arrayListOf("--no-video", "--audio-time-stretch"))
    private val mp = MediaPlayer(lib)
    private var fd: ParcelFileDescriptor? = null

    private var items: List<MediaItem> = emptyList()
    private var index = 0
    private var order: List<Int> = emptyList()
    private var playWhenReady = false
    private var state = Player.STATE_IDLE
    private var positionMs = 0L
    private var positionAt = 0L
    private var durations = mutableMapOf<Int, Long>()
    private var repeat = Player.REPEAT_MODE_OFF
    private var shuffle = false
    private var speed = 1f
    private var volume = 1f

    init {
        mp.setEventListener { e ->
            when (e.type) {
                MediaPlayer.Event.Playing -> { state = Player.STATE_READY; mark(mp.time) }
                MediaPlayer.Event.Paused -> mark(mp.time)
                MediaPlayer.Event.Buffering -> if (e.buffering < 100f && state != Player.STATE_READY) state = Player.STATE_BUFFERING
                MediaPlayer.Event.TimeChanged -> mark(e.timeChanged)
                MediaPlayer.Event.LengthChanged -> durations[index] = e.lengthChanged
                MediaPlayer.Event.EndReached -> onEnded()
                MediaPlayer.Event.EncounteredError -> onEnded()
            }
            invalidateState()
        }
    }

    private fun mark(ms: Long) { positionMs = ms.coerceAtLeast(0); positionAt = android.os.SystemClock.elapsedRealtime() }

    private fun rebuildOrder() {
        order = if (shuffle) items.indices.shuffled().let { l -> listOf(index) + l.filter { it != index } } else items.indices.toList()
    }

    private fun nextIndex(): Int? {
        val pos = order.indexOf(index)
        return when {
            repeat == Player.REPEAT_MODE_ONE -> index
            pos < order.lastIndex -> order[pos + 1]
            repeat == Player.REPEAT_MODE_ALL && order.isNotEmpty() -> order.first()
            else -> null
        }
    }

    private fun onEnded() {
        val n = nextIndex()
        if (n == null) { state = Player.STATE_ENDED; playWhenReady = false; positionMs = durations[index] ?: positionMs } else load(n, 0, playWhenReady)
    }

    private fun load(i: Int, startMs: Long, play: Boolean) {
        val item = items.getOrNull(i) ?: return
        index = i
        mark(startMs)
        state = Player.STATE_BUFFERING
        fd?.close(); fd = null
        val uri = item.localConfiguration?.uri ?: android.net.Uri.parse(item.mediaId)
        val media = if (uri.scheme == "content") {
            val pfd = runCatching { context.contentResolver.openFileDescriptor(uri, "r") }.getOrNull() ?: run { state = Player.STATE_IDLE; return }
            fd = pfd
            Media(lib, pfd.fileDescriptor)
        } else Media(lib, uri)
        if (startMs > 0) media.addOption(":start-time=${startMs / 1000.0}")
        mp.media = media
        media.release()
        mp.rate = speed
        mp.volume = (volume * 100).toInt()
        if (play) mp.play()
    }

    override fun getState(): State {
        val playing = playWhenReady && state == Player.STATE_READY
        val playlist = items.mapIndexed { i, item ->
            MediaItemData.Builder("$i-${item.mediaId}").setMediaItem(item)
                .setDurationUs(durations[i]?.let { it * 1000 } ?: C.TIME_UNSET).setIsSeekable(true).build()
        }
        return State.Builder()
            .setAvailableCommands(
                Player.Commands.Builder().addAll(
                    COMMAND_PLAY_PAUSE, COMMAND_PREPARE, COMMAND_STOP, COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                    COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_PREVIOUS,
                    COMMAND_SEEK_TO_MEDIA_ITEM, COMMAND_SEEK_TO_DEFAULT_POSITION, COMMAND_SET_REPEAT_MODE, COMMAND_SET_SHUFFLE_MODE,
                    COMMAND_SET_SPEED_AND_PITCH, COMMAND_GET_CURRENT_MEDIA_ITEM, COMMAND_GET_TIMELINE, COMMAND_GET_METADATA,
                    COMMAND_CHANGE_MEDIA_ITEMS, COMMAND_SET_MEDIA_ITEM, COMMAND_RELEASE, COMMAND_SET_VOLUME, COMMAND_GET_VOLUME,
                ).build(),
            )
            .setPlayWhenReady(playWhenReady, PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(if (items.isEmpty()) Player.STATE_IDLE else state)
            .setPlaylist(playlist)
            .setCurrentMediaItemIndex(index.coerceIn(0, (items.size - 1).coerceAtLeast(0)))
            .setContentPositionMs(if (playing) PositionSupplier.getExtrapolating(positionMs, speed) else PositionSupplier.getConstant(positionMs))
            .setRepeatMode(repeat)
            .setShuffleModeEnabled(shuffle)
            .setPlaybackParameters(PlaybackParameters(speed))
            .setVolume(volume)
            .build()
    }

    private fun done(): ListenableFuture<*> = Futures.immediateVoidFuture()

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        this.playWhenReady = playWhenReady
        if (playWhenReady) {
            if (state == Player.STATE_ENDED) load(order.firstOrNull() ?: 0, 0, true)
            else if (mp.hasMedia()) mp.play() else load(index, positionMs, true)
        } else mp.pause()
        return done()
    }

    override fun handlePrepare(): ListenableFuture<*> {
        if (items.isNotEmpty() && !mp.hasMedia()) load(index, positionMs, playWhenReady)
        return done()
    }

    override fun handleStop(): ListenableFuture<*> { mp.stop(); state = Player.STATE_IDLE; playWhenReady = false; return done() }

    override fun handleRelease(): ListenableFuture<*> {
        mp.setEventListener(null); mp.stop(); mp.release(); lib.release(); fd?.close()
        return done()
    }

    override fun handleSetMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<*> {
        items = mediaItems.toList()
        durations.clear()
        index = if (startIndex == C.INDEX_UNSET) 0 else startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        rebuildOrder()
        mp.stop()
        positionMs = if (startPositionMs == C.TIME_UNSET) 0 else startPositionMs
        state = Player.STATE_IDLE
        return done()
    }

    override fun handleAddMediaItems(index: Int, mediaItems: MutableList<MediaItem>): ListenableFuture<*> {
        val at = index.coerceIn(0, items.size)
        items = items.toMutableList().apply { addAll(at, mediaItems) }
        if (at <= this.index && items.size > mediaItems.size) this.index += mediaItems.size
        rebuildOrder()
        return done()
    }

    override fun handleRemoveMediaItems(fromIndex: Int, toIndex: Int): ListenableFuture<*> {
        val removingCurrent = index in fromIndex until toIndex
        items = items.toMutableList().apply { subList(fromIndex, toIndex).clear() }
        if (index >= toIndex) index -= (toIndex - fromIndex)
        if (removingCurrent) { index = fromIndex.coerceAtMost(items.lastIndex).coerceAtLeast(0); if (items.isNotEmpty()) load(index, 0, playWhenReady) else mp.stop() }
        durations.clear()
        rebuildOrder()
        return done()
    }

    override fun handleMoveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int): ListenableFuture<*> {
        val list = items.toMutableList()
        val moved = list.subList(fromIndex, toIndex).toList()
        val current = items.getOrNull(index)
        list.subList(fromIndex, toIndex).clear()
        list.addAll(newIndex.coerceIn(0, list.size), moved)
        items = list
        index = items.indexOf(current).coerceAtLeast(0)
        durations.clear()
        rebuildOrder()
        return done()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        val target = if (mediaItemIndex == C.INDEX_UNSET) index else mediaItemIndex
        val pos = if (positionMs == C.TIME_UNSET) 0 else positionMs
        when (seekCommand) {
            COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> {
                val pos2 = order.indexOf(index)
                val n = if (pos2 < order.lastIndex) order[pos2 + 1] else if (repeat == Player.REPEAT_MODE_ALL) order.firstOrNull() else null
                if (n != null) load(n, 0, playWhenReady)
            }
            COMMAND_SEEK_TO_PREVIOUS, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                val pos2 = order.indexOf(index)
                if (pos2 > 0) load(order[pos2 - 1], 0, playWhenReady) else { mp.setTime(0, true); mark(0) }
            }
            else -> if (target != index || !mp.hasMedia()) load(target, pos, playWhenReady) else { mp.setTime(pos, false); mark(pos) }
        }
        return done()
    }

    override fun handleSetVolume(volume: Float): ListenableFuture<*> {
        this.volume = volume.coerceIn(0f, 1f)
        mp.volume = (this.volume * 100).toInt()
        return done()
    }

    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> { repeat = repeatMode; return done() }
    override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> { shuffle = shuffleModeEnabled; rebuildOrder(); return done() }
    override fun handleSetPlaybackParameters(playbackParameters: PlaybackParameters): ListenableFuture<*> {
        speed = playbackParameters.speed; mp.rate = speed; return done()
    }
}
