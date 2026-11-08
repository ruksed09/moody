package com.moody.player.ui.vault

import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moody.player.data.media.VideoItem
import com.moody.player.data.vault.MoveProgress
import com.moody.player.data.vault.MoveResult
import com.moody.player.design.components.LocalToaster
import com.moody.player.domain.formatBytes
import kotlinx.coroutines.launch

/**
 * Hiding from the library: copy each video into the vault, then ask Android to delete the originals.
 * If the user declines, the copies are thrown away and nothing changes.
 */
@Composable
fun rememberHideFlow(onNeedsSetup: () -> Unit): (List<VideoItem>, () -> Unit) -> Unit {
    val vm: VaultViewModel = hiltViewModel()
    val info by vm.info.collectAsStateWithLifecycle()
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf<MoveProgress?>(null) }
    var pending by remember { mutableStateOf<Pair<List<String>, () -> Unit>?>(null) }

    fun finish(ids: List<String>, done: () -> Unit) = scope.launch {
        vm.manager.finishHide(ids)
        toaster.show(if (ids.size == 1) "Hidden in the vault" else "${ids.size} videos hidden in the vault")
        done()
    }

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        val (ids, done) = pending ?: return@rememberLauncherForActivityResult
        pending = null
        if (r.resultCode == Activity.RESULT_OK) {
            scope.launch {
                if (Build.VERSION.SDK_INT < 30) vm.manager.deleteDirect(ids)
                finish(ids, done)
            }
        } else scope.launch {
            vm.manager.rollback(ids)
            toaster.show("Nothing was hidden")
        }
    }

    if (progress != null) MoveSheet(progress!!, "Hiding")

    return remember(info) {
        { videos, done ->
            if (info?.setUp != true) {
                toaster.show("Set a vault PIN first")
                onNeedsSetup()
            } else scope.launch {
                val (ids, failures) = vm.manager.copyIn(videos) { progress = it }
                progress = null
                failures.firstOrNull()?.let { f ->
                    toaster.show(when (f) {
                        is MoveResult.NoSpace -> "Not enough space to hide ${f.name} (needs ${formatBytes(f.needBytes)})"
                        is MoveResult.Failed -> "Couldn't hide ${f.name}"
                        else -> "Some videos weren't hidden"
                    })
                }
                if (ids.isEmpty()) return@launch
                val sender = if (Build.VERSION.SDK_INT >= 30) vm.manager.deleteRequest(ids) else runCatching { vm.manager.deleteDirect(ids) }.getOrNull()
                if (sender != null) {
                    pending = ids to done
                    consent.launch(IntentSenderRequest.Builder(sender).build())
                } else if (Build.VERSION.SDK_INT < 30) {
                    finish(ids, done)
                } else {
                    vm.manager.rollback(ids)
                }
            }
        }
    }
}
