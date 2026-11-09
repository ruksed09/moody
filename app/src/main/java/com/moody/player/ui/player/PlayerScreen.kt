package com.moody.player.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.systemGestureExclusion
import com.moody.player.domain.Aspect
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moody.player.design.MoodyTheme
import com.moody.player.design.OnVideo
import com.moody.player.design.Radii
import com.moody.player.design.components.Motion
import com.moody.player.design.icons.MoodyIcons
import com.moody.player.domain.Decoder
import com.moody.player.domain.formatDuration
import com.moody.player.player.video.PlayerState
import kotlinx.coroutines.delay
import org.videolan.libvlc.util.VLCVideoLayout

/** Things the activity owns (window, orientation, PiP) that the screen can trigger. */
data class PlayerHost(
    val onBack: () -> Unit,
    val onPip: (() -> Unit)?,
    val onRotate: () -> Unit,
    val orientationLabel: String,
)

@Composable
fun PlayerScreen(vm: PlayerViewModel, host: PlayerHost, inPip: Boolean) {
    val st by vm.engine.state.collectAsStateWithLifecycle()
    val index by vm.index.collectAsStateWithLifecycle()
    val resumedAt by vm.resumedAt.collectAsStateWithLifecycle()
    val ab by vm.ab.collectAsStateWithLifecycle()
    var controls by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var scrubbing by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf<PlayerMenu?>(null) }
    var showRemaining by remember { mutableStateOf(false) }
    var poke by remember { mutableStateOf(0) }

    // Controls hide 3 s after the last touch while playing.
    LaunchedEffect(controls, st.playing, scrubbing, menu, poke) {
        if (controls && st.playing && !scrubbing && menu == null) { delay(3_000); controls = false }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx -> VLCVideoLayout(ctx).also { vm.engine.attach(it) } },
            onRelease = { vm.engine.detach(it) },
            modifier = Modifier.fillMaxSize(),
        )
        if (inPip) return@Box

        GestureLayer(
            vm = vm,
            state = st,
            locked = locked,
            onTap = { controls = !controls; poke++ },
            modifier = Modifier.fillMaxSize(),
        )

        if (st.buffering && st.error == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(40.dp), color = MoodyTheme.colors.accent, strokeWidth = 2.dp, trackColor = OnVideo.rail)
        }

        AnimatedVisibility(controls && !locked, enter = fadeIn(Motion.base()), exit = fadeOut(Motion.base())) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().height(140.dp).background(Brush.verticalGradient(listOf(Color(0xA608070B), Color.Transparent))))
                Box(Modifier.fillMaxWidth().height(220.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE008070B)))))
                Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    TitleBar(vm.current?.title.orEmpty(), st, host.onBack) { menu = PlayerMenu.MORE; poke++ }
                    Spacer(Modifier.weight(1f))
                    BottomControls(
                        st = st, showRemaining = showRemaining, ab = ab,
                        canPrev = vm.hasPrev || st.positionMs > 5_000, hasNext = vm.hasNext,
                        onPrev = { vm.prev(); poke++ }, onToggle = { vm.engine.toggle(); poke++ }, onNext = { vm.next(); poke++ },
                        onToggleTime = { showRemaining = !showRemaining; poke++ },
                        onSeek = { vm.engine.seekTo(it); poke++ },
                        onScrubbing = { scrubbing = it },
                        onLock = { locked = true; controls = false; vm.toast("Controls locked") },
                        onSpeed = { menu = PlayerMenu.SPEED; poke++ },
                        onFit = {
                            val a = if (st.aspect == Aspect.FILL) Aspect.FIT else Aspect.FILL
                            vm.engine.setAspect(a); vm.toast(if (a == Aspect.FILL) "Fill screen" else "Fit to screen"); poke++
                        },
                    )
                }
            }
        }

        if (locked) {
            LockedOverlay(controls) { locked = false; controls = true; vm.toast("Controls unlocked") }
        }

        resumedAt?.let { at ->
            ResumeChip(at, onStartOver = vm::startOver, modifier = Modifier.align(Alignment.BottomStart).windowInsetsPadding(WindowInsets.safeDrawing).padding(start = 16.dp, bottom = if (controls) 188.dp else 24.dp))
        }

        st.error?.let { msg ->
            ErrorSheet(
                message = msg,
                canSoftware = st.decoder != Decoder.SW,
                hasNext = vm.hasNext,
                onRetry = { vm.retry() },
                onSoftware = { vm.retry(Decoder.SW) },
                onNext = { vm.next() },
                onBack = host.onBack,
            )
        }

        menu?.let { m -> PlayerMenus(m, vm, st, host, onNavigate = { menu = it }, onDismiss = { menu = null }) }
    }
}

@Composable
private fun TitleBar(title: String, st: PlayerState, onBack: () -> Unit, onMore: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        VideoIcon(MoodyIcons.ArrowBack, "Back", onBack)
        Text(title, style = MoodyTheme.type.heading, color = OnVideo.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 4.dp))
        resolution(st)?.let {
            Text(
                it, style = MoodyTheme.type.monoSmall, color = OnVideo.text.copy(alpha = 0.88f),
                modifier = Modifier.border(1.dp, OnVideo.text.copy(alpha = 0.24f), Radii.sm).padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
        VideoIcon(MoodyIcons.More, "More options", onMore)
    }
}

private fun resolution(st: PlayerState): String? = when {
    st.videoHeight >= 2000 || st.videoWidth >= 3800 -> "4K"
    st.videoHeight >= 1000 || st.videoWidth >= 1900 -> "1080p"
    st.videoHeight >= 700 || st.videoWidth >= 1260 -> "720p"
    st.videoHeight > 0 -> "${st.videoHeight}p"
    else -> null
}

/**
 * The bottom section, built for vertical video: seek bar with times, then the three primary controls
 * (play/pause clearly largest), then a small row of lock, speed and fit/fill. Everything else is in the overflow menu.
 */
@Composable
private fun BottomControls(
    st: PlayerState,
    showRemaining: Boolean,
    ab: Pair<Long?, Long?>,
    canPrev: Boolean,
    hasNext: Boolean,
    onPrev: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onToggleTime: () -> Unit,
    onSeek: (Long) -> Unit,
    onScrubbing: (Boolean) -> Unit,
    onLock: () -> Unit,
    onSpeed: () -> Unit,
    onFit: () -> Unit,
) {
    val t = MoodyTheme.type
    val accent = MoodyTheme.colors.accent
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp)) {
        SeekBar(st.positionMs, st.durationMs, onSeek, onScrubbing, Modifier.fillMaxWidth().systemGestureExclusion(), ab.first, ab.second)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(formatDuration(st.positionMs).let { if (it == "--:--") "0:00" else it }, style = t.mono, color = OnVideo.text)
            Spacer(Modifier.weight(1f))
            Text(
                if (showRemaining) "-" + formatDuration((st.durationMs - st.positionMs).coerceAtLeast(1000)) else formatDuration(st.durationMs),
                style = t.mono, color = OnVideo.text.copy(alpha = 0.7f),
                modifier = Modifier.clip(Radii.sm).clickable(onClickLabel = "Show remaining time", onClick = onToggleTime).padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            VideoIcon(MoodyIcons.Prev, "Previous", onPrev, size = 24.dp, enabled = canPrev)
            Spacer(Modifier.width(28.dp))
            val icon = when { st.ended -> MoodyIcons.Replay; st.playing -> MoodyIcons.Pause; else -> MoodyIcons.Play }
            Box(
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onToggle)
                    .semantics { contentDescription = when { st.ended -> "Replay"; st.playing -> "Pause"; else -> "Play" } },
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, Modifier.size(40.dp), tint = OnVideo.text) }
            Spacer(Modifier.width(28.dp))
            VideoIcon(MoodyIcons.Next, "Next", onNext, size = 24.dp, enabled = hasNext)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            VideoIcon(MoodyIcons.Lock, "Lock controls", onLock, size = 20.dp, tint = OnVideo.text.copy(alpha = 0.85f))
            SpeedButton(st.rate, onSpeed)
            val filled = st.aspect == Aspect.FILL
            VideoIcon(
                if (filled) MoodyIcons.FitScreen else MoodyIcons.Fullscreen,
                if (filled) "Fit to screen" else "Fill screen", onFit, size = 20.dp,
                tint = if (filled) accent else OnVideo.text.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun SpeedButton(rate: Float, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = "Speed ${rateLabel(rate)}" },
        contentAlignment = Alignment.Center,
    ) {
        Text(rateLabel(rate), style = MoodyTheme.type.label, color = if (rate == 1f) OnVideo.text.copy(alpha = 0.85f) else MoodyTheme.colors.accent)
    }
}

fun rateLabel(r: Float): String = (if (r % 1f == 0f) "%.0f" else if ((r * 10) % 1f == 0f) "%.1f" else "%.2f").format(r) + "×"

@Composable
fun VideoIcon(icon: ImageVector, label: String, onClick: () -> Unit, size: Dp = 22.dp, enabled: Boolean = true, tint: Color = OnVideo.text) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(size), tint = if (enabled) tint else tint.copy(alpha = 0.35f)) }
}

@Composable
private fun LockedOverlay(showHint: Boolean, onUnlock: () -> Unit) {
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp)) {
        Row(
            Modifier
                .align(Alignment.CenterStart)
                .clip(Radii.pill)
                .background(OnVideo.ink)
                .clickable(role = Role.Button, onClick = onUnlock)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .semantics { contentDescription = "Unlock controls" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(MoodyIcons.Unlock, null, Modifier.size(20.dp), tint = OnVideo.text)
            if (showHint) {
                Spacer(Modifier.width(8.dp))
                Text("Tap to unlock", style = MoodyTheme.type.label, color = OnVideo.text)
            }
        }
    }
}

@Composable
private fun ResumeChip(at: Long, onStartOver: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(Radii.pill).background(OnVideo.ink).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Resumed at ${formatDuration(at)}", style = MoodyTheme.type.label, color = OnVideo.text)
        Box(Modifier.padding(start = 8.dp).height(16.dp).width(1.dp).background(OnVideo.text.copy(alpha = 0.2f)))
        Text(
            "Start over", style = MoodyTheme.type.label, color = MoodyTheme.colors.accent,
            modifier = Modifier.clip(Radii.pill).clickable(role = Role.Button, onClick = onStartOver).padding(horizontal = 12.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun ErrorSheet(message: String, canSoftware: Boolean, hasNext: Boolean, onRetry: () -> Unit, onSoftware: () -> Unit, onNext: () -> Unit, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xED0B0A0F)).windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(400.dp)) {
            Text("Playback stopped", style = MoodyTheme.type.title, color = OnVideo.text)
            Spacer(Modifier.height(8.dp))
            Text(message, style = MoodyTheme.type.body, color = OnVideo.text.copy(alpha = 0.72f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OverlayButton("Try again", onRetry, primary = true)
                if (canSoftware) OverlayButton("Use software decoder", onSoftware)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasNext) OverlayButton("Next video", onNext)
                OverlayButton("Back to library", onBack)
            }
        }
    }
}

@Composable
fun OverlayButton(text: String, onClick: () -> Unit, primary: Boolean = false) {
    val c = MoodyTheme.colors
    Box(
        Modifier
            .height(44.dp)
            .clip(Radii.md)
            .background(if (primary) c.accent else Color.Transparent)
            .border(1.dp, if (primary) c.accent else OnVideo.text.copy(alpha = 0.24f), Radii.md)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MoodyTheme.type.label, color = if (primary) c.accentInk else OnVideo.text) }
}
