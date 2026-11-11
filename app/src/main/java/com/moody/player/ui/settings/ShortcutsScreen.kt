package com.moody.player.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moody.player.design.MoodyTheme
import com.moody.player.design.OnVideo
import com.moody.player.design.Radii
import com.moody.player.design.components.IconAction
import com.moody.player.design.components.LocalToaster
import com.moody.player.design.icons.MoodyIcons
import com.moody.player.domain.PlayerShortcut
import com.moody.player.domain.addShortcut
import com.moody.player.domain.moveShortcut
import com.moody.player.domain.removeShortcut
import kotlin.math.roundToInt

fun shortcutIcon(s: PlayerShortcut): ImageVector = when (s) {
    PlayerShortcut.LOOP -> MoodyIcons.Repeat
    PlayerShortcut.SHUFFLE -> MoodyIcons.Shuffle
    PlayerShortcut.SCREENSHOT -> MoodyIcons.Camera
    PlayerShortcut.AB_REPEAT -> MoodyIcons.AbRepeat
    PlayerShortcut.MUTE -> MoodyIcons.VolumeHigh
    PlayerShortcut.ROTATE -> MoodyIcons.Rotate
    PlayerShortcut.BOOKMARK -> MoodyIcons.Bookmark
    PlayerShortcut.TRANSCRIPT -> MoodyIcons.Lyrics
}

/** Settings > Customize player shortcuts: pick, order and preview the quick buttons under the player's title bar. */
@Composable
fun ShortcutsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    SettingsPage("Player shortcuts", onBack) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                ShortcutEditor(s.playerShortcuts) { list -> vm.update { it.copy(playerShortcuts = list) } }
            }
        }
    }
}

/** Preview, selected list (drag to reorder, remove) and available list. Used in Settings and in the player sheet. */
@Composable
fun ShortcutEditor(selected: List<PlayerShortcut>, save: (List<PlayerShortcut>) -> Unit) {
    val c = MoodyTheme.colors
    val toaster = LocalToaster.current
    Column {
            Text("Quick buttons shown under the video's title bar. Add as many as you like.", style = MoodyTheme.type.caption, color = c.text2)
            Spacer(Modifier.height(12.dp))
            Preview(selected)

            Label("Selected")
            if (selected.isEmpty()) {
                Text("None. Add one from the list below.", style = MoodyTheme.type.caption, color = c.text3, modifier = Modifier.padding(vertical = 8.dp))
            }
            SelectedList(selected, onMove = { from, to -> save(moveShortcut(selected, from, to)) }, onRemove = { save(removeShortcut(selected, it)) })

            Label("Available")
            val available = PlayerShortcut.entries.filter { it !in selected }
            if (available.isEmpty()) Text("All shortcuts are in use.", style = MoodyTheme.type.caption, color = c.text3, modifier = Modifier.padding(vertical = 8.dp))
            available.forEach { sc ->
                Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                    ShortcutTile(sc)
                    Spacer(Modifier.width(12.dp))
                    Text(sc.label, style = MoodyTheme.type.body, color = c.text, modifier = Modifier.weight(1f))
                    IconAction(MoodyIcons.Plus, "Add ${sc.label}", { save(addShortcut(selected, sc)) }, tint = c.accent, size = 20.dp)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Lock, Previous, Play/Pause, Next, Screen size and Popup always stay in the bottom row. Audio and CC stay in the title bar.",
                style = MoodyTheme.type.caption, color = c.text3,
            )
    }
}

@Composable
private fun Label(text: String) = Text(text, style = MoodyTheme.type.heading, color = MoodyTheme.colors.accent, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))

@Composable
private fun ShortcutTile(sc: PlayerShortcut) {
    Box(Modifier.size(32.dp).background(MoodyTheme.colors.surface, Radii.sm), contentAlignment = Alignment.Center) {
        Icon(shortcutIcon(sc), null, Modifier.size(18.dp), tint = MoodyTheme.colors.text)
    }
}

/** A small mock of the player's top area, showing the shortcuts exactly as they will appear. */
@Composable
private fun Preview(selected: List<PlayerShortcut>) {
    Column(
        Modifier.fillMaxWidth().clip(Radii.lg).background(Color(0xFF060509)).border(1.dp, MoodyTheme.colors.line2, Radii.lg).padding(vertical = 6.dp)
            .semantics { contentDescription = "Preview: ${selected.joinToString { it.label }.ifEmpty { "no shortcuts" }}" },
    ) {
        Row(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(MoodyIcons.ArrowBack, null, Modifier.size(18.dp), tint = OnVideo.text)
            Spacer(Modifier.width(12.dp))
            Text("My video.mp4", style = MoodyTheme.type.label, color = OnVideo.text, modifier = Modifier.weight(1f))
            listOf(MoodyIcons.Audio, MoodyIcons.Cc, MoodyIcons.More).forEach {
                Icon(it, null, Modifier.padding(horizontal = 8.dp).size(16.dp), tint = OnVideo.text)
            }
        }
        Row(Modifier.height(36.dp).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            selected.forEach { Icon(shortcutIcon(it), null, Modifier.padding(horizontal = 9.dp).size(17.dp), tint = OnVideo.text.copy(alpha = 0.9f)) }
        }
        Spacer(Modifier.height(48.dp))
    }
}

/** Selected shortcuts: hold the handle and drag to reorder, or remove. */
@Composable
private fun SelectedList(selected: List<PlayerShortcut>, onMove: (Int, Int) -> Unit, onRemove: (PlayerShortcut) -> Unit) {
    val c = MoodyTheme.colors
    val rowPx = with(LocalDensity.current) { 52.dp.toPx() }
    var dragIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    Column {
        selected.forEachIndexed { i, sc ->
            val dragging = i == dragIndex
            Row(
                Modifier
                    .zIndex(if (dragging) 1f else 0f)
                    .offset { IntOffset(0, if (dragging) dragOffset.roundToInt() else 0) }
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(Radii.md)
                    .background(if (dragging) c.raised else Color.Transparent),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(40.dp).pointerInput(selected) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { dragIndex = i; dragOffset = 0f },
                            onDragEnd = {
                                val target = (dragIndex + (dragOffset / rowPx).roundToInt()).coerceIn(0, selected.lastIndex)
                                onMove(dragIndex, target)
                                dragIndex = -1; dragOffset = 0f
                            },
                            onDragCancel = { dragIndex = -1; dragOffset = 0f },
                        ) { ch, d -> ch.consume(); dragOffset += d.y }
                    }.semantics { contentDescription = "Hold and drag to reorder ${sc.label}" },
                    contentAlignment = Alignment.Center,
                ) { Icon(MoodyIcons.DragHandle, null, Modifier.size(18.dp), tint = c.text3) }
                ShortcutTile(sc)
                Spacer(Modifier.width(12.dp))
                Text(sc.label, style = MoodyTheme.type.body, color = c.text, modifier = Modifier.weight(1f))
                IconAction(MoodyIcons.Close, "Remove ${sc.label}", { onRemove(sc) }, tint = c.text2, size = 18.dp)
            }
        }
    }
}
