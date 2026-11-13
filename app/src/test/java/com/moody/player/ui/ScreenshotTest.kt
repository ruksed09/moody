package com.moody.player.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val out = File("build/screenshots").apply { mkdirs() }

    private fun shot(name: String, mode: ThemeMode, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MoodyTheme(mode) {
                Box(Modifier.fillMaxSize().background(MoodyTheme.colors.bg)) {
                    androidx.compose.runtime.CompositionLocalProvider(com.moody.player.ui.components.LocalBottomInset provides 84.dp) { content() }
                    MoodyBottomBar(MainTab.VIDEO, {}, Modifier.align(androidx.compose.ui.Alignment.BottomCenter))
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
        override fun setFolderSort(s: SortSpec) {}
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
    private val musicNav = com.moody.player.ui.music.MusicNav({ _, _ -> }, {}, {}, {}, {}, {})
    private fun musicState(): com.moody.player.ui.music.MusicUiState {
        val (albums, artists, folders) = com.moody.player.ui.music.groupMusic(songs)
        return com.moody.player.ui.music.MusicUiState(false, songs, albums, artists, folders, emptyList(), songs.take(2), songs.sumOf { it.durationMs })
    }

    @Test fun musicSongsDark() = shot("music-songs-dark", ThemeMode.DARK) {
        val mixes = listOf(
            com.moody.player.domain.MoodMix("now", "Evening picks", "What you usually play around this time", songs),
            com.moody.player.domain.MoodMix("loved", "Loved", "Your favourites, shuffled", songs.reversed(), shuffle = true),
            com.moody.player.domain.MoodMix("focus", "Focus", "Long tracks, 5 minutes and up", songs.take(3)),
        )
        com.moody.player.ui.music.MusicContent(musicState().copy(mixes = mixes), com.moody.player.ui.music.MusicTab.SONGS, songs[0].uri.toString(), true, true, musicNav, {}, { _, _ -> }, {}, {}, {}, onSort = {})
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

    private fun statsUi(range: com.moody.player.ui.stats.StatsRange, empty: Boolean = false): com.moody.player.ui.stats.StatsUi {
        val today = java.time.LocalDate.of(2026, 11, 11).toEpochDay()
        val rows = if (empty) emptyList() else (0 until range.days).flatMap { d ->
            val day = today - d
            listOf(
                com.moody.player.domain.StatRow(day, 21, "content://v/${d % 3}", com.moody.player.domain.StatKind.VIDEO, "Sample video ${d % 3 + 1}", ((d * 37) % 90 + 5) * 60_000L, 1),
                com.moody.player.domain.StatRow(day, 8, "content://a/${d % 4}", com.moody.player.domain.StatKind.MUSIC, "Sample song ${d % 4 + 1}", ((d * 53) % 70) * 60_000L, 2),
            )
        }
        val visible = (0..3).flatMap { listOf("content://v/$it", "content://a/$it") }.toSet()
        return com.moody.player.ui.stats.StatsUi(range, com.moody.player.domain.summarize(rows, today - range.days + 1, today, visible))
    }
    @Test fun statsWeekDark() = bare("stats-week-dark", ThemeMode.DARK) { com.moody.player.ui.stats.StatsContent(statsUi(com.moody.player.ui.stats.StatsRange.WEEK), {}, {}, {}, {}) }
    @Test fun statsMonthLight() = bare("stats-month-light", ThemeMode.LIGHT) { com.moody.player.ui.stats.StatsContent(statsUi(com.moody.player.ui.stats.StatsRange.MONTH), {}, {}, {}, {}) }
    @Test fun statsEmptyMidnight() = bare("stats-empty-midnight", ThemeMode.MIDNIGHT) { com.moody.player.ui.stats.StatsContent(statsUi(com.moody.player.ui.stats.StatsRange.WEEK, empty = true), {}, {}, {}, {}) }

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


    /** Discs at the sizes Moody uses, with a cover (a made-up gradient) and without one. */
    @Composable private fun discs() {
        val art = File(out, "disc-art.jpg").also { f ->
            val b = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
            val cv = android.graphics.Canvas(b)
            val p = android.graphics.Paint().apply { shader = android.graphics.LinearGradient(0f, 0f, 256f, 256f, 0xFFE8B04A.toInt(), 0xFF2A5D8F.toInt(), android.graphics.Shader.TileMode.CLAMP) }
            cv.drawRect(0f, 0f, 256f, 256f, p)
            f.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        }
        val plain = songs[0]
        val covered = songs[1].copy(cover = art.path)
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)) {
            for (item in listOf(plain, covered)) {
                androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    listOf(36, 44, 96, 140).forEach { com.moody.player.ui.music.Disc(item, it.dp) }
                }
            }
            com.moody.player.ui.music.Disc(covered, 300.dp)
        }
    }
    @Test fun seekMarks() = bare("seek-marks", ThemeMode.DARK) {
        androidx.compose.foundation.layout.Box(
            androidx.compose.ui.Modifier.background(androidx.compose.ui.graphics.Color(0xFF202428)).padding(24.dp),
        ) {
            androidx.compose.foundation.layout.Column {
                // A short video: two moments close together, overlapping ranges, the middle one playing.
                com.moody.player.ui.player.SeekBar(
                    positionMs = 140_000, durationMs = 400_000, onSeek = {}, onScrubbing = {},
                    marks = listOf(
                        com.moody.player.ui.player.SeekMark(40_000, null, "Intro"),
                        com.moody.player.ui.player.SeekMark(43_000, null, "Title"),
                        com.moody.player.ui.player.SeekMark(90_000, 170_000, "A"),
                        com.moody.player.ui.player.SeekMark(120_000, 200_000, "B"),
                        com.moody.player.ui.player.SeekMark(130_000, 150_000, "C"),
                        com.moody.player.ui.player.SeekMark(250_000, null, "Drop"),
                        com.moody.player.ui.player.SeekMark(300_000, 360_000, "Outro"),
                    ),
                )
                androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.size(24.dp))
                // Two hours, the ranges from the question: 1:12:23-1:13:45 and 1:12:56-1:15:34.
                com.moody.player.ui.player.SeekBar(
                    positionMs = 4_400_000, durationMs = 7_200_000, onSeek = {}, onScrubbing = {},
                    marks = listOf(
                        com.moody.player.ui.player.SeekMark(4_343_000, 4_425_000, "A"),
                        com.moody.player.ui.player.SeekMark(4_376_000, 4_534_000, "B"),
                    ),
                )
            }
        }
    }
    @Config(qualifiers = "w320dp-h640dp-xhdpi")
    @Test fun abPills() = bare("ab-pills-320", ThemeMode.DARK) {
        androidx.compose.foundation.layout.Column(
            androidx.compose.ui.Modifier.background(androidx.compose.ui.graphics.Color(0xFF202428)).padding(8.dp),
        ) {
            com.moody.player.ui.player.AbBar(4_343_000, null, true, 4_350_000, {}, {}, {}, {}, {}, {})
            androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.size(12.dp))
            com.moody.player.ui.player.AbBar(4_343_000, 4_534_000, true, 4_400_000, {}, {}, {}, {}, {}, {})
            androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.size(12.dp))
            com.moody.player.ui.player.AbBar(4_343_000, 4_534_000, false, 4_400_000, {}, {}, {}, {}, {}, {})
        }
    }
    /** The player's top bar with Play in background and the audio-track icon, then the icons it must not be confused with. */
    @Composable private fun playerTopBar() {
        val st = com.moody.player.player.video.PlayerState(videoWidth = 1920, videoHeight = 1080, playing = true, buffering = false)
        val icons = com.moody.player.design.icons.MoodyIcons
        Column(androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF202428))) {
            com.moody.player.ui.player.TitleBar("Holiday in the mountains, day 3 (final cut).mp4", st, {}, {}, {}, {}, {})
            // A vault video: no Play in background button.
            com.moody.player.ui.player.TitleBar("Holiday in the mountains, day 3 (final cut).mp4", st, {}, null, {}, {}, {})
            androidx.compose.foundation.layout.Row(androidx.compose.ui.Modifier.padding(16.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(20.dp)) {
                listOf(icons.Headphones, icons.AudioTrack, icons.VolumeHigh, icons.VolumeLow, icons.VolumeMute, icons.Equalizer, icons.Chart, icons.Pulse).forEach {
                    androidx.compose.material3.Icon(it, null, androidx.compose.ui.Modifier.size(20.dp), tint = androidx.compose.ui.graphics.Color.White)
                }
            }
        }
    }
    @Test fun playerTopBarDark() = bare("player-top-bar-dark", ThemeMode.DARK) { playerTopBar() }
    @Config(qualifiers = "w320dp-h640dp-xhdpi")
    @Test fun playerTopBar320() = bare("player-top-bar-320", ThemeMode.DARK) { playerTopBar() }
    @Test fun discsDark() = bare("discs-dark", ThemeMode.DARK) { discs() }
    @Test fun discsLight() = bare("discs-light", ThemeMode.LIGHT) { discs() }
    /** Every disc style at card size, without a cover, in four colours; then the smallest size. */
    @Composable private fun discStyles() {
        val palettes = listOf(com.moody.player.domain.DiscPalette.CRIMSON, com.moody.player.domain.DiscPalette.AMBER, com.moody.player.domain.DiscPalette.COBALT, com.moody.player.domain.DiscPalette.SAND)
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
            com.moody.player.domain.DiscStyle.entries.forEach { st ->
                androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    palettes.forEach { p -> com.moody.player.ui.music.Disc(null, 76.dp, look = com.moody.player.domain.DiscLook(st, p)) }
                    com.moody.player.ui.music.Disc(null, 40.dp, look = com.moody.player.domain.DiscLook(st, palettes[0]))
                }
            }
            com.moody.player.domain.DiscStyle.entries.chunked(3).forEach { row ->
                androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                    row.forEach { st -> com.moody.player.ui.music.Disc(null, 150.dp, look = com.moody.player.domain.DiscLook(st, com.moody.player.domain.DiscPalette.LAGOON)) }
                }
            }
        }
    }
    /** Every visualizer backdrop on the visualizer's black, in a strip each. */
    @Test fun vizBackdropsDark() = bare("viz-backdrops-dark", ThemeMode.DARK) {
        val all = com.moody.player.domain.VizBackdrop.entries.drop(1)
        Column(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF040506))) {
            all.chunked(3).forEach { row ->
                androidx.compose.foundation.layout.Row(Modifier.weight(1f)) {
                    row.forEach { b ->
                        Box(Modifier.weight(1f).fillMaxSize()) {
                            com.moody.player.ui.music.VizBackdropLayer(b, androidx.compose.ui.graphics.Color(0xFFDC143C), androidx.compose.ui.graphics.Color(0xFF4FD1C5), moving = false, modifier = Modifier.fillMaxSize())
                            androidx.compose.material3.Text(b.label, color = androidx.compose.ui.graphics.Color(0x99FFFFFF), modifier = Modifier.padding(6.dp))
                        }
                    }
                }
            }
        }
    }
    @Test fun discStylesDark() = bare("disc-styles-dark", ThemeMode.DARK) { discStyles() }
    @Test fun discStylesLight() = bare("disc-styles-light", ThemeMode.LIGHT) { discStyles() }
    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    @Test fun discPickerDark() = bare("disc-picker-dark", ThemeMode.DARK) {
        val look = com.moody.player.domain.DiscLook(com.moody.player.domain.DiscStyle.VINYL, com.moody.player.domain.DiscPalette.AMBER)
        Column(Modifier.padding(16.dp)) {
            androidx.compose.foundation.layout.FlowRow { com.moody.player.ui.music.DiscStyleChoices(look, {}) }
            androidx.compose.foundation.layout.FlowRow { com.moody.player.ui.music.DiscPaletteChoices(look, {}) }
        }
    }

    @Test fun welcomeDark() = bare("welcome-dark", ThemeMode.DARK) { com.moody.player.ui.onboarding.WelcomePager {} }
    @Test fun welcomeLight() = bare("welcome-light", ThemeMode.LIGHT) { com.moody.player.ui.onboarding.WelcomePager {} }
    @Test fun permissionsDark() = bare("permissions-dark", ThemeMode.DARK) { com.moody.player.ui.onboarding.PermissionsScreen({}) }
    @Test fun logoMid() = bare("logo-mid", ThemeMode.DARK, 700) { com.moody.player.ui.onboarding.LogoAnimation {} }
    @Test fun logoEnd() = bare("logo-end", ThemeMode.DARK, 1_700) { com.moody.player.ui.onboarding.LogoAnimation {} }

    @Config(qualifiers = "w320dp-h640dp-xhdpi")
    @Test fun videoListSmallPhone() = shot("size-320-video-list", ThemeMode.DARK) { VideoLibraryContent(state(VideoView.ALL, LayoutMode.LIST), noop, nav) }
    @Config(qualifiers = "w600dp-h960dp-xhdpi")
    @Test fun videoGridLargeScreen() = shot("size-600-video-grid", ThemeMode.MIDNIGHT) { VideoLibraryContent(state(VideoView.ALL, LayoutMode.GRID), noop, nav) }

    @Test fun videoFoldersDark() = shot("video-folders-dark", ThemeMode.DARK) { VideoLibraryContent(state(VideoView.FOLDERS), noop, nav) }
    @Test fun videoFoldersLight() = shot("video-folders-light", ThemeMode.LIGHT) { VideoLibraryContent(state(VideoView.FOLDERS), noop, nav) }
    @Test fun videoAllListDark() = shot("video-all-list-dark", ThemeMode.DARK) { VideoLibraryContent(state(VideoView.ALL, LayoutMode.LIST), noop, nav) }
    @Test fun videoLoading() = shot("video-loading-dark", ThemeMode.DARK) { VideoLibraryContent(VideoUiState(), noop, nav) }
    @Test fun videoEmpty() = shot("video-empty-dark", ThemeMode.DARK) { VideoLibraryContent(VideoUiState(loading = false), noop, nav) }
    @Test fun videoNoAccessLight() = shot("video-noaccess-light", ThemeMode.LIGHT) { VideoLibraryContent(VideoUiState(loading = false, access = AccessLevel.NONE), noop, nav) }

    // ---- Media health, Your stats (redesign), sorting and mix options ----
    private fun healthUi(problems: Boolean, firstRun: Boolean = false): com.moody.player.ui.health.HealthUi {
        if (firstRun) return com.moody.player.ui.health.HealthUi(loaded = true)
        val now = System.currentTimeMillis()
        val f = com.moody.player.domain.Finding("x", com.moody.player.domain.Severity.CRITICAL, "The file ends early", "d", emptyList())
        val w = com.moody.player.domain.Finding("y", com.moody.player.domain.Severity.WARNING, "Sound can't be played here", "d", emptyList())
        val reports = (0 until 71).map { com.moody.player.domain.HealthReport("content://v/$it", "Holiday clip $it.mp4", com.moody.player.domain.MediaKind.VIDEO, emptyList(), now) } +
            if (problems) listOf(
                com.moody.player.domain.HealthReport("content://v/c", "Concert recording, part two (cut short).mp4", com.moody.player.domain.MediaKind.VIDEO, listOf(f), now),
                com.moody.player.domain.HealthReport("content://a/w", "Voice memo 14.m4a", com.moody.player.domain.MediaKind.AUDIO, listOf(w), now),
            ) else emptyList()
        val sorted = reports.sortedByDescending { it.severity }
        return com.moody.player.ui.health.HealthUi(
            reports = sorted,
            counts = com.moody.player.domain.Severity.entries.associateWith { s -> sorted.count { it.severity == s } },
            lastScan = now - 21 * 3_600_000L, loaded = true,
            removed = listOf(
                com.moody.player.ui.health.RemovedFile("content://v/r1", "Screen recording 2026-10-02.mp4", 48_300_000, com.moody.player.domain.MediaKind.VIDEO),
                com.moody.player.ui.health.RemovedFile("content://a/r2", "Ringtone draft.mp3", null, com.moody.player.domain.MediaKind.AUDIO),
            ),
        )
    }
    @Composable private fun health(u: com.moody.player.ui.health.HealthUi) = com.moody.player.ui.health.HealthContent(u, {}, {}, {}, {}, {}, {})
    @Config(qualifiers = "w320dp-h720dp-xhdpi")
    @Test fun healthCleanDark() = bare("health-clean-dark-320", ThemeMode.DARK) { health(healthUi(problems = false)) }
    @Test fun healthCleanLight() = bare("health-clean-light", ThemeMode.LIGHT) { health(healthUi(problems = false)) }
    @Test fun healthProblemsMidnight() = bare("health-problems-midnight", ThemeMode.MIDNIGHT) { health(healthUi(problems = true)) }
    @Test fun healthFirstRunDark() = bare("health-first-dark", ThemeMode.DARK) { health(healthUi(problems = false, firstRun = true)) }
    @Config(qualifiers = "w320dp-h720dp-xhdpi")
    @Test fun statsWeekDark320() = bare("stats-week-dark-320", ThemeMode.DARK) { com.moody.player.ui.stats.StatsContent(statsUi(com.moody.player.ui.stats.StatsRange.WEEK), {}, {}, {}, {}) }
    @Test fun statsOffLight() = bare("stats-off-light", ThemeMode.LIGHT) {
        com.moody.player.ui.stats.StatsContent(statsUi(com.moody.player.ui.stats.StatsRange.WEEK).copy(keepStats = false), {}, {}, {}, {})
    }
    @Test fun mixSheetRowsDark() = bare("mix-sheet-rows-dark", ThemeMode.DARK) {
        val c = com.moody.player.design.MoodyTheme.colors
        androidx.compose.foundation.layout.Column(Modifier.background(c.surface).padding(vertical = 8.dp)) {
            com.moody.player.ui.music.SheetRow(com.moody.player.design.icons.MoodyIcons.Rename, "Rename") {}
            com.moody.player.ui.music.SheetRow(com.moody.player.design.icons.MoodyIcons.Delete, "Remove mix", trailing = {
                com.moody.player.design.components.IconAction(com.moody.player.design.icons.MoodyIcons.Info, "Info", {}, tint = c.text3)
            }) {}
        }
    }
    @Test fun sortChoicesDark() = bare("sort-songs-dark", ThemeMode.DARK) {
        androidx.compose.foundation.layout.Column(Modifier.padding(24.dp)) {
            com.moody.player.ui.components.SortChoices(
                listOf(com.moody.player.domain.SortKey.TITLE to "Title") + com.moody.player.ui.components.FileSorts,
                com.moody.player.domain.SortSpec(com.moody.player.domain.SortKey.DATE, false), {},
            )
        }
    }
    @Test fun mixesBringBackDark() = bare("mixes-bring-back-dark", ThemeMode.DARK) {
        val mixes = listOf(
            com.moody.player.domain.MoodMix("now", "Rainy drive", "What you usually play around this time", songs.take(5)),
            com.moody.player.domain.MoodMix("repeat", "On repeat", "Your most played this month", songs.drop(2).take(4)),
        )
        com.moody.player.ui.music.MoodMixesRow(mixes, {}, hidden = 2, onBringBack = {})
    }

    // ---- Updates: the popup and the header badge ----
    private val release = com.moody.player.domain.ReleaseInfo(
        "1.3.0",
        "- **Faster** thumbnails in big folders\n- Subtitles keep their timing after a seek\n- The sleep timer can wait for the song to end\n- Fixed a crash when a playlist was renamed twice\n- Smaller download",
        "https://example.com/moody-1.3.0.apk", 36_700_160, false,
    )
    /** The popup as the dialog window shows it: over the dimmed app. A real Dialog is its own window and would not be drawn here. */
    @Composable private fun updateModal(info: com.moody.player.domain.ReleaseInfo = release, state: com.moody.player.data.update.UpdateState = com.moody.player.data.update.UpdateState.Available(info)) {
        VideoLibraryContent(state(VideoView.FOLDERS), noop, nav)
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f)), contentAlignment = androidx.compose.ui.Alignment.Center) {
            com.moody.player.ui.update.UpdateModal(info, state, canDismiss = !info.required, needsInstallPermission = true, {}, {}, {}, {})
        }
    }
    @Composable private fun withBadge(content: @Composable () -> Unit) = androidx.compose.runtime.CompositionLocalProvider(
        com.moody.player.ui.update.LocalUpdateBadge provides com.moody.player.ui.update.UpdateBadge("1.3.0") {}, content = content,
    )
    @Test fun updateModalDark() = bare("update-modal-dark", ThemeMode.DARK) { updateModal() }
    @Test fun updateModalLight() = bare("update-modal-light", ThemeMode.LIGHT) { updateModal() }
    @Test fun updateModalMidnight() = bare("update-modal-midnight", ThemeMode.MIDNIGHT) { updateModal() }
    @Config(qualifiers = "w320dp-h640dp-xhdpi")
    @Test fun updateModal320() = bare("update-modal-dark-320", ThemeMode.DARK) { updateModal() }
    @Test fun updateModalRequiredLight() = bare("update-modal-required-light", ThemeMode.LIGHT) { updateModal(release.copy(required = true, notes = "- A fix Moody needs to keep playing your library")) }
    @Config(qualifiers = "w320dp-h640dp-xhdpi")
    @Test fun updateModalDownloading320() = bare("update-modal-downloading-320", ThemeMode.MIDNIGHT) { updateModal(state = com.moody.player.data.update.UpdateState.Downloading(release, 12_400_000, 36_700_160)) }
    @Test fun updateModalReadyDark() = bare("update-modal-ready-dark", ThemeMode.DARK) { updateModal(state = com.moody.player.data.update.UpdateState.Ready(release, File("moody-1.3.0.apk"))) }
    @Test fun updateBadgeVideosDark() = shot("update-badge-videos-dark", ThemeMode.DARK) { withBadge { VideoLibraryContent(state(VideoView.FOLDERS), noop, nav) } }
    @Config(qualifiers = "w320dp-h640dp-xhdpi")
    @Test fun updateBadgeVideos320() = shot("update-badge-videos-320", ThemeMode.MIDNIGHT) { withBadge { VideoLibraryContent(state(VideoView.FOLDERS), noop, nav) } }
    @Test fun updateBadgeMusicLight() = shot("update-badge-music-light", ThemeMode.LIGHT) {
        withBadge { com.moody.player.ui.music.MusicContent(musicState(), com.moody.player.ui.music.MusicTab.SONGS, null, true, true, musicNav, {}, { _, _ -> }, {}, {}, {}) }
    }
}
