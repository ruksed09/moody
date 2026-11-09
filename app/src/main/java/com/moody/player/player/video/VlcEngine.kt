package com.moody.player.player.video

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.moody.player.domain.Aspect
import com.moody.player.domain.Decoder
import com.moody.player.domain.SubBg
import com.moody.player.domain.SubSize
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.VLCVideoLayout
import javax.inject.Inject
import javax.inject.Singleton

data class Track(val id: Int, val name: String)

data class PlayerState(
    val uri: Uri? = null,
    val playing: Boolean = false,
    val buffering: Boolean = true,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val ended: Boolean = false,
    val error: String? = null,
    val audioTracks: List<Track> = emptyList(),
    val subTracks: List<Track> = emptyList(),
    val selectedAudio: Int = -1,
    val selectedSub: Int = -1,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    /** Size as it should appear on screen: rotation metadata and pixel aspect applied. 0 until known. */
    val displayWidth: Int = 0,
    val displayHeight: Int = 0,
    val muted: Boolean = false,
    val rate: Float = 1f,
    val aspect: Aspect = Aspect.FIT,
    val decoder: Decoder = Decoder.HW,
    val audioDelayMs: Long = 0,
    val subDelayMs: Long = 0,
)

/** Subtitle look is fixed per LibVLC instance, so a change rebuilds the instance before the next open. */
data class SubStyle(val size: SubSize = SubSize.M, val bg: SubBg = SubBg.SHADOW, val color: Long = 0xFFFFFAF2)

sealed interface EngineEvent {
    data object SwitchedToSoftware : EngineEvent
    data object Ended : EngineEvent
}

/**
 * The video engine: one libVLC player shared by the player screen and the background service.
 * Every call is made on the main thread.
 */
@Singleton
class VlcEngine @Inject constructor(@ApplicationContext private val context: Context) {
    private var libVlc: LibVLC? = null
    private var player: MediaPlayer? = null
    private var style: SubStyle? = null
    private var fd: ParcelFileDescriptor? = null
    private var layout: VLCVideoLayout? = null
    private var triedSoftware = false
    private var lastOpen: Triple<Uri, Long, Decoder>? = null

    /** libVLC stalls on some phones if playback starts before a video surface exists, so play waits for attach(). */
    private var pendingPlay = false
    /** Set once libVLC reports playback time; if it never does, the decoder is treated as stuck. */
    private var sawTime = false
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val stallCheck = Runnable {
        val st = _state.value
        if (!sawTime && st.error == null && !userPaused && player != null) {
            android.util.Log.w("Moody", "Playback stalled on ${st.decoder}; retrying")
            onError()
        }
    }
    private var userPaused = false

    // Audio focus: libVLC doesn't ask for it, so the engine does. Calls and other apps pause or duck the video.
    private val audio = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
    private var pausedByFocus = false
    private var hasFocus = false
    private val focusListener = android.media.AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            android.media.AudioManager.AUDIOFOCUS_LOSS -> { pausedByFocus = false; player?.pause(); abandonFocus() }
            android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> if (_state.value.playing) { pausedByFocus = true; player?.pause() }
            android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> player?.volume = 30
            android.media.AudioManager.AUDIOFOCUS_GAIN -> {
                player?.volume = 100
                if (pausedByFocus) { pausedByFocus = false; player?.play() }
            }
        }
    }
    private val focusRequest = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE)
                .build(),
        )
        .setOnAudioFocusChangeListener(focusListener)
        .build()

    private fun requestFocus() {
        if (hasFocus) return
        hasFocus = audio.requestAudioFocus(focusRequest) == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        if (!hasFocus) return
        audio.abandonAudioFocusRequest(focusRequest)
        hasFocus = false
    }

    private var noisyRegistered = false
    private val noisy = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: android.content.Context, i: android.content.Intent) { player?.pause() }
    }

    private fun setNoisy(on: Boolean) {
        if (on == noisyRegistered) return
        if (on) androidx.core.content.ContextCompat.registerReceiver(
            context, noisy, android.content.IntentFilter(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        ) else runCatching { context.unregisterReceiver(noisy) }
        noisyRegistered = on
    }

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<EngineEvent> = _events.asSharedFlow()

    val audioSessionActive: Boolean get() = player?.isPlaying == true

    private fun options(s: SubStyle): ArrayList<String> = arrayListOf(
        "--audio-time-stretch",
        "--avcodec-skiploopfilter=1",
        "--freetype-rel-fontsize=${s.size.vlcRelSize}",
        "--freetype-color=${(s.color and 0xFFFFFF).toInt()}",
        "--freetype-outline-thickness=${if (s.bg == SubBg.SHADOW) 4 else 0}",
        "--freetype-shadow-opacity=${if (s.bg == SubBg.SHADOW) 160 else 0}",
        "--freetype-background-opacity=${when (s.bg) { SubBg.SHADOW -> 0; SubBg.SOFT -> 128; SubBg.SOLID -> 255 }}",
        "--freetype-background-color=0",
        "--sub-autodetect-fuzzy=3",
        "--network-caching=1500",
    ).apply { if (com.moody.player.BuildConfig.DEBUG) add("-vv") }

    private fun ensure(s: SubStyle): MediaPlayer {
        val p = player
        if (p != null && style == s) return p
        val wasAttached = layout
        releasePlayer()
        val lib = LibVLC(context, options(s))
        val mp = MediaPlayer(lib)
        mp.setEventListener(::onEvent)
        libVlc = lib
        player = mp
        style = s
        wasAttached?.let { mp.attachViews(it, null, true, false) }
        return mp
    }

    fun attach(view: VLCVideoLayout) {
        if (layout === view) return
        player?.let { if (layout != null) it.detachViews(); it.attachViews(view, null, true, false) }
        layout = view
        applyAspect(_state.value.aspect)
        if (pendingPlay) { pendingPlay = false; player?.play() }
    }

    fun detach(view: VLCVideoLayout) {
        if (layout !== view) return
        player?.detachViews()
        layout = null
    }

    fun open(uri: Uri, startMs: Long, decoder: Decoder, subStyle: SubStyle, extraSubtitle: Uri? = null) {
        val mp = ensure(subStyle)
        val lib = libVlc ?: return
        triedSoftware = decoder == Decoder.SW
        lastOpen = Triple(uri, startMs, decoder)
        fd?.close(); fd = null
        val media = when (uri.scheme) {
            "content" -> {
                val pfd = try { context.contentResolver.openFileDescriptor(uri, "r") } catch (e: Exception) { null }
                if (pfd == null) {
                    _state.value = PlayerState(uri = uri, buffering = false, error = "Moody could not open this file. It may have been moved or deleted.")
                    return
                }
                fd = pfd
                Media(lib, pfd.fileDescriptor)
            }
            else -> Media(lib, uri)
        }
        when (decoder) {
            Decoder.SW -> media.setHWDecoderEnabled(false, false)
            Decoder.HW -> media.setHWDecoderEnabled(true, false)
            Decoder.HW_PLUS -> media.setHWDecoderEnabled(true, true)
        }
        if (startMs > 0) media.addOption(":start-time=${startMs / 1000.0}")
        mp.media = media
        media.release()
        extraSubtitle?.let { mp.addSlave(IMedia.Slave.Type.Subtitle, it, true) }
        val keep = _state.value
        _state.value = PlayerState(uri = uri, positionMs = startMs, buffering = true, rate = keep.rate, aspect = keep.aspect, decoder = decoder)
        mp.rate = keep.rate
        sawTime = false
        userPaused = false
        main.removeCallbacks(stallCheck)
        main.postDelayed(stallCheck, if (uri.scheme == "http" || uri.scheme == "https" || uri.scheme?.startsWith("rt") == true) 20_000L else 8_000L)
        if (layout != null) mp.play() else pendingPlay = true
    }

    private fun onEvent(e: MediaPlayer.Event) {
        val mp = player ?: return
        when (e.type) {
            MediaPlayer.Event.Playing -> {
                requestFocus(); setNoisy(true)
                _state.update { it.copy(playing = true, buffering = false, ended = false, error = null) }
            }
            MediaPlayer.Event.Paused -> { setNoisy(false); if (!pausedByFocus) abandonFocus(); _state.update { it.copy(playing = false) } }
            MediaPlayer.Event.Stopped -> { setNoisy(false); abandonFocus(); _state.update { it.copy(playing = false) } }
            MediaPlayer.Event.Buffering -> _state.update { it.copy(buffering = e.buffering < 100f) }
            MediaPlayer.Event.TimeChanged -> {
                if (!sawTime) { sawTime = true; main.removeCallbacks(stallCheck) }
                _state.update { it.copy(positionMs = e.timeChanged, buffering = false) }
            }
            MediaPlayer.Event.LengthChanged -> _state.update { it.copy(durationMs = e.lengthChanged) }
            MediaPlayer.Event.EndReached -> {
                _state.update { it.copy(playing = false, ended = true, positionMs = it.durationMs) }
                _events.tryEmit(EngineEvent.Ended)
            }
            MediaPlayer.Event.EncounteredError -> onError()
            MediaPlayer.Event.ESAdded, MediaPlayer.Event.ESDeleted, MediaPlayer.Event.ESSelected, MediaPlayer.Event.Vout -> refreshTracks(mp)
        }
    }

    private fun onError() {
        val last = lastOpen
        if (!triedSoftware && last != null) {
            triedSoftware = true
            _events.tryEmit(EngineEvent.SwitchedToSoftware)
            open(last.first, _state.value.positionMs, com.moody.player.domain.Decoder.SW, style ?: SubStyle())
            return
        }
        _state.update { it.copy(playing = false, buffering = false, error = "This file could not be played. It may be damaged or use a format Moody can't read.") }
    }

    private fun refreshTracks(mp: MediaPlayer) {
        val v = mp.currentVideoTrack
        val (dw, dh) = if (v != null && v.width > 0 && v.height > 0) displaySize(v.width, v.height, v.sarNum, v.sarDen, v.orientation) else 0 to 0
        _state.update {
            it.copy(
                audioTracks = mp.audioTracks?.map { t -> Track(t.id, t.name) }.orEmpty(),
                subTracks = mp.spuTracks?.map { t -> Track(t.id, t.name) }.orEmpty(),
                selectedAudio = mp.audioTrack,
                selectedSub = mp.spuTrack,
                videoWidth = v?.width ?: it.videoWidth,
                videoHeight = v?.height ?: it.videoHeight,
                displayWidth = if (dw > 0) dw else it.displayWidth,
                displayHeight = if (dh > 0) dh else it.displayHeight,
            )
        }
    }

    fun play() {
        userPaused = false
        val mp = player ?: return
        if (_state.value.ended) { mp.stop(); lastOpen?.let { open(it.first, 0, it.third, style ?: SubStyle()) }; return }
        mp.play()
    }

    fun pause() { userPaused = true; pendingPlay = false; player?.pause() }
    fun toggle() { if (_state.value.playing) pause() else play() }

    fun seekTo(ms: Long) {
        val mp = player ?: return
        val d = _state.value.durationMs
        val target = if (d > 0) ms.coerceIn(0, d - 500) else ms.coerceAtLeast(0)
        mp.setTime(target, false)
        _state.update { it.copy(positionMs = target, ended = false) }
    }

    fun seekBy(deltaMs: Long) = seekTo(_state.value.positionMs + deltaMs)

    fun setMuted(on: Boolean) {
        player?.volume = if (on) 0 else 100
        _state.update { it.copy(muted = on) }
    }

    /**
     * Grabs the current frame from the video surface (libVLC 3 has no snapshot call on Android).
     * [onResult] runs on the main thread with null when the surface isn't ready.
     */
    fun snapshot(onResult: (android.graphics.Bitmap?) -> Unit) {
        val surface = layout?.let { findSurface(it) }
        if (surface == null || surface.width == 0 || surface.height == 0 || !surface.holder.surface.isValid) { onResult(null); return }
        val bmp = android.graphics.Bitmap.createBitmap(surface.width, surface.height, android.graphics.Bitmap.Config.ARGB_8888)
        android.view.PixelCopy.request(surface, bmp, { code -> onResult(if (code == android.view.PixelCopy.SUCCESS) bmp else null) }, main)
    }

    private fun findSurface(v: android.view.View): android.view.SurfaceView? {
        if (v is android.view.SurfaceView && v.visibility == android.view.View.VISIBLE && v.width > 0) return v
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) findSurface(v.getChildAt(i))?.let { return it }
        return null
    }

    fun setRate(r: Float) {
        player?.rate = r
        _state.update { it.copy(rate = r) }
    }

    fun setAspect(a: Aspect) {
        _state.update { it.copy(aspect = a) }
        applyAspect(a)
    }

    private fun applyAspect(a: Aspect) {
        val mp = player ?: return
        mp.videoScale = when (a) {
            Aspect.FIT -> MediaPlayer.ScaleType.SURFACE_BEST_FIT
            Aspect.FILL -> MediaPlayer.ScaleType.SURFACE_FIT_SCREEN
            Aspect.STRETCH -> MediaPlayer.ScaleType.SURFACE_FILL
            Aspect.R16_9 -> MediaPlayer.ScaleType.SURFACE_16_9
            Aspect.R4_3 -> MediaPlayer.ScaleType.SURFACE_4_3
            Aspect.ORIGINAL -> MediaPlayer.ScaleType.SURFACE_ORIGINAL
        }
    }

    /** Free zoom for pinch: 0 = fit, otherwise a scale factor relative to the video's native size. */
    fun setZoom(scale: Float) { player?.scale = scale }

    fun selectAudio(id: Int) { player?.let { it.audioTrack = id; refreshTracks(it) } }
    fun selectSub(id: Int) { player?.let { it.spuTrack = id; refreshTracks(it) } }
    fun addSubtitle(uri: Uri) { player?.addSlave(IMedia.Slave.Type.Subtitle, uri, true) }

    fun setAudioDelayMs(ms: Long) { player?.audioDelay = ms * 1000; _state.update { it.copy(audioDelayMs = ms) } }
    fun setSubDelayMs(ms: Long) { player?.spuDelay = ms * 1000; _state.update { it.copy(subDelayMs = ms) } }

    /** [preset] index from [equalizerPresets], or custom [bands] in dB; both null turns the equalizer off. */
    fun setEqualizer(preset: Int?, bands: FloatArray?, preamp: Float? = null) {
        val mp = player ?: return
        when {
            bands != null -> {
                val eq = MediaPlayer.Equalizer.create()
                bands.forEachIndexed { i, v -> eq.setAmp(i, v) }
                preamp?.let { eq.setPreAmp(it) }
                mp.setEqualizer(eq)
            }
            preset != null -> mp.setEqualizer(MediaPlayer.Equalizer.createFromPreset(preset))
            else -> mp.setEqualizer(null)
        }
    }

    fun equalizerPresets(): List<String> = (0 until MediaPlayer.Equalizer.getPresetCount()).map { MediaPlayer.Equalizer.getPresetName(it) }
    fun equalizerBands(): List<Float> = (0 until MediaPlayer.Equalizer.getBandCount()).map { MediaPlayer.Equalizer.getBandFrequency(it) }
    fun presetAmps(preset: Int): FloatArray {
        val eq = MediaPlayer.Equalizer.createFromPreset(preset)
        return FloatArray(MediaPlayer.Equalizer.getBandCount()) { eq.getAmp(it) }
    }

    /** The player screen currently driving the engine. A closing screen may only stop playback it still owns. */
    @Volatile private var owner: Any? = null

    fun claim(token: Any) { owner = token }

    fun stopIfOwner(token: Any) { if (owner === token) stop() }

    fun stop() {
        main.removeCallbacks(stallCheck)
        pendingPlay = false
        player?.stop()
        fd?.close(); fd = null
        _state.update { it.copy(playing = false) }
    }

    private fun releasePlayer() {
        player?.let {
            it.setEventListener(null)
            if (layout != null) it.detachViews()
            it.stop()
            it.release()
        }
        libVlc?.release()
        player = null
        libVlc = null
        fd?.close(); fd = null
    }

    fun release() {
        releasePlayer()
        layout = null
        _state.value = PlayerState()
    }
}

/**
 * On-screen size of a video track: pixel aspect ([sarNum]/[sarDen]) applied, and width/height swapped when the
 * rotation metadata turns the picture sideways (libVLC orientations 4..7 are the transposed ones).
 */
fun displaySize(width: Int, height: Int, sarNum: Int, sarDen: Int, orientation: Int): Pair<Int, Int> {
    val w = if (sarNum > 0 && sarDen > 0) (width.toLong() * sarNum / sarDen).toInt() else width
    return if (orientation in 4..7) height to w else w to height
}
