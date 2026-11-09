package com.moody.player.ui.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.provider.Settings as SysSettings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moody.player.design.MoodyTheme
import com.moody.player.design.OnVideo
import com.moody.player.design.Radii
import com.moody.player.design.components.Motion
import com.moody.player.design.icons.MoodyIcons
import com.moody.player.domain.Aspect
import com.moody.player.domain.formatDelta
import com.moody.player.domain.formatDuration
import com.moody.player.domain.levelDelta
import com.moody.player.domain.seekDeltaMs
import com.moody.player.player.video.PlayerState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import com.moody.player.domain.PlayerGesture
import com.moody.player.domain.TapZone
import com.moody.player.domain.tapZone

private enum class Drag { NONE, SEEK, BRIGHTNESS, VOLUME, PINCH }

private const val DOUBLE_TAP_MS = 260L

/**
 * MX-style gestures over the video:
 * tap toggles controls; double-tap left/right seeks (taps add up), double-tap the middle plays/pauses;
 * long-press plays at 2x while held;
 * vertical swipe left = brightness, right = volume; horizontal swipe seeks with a preview; pinch switches Fit/Fill.
 */
@Composable
fun GestureLayer(vm: PlayerViewModel, state: PlayerState, locked: Boolean, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val slop = LocalViewConfiguration.current.touchSlop
    val st by rememberUpdatedState(state)
    val tap by rememberUpdatedState(onTap)

    var level by remember { mutableStateOf<Pair<Drag, Float>?>(null) }
    var seekPreview by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var ping by remember { mutableStateOf<Pair<Boolean, Int>?>(null) } // (right side, seconds)
    var boosted by remember { mutableStateOf(false) }
    var lastTapAt by remember { mutableLongStateOf(0L) }
    var lastZone by remember { mutableStateOf(TapZone.CENTER) }
    var flash by remember { mutableStateOf<ImageVector?>(null) }
    var flashJob by remember { mutableStateOf<Job?>(null) }
    val density = LocalDensity.current
    val edges = WindowInsets.systemGestures
    val layoutDir = LocalLayoutDirection.current
    var pendingTap by remember { mutableStateOf<Job?>(null) }
    var pingJob by remember { mutableStateOf<Job?>(null) }
    var brightness by remember { mutableFloatStateOf(-1f) }

    fun currentBrightness(): Float {
        if (brightness >= 0f) return brightness
        val w = activity?.window?.attributes?.screenBrightness ?: -1f
        return if (w >= 0f) w else runCatching { SysSettings.System.getInt(context.contentResolver, SysSettings.System.SCREEN_BRIGHTNESS) / 255f }.getOrDefault(0.5f)
    }

    fun setBrightness(v: Float) {
        brightness = v.coerceIn(0.01f, 1f)
        activity?.window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = brightness } }
    }

    val gesturesOn = settings.gestures && !locked
    val off = settings.disabledGestures
    fun on(g: PlayerGesture) = gesturesOn && g !in off
    val stepSec = settings.doubleTapSeekSec

    Box(modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(gesturesOn, stepSec, off) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val start = down.position
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        var mode = Drag.NONE
                        var moved = false
                        var startLevel = 0f
                        var startPos = 0L
                        var pinchStart = 0f
                        var pinchRatio = 1f
                        var longPress: Job? = null
                        // Drags that start in the system gesture areas (back swipe edges, Home bar, status bar)
                        // belong to Android, not the player.
                        val inSystemEdge = with(density) {
                            start.x < edges.getLeft(this, layoutDir) || start.x > w - edges.getRight(this, layoutDir) ||
                                start.y > h - edges.getBottom(this) || start.y < edges.getTop(this)
                        }
                        if (on(PlayerGesture.HOLD_SPEED) && !inSystemEdge) {
                            longPress = scope.launch {
                                delay(450)
                                if (!moved && st.playing) {
                                    boosted = true
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    vm.engine.setRate(2f)
                                }
                            }
                        }
                        var prevRate = st.rate
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            if (!gesturesOn) continue
                            if (pressed.size >= 2 && mode != Drag.SEEK && on(PlayerGesture.PINCH_ZOOM)) {
                                val d = (pressed[0].position - pressed[1].position).getDistance()
                                if (mode != Drag.PINCH) { mode = Drag.PINCH; pinchStart = d; moved = true; longPress?.cancel() }
                                else if (pinchStart > 0f) pinchRatio = d / pinchStart
                                event.changes.forEach { it.consume() }
                                continue
                            }
                            val p = pressed.first()
                            val delta = p.position - start
                            if (mode == Drag.NONE && !boosted && (abs(delta.x) > slop || abs(delta.y) > slop)) {
                                moved = true
                                longPress?.cancel()
                                mode = when {
                                    inSystemEdge -> Drag.NONE
                                    abs(delta.x) > abs(delta.y) -> if (on(PlayerGesture.SWIPE_SEEK)) Drag.SEEK.also { startPos = st.positionMs } else Drag.NONE
                                    start.x < w / 2 -> if (on(PlayerGesture.BRIGHTNESS)) Drag.BRIGHTNESS.also { startLevel = currentBrightness() } else Drag.NONE
                                    else -> if (on(PlayerGesture.VOLUME)) Drag.VOLUME.also {
                                        startLevel = audio.getStreamVolume(AudioManager.STREAM_MUSIC) / audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
                                    } else Drag.NONE
                                }
                            }
                            when (mode) {
                                Drag.SEEK -> {
                                    val d = seekDeltaMs(delta.x, w, st.durationMs)
                                    val target = (startPos + d).coerceIn(0, st.durationMs.coerceAtLeast(0))
                                    seekPreview = target to (target - startPos)
                                    p.consume()
                                }
                                Drag.BRIGHTNESS -> {
                                    val v = (startLevel + levelDelta(delta.y, h * 0.8f)).coerceIn(0f, 1f)
                                    setBrightness(v)
                                    level = Drag.BRIGHTNESS to v
                                    p.consume()
                                }
                                Drag.VOLUME -> {
                                    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                    val v = (startLevel + levelDelta(delta.y, h * 0.8f)).coerceIn(0f, 1f)
                                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, (v * max).toInt(), 0)
                                    level = Drag.VOLUME to v
                                    p.consume()
                                }
                                else -> Unit
                            }
                        }
                        longPress?.cancel()
                        // Release.
                        when {
                            boosted -> { boosted = false; vm.engine.setRate(if (prevRate == 2f) 1f else prevRate) }
                            mode == Drag.SEEK -> { seekPreview?.let { vm.engine.seekTo(it.first) }; seekPreview = null }
                            mode == Drag.PINCH -> {
                                val a = when {
                                    pinchRatio > 1.12f -> Aspect.FILL
                                    pinchRatio < 0.9f -> Aspect.FIT
                                    else -> null
                                }
                                if (a != null && a != st.aspect) { vm.engine.setAspect(a); vm.toast(if (a == Aspect.FILL) "Zoomed to fill" else "Fit to screen") }
                            }
                            mode == Drag.BRIGHTNESS || mode == Drag.VOLUME -> scope.launch { delay(600); level = null }
                            !moved -> {
                                val now = System.currentTimeMillis()
                                val zone = tapZone(start.x, w)
                                val right = zone == TapZone.RIGHT
                                val doubleOn = if (zone == TapZone.CENTER) on(PlayerGesture.DOUBLE_TAP_PLAY) else on(PlayerGesture.DOUBLE_TAP_SEEK)
                                // A second tap in the same zone while the first is still waiting is a double-tap;
                                // further taps on a side keep adding to the seek.
                                val second = pendingTap?.isActive == true && zone == lastZone && now - lastTapAt < DOUBLE_TAP_MS
                                val chaining = zone != TapZone.CENTER && ping?.first == right && now - lastTapAt < 650
                                if (doubleOn && (second || chaining)) {
                                    pendingTap?.cancel()
                                    if (zone == TapZone.CENTER) {
                                        vm.dismissResume()
                                        val willPlay = !st.playing
                                        vm.engine.toggle()
                                        flash = if (willPlay) MoodyIcons.Play else MoodyIcons.Pause
                                        flashJob?.cancel()
                                        flashJob = scope.launch { delay(550); flash = null }
                                    } else {
                                        val secs = (if (chaining) ping!!.second else 0) + stepSec
                                        vm.engine.seekBy(if (right) stepSec * 1000L else -stepSec * 1000L)
                                        ping = right to secs
                                        pingJob?.cancel()
                                        pingJob = scope.launch { delay(650); ping = null }
                                    }
                                    lastTapAt = now
                                } else {
                                    lastTapAt = now
                                    lastZone = zone
                                    pendingTap?.cancel()
                                    // Single tap waits out the double-tap window only where a double-tap is possible.
                                    pendingTap = scope.launch { if (doubleOn) delay(DOUBLE_TAP_MS); tap() }
                                }
                            }
                        }
                    }
                },
        )

        // Feedback.
        level?.let { (kind, v) ->
            LevelIndicator(
                if (kind == Drag.BRIGHTNESS) MoodyIcons.Brightness else if (v == 0f) MoodyIcons.VolumeMute else if (v < 0.5f) MoodyIcons.VolumeLow else MoodyIcons.VolumeHigh,
                v,
                Modifier.align(if (kind == Drag.BRIGHTNESS) Alignment.CenterStart else Alignment.CenterEnd).padding(horizontal = 48.dp),
            )
        }
        seekPreview?.let { (target, delta) ->
            Column(Modifier.align(Alignment.Center).clip(Radii.md).background(OnVideo.ink).padding(horizontal = 20.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${formatDuration(target).let { if (it == "--:--") "0:00" else it } } / ${formatDuration(st.durationMs)}", style = MoodyTheme.type.heading, color = OnVideo.text)
                Text(formatDelta(delta), style = MoodyTheme.type.mono, color = MoodyTheme.colors.accent)
            }
        }
        AnimatedVisibility(ping != null, Modifier.align(if (ping?.first == true) Alignment.CenterEnd else Alignment.CenterStart).padding(horizontal = 56.dp), enter = fadeIn(Motion.fast()), exit = fadeOut(Motion.base())) {
            val p = ping
            Column(Modifier.clip(Radii.lg).background(OnVideo.ink).padding(horizontal = 16.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(if (p?.first == true) MoodyIcons.SeekForward else MoodyIcons.SeekBack, null, Modifier.size(22.dp), tint = OnVideo.text)
                Spacer(Modifier.height(4.dp))
                Text("${if (p?.first == true) "+" else "-"}${p?.second ?: 0} s", style = MoodyTheme.type.mono, color = OnVideo.text)
            }
        }
        AnimatedVisibility(
            flash != null, Modifier.align(Alignment.Center),
            enter = fadeIn(Motion.fast()) + scaleIn(initialScale = 0.8f), exit = fadeOut(Motion.base()) + scaleOut(targetScale = 1.1f),
        ) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(OnVideo.ink), contentAlignment = Alignment.Center) {
                Icon(flash ?: MoodyIcons.Play, null, Modifier.size(28.dp), tint = OnVideo.text)
            }
        }
        if (boosted) {
            Text(
                "2× speed", style = MoodyTheme.type.label, color = OnVideo.text,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 56.dp).clip(Radii.pill).background(OnVideo.ink).padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun LevelIndicator(icon: ImageVector, value: Float, modifier: Modifier = Modifier) {
    val accent = MoodyTheme.colors.accent
    Column(
        modifier.width(56.dp).clip(Radii.lg).background(OnVideo.ink).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("${(value * 100).toInt()}", style = MoodyTheme.type.mono, color = OnVideo.text)
        Canvas(Modifier.width(4.dp).height(120.dp)) {
            val r = CornerRadius(size.width / 2)
            drawRoundRect(OnVideo.rail, cornerRadius = r)
            val fill = size.height * value
            drawRoundRect(accent, Offset(0f, size.height - fill), Size(size.width, fill), r)
        }
        Icon(icon, null, Modifier.size(20.dp), tint = OnVideo.text)
    }
}
