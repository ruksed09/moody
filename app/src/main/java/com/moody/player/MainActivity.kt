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

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        var firstSettings: Settings? = null
        splash.setKeepOnScreenCondition { firstSettings == null }
        lifecycleScope.launch { firstSettings = settingsStore.settings.first() }
        enableEdgeToEdge()
        if (intent?.getBooleanExtra(EXTRA_OPEN_NOW_PLAYING, false) == true) openNowPlaying++
        lifecycleScope.launch { vault.recoverInterruptedMoves() }
        setContent {
            val current by settingsStore.settings.collectAsStateWithLifecycle(null)
            val settings = current ?: return@setContent
            val start = remember { if (settings.onboarded) Routes.MAIN else Routes.ONBOARDING }
            val musicState by music.state.collectAsStateWithLifecycle()
            MoodyRoot(
                settings,
                AppSlots(
                    videoNav = { nav ->
                        LaunchedEffect(openNowPlaying) { if (openNowPlaying > 0) nav.navigate(Routes.NOW_PLAYING) { launchSingleTop = true } }
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
                            ),
                            hasAccess = hasAudioAccess(this@MainActivity),
                            backgrounds = settings.backgrounds,
                        )
                    },
                    miniPlayer = { nav ->
                        MiniPlayer(musicState, onOpen = { nav.navigate(Routes.NOW_PLAYING) { launchSingleTop = true } }, onToggle = music::toggle, onNext = music::next)
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
                            SettingsScreen(
                                hiltViewModel(), onBack = { nav.popBackStack() },
                                onOpenVault = { nav.navigate(Routes.VAULT) { launchSingleTop = true } },
                                updates = { UpdateSettingsRow(updates, settings.lastUpdateCheck) { lifecycleScope.launch { updates.check(force = true) } } },
                            )
                        }
                        composable(Routes.VAULT) {
                            VaultRoute(hiltViewModel(), onBack = { nav.popBackStack() })
                        }
                        composable(Routes.NOW_PLAYING) {
                            var eq by remember { mutableStateOf(false) }
                            NowPlayingScreen(hiltViewModel(), onClose = { nav.popBackStack() }, onEqualizer = { eq = true })
                            if (eq) EqualizerSheet(music, onDismiss = { eq = false })
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
                    overlay = {
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
    }

    override fun onStart() {
        super.onStart()
        music.connect()
    }

    override fun onResume() {
        super.onResume()
        media.refreshAccess()
    }

    companion object {
        const val EXTRA_OPEN_NOW_PLAYING = "open_now_playing"
    }
}
