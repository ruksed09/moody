package com.moody.player.ui.music

import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.detectTapGestures
import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moody.player.design.MoodyTheme
import com.moody.player.design.components.IconAction
import com.moody.player.design.icons.MoodyIcons
import kotlinx.coroutines.delay

/**
 * The visualizer across the whole screen. System bars hide and the screen stays on; a tap brings the song
 * title and controls back for a few seconds. The style chips stay on screen.
 */
@Composable
fun VisualizerScreen(vm: NowPlayingViewModel, onBack: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    // The picture is always on black, so its chrome always uses the dark palette, whatever the app theme.
    val c = com.moody.player.design.DarkColors
    val view = LocalView.current
    var chrome by remember { mutableStateOf(true) }
    var poke by remember { mutableIntStateOf(0) }
    var tuneOpen by remember { mutableStateOf(false) }
    // Locked: only the picture shows and touches do nothing; a tap brings up a chip to hold for unlocking.
    var locked by remember { mutableStateOf(false) }
    var chip by remember { mutableStateOf(false) }
    var chipPoke by remember { mutableIntStateOf(0) }
    var holding by remember { mutableStateOf(false) }
    LaunchedEffect(chip, chipPoke, holding) { if (chip && !holding) { delay(3_000); chip = false } }
    androidx.activity.compose.BackHandler(locked) { chip = true; chipPoke++ }

    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        val ctl = window?.let { WindowCompat.getInsetsController(it, view) }
        ctl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ctl?.hide(WindowInsetsCompat.Type.systemBars())
        view.keepScreenOn = true
        onDispose {
            ctl?.show(WindowInsetsCompat.Type.systemBars())
            view.keepScreenOn = false
        }
    }
    val keep = rememberKeepControls()
    LaunchedEffect(chrome, poke, tuneOpen) { if (chrome && !tuneOpen && !keep) { delay(VIZ_CONTROLS_MS); chrome = false } }

    val frames = rememberVizFrames(active = true, playing = s.playing, s = settings)
    val fallback by com.moody.player.player.music.AudioTap.onFallbackEngine.collectAsStateWithLifecycle()
    val sideFailed by com.moody.player.player.music.AudioTap.sidecarFailed.collectAsStateWithLifecycle()
    androidx.compose.runtime.CompositionLocalProvider(com.moody.player.design.LocalMoodyColors provides c) {
    Box(
        Modifier.fillMaxSize().background(Color(0xFF040506))
            .clickable(remember { MutableInteractionSource() }, indication = null) {
                if (locked) { chip = true; chipPoke++ } else { chrome = !chrome; poke++ }
            },
    ) {
        if (fallback && sideFailed) VizUnavailable(Modifier.fillMaxSize())
        else VisualizerCanvas(
            settings.vizStyle, frames, c.accent, c.accentHi, settings.vizIntensity,
            // Centred between the top bar and the style picker, so the picture never runs under them.
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(start = 24.dp, end = 24.dp, top = 64.dp, bottom = 88.dp),
            item = s.current, hole = Color(0xFF040506),
        )
        AnimatedVisibility(chrome && !locked, enter = fadeIn(), exit = fadeOut()) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconAction(MoodyIcons.ArrowBack, "Back", onBack, tint = c.text)
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(s.current?.title ?: "Nothing playing", style = MoodyTheme.type.title, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        s.current?.artist?.let { Text(it, style = MoodyTheme.type.caption, color = c.text2, maxLines = 1) }
                    }
                    IconAction(if (s.playing) MoodyIcons.Pause else MoodyIcons.Play, if (s.playing) "Pause" else "Play", { vm.music.toggle(); poke++ }, tint = c.text)
                    IconAction(MoodyIcons.Equalizer, "Tune the visualizer", { tuneOpen = true }, tint = c.text)
                    IconAction(MoodyIcons.Lock, "Lock: show only the visual", { locked = true; chrome = false; chip = true; chipPoke++ }, tint = c.text)
                }
            }
        }
        // The style buttons come and go with the rest of the controls; a tap on the picture brings them back.
        AnimatedVisibility(
            chrome && !locked,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(androidx.compose.animation.core.tween(220)) + androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(220, easing = com.moody.player.design.components.Motion.Ease)) { it / 3 },
            exit = fadeOut(androidx.compose.animation.core.tween(160)) + androidx.compose.animation.slideOutVertically(androidx.compose.animation.core.tween(200)) { it / 3 },
        ) {
            VizStylePicker(
                settings.vizStyle, { v -> vm.updateViz { it.copy(vizStyle = v) }; poke++ },
                Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 12.dp, vertical = 20.dp).widthIn(max = 420.dp).fillMaxWidth(),
            )
        }
    }
    AnimatedVisibility(
        locked && chip,
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(bottom = 32.dp),
        enter = fadeIn(), exit = fadeOut(),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            HoldToUnlockChip(onHolding = { holding = it }) { locked = false; chip = false; chrome = true; poke++ }
        }
    }
    if (tuneOpen) VizTuneSheet(settings, { f -> vm.updateViz(f) }, onDismiss = { tuneOpen = false; poke++ })
    }
}

/**
 * "Hold to unlock": a ring fills while the chip is held and unlocks after 0.8 s, with a tick. Letting go
 * early empties the ring. Screen readers get a plain Unlock action.
 */
@Composable
internal fun HoldToUnlockChip(onHolding: (Boolean) -> Unit = {}, onUnlock: () -> Unit) {
    val c = MoodyTheme.colors
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Row(
        Modifier
            .clip(com.moody.player.design.Radii.pill)
            .background(Color(0xE6101214))
            .semantics(mergeDescendants = true) {
                role = androidx.compose.ui.semantics.Role.Button
                onClick("Unlock") { onUnlock(); true }
            }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    onHolding(true)
                    val run = scope.launch {
                        progress.animateTo(1f, androidx.compose.animation.core.tween(800, easing = androidx.compose.animation.core.LinearEasing))
                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onUnlock()
                    }
                    tryAwaitRelease()
                    onHolding(false)
                    if (progress.value < 1f) { run.cancel(); scope.launch { progress.animateTo(0f, androidx.compose.animation.core.tween(200)) } }
                })
            }
            .padding(start = 12.dp, end = 18.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val w = 2.dp.toPx()
                drawCircle(Color.White.copy(alpha = 0.18f), radius = size.minDimension / 2 - w / 2, style = androidx.compose.ui.graphics.drawscope.Stroke(w))
                drawArc(
                    c.accent, -90f, 360f * progress.value, useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                )
            }
            androidx.compose.material3.Icon(MoodyIcons.Unlock, null, Modifier.size(14.dp), tint = Color.White)
        }
        Spacer(Modifier.width(10.dp))
        Text("Hold to unlock", style = MoodyTheme.type.label, color = Color.White)
    }
}
