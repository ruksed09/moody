package com.moody.player

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.composable
import com.moody.player.data.media.MediaRepository
import com.moody.player.data.prefs.Settings
import com.moody.player.data.prefs.SettingsStore
import com.moody.player.data.media.hasAudioAccess
import com.moody.player.player.music.MusicController
import com.moody.player.ui.AppSlots
import com.moody.player.ui.MoodyRoot
import com.moody.player.ui.music.EqualizerSheet
import com.moody.player.ui.music.MiniPlayer
import com.moody.player.ui.music.MusicNav
import com.moody.player.ui.music.MusicScreen
import com.moody.player.ui.music.MusicViewModel
import com.moody.player.ui.music.SongListKind
import com.moody.player.ui.music.SongListScreen
import com.moody.player.ui.music.NowPlayingScreen
import com.moody.player.ui.nav.Routes
import com.moody.player.ui.rememberVideoNav
import com.moody.player.ui.vault.VaultRoute
import com.moody.player.ui.vault.rememberHideFlow
import com.moody.player.data.vault.VaultManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import com.moody.player.ui.onboarding.OnboardingFlow
import com.moody.player.ui.onboarding.PermissionsScreen
import com.moody.player.ui.settings.SettingsScreen
import com.moody.player.ui.update.UpdateDialogHost
import com.moody.player.ui.update.UpdateSettingsRow
import com.moody.player.data.update.UpdateManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settingsStore: SettingsStore
    @Inject lateinit var media: MediaRepository
    @Inject lateinit var music: MusicController
    @Inject lateinit var vault: VaultManager
    @Inject lateinit var updates: UpdateManager

    /** Bumped when a notification asks to open Now Playing. */
    private var openNowPlaying by mutableIntStateOf(0)
    /** The last request already acted on, so a screen coming back never replays it. */
    private var handledNowPlaying = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        var firstSettings: Settings? = null
        splash.setKeepOnScreenCondition { firstSettings == null }
        lifecycleScope.launch { firstSettings = settingsStore.settings.first() }
        enableEdgeToEdge()
        // A recreated activity (rotation, theme, Recents) still carries its first intent: act on it only once.
        val fresh = savedInstanceState == null && ((intent?.flags ?: 0) and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0
        if (fresh) {
            if (intent?.getBooleanExtra(EXTRA_OPEN_NOW_PLAYING, false) == true) openNowPlaying++
            handleAudioView(intent)
        }
        lifecycleScope.launch { vault.recoverInterruptedMoves() }
        setContent {
            val current by settingsStore.settings.collectAsStateWithLifecycle(null)
            val settings = current ?: return@setContent
            val start = remember { if (settings.onboarded) Routes.MAIN else Routes.ONBOARDING }
            val musicState by music.state.collectAsStateWithLifecycle()
            com.moody.player.ui.music.DriveDiscClock(musicState.playing)
            MoodyRoot(
                settings,
                AppSlots(
                    videoNav = { nav ->
                        rememberVideoNav(
                            nav,
                            onHide = rememberHideFlow(onNeedsSetup = { nav.navigate(Routes.VAULT) { launchSingleTop = true } }),
                            onRequestAccess = { nav.navigate(Routes.PERMISSIONS) { launchSingleTop = true } },
                            onChanged = { media.refreshAccess() },
                        )
                    },
                    music = { nav ->
                        val vm: MusicViewModel = hiltViewModel()
                        MusicScreen(
                            vm,
                            MusicNav(
                                openAlbum = { nav.navigate(Routes.album(it)) },
                                openArtist = { nav.navigate(Routes.artist(it)) },
                                openFolder = { nav.navigate(Routes.musicFolder(it)) },
                                openPlaylist = { nav.navigate(Routes.playlist(it)) },
                                openSettings = { nav.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                                requestAccess = { nav.navigate(Routes.PERMISSIONS) { launchSingleTop = true } },
                                openStats = { nav.navigate(Routes.STATS) { launchSingleTop = true } },
                            ),
                            hasAccess = hasAudioAccess(this@MainActivity),
                            backgrounds = settings.backgrounds,
                        )
                    },
                    miniPlayer = { nav ->
                        MiniPlayer(musicState, onOpen = { nav.navigate(Routes.NOW_PLAYING) { launchSingleTop = true } }, onToggle = music::toggle, onNext = music::next, onClose = music::close)
                    },
                    extraRoutes = { nav ->
                        composable(Routes.ONBOARDING) {
                            OnboardingFlow(onFinished = {
                                lifecycleScope.launch { settingsStore.update { it.copy(onboarded = true) } }
                                media.refreshAccess()
                                nav.navigate(Routes.MAIN) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                            })
                        }
                        composable(Routes.PERMISSIONS) {
                            PermissionsScreen(onContinue = { media.refreshAccess(); nav.popBackStack() }, onBack = { nav.popBackStack() })
                        }
                        composable(Routes.SETTINGS) {
                            SettingsScreen(hiltViewModel(), onBack = { nav.popBackStack() }, onSection = { nav.navigate(Routes.settingsSection(it.id)) { launchSingleTop = true } }, onStats = { nav.navigate(Routes.STATS) { launchSingleTop = true } }, onHealth = { nav.navigate(Routes.health()) { launchSingleTop = true } })
                        }
                        composable(
                            Routes.HEALTH,
                            arguments = listOf(androidx.navigation.navArgument("uri") { type = androidx.navigation.NavType.StringType; nullable = true; defaultValue = null }),
                        ) {
                            com.moody.player.ui.health.HealthScreen(hiltViewModel(), onBack = { nav.popBackStack() })
                        }
                        composable(Routes.STATS) {
                            com.moody.player.ui.stats.StatsScreen(hiltViewModel(), onBack = { nav.popBackStack() })
                        }
                        composable(Routes.SETTINGS_SECTION) { entry ->
                            val section = com.moody.player.ui.settings.SettingsSection.of(entry.arguments?.getString("id"))
                            if (section == null) { LaunchedEffect(Unit) { nav.popBackStack() }; return@composable }
                            com.moody.player.ui.settings.SettingsSectionScreen(
                                section, hiltViewModel(), onBack = { nav.popBackStack() },
                                onOpenVault = { nav.navigate(Routes.VAULT) { launchSingleTop = true } },
                                onShortcuts = { nav.navigate(Routes.SHORTCUTS) { launchSingleTop = true } },
                                onGestures = { nav.navigate(Routes.GESTURES) { launchSingleTop = true } },
                                onPermissions = { nav.navigate(Routes.PERMISSIONS) { launchSingleTop = true } },
                                updates = { UpdateSettingsRow(updates, settings.lastUpdateCheck) { lifecycleScope.launch { updates.check(force = true) } } },
                            )
                        }
                        composable(Routes.GESTURES) {
                            com.moody.player.ui.settings.GesturesScreen(hiltViewModel(), onBack = { nav.popBackStack() })
                        }
                        composable(Routes.SHORTCUTS) {
                            com.moody.player.ui.settings.ShortcutsScreen(hiltViewModel(), onBack = { nav.popBackStack() })
                        }
                        composable(Routes.VAULT) {
                            VaultRoute(hiltViewModel(), onBack = { nav.popBackStack() })
                        }
                        composable(Routes.NOW_PLAYING) {
                            var eq by remember { mutableStateOf(false) }
                            NowPlayingScreen(hiltViewModel(), onClose = { nav.popBackStack() }, onEqualizer = { eq = true }, onFullscreenViz = { nav.navigate(Routes.VISUALIZER) { launchSingleTop = true } })
                            if (eq) EqualizerSheet(music, onDismiss = { eq = false })
                        }
                        composable(Routes.VISUALIZER) {
                            com.moody.player.ui.music.VisualizerScreen(hiltViewModel(), onBack = { nav.popBackStack() })
                        }
                        listOf(
                            Routes.ALBUM to SongListKind.ALBUM, Routes.ARTIST to SongListKind.ARTIST,
                            Routes.MUSIC_FOLDER to SongListKind.FOLDER, Routes.PLAYLIST to SongListKind.PLAYLIST,
                        ).forEach { (route, kind) ->
                            composable(route) { entry ->
                                val key = Routes.decodePath(entry.arguments?.getString("key"))
                                val vm: MusicViewModel = hiltViewModel(nav.getBackStackEntry(Routes.MAIN))
                                SongListScreen(vm, kind, key, onBack = { nav.popBackStack() })
                            }
                        }
                    },
                    startRoute = start,
                    overlay = { nav ->
                        // One-shot: open Now Playing for each new request, never again on recomposition.
                        LaunchedEffect(openNowPlaying) {
                            if (openNowPlaying > handledNowPlaying && settings.onboarded) {
                                handledNowPlaying = openNowPlaying
                                nav.navigate(Routes.NOW_PLAYING) { launchSingleTop = true }
                            }
                        }
                        if (settings.onboarded) {
                            LaunchedEffect(Unit) { updates.check(force = false) }
                            UpdateDialogHost(updates)
                        }
                    },
                ),
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_NOW_PLAYING, false)) openNowPlaying++
        handleAudioView(intent)
    }

    /** A song opened from another app plays as a one-song queue and Now Playing opens. */
    private fun handleAudioView(i: Intent?) {
        val uri = i?.data ?: return
        if (i.action != Intent.ACTION_VIEW || i.type?.startsWith("audio/") != true && i.type != null) return
        val name = runCatching {
            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull() ?: uri.lastPathSegment.orEmpty()
        val song = com.moody.player.data.media.AudioItem(
            uri, 0, name.substringBeforeLast('.'), "Unknown artist", "", 0, 0, "", i.type ?: "audio/*", 0, name, 0,
        )
        music.playQueue(listOf(song), 0)
        openNowPlaying++
        i.data = null
    }

    override fun onStart() {
        super.onStart()
        // After Close the music service is shut down; it starts again only when something is played.
        if (!music.state.value.dismissed) music.connect()
    }

    override fun onResume() {
        super.onResume()
        media.refreshAccess()
    }

    companion object {
        const val EXTRA_OPEN_NOW_PLAYING = "open_now_playing"
    }
}
