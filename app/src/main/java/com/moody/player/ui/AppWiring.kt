package com.moody.player.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.moody.player.data.media.VideoItem
import com.moody.player.ui.nav.Routes
import com.moody.player.ui.player.openVideo
import com.moody.player.ui.video.StreamDialog
import com.moody.player.ui.video.StreamViewModel
import com.moody.player.ui.video.VideoNav
import com.moody.player.ui.video.rememberLibraryActions

/** Builds the Video tab's callbacks and owns the dialogs they open. */
@Composable
fun rememberVideoNav(
    nav: NavHostController,
    onHide: (List<VideoItem>, () -> Unit) -> Unit,
    onRequestAccess: () -> Unit,
    onChanged: () -> Unit,
): VideoNav {
    val context: Context = LocalContext.current
    var streamOpen by remember { mutableStateOf(false) }
    val actions = rememberLibraryActions(onHide, onChanged)
    if (streamOpen) {
        val vm: StreamViewModel = hiltViewModel()
        StreamDialog(vm, onDismiss = { streamOpen = false }) { uri ->
            streamOpen = false
            context.openVideo(listOf(uri), 0, titles = listOf(uri.host ?: "Stream"))
        }
    }
    return remember(nav, actions) {
        VideoNav(
            openFolder = { nav.navigate(Routes.folder(it)) },
            play = { list, i -> context.openVideo(list.map { it.uri }, i, titles = list.map { it.title }) },
            openVault = { nav.navigate(Routes.VAULT) { launchSingleTop = true } },
            openSettings = { nav.navigate(Routes.SETTINGS) { launchSingleTop = true } },
            openStream = { streamOpen = true },
            requestAccess = onRequestAccess,
            onSelection = actions.handle,
        )
    }
}
