package com.moody.player.ui.video

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moody.player.data.db.PlaybackPosition
import com.moody.player.data.db.PositionDao
import com.moody.player.data.media.AccessLevel
import com.moody.player.data.media.Folder
import com.moody.player.data.media.MediaRepository
import com.moody.player.data.media.VideoItem
import com.moody.player.data.media.groupIntoFolders
import com.moody.player.data.prefs.Settings
import com.moody.player.data.prefs.SettingsStore
import com.moody.player.domain.LayoutMode
import com.moody.player.domain.SortSpec
import com.moody.player.domain.VideoView
import com.moody.player.domain.sortedBy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ContinueItem(val video: VideoItem, val position: PlaybackPosition)

data class VideoUiState(
    val loading: Boolean = true,
    val access: AccessLevel = AccessLevel.FULL,
    val view: VideoView = VideoView.FOLDERS,
    val layout: LayoutMode = LayoutMode.GRID,
    val sort: SortSpec = Settings().sort,
    val query: String = "",
    val all: List<VideoItem> = emptyList(),
    val shown: List<VideoItem> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val continueWatching: List<ContinueItem> = emptyList(),
    val positions: Map<String, PlaybackPosition> = emptyMap(),
    val totalBytes: Long = 0,
    val disguiseVault: Boolean = false,
    val backgrounds: Boolean = true,
    val refreshing: Boolean = false,
)

@HiltViewModel
class VideoViewModel @Inject constructor(
    private val repo: MediaRepository,
    private val settingsStore: SettingsStore,
    positionDao: PositionDao,
) : ViewModel(), VideoActions {
    private val query = MutableStateFlow("")

    val state: StateFlow<VideoUiState> = combine(
        repo.videos, settingsStore.settings, positionDao.all(), query, combine(repo.access, repo.scanning) { a, s -> a to s },
    ) { videos, s, positions, q, (access, scanning) ->
        val list = videos.orEmpty().filter { it.relativePath !in s.hiddenFolders }
        val posMap = positions.associateBy { it.uri }
        val sorted = list.sortedBy(s.sort, { it.name }, { it.dateAdded }, { it.sizeBytes }, { it.durationMs ?: 0 })
        val shown = if (q.isBlank()) sorted else sorted.filter { it.name.contains(q.trim(), ignoreCase = true) }
        val byUri = list.associateBy { it.uri.toString() }
        VideoUiState(
            loading = videos == null,
            access = access,
            view = s.videoView,
            layout = s.videoLayout,
            sort = s.sort,
            query = q,
            all = list,
            shown = shown,
            folders = groupIntoFolders(list, s.hiddenFolders),
            continueWatching = positions
                .filter { !it.watched && it.positionMs > 5_000 }
                .sortedByDescending { it.updatedAt }
                .mapNotNull { p -> byUri[p.uri]?.let { ContinueItem(it, p) } }
                .take(12),
            positions = posMap,
            totalBytes = list.sumOf { it.sizeBytes },
            disguiseVault = s.disguiseVault,
            backgrounds = s.backgrounds,
            refreshing = scanning && videos != null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VideoUiState())

    override fun setQuery(q: String) { query.value = q }
    override fun setView(v: VideoView) { viewModelScope.launch { settingsStore.update { it.copy(videoView = v) } } }
    override fun setLayout(l: LayoutMode) { viewModelScope.launch { settingsStore.update { it.copy(videoLayout = l) } } }
    override fun setSort(s: SortSpec) { viewModelScope.launch { settingsStore.update { it.copy(sort = s) } } }
    override fun refresh() { viewModelScope.launch { repo.rescan() } }
    fun refreshAccess() = repo.refreshAccess()
    fun hideFolder(path: String) = viewModelScope.launch { settingsStore.update { it.copy(hiddenFolders = it.hiddenFolders + path) } }

    fun inFolder(path: String): List<VideoItem> = state.value.all.filter { it.relativePath == path }
        .sortedBy(state.value.sort, { it.name }, { it.dateAdded }, { it.sizeBytes }, { it.durationMs ?: 0 })

    fun find(uri: Uri) = repo.video(uri)
}
