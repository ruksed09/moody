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
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.foundation.layout.fillMaxWidth
import com.moody.player.ui.components.LocalBottomInset
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
    /** Drawn above every screen, e.g. the update popup. Always composed, once. */
    val overlay: @Composable (NavHostController) -> Unit = {},
)

@Composable
fun MoodyRoot(settings: Settings, slots: AppSlots, toaster: Toaster = remember { Toaster() }) {
    MoodyTheme(settings.themeMode, settings.fontMode) {
        CompositionLocalProvider(LocalToaster provides toaster) {
            val nav = rememberNavController()
            val openHealth = remember(nav) { { uri: android.net.Uri -> nav.navigate(Routes.health(uri)) { launchSingleTop = true } } }
            CompositionLocalProvider(com.moody.player.ui.health.LocalOpenHealth provides openHealth) {
            Box(Modifier.fillMaxSize().background(MoodyTheme.colors.bg)) {
                NavHost(
                    nav,
                    startDestination = slots.startRoute,
                    enterTransition = { fadeIn(Motion.enter()) + slideInHorizontally(Motion.enter()) { it / 10 } },
                    exitTransition = { fadeOut(Motion.fast()) },
                    popEnterTransition = { fadeIn(Motion.enter()) + slideInHorizontally(Motion.enter()) { -it / 10 } },
                    popExitTransition = { fadeOut(Motion.fast()) + slideOutHorizontally(Motion.fast()) { it / 10 } },
                ) {
                    composable(Routes.MAIN) { MainScreen(nav, slots) }
                    composable(Routes.FOLDER, arguments = listOf(navArgument("path") { type = NavType.StringType })) { entry ->
                        val vm: VideoViewModel = hiltViewModel(nav.getBackStackEntry(Routes.MAIN))
                        FolderScreen(Routes.decodePath(entry.arguments?.getString("path")), vm, slots.videoNav(nav), onBack = { nav.popBackStack() })
                    }
                    slots.extraRoutes(this, nav)
                }
                slots.overlay(nav)
                ToastHost(toaster)
            }
            }
        }
    }
}

@Composable
private fun MainScreen(nav: NavHostController, slots: AppSlots) {
    var tab by rememberSaveable { mutableStateOf(MainTab.VIDEO) }
    val videoVm: VideoViewModel = hiltViewModel()
    // Each tab keeps its own saved state (scroll position, sub-tab) while the other is showing.
    val holder = rememberSaveableStateHolder()
    val density = LocalDensity.current
    var overlayPx by remember { mutableIntStateOf(0) }
    val bottomInset = with(density) { overlayPx.toDp() }
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalBottomInset provides bottomInset) {
            val shift = with(density) { 24.dp.roundToPx() }
            AnimatedContent(tab, transitionSpec = {
                // A short slide toward the tab you picked, so the switch reads as sideways.
                val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (fadeIn(Motion.base()) + slideInHorizontally(Motion.base()) { dir * shift }) togetherWith
                    (fadeOut(Motion.fast()) + slideOutHorizontally(Motion.fast()) { -dir * shift })
            }, label = "tab") { t ->
                holder.SaveableStateProvider(t.name) {
                    when (t) {
                        MainTab.VIDEO -> VideoLibraryScreen(videoVm, slots.videoNav(nav))
                        MainTab.MUSIC -> slots.music(nav)
                    }
                }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { overlayPx = it.height }) {
            slots.miniPlayer(nav)
            MoodyBottomBar(tab, { tab = it })
        }
    }
}
