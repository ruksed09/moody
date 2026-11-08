package com.moody.player.data.media

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.moody.player.data.db.VaultDao
import com.moody.player.data.prefs.SettingsStore
import com.moody.player.di.AppScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** The library. `null` in a flow means "not scanned yet" so screens can show skeletons. */
@Singleton
class MediaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsStore,
    private val vaultDao: VaultDao,
    @AppScope private val scope: CoroutineScope,
) {
    private val scanner = MediaScanner(context)
    private val _videos = MutableStateFlow<List<VideoItem>?>(null)
    private val _audio = MutableStateFlow<List<AudioItem>?>(null)
    private val _access = MutableStateFlow(accessLevel(context))
    private val _scanning = MutableStateFlow(false)
    val videos: StateFlow<List<VideoItem>?> = _videos.asStateFlow()
    val audio: StateFlow<List<AudioItem>?> = _audio.asStateFlow()
    val access: StateFlow<AccessLevel> = _access.asStateFlow()
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val mutex = Mutex()

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) { changes.tryEmit(Unit) }
    }

    init {
        context.contentResolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
        context.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
        @OptIn(FlowPreview::class)
        scope.launch { changes.debounce(500).collect { rescan() } }
        scope.launch { settings.settings.map { it.minSongSec }.distinctUntilChanged().collect { rescan() } }
    }

    fun refreshAccess() {
        _access.value = accessLevel(context)
        scope.launch { rescan() }
    }

    suspend fun rescan() = mutex.withLock {
        _scanning.value = true
        try {
            _access.value = accessLevel(context)
            val minSong = settings.settings.first().minSongSec * 1000L
            val vaulted = vaultDao.sourceUris().first().toSet()
            withContext(Dispatchers.IO) {
                _videos.value = if (_access.value == AccessLevel.NONE) emptyList() else scanner.videos().filter { it.uri.toString() !in vaulted }
                _audio.value = if (hasAudioAccess(context)) scanner.audio(minSong) else emptyList()
            }
        } finally {
            _scanning.value = false
        }
    }

    fun video(uri: Uri): VideoItem? = _videos.value?.firstOrNull { it.uri == uri }
}
