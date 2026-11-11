package com.moody.player.ui.vault

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.moody.player.data.db.VaultItem
import com.moody.player.data.vault.MoveProgress
import com.moody.player.data.vault.MoveResult
import com.moody.player.data.vault.VaultInfo
import com.moody.player.data.vault.VaultLock
import com.moody.player.data.vault.VaultManager
import com.moody.player.design.MoodyTheme
import com.moody.player.design.Radii
import com.moody.player.design.components.EmptyState
import com.moody.player.design.components.IconAction
import com.moody.player.design.components.LocalToaster
import com.moody.player.design.components.ReelLine
import com.moody.player.design.icons.MoodyIcons
import com.moody.player.design.illustrations.EmptyVault
import com.moody.player.domain.formatBytes
import com.moody.player.ui.player.openVideo
import com.moody.player.ui.video.ConfirmDialog
import com.moody.player.ui.video.Thumb
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class VaultViewModel @Inject constructor(val lock: VaultLock, val manager: VaultManager) : ViewModel() {
    val info = lock.info.stateIn(viewModelScope, SharingStarted.Eagerly, null as VaultInfo?)
    val unlocked = lock.unlocked
    val items = manager.items.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null as List<VaultItem>?)
}

/** Setup, unlock, or the vault itself, depending on state. */
@Composable
fun VaultRoute(vm: VaultViewModel, onBack: () -> Unit) {
    val info by vm.info.collectAsStateWithLifecycle()
    val unlocked by vm.unlocked.collectAsStateWithLifecycle()
    val i = info ?: return
    when {
        !i.setUp -> VaultSetupScreen(vm.lock, onBack)
        !unlocked -> VaultUnlockScreen(vm.lock, i, onBack)
        else -> VaultScreen(vm, onBack)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VaultScreen(vm: VaultViewModel, onBack: () -> Unit) {
    SecureWindow()
    val c = MoodyTheme.colors
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val items by vm.items.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf(setOf<String>()) }
    var progress by remember { mutableStateOf<MoveProgress?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()
    BackHandler(selecting) { selected = emptySet() }

    fun toggle(id: String) { selected = if (id in selected) selected - id else selected + id }
    val chosen = items.orEmpty().filter { it.id in selected }

    Box(Modifier.fillMaxSize().pointerInput(Unit) { awaitPointerEventScope { while (true) { awaitPointerEvent(); vm.lock.touch() } } }) {
        LazyVerticalGrid(
            columns = com.moody.player.ui.components.MediaGridCells(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.statusBarsPadding().padding(bottom = 8.dp)) {
                    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (selecting) {
                            IconAction(MoodyIcons.Close, "Clear selection", { selected = emptySet() }, tint = c.text)
                            Text("${selected.size} selected", style = MoodyTheme.type.heading, color = c.text, modifier = Modifier.weight(1f))
                            IconAction(MoodyIcons.Eye, "Unhide", {
                                val list = chosen
                                selected = emptySet()
                                scope.launch {
                                    val results = vm.manager.unhide(list) { progress = it }
                                    progress = null
                                    val ok = results.filterIsInstance<MoveResult.Ok>()
                                    val failed = results.size - ok.size
                                    toaster.show(when {
                                        failed > 0 -> "$failed couldn't be moved back"
                                        ok.size == 1 -> "Back in ${ok[0].landedPath.trimEnd('/')}"
                                        else -> "${ok.size} videos back in your library"
                                    })
                                }
                            })
                            IconAction(MoodyIcons.Delete, "Delete forever", { confirmDelete = true })
                        } else {
                            IconAction(MoodyIcons.ArrowBack, "Back", onBack, tint = c.text)
                            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                            IconAction(MoodyIcons.Lock, "Lock vault", { vm.lock.lock() })
                        }
                    }
                    Text("Vault", style = MoodyTheme.type.hero, color = c.text)
                    Text(
                        items?.let { "${it.size} hidden · ${formatBytes(it.sumOf { v -> v.sizeBytes })}" } ?: "Opening",
                        style = MoodyTheme.type.mono, color = c.text3, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            val list = items
            if (list != null && list.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState({ EmptyVault() }, "Nothing hidden yet", "In Videos, press and hold a video, then tap the eye to hide it here")
                }
            }
            items(list.orEmpty(), key = { it.id }) { v ->
                Column(
                    Modifier.clip(Radii.md).combinedClickable(
                        onClick = {
                            if (selecting) toggle(v.id)
                            else {
                                val all = list.orEmpty()
                                context.openVideo(all.map { Uri.fromFile(vm.manager.fileFor(it)) }, all.indexOf(v), fromVault = true, titles = all.map { it.originalName.substringBeforeLast('.') })
                            }
                        },
                        onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); toggle(v.id) },
                    ),
                ) {
                    val thumb = vm.manager.thumbFor(v).takeIf(File::exists)
                    Thumb(thumb ?: Uri.fromFile(vm.manager.fileFor(v)), Modifier.fillMaxWidth(), v.durationMs, null, selected = v.id in selected)
                    Text(v.originalName.substringBeforeLast('.'), style = MoodyTheme.type.bodyStrong, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                    Text(formatBytes(v.sizeBytes), style = MoodyTheme.type.monoSmall, color = c.text3)
                }
            }
        }
        progress?.let { MoveSheet(it, "Moving back") }
    }

    if (confirmDelete) {
        ConfirmDialog(
            if (chosen.size == 1) "Delete this video forever?" else "Delete ${chosen.size} videos forever?",
            "They are erased from the vault and can't be recovered.", "Delete forever",
            onDismiss = { confirmDelete = false },
        ) {
            confirmDelete = false
            val l = chosen
            selected = emptySet()
            scope.launch { vm.manager.deleteForever(l); toaster.show(if (l.size == 1) "Deleted" else "${l.size} deleted") }
        }
    }
}

/** Progress for vault moves: file n of m, with the reel line. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveSheet(p: MoveProgress, title: String) {
    val c = MoodyTheme.colors
    ModalBottomSheet(onDismissRequest = {}, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.surface) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(title, style = MoodyTheme.type.title, color = c.text)
            Text("${p.index + 1} of ${p.total} · ${p.currentName}", style = MoodyTheme.type.caption, color = c.text3, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
            ReelLine(if (p.bytesTotal > 0) p.bytesDone / p.bytesTotal.toFloat() else null, height = 3.dp)
            Text("${formatBytes(p.bytesDone)} of ${formatBytes(p.bytesTotal)}", style = MoodyTheme.type.mono, color = c.text2, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
