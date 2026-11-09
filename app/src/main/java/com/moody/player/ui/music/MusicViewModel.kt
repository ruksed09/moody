package com.moody.player.ui.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moody.player.data.db.Playlist
import com.moody.player.data.db.PlaylistDao
import com.moody.player.data.db.FavoriteDao
import com.moody.player.data.media.AudioItem
import com.moody.player.data.media.MediaRepository
import com.moody.player.data.media.folderName
import com.moody.player.domain.NaturalOrder
import com.moody.player.player.music.MusicController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class MusicTab(val label: String) { SONGS("Songs"), ALBUMS("Albums"), ARTISTS("Artists"), FOLDERS("Folders"), PLAYLISTS("Playlists") }

data class AlbumGroup(val id: Long, val name: String, val artist: String, val songs: List<AudioItem>)
data class ArtistGroup(val name: String, val songs: List<AudioItem>, val albumCount: Int)
data class SongFolder(val path: String, val name: String, val songs: List<AudioItem>)
data class PlaylistRow(val playlist: Playlist, val count: Int)

data class MusicUiState(
    val loading: Boolean = true,
    val songs: List<AudioItem> = emptyList(),
    val albums: List<AlbumGroup> = emptyList(),
    val artists: List<ArtistGroup> = emptyList(),
    val folders: List<SongFolder> = emptyList(),
    val playlists: List<PlaylistRow> = emptyList(),
    val favorites: List<AudioItem> = emptyList(),
    val totalMs: Long = 0,
)

/** Groups the library for the Music tab's sub-tabs. Pure, so it can be tested. */
fun groupMusic(songs: List<AudioItem>): Triple<List<AlbumGroup>, List<ArtistGroup>, List<SongFolder>> {
    val albums = songs.groupBy { it.albumId to it.album }.map { (k, list) ->
        AlbumGroup(k.first, k.second, list.groupingBy { it.artist }.eachCount().maxByOrNull { it.value }?.key ?: "", list.sortedWith(compareBy<AudioItem> { it.trackNo }.thenComparator { a, b -> NaturalOrder.compare(a.title, b.title) }))
    }.sortedWith { a, b -> NaturalOrder.compare(a.name, b.name) }
    val artists = songs.groupBy { it.artist }.map { (name, list) -> ArtistGroup(name, list.sortedWith { a, b -> NaturalOrder.compare(a.title, b.title) }, list.map { it.albumId }.distinct().size) }
        .sortedWith { a, b -> NaturalOrder.compare(a.name, b.name) }
    val folders = songs.groupBy { it.relativePath }.map { (p, list) -> SongFolder(p, folderName(p), list.sortedWith { a, b -> NaturalOrder.compare(a.fileName, b.fileName) }) }
        .sortedWith { a, b -> NaturalOrder.compare(a.name, b.name) }
    return Triple(albums, artists, folders)
}

@HiltViewModel
class MusicViewModel @Inject constructor(
    private val media: MediaRepository,
    private val playlistDao: PlaylistDao,
    private val favoriteDao: FavoriteDao,
    val music: MusicController,
) : ViewModel() {
    val tab = MutableStateFlow(MusicTab.SONGS)

    val state: StateFlow<MusicUiState> = combine(
        media.audio, playlistDao.all(), playlistDao.counts(), favoriteDao.all(),
    ) { audio, playlists, counts, favs ->
        val songs = audio.orEmpty().sortedWith { a, b -> NaturalOrder.compare(a.title, b.title) }
        val (albums, artists, folders) = groupMusic(songs)
        val byUri = songs.associateBy { it.uri.toString() }
        val countMap = counts.associate { it.playlistId to it.n }
        MusicUiState(
            loading = audio == null,
            songs = songs, albums = albums, artists = artists, folders = folders,
            playlists = playlists.map { PlaylistRow(it, countMap[it.id] ?: 0) },
            favorites = favs.mapNotNull { byUri[it.audioUri] },
            totalMs = songs.sumOf { it.durationMs },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MusicUiState())

    fun playlistSongs(id: Long): Flow<List<AudioItem>> = combine(playlistDao.entries(id), state) { entries, s ->
        val byUri = s.songs.associateBy { it.uri.toString() }
        entries.mapNotNull { byUri[it.audioUri] }
    }

    fun createPlaylist(name: String, songs: List<AudioItem> = emptyList()) = viewModelScope.launch {
        val id = playlistDao.create(Playlist(name = name.trim(), createdAt = System.currentTimeMillis()))
        if (songs.isNotEmpty()) playlistDao.append(id, songs.map { it.uri.toString() })
    }
    /** A song's file was deleted: drop it from playlists, favourites and the queue, then refresh the library. */
    fun forgetDeleted(song: AudioItem) = viewModelScope.launch {
        val u = song.uri.toString()
        playlistDao.removeEverywhere(u)
        favoriteDao.remove(u)
        music.removeUri(song.uri)
        media.rescan()
    }

    fun addToPlaylist(id: Long, songs: List<AudioItem>) = viewModelScope.launch { playlistDao.append(id, songs.map { it.uri.toString() }) }
    fun renamePlaylist(id: Long, name: String) = viewModelScope.launch { playlistDao.rename(id, name.trim()) }
    fun deletePlaylist(id: Long) = viewModelScope.launch { playlistDao.delete(id) }
    fun removeFromPlaylist(id: Long, song: AudioItem) = viewModelScope.launch { playlistDao.removeEntry(id, song.uri.toString()) }
    fun reorderPlaylist(id: Long, songs: List<AudioItem>) = viewModelScope.launch { playlistDao.reorder(id, songs.map { it.uri.toString() }) }
    fun setFavorite(song: AudioItem, on: Boolean) = viewModelScope.launch {
        if (on) favoriteDao.add(com.moody.player.data.db.Favorite(song.uri.toString())) else favoriteDao.remove(song.uri.toString())
    }
    val favoriteSet: StateFlow<Set<String>> = favoriteDao.all().map { l -> l.map { it.audioUri }.toSet() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
}
