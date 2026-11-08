package com.moody.player.ui.player

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moody.player.data.db.FileSettings
import com.moody.player.data.db.FileSettingsDao
import com.moody.player.data.db.PlaybackPosition
import com.moody.player.data.db.PositionDao
import com.moody.player.data.media.MediaRepository
import com.moody.player.data.prefs.Settings
import com.moody.player.data.prefs.SettingsStore
import com.moody.player.domain.Decoder
import com.moody.player.domain.LoopMode
import com.moody.player.domain.formatDuration
import com.moody.player.domain.isWatched
import com.moody.player.domain.resumePosition
import com.moody.player.player.video.EngineEvent
import com.moody.player.player.video.SubStyle
import com.moody.player.player.video.VlcEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class QueueEntry(val uri: Uri, val title: String)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    handle: SavedStateHandle,
    val engine: VlcEngine,
    private val positions: PositionDao,
    private val fileSettings: FileSettingsDao,
    private val settingsStore: SettingsStore,
    private val media: MediaRepository,
) : ViewModel() {
    val isVault: Boolean = handle.get<Boolean>(EXTRA_VAULT) ?: false
    private val uris: List<Uri> = handle.get<ArrayList<Uri>>(EXTRA_QUEUE).orEmpty()
    private val titles: List<String> = handle.get<ArrayList<String>>(EXTRA_TITLES).orEmpty()

    val queue: List<QueueEntry> = uris.mapIndexed { i, u -> QueueEntry(u, titles.getOrNull(i) ?: titleOf(u)) }
    private val _index = MutableStateFlow((handle.get<Int>(EXTRA_INDEX) ?: 0).coerceIn(0, (queue.size - 1).coerceAtLeast(0)))
    val index: StateFlow<Int> = _index.asStateFlow()

    val settings: StateFlow<Settings> = settingsStore.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toasts: SharedFlow<String> = _toasts.asSharedFlow()

    /** "Resumed at 12:04" chip; null when hidden. */
    private val _resumedAt = MutableStateFlow<Long?>(null)
    val resumedAt: StateFlow<Long?> = _resumedAt.asStateFlow()

    private val _loop = MutableStateFlow(LoopMode.OFF)
    val loop: StateFlow<LoopMode> = _loop.asStateFlow()
    private val _ab = MutableStateFlow<Pair<Long?, Long?>>(null to null)
    val ab: StateFlow<Pair<Long?, Long?>> = _ab.asStateFlow()

    /** Sleep timer end, epoch ms; -1 = at the end of this video; null = off. */
    private val _sleepAt = MutableStateFlow<Long?>(null)
    val sleepAt: StateFlow<Long?> = _sleepAt.asStateFlow()
    private var sleepJob: Job? = null

    private var resumeChipJob: Job? = null

    init {
        viewModelScope.launch {
            engine.events.collect { e ->
                when (e) {
                    EngineEvent.SwitchedToSoftware -> _toasts.tryEmit("Switched to software decoding")
                    EngineEvent.Ended -> onEnded()
                }
            }
        }
        // Save progress every 5 s while playing; enforce A-B repeat.
        viewModelScope.launch {
            var lastSave = 0L
            engine.state.collect { s ->
                val (a, b) = _ab.value
                if (a != null && b != null && s.positionMs >= b) engine.seekTo(a)
                if (s.playing && System.currentTimeMillis() - lastSave > 5_000) {
                    lastSave = System.currentTimeMillis()
                    savePosition()
                }
            }
        }
        if (queue.isNotEmpty()) open(_index.value)
    }

    val current: QueueEntry? get() = queue.getOrNull(_index.value)
    val hasNext: Boolean get() = _index.value < queue.lastIndex
    val hasPrev: Boolean get() = _index.value > 0

    fun open(i: Int, fromStart: Boolean = false, decoder: Decoder? = null) = viewModelScope.launch {
        val entry = queue.getOrNull(i) ?: return@launch
        savePosition()
        _index.value = i
        val s = settingsStore.settings.first()
        val saved = if (isVault || fromStart || !s.resume) null else positions.get(entry.uri.toString())
        val start = saved?.let { resumePosition(it.positionMs, it.durationMs) } ?: 0L
        val perFile = if (s.perFileSettings && !isVault) fileSettings.get(entry.uri.toString()) else null
        engine.open(entry.uri, start, decoder ?: s.decoder, SubStyle(s.subSize, s.subBg, s.subColor), sidecarSubtitle(entry.uri))
        perFile?.let { f ->
            if (f.speed != 1f) engine.setRate(f.speed)
            launch { delay(1200); if (f.audioTrack >= 0) engine.selectAudio(f.audioTrack); if (f.subTrack >= -1) engine.selectSub(f.subTrack) }
        }
        _ab.value = null to null
        if (start > 0) showResumeChip(start)
    }

    private fun showResumeChip(at: Long) {
        _resumedAt.value = at
        resumeChipJob?.cancel()
        resumeChipJob = viewModelScope.launch { delay(6_000); _resumedAt.value = null }
    }

    fun startOver() {
        _resumedAt.value = null
        engine.seekTo(0)
    }

    fun next() { if (hasNext) open(_index.value + 1) else if (_loop.value == LoopMode.FOLDER && queue.isNotEmpty()) open(0, fromStart = true) }
    fun prev() {
        if (engine.state.value.positionMs > 5_000 || !hasPrev) engine.seekTo(0) else open(_index.value - 1)
    }

    fun retry(decoder: Decoder? = null) = open(_index.value, decoder = decoder)

    /** Reopens the current video at the current position, e.g. after a decoder or subtitle-style change. */
    fun reopenHere(decoder: Decoder? = null) = viewModelScope.launch {
        val entry = current ?: return@launch
        val pos = engine.state.value.positionMs
        val s = settingsStore.settings.first()
        engine.open(entry.uri, pos, decoder ?: engine.state.value.decoder, SubStyle(s.subSize, s.subBg, s.subColor), sidecarSubtitle(entry.uri))
    }

    fun updateSubStyle(size: com.moody.player.domain.SubSize? = null, bg: com.moody.player.domain.SubBg? = null, color: Long? = null) = viewModelScope.launch {
        settingsStore.update { it.copy(subSize = size ?: it.subSize, subBg = bg ?: it.subBg, subColor = color ?: it.subColor) }
        reopenHere()
    }

    private fun onEnded() {
        viewModelScope.launch { savePosition(ended = true) }
        if (_sleepAt.value == -1L) { _sleepAt.value = null; return }
        when (_loop.value) {
            LoopMode.ONE -> open(_index.value, fromStart = true)
            LoopMode.FOLDER -> if (hasNext) open(_index.value + 1, fromStart = true) else open(0, fromStart = true)
            LoopMode.OFF -> if (hasNext) open(_index.value + 1, fromStart = true)
        }
    }

    fun setLoop(m: LoopMode) {
        _loop.value = m
        _toasts.tryEmit(if (m == LoopMode.OFF) "Loop off" else "Loop: ${m.label.lowercase()}")
    }

    fun markA() {
        val pos = engine.state.value.positionMs
        _ab.value = pos to null
        _toasts.tryEmit("A set at ${formatDuration(pos)}")
    }

    fun markB() {
        val a = _ab.value.first ?: return
        val pos = engine.state.value.positionMs
        if (pos <= a + 500) { _toasts.tryEmit("B must come after A"); return }
        _ab.value = a to pos
        _toasts.tryEmit("Repeating ${formatDuration(a)} to ${formatDuration(pos)}")
        engine.seekTo(a)
    }

    fun clearAb() { _ab.value = null to null; _toasts.tryEmit("A-B repeat off") }

    fun sleepIn(minutes: Int?) {
        sleepJob?.cancel()
        when {
            minutes == null -> { _sleepAt.value = null; _toasts.tryEmit("Sleep timer off") }
            minutes < 0 -> { _sleepAt.value = -1L; _toasts.tryEmit("Stops at the end of this video") }
            else -> {
                val at = System.currentTimeMillis() + minutes * 60_000L
                _sleepAt.value = at
                _toasts.tryEmit("Stops in $minutes min")
                sleepJob = viewModelScope.launch { delay(minutes * 60_000L); engine.pause(); _sleepAt.value = null }
            }
        }
    }

    fun toast(text: String) { _toasts.tryEmit(text) }

    fun rememberFileSettings() = viewModelScope.launch {
        val s = settings.value
        val e = current ?: return@launch
        if (!s.perFileSettings || isVault) return@launch
        val st = engine.state.value
        fileSettings.save(FileSettings(e.uri.toString(), st.rate, st.selectedAudio, st.selectedSub))
    }

    suspend fun savePosition(ended: Boolean = false) {
        if (isVault) return
        val e = current ?: return
        val st = engine.state.value
        if (st.uri != e.uri || st.durationMs <= 0) return
        val pos = if (ended) st.durationMs else st.positionMs
        positions.save(PlaybackPosition(e.uri.toString(), pos, st.durationMs, System.currentTimeMillis(), ended || isWatched(pos, st.durationMs)))
    }

    fun onPauseScreen() = viewModelScope.launch { savePosition(); rememberFileSettings() }

    private fun titleOf(uri: Uri): String {
        media.video(uri)?.let { return it.title }
        if (uri.scheme == "content") {
            runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) return c.getString(0).substringBeforeLast('.')
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "Video"
    }

    /** A .srt/.ass/.ssa/.vtt with the same name next to the video, when the file system lets Moody see it. */
    @Suppress("DEPRECATION")
    private fun sidecarSubtitle(uri: Uri): Uri? {
        val path: String = when (uri.scheme) {
            "file" -> uri.path
            "content" -> runCatching {
                context.contentResolver.query(uri, arrayOf(android.provider.MediaStore.MediaColumns.DATA), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            }.getOrNull()
            else -> null
        } ?: return null
        val base = path.substringBeforeLast('.')
        return listOf("srt", "ass", "ssa", "vtt").map { File("$base.$it") }.firstOrNull { it.canRead() }?.let { Uri.fromFile(it) }
    }

    override fun onCleared() {
        // The engine outlives this screen only when the background service has taken it.
        if (!VideoBackgroundState.active) engine.stop()
    }

    companion object {
        const val EXTRA_QUEUE = "queue"
        const val EXTRA_INDEX = "index"
        const val EXTRA_VAULT = "vault"
        const val EXTRA_TITLES = "titles"
    }
}

/** Set by the background service while it owns playback. */
object VideoBackgroundState {
    @Volatile var active: Boolean = false
}
