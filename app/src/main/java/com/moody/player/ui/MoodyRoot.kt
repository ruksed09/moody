package com.moody.player.ui

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.moody.player.data.prefs.Settings
import com.moody.player.design.MoodyTheme
import com.moody.player.design.components.Motion
import com.moody.player.design.components.LocalToaster
import com.moody.player.design.components.ToastHost
import com.moody.player.design.components.Toaster
import com.moody.player.ui.components.MainTab
import com.moody.player.ui.components.MoodyBottomBar
import com.moody.player.ui.nav.Routes
import com.moody.player.ui.video.FolderScreen
import com.moody.player.ui.video.VideoLibraryScreen
import com.moody.player.ui.video.VideoNav
import com.moody.player.ui.video.VideoViewModel

/** Slots filled by later features (music, vault, settings, onboarding). Keeps MoodyRoot's wiring in one place. */
data class AppSlots(
    val videoNav: @Composable (NavHostController) -> VideoNav,
    val music: @Composable (NavHostController) -> Unit,
    val miniPlayer: @Composable (NavHostController) -> Unit = {},
    val extraRoutes: androidx.navigation.NavGraphBuilder.(NavHostController) -> Unit = {},
    val startRoute: String = Routes.MAIN,
    /** Drawn above every screen, e.g. the update popup. */
    val overlay: @Composable () -> Unit = {},
)

@Composable
fun MoodyRoot(settings: Settings, slots: AppSlots, toaster: Toaster = remember { Toaster() }) {
    MoodyTheme(settings.themeMode, settings.fontMode) {
        CompositionLocalProvider(LocalToaster provides toaster) {
            val nav = rememberNavController()
            Box(Modifier.fillMaxSize().background(MoodyTheme.colors.bg)) {
                NavHost(
                    nav,
                    startDestination = slots.startRoute,
                    enterTransition = { fadeIn(Motion.enter()) + slideInHorizontally(Motion.enter()) { it / 12 } },
                    exitTransition = { fadeOut(Motion.fast()) },
                    popEnterTransition = { fadeIn(Motion.enter()) },
                    popExitTransition = { fadeOut(Motion.fast()) + slideOutHorizontally(Motion.fast()) { it / 12 } },
                ) {
                    composable(Routes.MAIN) { MainScreen(nav, slots) }
                    composable(Routes.FOLDER, arguments = listOf(navArgument("path") { type = NavType.StringType })) { entry ->
                        val vm: VideoViewModel = hiltViewModel(nav.getBackStackEntry(Routes.MAIN))
                        FolderScreen(Routes.decodePath(entry.arguments?.getString("path")), vm, slots.videoNav(nav), onBack = { nav.popBackStack() })
                    }
                    slots.extraRoutes(this, nav)
                }
                slots.overlay()
                ToastHost(toaster)
            }
        }
    }
}

@Composable
private fun MainScreen(nav: NavHostController, slots: AppSlots) {
    var tab by rememberSaveable { mutableStateOf(MainTab.VIDEO) }
    val videoVm: VideoViewModel = hiltViewModel()
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            AnimatedContent(tab, transitionSpec = { fadeIn(Motion.base()) togetherWith fadeOut(Motion.fast()) }, label = "tab") { t ->
                when (t) {
                    MainTab.VIDEO -> VideoLibraryScreen(videoVm, slots.videoNav(nav))
                    MainTab.MUSIC -> slots.music(nav)
                }
            }
        }
        slots.miniPlayer(nav)
        MoodyBottomBar(tab, { tab = it })
    }
}
