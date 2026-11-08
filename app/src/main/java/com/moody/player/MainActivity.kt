package com.moody.player

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.composable
import com.moody.player.data.media.MediaRepository
import com.moody.player.data.prefs.Settings
import com.moody.player.data.prefs.SettingsStore
import com.moody.player.design.components.EmptyState
import com.moody.player.design.illustrations.EmptyMusic
import com.moody.player.player.music.MusicController
import com.moody.player.ui.AppSlots
import com.moody.player.ui.MoodyRoot
import com.moody.player.ui.music.MiniPlayer
import com.moody.player.ui.music.NowPlayingScreen
import com.moody.player.ui.nav.Routes
import com.moody.player.ui.rememberVideoNav
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settingsStore: SettingsStore
    @Inject lateinit var media: MediaRepository
    @Inject lateinit var music: MusicController

    /** Bumped when a notification asks to open Now Playing. */
    private var openNowPlaying by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (intent?.getBooleanExtra(EXTRA_OPEN_NOW_PLAYING, false) == true) openNowPlaying++
        setContent {
            val settings by settingsStore.settings.collectAsStateWithLifecycle(Settings())
            val musicState by music.state.collectAsStateWithLifecycle()
            MoodyRoot(
                settings,
                AppSlots(
                    videoNav = { nav ->
                        LaunchedEffect(openNowPlaying) { if (openNowPlaying > 0) nav.navigate(Routes.NOW_PLAYING) { launchSingleTop = true } }
                        rememberVideoNav(
                            nav,
                            onHide = { _, done -> done() },
                            onRequestAccess = {},
                            onChanged = { media.refreshAccess() },
                        )
                    },
                    music = { EmptyState({ EmptyMusic() }, "Music", "Coming together") },
                    miniPlayer = { nav ->
                        MiniPlayer(musicState, onOpen = { nav.navigate(Routes.NOW_PLAYING) { launchSingleTop = true } }, onToggle = music::toggle, onNext = music::next)
                    },
                    extraRoutes = { nav ->
                        composable(Routes.NOW_PLAYING) {
                            NowPlayingScreen(hiltViewModel(), onClose = { nav.popBackStack() }, onEqualizer = {})
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
