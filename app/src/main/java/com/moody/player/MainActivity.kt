package com.moody.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moody.player.data.media.MediaRepository
import com.moody.player.data.prefs.Settings
import com.moody.player.data.prefs.SettingsStore
import com.moody.player.design.components.EmptyState
import com.moody.player.design.illustrations.EmptyMusic
import com.moody.player.ui.AppSlots
import com.moody.player.ui.MoodyRoot
import com.moody.player.ui.nav.Routes
import com.moody.player.ui.video.VideoNav
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settingsStore: SettingsStore
    @Inject lateinit var media: MediaRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by settingsStore.settings.collectAsStateWithLifecycle(Settings())
            MoodyRoot(
                settings,
                AppSlots(
                    videoNav = { nav ->
                        VideoNav(
                            openFolder = { nav.navigate(Routes.folder(it)) },
                            play = { _, _ -> },
                            openVault = {},
                            openSettings = {},
                            openStream = {},
                            requestAccess = {},
                            onSelection = { _, _, done -> done() },
                        )
                    },
                    music = { EmptyState({ EmptyMusic() }, "Music", "Coming together") },
                ),
            )
        }
    }

    override fun onResume() {
        super.onResume()
        media.refreshAccess()
    }
}
