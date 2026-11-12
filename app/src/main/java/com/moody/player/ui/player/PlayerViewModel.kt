package com.moody.player.ui.player

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import com.moody.player.domain.formatClock
import com.moody.player.domain.abError
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
    private val bookmarkDao: com.moody.player.data.db.BookmarkDao,
    private val stats: com.moody.player.data.stats.PlayStats,
    @com.moody.player.di.AppScope private val appScope: kotlinx.coroutines.CoroutineScope,
) : ViewModel() {
    /** Identifies this screen to the shared engine (see [VlcEngine.claim]). */
    val token = Any()

    var isVault: Boolean by androidx.compose.runtime.mutableStateOf(handle.get<Boolean>(EXTRA_VAULT) ?: false)
        private set
    private val uris: List<Uri> = handle.get<ArrayList<Uri>>(EXTRA_QUEUE).orEmpty()
    private val titles: List<String> = handle.get<ArrayList<String>>(EXTRA_TITLES).orEmpty()

    var queue: List<QueueEntry> by androidx.compose.runtime.mutableStateOf(uris.mapIndexed { i, u -> QueueEntry(u, titles.getOrNull(i) ?: titleOf(u)) })
        private set
    private val _index = MutableStateFlow((handle.get<Int>(EXTRA_INDEX) ?: 0).coerceIn(0, (queue.size - 1).coerceAtLeast(0)))
    val index: StateFlow<Int> = _index.asStateFlow()

    val settings: StateFlow<Settings> = settingsStore.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toasts: SharedFlow<String> = _toasts.asSharedFlow()

    /** "Resumed at 12:04" chip; null when hidden. */
    /** "Resumed at" chip for one specific video: (that video's uri, position). Null when hidden. */
    private val _resumedAt = MutableStateFlow<Pair<Uri, Long>?>(null)
    val resumedAt: StateFlow<Pair<Uri, Long>?> = _resumedAt.asStateFlow()

    private val _shuffle = MutableStateFlow(false)
    val shuffle: StateFlow<Boolean> = _shuffle.asStateFlow()

    private val _loop = MutableStateFlow(LoopMode.OFF)
    val loop: StateFlow<LoopMode> = _loop.asStateFlow()
    private val _ab = MutableStateFlow<Pair<Long?, Long?>>(null to null)
    val ab: StateFlow<Pair<Long?, Long?>> = _ab.asStateFlow()
    // A-B state lives up here, before init: init's open() calls resetAb() at once, so anything it touches
    // has to exist by then (declared further down, it was still null and the player crashed on open).
    private val _abOn = MutableStateFlow(true)
    /** False while the loop is paused: A and B stay, playback runs past B. */
    val abOn: StateFlow<Boolean> = _abOn.asStateFlow()
    private var abUri: Uri? = null
    @Volatile private var scrubbing = false
    @Volatile private var abEditing = false
    private val loopGuard = com.moody.player.domain.LoopGuard()
    private val _abEdit = MutableStateFlow<Boolean?>(null)
    /** The A-B time editor is open: true for A, false for B. Kept here so it outlives the screen. */
    val abEdit: StateFlow<Boolean?> = _abEdit.asStateFlow()
    private var editFrom: Pair<Long, Boolean>? = null // position and playing when the editor opened
    private var previewJob: kotlinx.coroutines.Job? = null

    /** Sleep timer end, epoch ms; -1 = at the end of this video; null = off. */
    private val _sleepAt = MutableStateFlow<Long?>(null)
    val sleepAt: StateFlow<Long?> = _sleepAt.asStateFlow()
    private var sleepJob: Job? = null

    private var resumeChipJob: Job? = null

    /** A subtitle file Moody draws itself (Subtitle Studio), and whether it is the one showing. */
    private val _studio = MutableStateFlow<com.moody.player.domain.SubtitleDoc?>(null)
    val studio: StateFlow<com.moody.player.domain.SubtitleDoc?> = _studio.asStateFlow()
    private val _studioOn = MutableStateFlow(false)
    val studioOn: StateFlow<Boolean> = _studioOn.asStateFlow()

    /** Bookmarks of the current video, by time. Always empty in the vault. */
    private val _bookmarks = MutableStateFlow<List<com.moody.player.data.db.Bookmark>>(emptyList())
    val bookmarks: StateFlow<List<com.moody.player.data.db.Bookmark>> = _bookmarks.asStateFlow()
    private var bookmarkJob: Job? = null

    // Play time not yet written to the stats, and the clock it is measured with.
    private var statTickAt = 0L
    private var statPending = 0L
    private val sitting = com.moody.player.data.stats.SittingCounter()

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
                // libVLC may pick a built-in track by itself; while Studio draws a file, keep libVLC's off.
                if (_studioOn.value && s.selectedSub != -1 && s.subTracks.isNotEmpty()) engine.selectSub(-1)
                val now = android.os.SystemClock.elapsedRealtime()
                if (s.playing && statTickAt > 0) statPending += (now - statTickAt).coerceIn(0, 2_000)
                statTickAt = if (s.playing) now else 0L
                if (statPending >= 5_000 || (!s.playing && statPending > 0)) flushStats()
                val (a, b) = _ab.value
                if (a != null && b != null && _abOn.value && !scrubbing && !abEditing && s.uri == abUri) {
                    // After a jump back the player still reports old times for a moment: wait until it is
                    // inside the loop again (or 1.5 s), so one pass never seeks twice.
                    if (loopGuard.shouldJump(s.positionMs, a, b, android.os.SystemClock.elapsedRealtime())) loopSeek()
                }
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
        // A-B belongs to one video: a retry of the same one keeps it, another video starts without.
        if (entry.uri != abUri) resetAb()
        dismissResume()
        flushStats()
        savePosition()
        _index.value = i
        val s = settingsStore.settings.first()
        val saved = if (isVault || fromStart || !s.resume) null else positions.get(entry.uri.toString())
        // Smart resume steps back a few seconds, so the line that was cut off plays again.
        val start = saved?.let { com.moody.player.domain.smartResume(resumePosition(it.positionMs, it.durationMs), s.smartResumeSec) } ?: 0L
        val perFile = if (s.perFileSettings && !isVault) fileSettings.get(entry.uri.toString()) else null
        engine.claim(token)
        // A matching subtitle file next to the video: Studio draws it when it can read it, else libVLC does.
        val side = sidecarSubtitle(entry.uri)
        val doc = if (s.subStudio && side != null) loadSubtitle(side) else null
        _studio.value = doc
        _studioOn.value = doc != null
        engine.open(entry.uri, start, decoder ?: s.decoder, SubStyle(s.subSize, s.subBg, s.subColor), if (doc != null) null else side, noAutoSubs = doc != null)
        perFile?.let { f ->
            if (f.speed != 1f) engine.setRate(f.speed)
            launch { delay(1200); if (f.audioTrack >= 0) engine.selectAudio(f.audioTrack); if (f.subTrack >= -1) engine.selectSub(f.subTrack) }
        }
        bookmarkJob?.cancel()
        _bookmarks.value = emptyList()
        if (!isPrivate(entry.uri)) bookmarkJob = launch {
            launch { runCatching { mergeMarksFromFile(entry.uri) } }
            bookmarkDao.forUri(entry.uri.toString()).collect { _bookmarks.value = it }
        }
        if (start > 0 && saved != null) showResumeChip(entry.uri, start)
    }

    private fun showResumeChip(uri: Uri, at: Long) {
        _resumedAt.value = uri to at
        resumeChipJob?.cancel()
        resumeChipJob = viewModelScope.launch { delay(6_000); if (_resumedAt.value?.first == uri) _resumedAt.value = null }
    }

    /** Hides the chip at once: on play/pause, seeking, Start over, or switching videos. */
    fun dismissResume() {
        resumeChipJob?.cancel()
        _resumedAt.value = null
    }

    /** Start over: back to 0 and forget this video's saved position. */
    fun startOver() {
        val uri = _resumedAt.value?.first ?: current?.uri
        dismissResume()
        engine.seekTo(0)
        if (uri != null && !isVault) viewModelScope.launch { positions.clear(uri.toString()) }
    }

    fun toggleShuffle() {
        _shuffle.value = !_shuffle.value
        _toasts.tryEmit(if (_shuffle.value) "Shuffle on" else "Shuffle off")
    }

    fun setShortcuts(list: List<com.moody.player.domain.PlayerShortcut>) = viewModelScope.launch {
        settingsStore.update { it.copy(playerShortcuts = list) }
    }

    fun toggleMute() {
        val on = !engine.state.value.muted
        engine.setMuted(on)
        _toasts.tryEmit(if (on) "Muted" else "Sound on")
    }


    /** A-B toggle: on marks A at the current time (a Set B chip then appears); off clears the loop. */
    /** The A-B shortcut: first tap sets A, the second sets B and starts the loop, the third clears it. */
    fun toggleAb() {
        val (a, b) = _ab.value
        when { a == null -> markA(); b == null -> markB(); else -> clearAb() }
    }

    fun cycleLoop() = setLoop(LoopMode.entries[(_loop.value.ordinal + 1) % LoopMode.entries.size])

    /** Saves the current frame to Pictures/Moody. */
    fun screenshot() {
        engine.snapshot { bmp ->
            if (bmp == null) { _toasts.tryEmit("Couldn't take a screenshot"); return@snapshot }
            viewModelScope.launch {
                val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { saveScreenshot(bmp) }
                _toasts.tryEmit(if (ok) "Screenshot saved to Pictures/Moody" else "Couldn't save the screenshot")
            }
        }
    }

    private fun saveScreenshot(bmp: android.graphics.Bitmap): Boolean = runCatching {
        val name = "Moody_${System.currentTimeMillis()}.png"
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Moody/")
            }
            val uri = context.contentResolver.insert(android.provider.MediaStore.Images.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY), values)!!
            context.contentResolver.openOutputStream(uri)!!.use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        } else {
            @Suppress("DEPRECATION")
            val dir = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES), "Moody").apply { mkdirs() }
            val f = java.io.File(dir, name)
            f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            android.media.MediaScannerConnection.scanFile(context, arrayOf(f.absolutePath), arrayOf("image/png"), null)
        }
        true
    }.getOrDefault(false)

    /** A new video arrived while this screen is open (another app, or the library): play it here. */
    fun load(newUris: List<Uri>, index: Int, newTitles: List<String>?, vault: Boolean) {
        if (newUris.isEmpty()) return
        flushStats()
        viewModelScope.launch { savePosition() }
        isVault = vault
        queue = newUris.mapIndexed { i, u -> QueueEntry(u, newTitles?.getOrNull(i) ?: titleOf(u)) }
        open(index.coerceIn(0, queue.lastIndex), fromStart = false)
    }

    fun next() {
        if (_shuffle.value && queue.size > 1) {
            open((queue.indices - _index.value).random(), fromStart = true)
            return
        }
        if (hasNext) open(_index.value + 1) else if (_loop.value == LoopMode.FOLDER && queue.isNotEmpty()) open(0, fromStart = true)
    }
    fun prev() {
        if (engine.state.value.positionMs > 5_000 || !hasPrev) engine.seekTo(0) else open(_index.value - 1)
    }

    fun retry(decoder: Decoder? = null) = open(_index.value, decoder = decoder)

    /** Reopens the current video at the current position, e.g. after a decoder or subtitle-style change. */
    fun reopenHere(decoder: Decoder? = null, at: Long? = null) = viewModelScope.launch {
        val entry = current ?: return@launch
        val pos = at ?: engine.state.value.positionMs
        val s = settingsStore.settings.first()
        engine.claim(token)
        val studioFile = _studio.value != null
        engine.open(entry.uri, pos, decoder ?: engine.state.value.decoder, SubStyle(s.subSize, s.subBg, s.subColor), if (studioFile) null else sidecarSubtitle(entry.uri), noAutoSubs = studioFile)
    }

    /** Reads and parses a subtitle file (5 MB at most). Null when it can't be read or isn't SRT, VTT or ASS/SSA. */
    private suspend fun loadSubtitle(uri: Uri): com.moody.player.domain.SubtitleDoc? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val bytes = (if (uri.scheme == "file") File(uri.path!!).inputStream() else context.contentResolver.openInputStream(uri)!!).use { input ->
                val buf = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(16_384)
                while (true) {
                    val n = input.read(chunk)
                    if (n < 0) break
                    buf.write(chunk, 0, n)
                    if (buf.size() > 5_000_000) return@runCatching null
                }
                buf.toByteArray()
            }
            val name = uri.lastPathSegment?.substringAfterLast('/').orEmpty()
            com.moody.player.domain.parseSubtitles(com.moody.player.domain.decodeSubtitleBytes(bytes), name.ifBlank { "Subtitles" }, name.substringAfterLast('.', ""))
        }.getOrNull()
    }

    /** A file the user picked: Studio takes it when it can, libVLC otherwise (e.g. image-based subtitles). */
    fun importSubtitle(file: Uri) = viewModelScope.launch {
        val doc = if (settings.value.subStudio) loadSubtitle(file) else null
        if (doc != null) {
            _studio.value = doc
            useStudio()
            _toasts.tryEmit("Subtitles added · ${doc.cues.size} lines")
        } else {
            engine.addSubtitle(file)
            _toasts.tryEmit("Subtitles added")
        }
    }

    fun updateSubLook(f: (com.moody.player.data.prefs.Settings) -> com.moody.player.data.prefs.Settings) = viewModelScope.launch { settingsStore.update(f) }

    fun useStudio() { if (_studio.value == null) return; _studioOn.value = true; engine.selectSub(-1) }
    fun useVlcSub(id: Int) { _studioOn.value = false; engine.selectSub(id) }
    fun subsOff() { _studioOn.value = false; engine.selectSub(-1) }

    /** Jumps to a subtitle line. The timing offset is applied, so the line shows the moment the picture gets there. */
    fun seekToCue(startMs: Long) {
        dismissResume()
        engine.seekTo((startMs + engine.state.value.subDelayMs).coerceAtLeast(0))
    }

    /** Previous or next subtitle line from where the video is now. */
    fun stepLine(next: Boolean) {
        val cues = _studio.value?.cues ?: return
        val t = engine.state.value.positionMs - engine.state.value.subDelayMs
        val target = if (next) com.moody.player.domain.nextCueStart(cues, t) else com.moody.player.domain.prevCueStart(cues, t)
        if (target == null) _toasts.tryEmit(if (next) "That's the last line" else "That's the first line") else seekToCue(target)
    }

    fun updateSubStyle(size: com.moody.player.domain.SubSize? = null, bg: com.moody.player.domain.SubBg? = null, color: Long? = null) = viewModelScope.launch {
        settingsStore.update { it.copy(subSize = size ?: it.subSize, subBg = bg ?: it.subBg, subColor = color ?: it.subColor) }
        // Studio draws its own subtitles live; only libVLC's built-in tracks need the reload.
        if (!_studioOn.value) reopenHere()
    }

    private fun onEnded() {
        // B at the very end: the video ends before reaching it, so start the loop again from A.
        val (a, b) = _ab.value
        if (a != null && b != null && _abOn.value && current?.uri == abUri) {
            loopGuard.seeked(android.os.SystemClock.elapsedRealtime())
            reopenHere(at = a)
            return
        }
        viewModelScope.launch { savePosition(ended = true) }
        if (_sleepAt.value == -1L) { _sleepAt.value = null; return }
        when (_loop.value) {
            LoopMode.ONE -> open(_index.value, fromStart = true)
            LoopMode.FOLDER -> if (hasNext) open(_index.value + 1, fromStart = true) else open(0, fromStart = true)
            // Loop off: stop at the end and let the screen offer Replay (shuffle keeps going).
            LoopMode.OFF -> if (_shuffle.value && queue.size > 1) next()
        }
    }

    /** Vault videos and videos in hidden folders leave no trace in bookmarks or stats. */
    private fun isPrivate(uri: Uri): Boolean =
        isVault || media.videos.value?.firstOrNull { it.uri == uri }?.relativePath?.let { it in settings.value.hiddenFolders } == true

    /**
     * Marks the current moment, or, while an A-B repeat runs, saves that range. Bookmarks are not kept for
     * private videos. Name them afterwards in Bookmarks.
     */
    fun addBookmark() {
        val e = current ?: return
        if (isPrivate(e.uri)) { _toasts.tryEmit(if (isVault) "Bookmarks are off for vault videos" else "Bookmarks are off for hidden folders"); return }
        val (a, b) = _ab.value
        val range = a != null && b != null
        val pos = if (range) a!! else engine.state.value.positionMs
        val end = if (range) b else null
        val dup = _bookmarks.value.any {
            if (range) it.endMs != null && kotlin.math.abs(it.positionMs - pos) < 500 && kotlin.math.abs(it.endMs - end!!) < 500
            else it.endMs == null && kotlin.math.abs(it.positionMs - pos) < 1_500
        }
        if (dup) { _toasts.tryEmit(if (range) "This range is already bookmarked" else "Already bookmarked here"); return }
        if (_bookmarks.value.size >= MAX_BOOKMARKS) { _toasts.tryEmit("Up to $MAX_BOOKMARKS bookmarks per video. Delete one to add more"); return }
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            bookmarkDao.add(com.moody.player.data.db.Bookmark(
                uri = e.uri.toString(), positionMs = pos, endMs = end, createdAt = now, updatedAt = now, uid = java.util.UUID.randomUUID().toString(),
            ))
            marksChanged()
        }
        _toasts.tryEmit("Bookmarked ${com.moody.player.domain.bookmarkLabel(pos, end)}. Name it in Bookmarks")
    }

    fun removeBookmark(b: com.moody.player.data.db.Bookmark) = viewModelScope.launch { bookmarkDao.remove(b.id, System.currentTimeMillis()); marksChanged() }

    /** Saves a name typed for [b]; only the allowed characters are kept. */
    fun renameBookmark(b: com.moody.player.data.db.Bookmark, raw: String) = viewModelScope.launch {
        bookmarkDao.rename(b.id, com.moody.player.domain.BookmarkNames.clean(raw), System.currentTimeMillis())
        marksChanged()
    }

    /** Jumps to a moment, or replays a saved range as an A-B repeat. */
    fun jumpTo(b: com.moody.player.data.db.Bookmark) {
        dismissResume()
        val end = b.endMs
        if (end != null && end > b.positionMs) {
            _ab.value = b.positionMs to end
            abUri = current?.uri; _abOn.value = true
            _toasts.tryEmit("Repeating ${com.moody.player.domain.bookmarkLabel(b.positionMs, end)}")
        }
        guardedSeek(b.positionMs)
        if (!engine.state.value.playing) engine.play()
    }

    // ---- Bookmarks inside the video file (MP4, MOV, 3GP) ----

    private val _fileWrite = MutableStateFlow<Uri?>(null)
    /** A video whose bookmarks should now be saved into the file; the screen gets Android's permission first. */
    val fileWrite: StateFlow<Uri?> = _fileWrite.asStateFlow()
    private val _consent29 = MutableStateFlow<android.content.IntentSender?>(null)
    /** Android 10 only: the prompt to allow changing one file. */
    val consent29: StateFlow<android.content.IntentSender?> = _consent29.asStateFlow()
    private val declined: MutableSet<Uri> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    private val unsupported: MutableSet<Uri> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    private var marksJob: kotlinx.coroutines.Job? = null

    private fun canCarry(uri: Uri) = !isPrivate(uri) && uri.scheme == "content" && uri !in declined && uri !in unsupported

    /** Called after any bookmark change of the current video: save into the file a moment later. */
    private fun marksChanged() {
        val u = current?.uri ?: return
        if (!canCarry(u)) return
        marksJob?.cancel()
        marksJob = viewModelScope.launch { delay(1_500); _fileWrite.value = u }
    }

    /** Android's answer to "allow Moody to modify this video?". */
    fun onFileConsent(ok: Boolean) {
        val u = _fileWrite.value ?: return
        _consent29.value = null
        if (ok) saveMarksToFile(u, asked = true)
        else { _fileWrite.value = null; declined += u; _toasts.tryEmit("Bookmarks kept in Moody only") }
    }

    /** Writes this video's bookmarks into its file. Runs to the end even if the player closes. */
    fun saveMarksToFile(uri: Uri, asked: Boolean = false) {
        appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val rows = bookmarkDao.allForUri(uri.toString()).map { b ->
                if (b.uid.isEmpty()) b.copy(uid = java.util.UUID.randomUUID().toString(), updatedAt = maxOf(b.updatedAt, b.createdAt)).also { bookmarkDao.update(it) } else b
            }
            // Live bookmarks first, then the newest removals: the file holds a fixed number, so old removals go first.
            val ordered = rows.filter { !it.deleted }.sortedBy { it.positionMs } + rows.filter { it.deleted }.sortedByDescending { it.updatedAt }
            val marks = ordered.map { com.moody.player.data.media.FileMark(it.uid, it.positionMs, it.endMs, it.name, it.updatedAt, it.deleted) }
            val result = try {
                com.moody.player.data.media.MarksInFile.write(context, uri, marks)
            } catch (e: android.app.RecoverableSecurityException) {
                _consent29.value = e.userAction.actionIntent.intentSender
                return@launch
            } catch (e: SecurityException) {
                com.moody.player.data.media.MarksWrite.Failed("Moody isn't allowed to change this video")
            } catch (e: Exception) {
                com.moody.player.data.media.MarksWrite.Failed("Saving into the file failed")
            }
            if (_fileWrite.value == uri) _fileWrite.value = null
            when (result) {
                com.moody.player.data.media.MarksWrite.Done -> if (asked) _toasts.tryEmit("Bookmarks saved into the video")
                com.moody.player.data.media.MarksWrite.NotSupported -> {
                    unsupported += uri
                    _toasts.tryEmit("This video type can't carry bookmarks. They stay in Moody")
                }
                is com.moody.player.data.media.MarksWrite.Failed -> _toasts.tryEmit("${result.why}. Bookmarks stay in Moody")
            }
        }
    }

    /** Reads bookmarks stored in the file (someone may have sent it) and adds the ones Moody doesn't have. */
    private suspend fun mergeMarksFromFile(uri: Uri) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (isPrivate(uri) || uri.scheme != "content") return@withContext
        val inFile = com.moody.player.data.media.MarksInFile.read(context, uri) ?: return@withContext
        val known = bookmarkDao.allForUri(uri.toString()).filter { it.uid.isNotEmpty() }.associateBy { it.uid }
        var changed = false
        for (m in inFile) {
            val have = known[m.uid]
            if (have == null) {
                if (m.deleted) continue
                bookmarkDao.add(com.moody.player.data.db.Bookmark(
                    uri = uri.toString(), positionMs = m.startMs, endMs = m.endMs, name = m.name, uid = m.uid,
                    createdAt = m.updatedAt.takeIf { it > 0 } ?: System.currentTimeMillis(), updatedAt = m.updatedAt,
                ))
                changed = true
            } else if (m.updatedAt > have.updatedAt) {
                bookmarkDao.update(have.copy(positionMs = m.startMs, endMs = m.endMs, name = m.name, deleted = m.deleted, updatedAt = m.updatedAt))
                changed = true
            }
        }
        if (changed) _toasts.tryEmit("Bookmarks found in this video")
    }

    /** Writes play time gathered so far. Vault videos and links are never counted. */
    private fun flushStats() {
        val ms = statPending
        statPending = 0
        val e = current ?: return
        if (ms <= 0 || isPrivate(e.uri) || (e.uri.scheme != "content" && e.uri.scheme != "file")) return
        val counts = sitting.add(e.uri.toString(), ms)
        appScope.launch { stats.record(e.uri.toString(), com.moody.player.domain.StatKind.VIDEO, e.title, ms, counts) }
    }

    fun setLoop(m: LoopMode) {
        _loop.value = m
        _toasts.tryEmit(if (m == LoopMode.OFF) "Loop off" else "Loop: ${m.label.lowercase()}")
    }

    // ---- A-B repeat ----
    // Its state is declared above init: open() runs resetAb() while the view model is still being built.


    /** The seek bar is being dragged: the loop waits, so it doesn't fight the finger. */
    fun setScrubbing(v: Boolean) { scrubbing = v }

    private fun resetAb() {
        _ab.value = null to null; _abOn.value = true; abUri = null; loopGuard.reset()
        if (abEditing) { abEditing = false; _abEdit.value = null; editFrom = null; previewJob?.cancel() }
    }

    /** Seeks and tells the loop to ignore the old times still on their way, so it never seeks twice. */
    private fun guardedSeek(ms: Long) {
        loopGuard.seeked(android.os.SystemClock.elapsedRealtime())
        engine.seekTo(ms)
    }

    /** The one place the loop jumps back to A. */
    private fun loopSeek() { _ab.value.first?.let(::guardedSeek) }

    fun markA() {
        val st = engine.state.value
        val pos = st.positionMs
        abError(true, pos, null, null, st.durationMs)?.let { _toasts.tryEmit(it); return }
        _ab.value = pos to null; abUri = current?.uri; _abOn.value = true; loopGuard.reset()
        _toasts.tryEmit("A set at ${formatClock(pos)}. Tap again for B")
    }

    fun markB() {
        val a = _ab.value.first ?: return
        val st = engine.state.value
        val pos = st.positionMs
        abError(false, pos, a, null, st.durationMs)?.let { _toasts.tryEmit(it); return }
        _ab.value = a to pos; _abOn.value = true
        _toasts.tryEmit("Repeating ${formatClock(a)} to ${formatClock(pos)}")
        loopSeek()
    }

    fun clearAb() { resetAb(); _toasts.tryEmit("A-B repeat off") }

    /** Pauses or resumes the loop; A and B stay. */
    fun toggleAbOn() {
        val (a, b) = _ab.value
        if (a == null || b == null) return
        val on = !_abOn.value
        _abOn.value = on
        _toasts.tryEmit(if (on) "A-B repeat on" else "A-B repeat paused")
        if (on) { loopGuard.reset(); val p = engine.state.value.positionMs; if (p < a || p >= b) loopSeek() }
    }

    /** Opens the A or B editor: playback pauses and the loop waits until Save or Cancel. */
    fun beginAbEdit(editingA: Boolean) {
        if (abEditing) return
        val st = engine.state.value
        editFrom = st.positionMs to st.playing
        abEditing = true
        _abEdit.value = editingA
        if (st.playing) engine.pause()
    }

    /** Where the video was when the editor opened ("Use current time"). */
    fun abEditOrigin(): Long = editFrom?.first ?: engine.state.value.positionMs

    /** Shows the frame at [ms] while editing; quick changes only seek once, to the last. */
    fun previewAb(ms: Long) {
        previewJob?.cancel()
        previewJob = viewModelScope.launch { delay(150); engine.seekTo(ms) }
    }

    private fun endAbEdit(seek: Long?) {
        previewJob?.cancel()
        val from = editFrom
        abEditing = false; _abEdit.value = null; editFrom = null
        val (a, b) = _ab.value
        when {
            seek != null -> guardedSeek(seek)
            a != null && b != null && _abOn.value && from != null && (from.first < a || from.first >= b) -> loopSeek()
            from != null -> guardedSeek(from.first)
        }
        if (from?.second == true) engine.play()
    }

    fun cancelAbEdit() { if (abEditing) endAbEdit(null) }

    /** Leaves the editor at A. */
    fun abEditGoToA() { val a = _ab.value.first ?: return; endAbEdit(a) }

    /** Saves a new A or B. Gives the reason when it can't be used (the editor stays open). */
    fun saveAb(editingA: Boolean, ms: Long): String? {
        val (a, b) = _ab.value
        abError(editingA, ms, a, b, engine.state.value.durationMs)?.let { return it }
        _ab.value = if (editingA) ms to b else (a ?: return "Set A first") to ms
        abUri = current?.uri
        if (!editingA || b != null) _abOn.value = true
        endAbEdit(null)
        val (na, nb) = _ab.value
        _toasts.tryEmit(if (nb != null) "Repeating ${formatClock(na!!)} to ${formatClock(nb)}" else "A set at ${formatClock(na!!)}")
        return null
    }

    /** Jumps back to A. */
    fun seekToA() { _ab.value.first?.let { dismissResume(); engine.seekTo(it) } }

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
        flushStats()
        // The engine outlives this screen only when the background service has taken it.
        if (!VideoBackgroundState.active) engine.stopIfOwner(token)
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
