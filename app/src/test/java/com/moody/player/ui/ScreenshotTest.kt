package com.moody.player.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.moody.player.data.db.PlaybackPosition
import com.moody.player.data.media.AccessLevel
import com.moody.player.data.media.VideoItem
import com.moody.player.data.media.groupIntoFolders
import com.moody.player.design.MoodyTheme
import com.moody.player.design.ThemeMode
import com.moody.player.domain.LayoutMode
import com.moody.player.domain.SortSpec
import com.moody.player.domain.VideoView
import com.moody.player.ui.components.MainTab
import com.moody.player.ui.components.MoodyBottomBar
import com.moody.player.ui.video.ContinueItem
import com.moody.player.ui.video.VideoActions
import com.moody.player.ui.video.VideoLibraryContent
import com.moody.player.ui.video.VideoNav
import com.moody.player.ui.video.VideoUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders screens with sample data to app/build/screenshots so the design can be looked at without a device.
 * Not a pixel-diff test: it fails only if a screen crashes while composing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val out = File("build/screenshots").apply { mkdirs() }

    private fun shot(name: String, mode: ThemeMode, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MoodyTheme(mode) {
                Column(Modifier.fillMaxSize().background(MoodyTheme.colors.bg)) {
                    Box(Modifier.weight(1f)) { content() }
                    MoodyBottomBar(MainTab.VIDEO, {})
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        compose.mainClock.advanceTimeBy(100)
        val v = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(android.graphics.Canvas(bmp))
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val noop = object : VideoActions {
        override fun setQuery(q: String) {}
        override fun setView(v: VideoView) {}
        override fun setLayout(l: LayoutMode) {}
        override fun setSort(s: SortSpec) {}
        override fun refresh() {}
        override fun hideFolder(path: String) {}
    }
    private val nav = VideoNav({}, { _, _ -> }, {}, {}, {}, {}, { _, _, d -> d() })

    private val names = listOf(
        "DCIM/Camera/" to listOf("VID_20261102_181544.mp4", "VID_20261030_090112.mp4", "VID_20261021_224410.mp4"),
        "Movies/Anime/" to listOf("Mushishi S01E04 - The Sea of Writing.mkv", "Mushishi S01E05.mkv"),
        "Download/" to listOf("Night on Earth (1991).mkv"),
        "Movies/Concerts/" to listOf("Live at Montreux 2019.mp4"),
    )
    private val videos = names.flatMapIndexed { fi, (path, files) ->
        files.mapIndexed { i, n ->
            val id = fi * 10L + i
            VideoItem(Uri.parse("content://media/external/video/media/$id"), id, n, path, 180_000_000L * (i + 1) + fi * 9_000_000, 1_000L * (420 + 977 * i + 1300 * fi), 1_730_000_000L - id * 9000, 1920, 1080, "video/mp4")
        }
    }
    private val positions = mapOf(
        videos[3].uri.toString() to PlaybackPosition(videos[3].uri.toString(), 620_000, videos[3].durationMs!!, 2, false),
        videos[5].uri.toString() to PlaybackPosition(videos[5].uri.toString(), 1_400_000, videos[5].durationMs!!, 1, false),
    )
    private fun state(view: VideoView, layout: LayoutMode = LayoutMode.GRID) = VideoUiState(
        loading = false, view = view, layout = layout, all = videos, shown = videos, folders = groupIntoFolders(videos, emptySet()),
        continueWatching = listOf(ContinueItem(videos[3], positions.values.first()), ContinueItem(videos[5], positions.values.last())),
        positions = positions, totalBytes = videos.sumOf { it.sizeBytes },
    )

    private fun song(id: Long, title: String, artist: String, album: String, albumId: Long, ms: Long) = com.moody.player.data.media.AudioItem(
        Uri.parse("content://media/external/audio/media/$id"), id, title, artist, album, albumId, ms, "Music/", "audio/mpeg", 0, "$title.mp3", id,
    )
    private val songs = listOf(
        song(1, "Says", "Nils Frahm", "Spaces", 1, 498_000), song(2, "Ambre", "Nils Frahm", "Wintermusik", 2, 260_000),
        song(3, "Avril 14th", "Aphex Twin", "Drukqs", 3, 125_000), song(4, "Svefn-g-englar", "Sigur Rós", "Ágætis byrjun", 4, 604_000),
        song(5, "Teardrop", "Massive Attack", "Mezzanine", 5, 330_000), song(6, "Re: Stacks", "Bon Iver", "For Emma, Forever Ago", 6, 401_000),
    )
    private val musicNav = com.moody.player.ui.music.MusicNav({}, {}, {}, {}, {}, {})
    private fun musicState(): com.moody.player.ui.music.MusicUiState {
        val (albums, artists, folders) = com.moody.player.ui.music.groupMusic(songs)
        return com.moody.player.ui.music.MusicUiState(false, songs, albums, artists, folders, emptyList(), songs.take(2), songs.sumOf { it.durationMs })
    }

    @Test fun musicSongsDark() = shot("music-songs-dark", ThemeMode.DARK) {
        com.moody.player.ui.music.MusicContent(musicState(), com.moody.player.ui.music.MusicTab.SONGS, songs[0].uri.toString(), true, true, musicNav, {}, { _, _ -> }, {}, {}, {})
    }
    @Test fun musicAlbumsLight() = shot("music-albums-light", ThemeMode.LIGHT) {
        com.moody.player.ui.music.MusicContent(musicState(), com.moody.player.ui.music.MusicTab.ALBUMS, null, true, true, musicNav, {}, { _, _ -> }, {}, {}, {})
    }
    @Test fun musicEmptyMidnight() = shot("music-empty-midnight", ThemeMode.MIDNIGHT) {
        com.moody.player.ui.music.MusicContent(com.moody.player.ui.music.MusicUiState(loading = false), com.moody.player.ui.music.MusicTab.SONGS, null, true, true, musicNav, {}, { _, _ -> }, {}, {}, {})
    }

    private fun lock() = com.moody.player.data.vault.VaultLock(
        org.robolectric.RuntimeEnvironment.getApplication(),
        com.moody.player.data.prefs.SettingsStore(org.robolectric.RuntimeEnvironment.getApplication()),
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
    )

    @Test fun vaultSetupDark() = shot("vault-setup-dark", ThemeMode.DARK) { com.moody.player.ui.vault.VaultSetupScreen(lock(), {}) }
    @Test fun vaultUnlockLight() = shot("vault-unlock-light", ThemeMode.LIGHT) {
        com.moody.player.ui.vault.VaultUnlockScreen(lock(), com.moody.player.data.vault.VaultInfo(true, 4, "Name of your first pet"), {})
    }

    private fun bare(name: String, mode: ThemeMode, ms: Long = 1_000, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent { MoodyTheme(mode) { Box(Modifier.fillMaxSize().background(MoodyTheme.colors.bg)) { content() } } }
        compose.mainClock.advanceTimeBy(ms)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        compose.mainClock.advanceTimeBy(100)
        val v = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(android.graphics.Canvas(bmp))
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun welcomeDark() = bare("welcome-dark", ThemeMode.DARK) { com.moody.player.ui.onboarding.WelcomePager {} }
    @Test fun welcomeLight() = bare("welcome-light", ThemeMode.LIGHT) { com.moody.player.ui.onboarding.WelcomePager {} }
    @Test fun permissionsDark() = bare("permissions-dark", ThemeMode.DARK) { com.moody.player.ui.onboarding.PermissionsScreen({}) }
    @Test fun logoMid() = bare("logo-mid", ThemeMode.DARK, 700) { com.moody.player.ui.onboarding.LogoAnimation {} }
    @Test fun logoEnd() = bare("logo-end", ThemeMode.DARK, 1_700) { com.moody.player.ui.onboarding.LogoAnimation {} }

    @Test fun videoFoldersDark() = shot("video-folders-dark", ThemeMode.DARK) { VideoLibraryContent(state(VideoView.FOLDERS), noop, nav) }
    @Test fun videoFoldersLight() = shot("video-folders-light", ThemeMode.LIGHT) { VideoLibraryContent(state(VideoView.FOLDERS), noop, nav) }
    @Test fun videoAllListDark() = shot("video-all-list-dark", ThemeMode.DARK) { VideoLibraryContent(state(VideoView.ALL, LayoutMode.LIST), noop, nav) }
    @Test fun videoLoading() = shot("video-loading-dark", ThemeMode.DARK) { VideoLibraryContent(VideoUiState(), noop, nav) }
    @Test fun videoEmpty() = shot("video-empty-dark", ThemeMode.DARK) { VideoLibraryContent(VideoUiState(loading = false), noop, nav) }
    @Test fun videoNoAccessLight() = shot("video-noaccess-light", ThemeMode.LIGHT) { VideoLibraryContent(VideoUiState(loading = false, access = AccessLevel.NONE), noop, nav) }
}
