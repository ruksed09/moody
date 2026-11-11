package com.moody.player.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
    var tracks by remember { mutableStateOf<TrackKind?>(null) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val loop by vm.loop.collectAsStateWithLifecycle()
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    val shuffle by vm.shuffle.collectAsStateWithLifecycle()
    val studio by vm.studio.collectAsStateWithLifecycle()
    val studioOn by vm.studioOn.collectAsStateWithLifecycle()
    var transcript by remember { mutableStateOf(false) }

    // Controls hide 3 s after the last touch while playing.
    LaunchedEffect(controls, st.playing, scrubbing, menu, tracks, poke, transcript) {
        if (controls && st.playing && !scrubbing && menu == null && tracks == null && !transcript) { delay(3_000); controls = false }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx -> VLCVideoLayout(ctx).also { vm.engine.attach(it) } },
            onRelease = { vm.engine.detach(it) },
            modifier = Modifier.fillMaxSize(),
        )
        // Subtitle files Moody draws itself (Subtitle Studio). Shown in picture-in-picture too.
        studio?.takeIf { studioOn }?.let { d -> SubtitleOverlay(d, st, settings, controlsShown = controls && !locked && !inPip) }
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

        // Finished with Loop off: a Replay button in the centre; the top controls step away, the bottom row stays usable.
        val ended = st.ended && st.error == null
        AnimatedVisibility(ended && !locked, Modifier.align(Alignment.Center), enter = fadeIn(Motion.base()), exit = fadeOut(Motion.fast())) {
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(OnVideo.ink)
                    .clickable(role = Role.Button) { vm.dismissResume(); vm.engine.play(); poke++ }
                    .semantics { contentDescription = "Replay" },
                contentAlignment = Alignment.Center,
            ) { Icon(MoodyIcons.Replay, null, Modifier.size(34.dp), tint = OnVideo.text) }
        }

        AnimatedVisibility((controls || ended) && !locked, enter = fadeIn(Motion.base()), exit = fadeOut(Motion.base())) {
            Box(Modifier.fillMaxSize()) {
                if (!ended) Box(Modifier.fillMaxWidth().height(150.dp).background(Brush.verticalGradient(listOf(Color(0xB308070B), Color.Transparent))))
                Box(Modifier.fillMaxWidth().height(200.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE008070B)))))
                Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    AnimatedVisibility(!ended, enter = fadeIn(Motion.base()), exit = fadeOut(Motion.fast())) {
                    Column {
                    TitleBar(
                        title = vm.current?.title.orEmpty(), st = st, onBack = host.onBack,
                        onAudio = { tracks = TrackKind.AUDIO; poke++ },
                        onCc = { tracks = TrackKind.SUBS; poke++ },
                        onMore = { menu = PlayerMenu.MORE; poke++ },
                    )
                    ShortcutRow(vm, st, settings.playerShortcuts, ab, loop, shuffle, host.onRotate, onTranscript = {
                        if (vm.studio.value != null) transcript = true else vm.toast("Load a subtitle file to see its transcript")
                    }) { poke++ }
                    }
                    }
                    Spacer(Modifier.weight(1f))
                    BottomControls(
                        st = st, showRemaining = showRemaining, ab = ab, marks = bookmarks.map { it.positionMs },
                        canPrev = vm.hasPrev || st.positionMs > 5_000, hasNext = vm.hasNext || (shuffle && vm.queue.size > 1),
                        onPrev = { vm.prev(); poke++ },
                        onToggle = { vm.dismissResume(); vm.engine.toggle(); poke++ },
                        onNext = { vm.next(); poke++ },
                        onToggleTime = { showRemaining = !showRemaining; poke++ },
                        onSeek = { vm.dismissResume(); vm.engine.seekTo(it); poke++ },
                        onScrubbing = { scrubbing = it; if (it) vm.dismissResume() },
                        onLock = { locked = true; controls = false; vm.toast("Controls locked") },
                        onScreenSize = {
                            val order = listOf(Aspect.FIT, Aspect.FILL, Aspect.STRETCH)
                            val a = order[(order.indexOf(st.aspect).coerceAtLeast(0) + 1) % order.size]
                            vm.engine.setAspect(a); vm.toast(a.label); poke++
                        },
                        onPopup = host.onPip ?: { vm.toast("Floating playback isn't available on this phone") },
                    )
                }
            }
        }

        if (locked) {
            LockedOverlay(controls) { locked = false; controls = true; vm.toast("Controls unlocked") }
        }

        // A-B: A is set, B not yet: offer the second mark right on the video.
        if (ab.first != null && ab.second == null && !locked) {
            Text(
                "Set B", style = MoodyTheme.type.label, color = MoodyTheme.colors.accentInk,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(end = 16.dp, bottom = if (controls) 132.dp else 24.dp)
                    .clip(Radii.pill)
                    .background(MoodyTheme.colors.accent)
                    .clickable(role = Role.Button) { vm.markB(); poke++ }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }

        resumedAt?.takeIf { it.first == vm.current?.uri }?.let { (_, at) ->
            ResumeChip(at, onStartOver = vm::startOver, modifier = Modifier.align(Alignment.BottomStart).windowInsetsPadding(WindowInsets.safeDrawing).padding(start = 16.dp, bottom = if (controls) 132.dp else 24.dp))
        }

        tracks?.let { kind -> TrackPopover(kind, vm, st, onDismiss = { tracks = null }) }

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

        menu?.let { m -> PlayerMenus(m, vm, st, host, onNavigate = { menu = it }, onDismiss = { menu = null }, onTranscript = { menu = null; transcript = true }) }
        if (transcript) TranscriptPanel(vm, st, onDismiss = { transcript = false; poke++ })
    }
}

/** Back, filename, resolution, Audio, CC, overflow. The filename gives way first; the buttons never move. */
@Composable
private fun TitleBar(title: String, st: PlayerState, onBack: () -> Unit, onAudio: () -> Unit, onCc: () -> Unit, onMore: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        VideoIcon(MoodyIcons.ArrowBack, "Back", onBack, size = 22.dp)
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            val maxChars = (maxWidth.value / 8.2f).toInt().coerceAtLeast(8)
            Text(middleEllipsize(title, maxChars), style = MoodyTheme.type.heading, color = OnVideo.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        resolution(st)?.let {
            Text(
                it, style = MoodyTheme.type.monoSmall, color = OnVideo.text.copy(alpha = 0.88f),
                modifier = Modifier.padding(end = 2.dp).border(1.dp, OnVideo.text.copy(alpha = 0.28f), Radii.sm).padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
        VideoIcon(MoodyIcons.Audio, "Audio tracks", onAudio, size = 20.dp)
        VideoIcon(MoodyIcons.Cc, "Subtitles", onCc, size = 20.dp)
        VideoIcon(MoodyIcons.More, "More options", onMore, size = 20.dp)
    }
}

/** "Snapinsta.to_AQM4igzf…X8NU.mp4": keeps the start and the end, which is where names differ. */
fun middleEllipsize(text: String, maxChars: Int): String {
    if (text.length <= maxChars || maxChars < 8) return text
    val tail = (maxChars / 3).coerceAtLeast(3)
    val head = maxChars - tail - 1
    return text.take(head).trimEnd() + "\u2026" + text.takeLast(tail).trimStart()
}

private fun resolution(st: PlayerState): String? {
    val w = if (st.displayWidth > 0) st.displayWidth else st.videoWidth
    val h = if (st.displayHeight > 0) st.displayHeight else st.videoHeight
    val short = minOf(w, h)
    val long = maxOf(w, h)
    return when {
        short <= 0 -> null
        short >= 2000 || long >= 3800 -> "4K"
        short >= 1000 || long >= 1900 -> "1080p"
        short >= 700 || long >= 1260 -> "720p"
        else -> "${short}p"
    }
}

/** The user's chosen shortcuts, small and in one row under the title bar (MX style). Never the six fixed controls. */
@Composable
private fun ShortcutRow(
    vm: PlayerViewModel, st: PlayerState, shortcuts: List<com.moody.player.domain.PlayerShortcut>,
    ab: Pair<Long?, Long?>, loop: com.moody.player.domain.LoopMode, shuffle: Boolean, onRotate: () -> Unit, onTranscript: () -> Unit, onUse: () -> Unit,
) {
    if (shortcuts.isEmpty()) return
    val accent = MoodyTheme.colors.accent
    val idle = OnVideo.text.copy(alpha = 0.9f)
    Row(Modifier.padding(start = 8.dp).horizontalScroll(androidx.compose.foundation.rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
        shortcuts.forEach { sc ->
            val (icon, label, active, action) = when (sc) {
                com.moody.player.domain.PlayerShortcut.LOOP -> Quad(
                    if (loop == com.moody.player.domain.LoopMode.ONE) MoodyIcons.RepeatOne else MoodyIcons.Repeat,
                    "Loop: ${loop.label}", loop != com.moody.player.domain.LoopMode.OFF, vm::cycleLoop,
                )
                com.moody.player.domain.PlayerShortcut.SHUFFLE -> Quad(MoodyIcons.Shuffle, "Shuffle", shuffle, vm::toggleShuffle)
                com.moody.player.domain.PlayerShortcut.SCREENSHOT -> Quad(MoodyIcons.Camera, "Screenshot", false, vm::screenshot)
                com.moody.player.domain.PlayerShortcut.AB_REPEAT -> Quad(MoodyIcons.AbRepeat, "A-B repeat", ab.first != null, vm::toggleAb)
                com.moody.player.domain.PlayerShortcut.MUTE -> Quad(if (st.muted) MoodyIcons.VolumeMute else MoodyIcons.VolumeHigh, if (st.muted) "Unmute" else "Mute", st.muted, vm::toggleMute)
                com.moody.player.domain.PlayerShortcut.ROTATE -> Quad(MoodyIcons.Rotate, "Rotate screen", false, onRotate)
                com.moody.player.domain.PlayerShortcut.BOOKMARK -> Quad(MoodyIcons.Bookmark, "Bookmark this moment", false, vm::addBookmark)
                com.moody.player.domain.PlayerShortcut.TRANSCRIPT -> Quad(MoodyIcons.Lyrics, "Transcript", false, onTranscript)
            }
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button) { action(); onUse() }.semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, Modifier.size(18.dp), tint = if (active) accent else idle) }
        }
    }
}

private data class Quad(val icon: ImageVector, val label: String, val active: Boolean, val action: () -> Unit)

/**
 * Progress bar with times, then exactly six controls in one row:
 * Lock, Previous, Play/Pause, Next, Screen size, Popup. The middle three are larger. Nothing else goes here.
 */
@Composable
private fun BottomControls(
    st: PlayerState,
    showRemaining: Boolean,
    ab: Pair<Long?, Long?>,
    marks: List<Long>,
    canPrev: Boolean,
    hasNext: Boolean,
    onPrev: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onToggleTime: () -> Unit,
    onSeek: (Long) -> Unit,
    onScrubbing: (Boolean) -> Unit,
    onLock: () -> Unit,
    onScreenSize: () -> Unit,
    onPopup: () -> Unit,
) {
    val t = MoodyTheme.type
    val accent = MoodyTheme.colors.accent
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 6.dp)) {
        SeekBar(st.positionMs, st.durationMs, onSeek, onScrubbing, Modifier.fillMaxWidth().systemGestureExclusion(), ab.first, ab.second, marks)
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(formatDuration(st.positionMs).let { if (it == "--:--") "0:00" else it }, style = t.mono, color = OnVideo.text)
            Spacer(Modifier.weight(1f))
            Text(
                if (showRemaining) "-" + formatDuration((st.durationMs - st.positionMs).coerceAtLeast(1000)) else formatDuration(st.durationMs),
                style = t.mono, color = OnVideo.text.copy(alpha = 0.7f),
                modifier = Modifier.clip(Radii.sm).clickable(onClickLabel = "Show remaining time", onClick = onToggleTime).padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
        // Three anchored groups, not six equal slots: Lock at the start, Previous/Play/Next at the exact
        // centre with a fixed gap, Screen size + Popup at the end. Sizes step down on narrow screens so the
        // centre group never meets the right group (checked at 320 dp).
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth >= 340.dp
            val btn = if (wide) 44.dp else 40.dp
            val playBox = if (wide) 52.dp else 48.dp
            val gap = if (wide) 4.dp else 0.dp
            val small = if (wide) 21.dp else 19.dp
            val main = if (wide) 26.dp else 24.dp
            val play = if (wide) 32.dp else 30.dp
            val secondary = OnVideo.text.copy(alpha = 0.85f)
            Box(Modifier.fillMaxWidth().height(56.dp)) {
                Box(Modifier.align(Alignment.CenterStart)) {
                    VideoIcon(MoodyIcons.Lock, "Lock controls", onLock, size = small, tint = secondary, box = btn)
                }
                Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(gap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VideoIcon(MoodyIcons.Prev, "Previous", onPrev, size = main, enabled = canPrev, box = btn)
                    val icon = when { st.ended -> MoodyIcons.Replay; st.playing -> MoodyIcons.Pause; else -> MoodyIcons.Play }
                    Box(
                        Modifier.size(playBox).clip(CircleShape).clickable(role = Role.Button, onClick = onToggle)
                            .semantics { contentDescription = when { st.ended -> "Replay"; st.playing -> "Pause"; else -> "Play" } },
                        contentAlignment = Alignment.Center,
                    ) { Icon(icon, null, Modifier.size(play), tint = OnVideo.text) }
                    VideoIcon(MoodyIcons.Next, "Next", onNext, size = main, enabled = hasNext, box = btn)
                }
                Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
                    VideoIcon(
                        if (st.aspect == Aspect.FIT) MoodyIcons.Fullscreen else MoodyIcons.FitScreen,
                        "Screen size: ${st.aspect.label}", onScreenSize, size = small,
                        tint = if (st.aspect == Aspect.FIT) secondary else accent, box = btn,
                    )
                    VideoIcon(MoodyIcons.Pip, "Popup window", onPopup, size = small, tint = secondary, box = btn)
                }
            }
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
fun VideoIcon(icon: ImageVector, label: String, onClick: () -> Unit, size: Dp = 22.dp, enabled: Boolean = true, tint: Color = OnVideo.text, box: Dp = 44.dp) {
    Box(
        Modifier
            .size(box)
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
