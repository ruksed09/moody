package com.moody.player.ui.player

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.semantics.onClick
import com.moody.player.design.components.QuietButton
import com.moody.player.design.components.PrimaryButton
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
    /** Keep the sound, drop the picture, leave the screen. Never offered for vault videos. */
    val onBackground: () -> Unit,
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
    // Lock: NORMAL hides every control; ADVANCED keeps only the seek bar. Survives rotation and the next video.
    var lockMode by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(LockMode.NONE) }
    val locked = lockMode != LockMode.NONE
    var lockReveal by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var lockShown by remember { mutableStateOf(false) }
    var lockHolding by remember { mutableStateOf(false) }
    val keepLock = com.moody.player.ui.music.rememberKeepControls()
    val abOn by vm.abOn.collectAsStateWithLifecycle()
    val abEdit by vm.abEdit.collectAsStateWithLifecycle()
    var markTip by remember { mutableStateOf<Pair<Boolean, com.moody.player.data.db.Bookmark>?>(null) }
    var markTipTick by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var scrubbing by remember { mutableStateOf(false) }
    // Where a sideways seek swipe points while the finger is down: the seek bar and the elapsed time follow it.
    var swipeSeekAt by remember { mutableStateOf<Long?>(null) }
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
    val seekMarks = remember(bookmarks, settings.showBookmarks) {
        if (settings.showBookmarks) bookmarks.mapIndexed { i, b -> SeekMark(b.positionMs, b.endMs, b.name.ifBlank { "Bookmark ${i + 1}" }) } else emptyList()
    }

    // Bookmarks go into the video file too: Android asks once per video (never, with Media management allowed).
    val fileWrite by vm.fileWrite.collectAsStateWithLifecycle()
    val consent29 by vm.consent29.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val consent = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()) { r ->
        vm.onFileConsent(r.resultCode == android.app.Activity.RESULT_OK)
    }
    LaunchedEffect(fileWrite) {
        val u = fileWrite ?: return@LaunchedEffect
        val granted = context.checkUriPermission(u, android.os.Process.myPid(), android.os.Process.myUid(), android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (android.os.Build.VERSION.SDK_INT >= 30 && !granted) {
            runCatching {
                consent.launch(androidx.activity.result.IntentSenderRequest.Builder(android.provider.MediaStore.createWriteRequest(context.contentResolver, listOf(u)).intentSender).build())
            }.onFailure { vm.onFileConsent(false) }
        } else vm.saveMarksToFile(u)
    }
    LaunchedEffect(consent29) {
        consent29?.let { consent.launch(androidx.activity.result.IntentSenderRequest.Builder(it).build()) }
    }

    // Controls hide 3 s after the last touch while playing.
    LaunchedEffect(controls, st.playing, scrubbing, menu, tracks, poke, transcript, lockHolding, swipeSeekAt != null) {
        if (controls && st.playing && !scrubbing && menu == null && tracks == null && !transcript && !lockHolding && swipeSeekAt == null) { delay(3_000); controls = false }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx -> VLCVideoLayout(ctx).also { vm.engine.attach(it) } },
            onRelease = { vm.engine.detach(it) },
            modifier = Modifier.fillMaxSize(),
        )
        // Subtitle files Moody draws itself (Subtitle Studio). Shown in picture-in-picture too.
        studio?.takeIf { studioOn }?.let { d -> SubtitleOverlay(d, st, settings, controlsShown = (controls && !locked || lockMode == LockMode.ADVANCED) && !inPip) }
        if (inPip) return@Box

        GestureLayer(
            vm = vm,
            state = st,
            locked = locked,
            onTap = { if (locked) lockReveal++ else { controls = !controls; poke++ } },
            modifier = Modifier.fillMaxSize(),
            onSideSeek = { right, at ->
                if (settings.showBookmarks && !locked) nearestBookmark(bookmarks, at, right)?.let { markTip = right to it; markTipTick++ }
            },
            // A seek swipe brings the controls up, so its target can be watched on the seek bar.
            onSeekPreview = { at -> if (at != null && !locked) controls = true; swipeSeekAt = at },
        )
        // After a double-tap or swipe seek: a tip on that side to jump straight to a bookmark.
        LaunchedEffect(markTipTick) { if (markTipTick > 0) { delay(3_500); markTip = null } }
        LaunchedEffect(locked, settings.showBookmarks) { if (locked || !settings.showBookmarks) markTip = null }
        LaunchedEffect(st.uri) { markTip = null }
        JumpToBookmarkTip(markTip, controls && !locked, abPill = ab.first != null && !locked) { b -> markTip = null; vm.jumpTo(b); poke++ }

        if (st.buffering && st.error == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(40.dp), color = MoodyTheme.colors.accent, strokeWidth = 2.dp, trackColor = OnVideo.rail)
        }

        // A video without a sound track (a silent screen recording, say) still plays; say so once, in the middle.
        val hasAudio = st.audioTracks.any { it.id >= 0 }
        val silent = st.uri != null && st.error == null && st.playing && st.videoWidth > 0 && st.positionMs >= 1_200 && !hasAudio
        var noAudio by remember(st.uri) { mutableStateOf(false) }
        var noAudioDone by remember(st.uri) { mutableStateOf(false) } // shown (or audio seen) once for this video
        // Only after it has stayed silent for a while: libVLC can report the audio track a moment late.
        LaunchedEffect(st.uri, silent) { if (silent && !noAudioDone) { delay(1_500); noAudioDone = true; noAudio = true } }
        // Fades away on its own; keyed only on itself, so later state changes can't cancel the timer and leave it stuck.
        LaunchedEffect(noAudio) { if (noAudio) { delay(2_500); noAudio = false } }
        LaunchedEffect(st.uri, hasAudio) { if (hasAudio) { noAudioDone = true; noAudio = false } }
        AnimatedVisibility(
            noAudio && !st.buffering, Modifier.align(Alignment.Center),
            enter = fadeIn(Motion.base()) + androidx.compose.animation.scaleIn(Motion.base(), initialScale = 0.92f),
            exit = fadeOut(Motion.base()),
        ) {
            Row(
                Modifier.clip(Radii.pill).background(OnVideo.ink).padding(start = 16.dp, end = 20.dp, top = 12.dp, bottom = 12.dp)
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(MoodyIcons.VolumeMute, null, Modifier.size(22.dp), tint = OnVideo.text)
                androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
                Text("No audio", style = MoodyTheme.type.label, color = OnVideo.text)
            }
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
                if (!ended) Box(Modifier.fillMaxWidth().height(150.dp).background(Brush.verticalGradient(listOf(Color(0xB3080A0C), Color.Transparent))))
                Box(Modifier.fillMaxWidth().height(200.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE0080A0C)))))
                Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    AnimatedVisibility(!ended, enter = fadeIn(Motion.base()), exit = fadeOut(Motion.fast())) {
                    Column {
                    TitleBar(
                        title = vm.current?.title.orEmpty(), st = st, onBack = host.onBack,
                        onBackground = if (vm.isVault) null else ({ host.onBackground() }),
                        onAudio = { tracks = TrackKind.AUDIO; poke++ },
                        onCc = { tracks = TrackKind.SUBS; poke++ },
                        onMore = { menu = PlayerMenu.MORE; poke++ },
                    )
                    ShortcutRow(vm, st, settings.playerShortcuts, settings.shortcutsExpanded, ab, loop, shuffle, host.onRotate, onTranscript = {
                        if (vm.studio.value != null) transcript = true else vm.toast("Load a subtitle file to see its transcript")
                    }) { poke++ }
                    }
                    }
                    Spacer(Modifier.weight(1f))
                    BottomControls(
                        st = swipeSeekAt?.let { st.copy(positionMs = it) } ?: st, showRemaining = showRemaining, ab = ab, marks = seekMarks,
                        canPrev = vm.hasPrev || st.positionMs > 5_000, hasNext = vm.hasNext || (shuffle && vm.queue.size > 1),
                        onPrev = { vm.prev(); poke++ },
                        onToggle = { vm.dismissResume(); vm.engine.toggle(); poke++ },
                        onNext = { vm.next(); poke++ },
                        onToggleTime = { showRemaining = !showRemaining; poke++ },
                        onSeek = { vm.dismissResume(); vm.engine.seekTo(it); poke++ },
                        onScrubbing = { scrubbing = it; vm.setScrubbing(it); if (it) vm.dismissResume() },
                        onLock = { lockReveal = 0; lockMode = LockMode.NORMAL; controls = false; vm.toast("Controls locked. Tap the screen to unlock") },
                        onAdvancedLock = { lockReveal = 0; lockHolding = false; lockMode = LockMode.ADVANCED; controls = false; vm.toast("Only the seek bar works. Tap the screen to show the lock") },
                        onLockHolding = { lockHolding = it },
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

        // Locked: a tap on the screen shows the way out for 3.5 s; each tap starts the 3.5 s again.
        LaunchedEffect(lockReveal, lockMode) {
            lockShown = false
            if (lockReveal > 0 && lockMode != LockMode.NONE) { lockShown = true; delay(3_500); lockShown = false }
        }
        androidx.activity.compose.BackHandler(locked) { lockReveal++ }
        LaunchedEffect(lockMode, st.uri) { scrubbing = false; vm.setScrubbing(false) }
        // Finished with nothing to play next: unlock so Replay can be reached.
        LaunchedEffect(st.ended) {
            if (st.ended && locked) { delay(800); if (vm.engine.state.value.ended) { lockMode = LockMode.NONE; controls = true } }
        }
        val unlock = { lockMode = LockMode.NONE; controls = true; poke++; vm.toast("Controls unlocked") }
        if (lockMode == LockMode.NORMAL) {
            AnimatedVisibility(lockShown || keepLock, enter = fadeIn(Motion.fast()), exit = fadeOut(Motion.base())) { LockedOverlay(true, unlock) }
        }
        if (lockMode == LockMode.ADVANCED) {
            AdvancedLockBar(
                st, ab, seekMarks, showUnlock = lockShown || keepLock,
                onSeek = { vm.dismissResume(); vm.engine.seekTo(it) },
                onScrubbing = { scrubbing = it; vm.setScrubbing(it) },
                onUnlock = unlock,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        // A-B repeat: a mini bar with the start and end times while it is set up or running.
        val abA = ab.first
        if (abA != null && !locked) {
            AbBar(
                a = abA, b = ab.second, on = abOn, at = st.positionMs,
                onEditA = { vm.beginAbEdit(true); poke++ },
                onEditB = { vm.beginAbEdit(false); poke++ },
                onSetB = { vm.markB(); poke++ },
                onToggleOn = { vm.toggleAbOn(); poke++ },
                onBookmark = { vm.addBookmark(); poke++ },
                onClear = { vm.clearAb(); poke++ },
                modifier = Modifier.align(Alignment.BottomEnd).windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(end = 16.dp, bottom = if (controls) 132.dp else 24.dp),
            )
        }

        abEdit?.let { editingA ->
            val origin = remember(editingA) { vm.abEditOrigin() }
            AbTimeEditor(
                editingA = editingA, a = ab.first, b = ab.second, durationMs = st.durationMs, origin = origin,
                onPreview = vm::previewAb,
                onSave = { ms -> vm.saveAb(editingA, ms).also { poke++ } },
                onCancel = { vm.cancelAbEdit(); poke++ },
                onGoToA = { vm.abEditGoToA(); poke++ },
            )
        }

        if (!locked) resumedAt?.takeIf { it.first == vm.current?.uri }?.let { (_, at) ->
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

/**
 * Back, filename, resolution, Play in background, Audio, CC, overflow. The filename gives way first; the buttons
 * never move. [onBackground] is null for vault videos, which never play in the background. With that fourth button
 * on a screen under 340 dp wide, the resolution tag steps aside so the filename keeps the room it had before.
 */
@Composable
internal fun TitleBar(title: String, st: PlayerState, onBack: () -> Unit, onBackground: (() -> Unit)?, onAudio: () -> Unit, onCc: () -> Unit, onMore: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        VideoIcon(MoodyIcons.ArrowBack, "Back", onBack, size = 22.dp)
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            val maxChars = (maxWidth.value / 8.2f).toInt().coerceAtLeast(8)
            Text(middleEllipsize(title, maxChars), style = MoodyTheme.type.heading, color = OnVideo.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val roomForResolution = onBackground == null || androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 340
        if (roomForResolution) resolution(st)?.let {
            Text(
                it, style = MoodyTheme.type.monoSmall, color = OnVideo.text.copy(alpha = 0.88f),
                modifier = Modifier.padding(end = 2.dp).border(1.dp, OnVideo.text.copy(alpha = 0.28f), Radii.sm).padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
        if (onBackground != null) VideoIcon(MoodyIcons.Headphones, "Play in background", onBackground, size = 20.dp)
        VideoIcon(MoodyIcons.AudioTrack, "Audio tracks", onAudio, size = 20.dp)
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

/** Shortcuts shown before the expand arrow. */
private const val SHORTCUTS_SHOWN = 3

/**
 * The user's chosen shortcuts, small and in one row under the title bar (MX style). Never the six fixed controls.
 * Up to three show directly; with more, the first three show and an arrow slides the rest open in the same row.
 * Open or closed is a setting, so it stays across videos and player restarts.
 */
@Composable
private fun ShortcutRow(
    vm: PlayerViewModel, st: PlayerState, shortcuts: List<com.moody.player.domain.PlayerShortcut>, expanded: Boolean,
    ab: Pair<Long?, Long?>, loop: com.moody.player.domain.LoopMode, shuffle: Boolean, onRotate: () -> Unit, onTranscript: () -> Unit, onUse: () -> Unit,
) {
    if (shortcuts.isEmpty()) return
    val accent = MoodyTheme.colors.accent
    val idle = OnVideo.text.copy(alpha = 0.9f)
    val first = shortcuts.take(SHORTCUTS_SHOWN)
    val rest = shortcuts.drop(SHORTCUTS_SHOWN)
    @Composable
    fun Button(sc: com.moody.player.domain.PlayerShortcut) {
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
    Row(Modifier.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f, fill = false).horizontalScroll(androidx.compose.foundation.rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
        first.forEach { Button(it) }
        if (rest.isNotEmpty()) {
            AnimatedVisibility(
                expanded,
                enter = androidx.compose.animation.expandHorizontally(com.moody.player.design.components.Motion.base(), expandFrom = Alignment.Start) +
                    androidx.compose.animation.fadeIn(com.moody.player.design.components.Motion.base()),
                exit = androidx.compose.animation.shrinkHorizontally(com.moody.player.design.components.Motion.base(), shrinkTowards = Alignment.Start) +
                    androidx.compose.animation.fadeOut(com.moody.player.design.components.Motion.fast()),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) { rest.forEach { Button(it) } }
            }
        }
        }
        if (rest.isNotEmpty()) {
            val turn by androidx.compose.animation.core.animateFloatAsState(if (expanded) 180f else 0f, com.moody.player.design.components.Motion.base(), label = "shortcuts-arrow")
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = if (expanded) "Show fewer shortcuts" else "Show all shortcuts") {
                        vm.updateSubLook { it.copy(shortcutsExpanded = !expanded) }; onUse()
                    }
                    .semantics { contentDescription = if (expanded) "Fewer shortcuts" else "${rest.size} more shortcuts" },
                contentAlignment = Alignment.Center,
            ) { Icon(MoodyIcons.ChevronRight, null, Modifier.size(18.dp).graphicsLayer { rotationZ = turn }, tint = idle) }
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
    marks: List<SeekMark>,
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
    onAdvancedLock: () -> Unit = {},
    onLockHolding: (Boolean) -> Unit = {},
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
                    LockButton(onLock, onAdvancedLock, onLockHolding, size = small, tint = secondary, box = btn)
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
    Box(Modifier.fillMaxSize().background(Color(0xED0C0E10)).windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp), contentAlignment = Alignment.Center) {
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

/** NONE: playing normally. NORMAL: every control hidden. ADVANCED: only the seek bar works. */
enum class LockMode { NONE, NORMAL, ADVANCED }

/**
 * The lock button. A tap (let go within 0.4 s) is Normal lock. Holding it shows a ring after 0.4 s that fills
 * by 2.5 s: then a tick and Advanced lock, with the finger still down. Letting go in between does nothing.
 * TalkBack gets both as actions, no holding needed.
 */
@Composable
private fun LockButton(onLock: () -> Unit, onAdvanced: () -> Unit, onHolding: (Boolean) -> Unit, size: Dp, tint: Color, box: Dp) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val ring = remember { androidx.compose.animation.core.Animatable(0f) }
    val lock by androidx.compose.runtime.rememberUpdatedState(onLock)
    val advanced by androidx.compose.runtime.rememberUpdatedState(onAdvanced)
    val holding by androidx.compose.runtime.rememberUpdatedState(onHolding)
    // Removed mid-hold (Advanced lock hides it): the controls may hide again.
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { holding(false) } }
    Box(
        Modifier
            .size(box)
            .clip(CircleShape)
            .semantics {
                contentDescription = "Lock controls"
                role = Role.Button
                onClick("Lock") { lock(); true }
                customActions = listOf(androidx.compose.ui.semantics.CustomAccessibilityAction("Advanced lock, seek bar only") { advanced(); true })
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    holding(true)
                    var entered = false
                    val job = scope.launch {
                        delay(400)
                        ring.snapTo(0f)
                        ring.animateTo(1f, androidx.compose.animation.core.tween(2_100, easing = androidx.compose.animation.core.LinearEasing))
                        entered = true
                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        advanced()
                    }
                    val up = waitForUpOrCancellation()
                    job.cancel()
                    holding(false)
                    scope.launch { ring.animateTo(0f, androidx.compose.animation.core.tween(150)) }
                    if (up != null) {
                        up.consume()
                        if (!entered && up.uptimeMillis - down.uptimeMillis < 400) lock()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (ring.value > 0f) androidx.compose.foundation.Canvas(Modifier.size(box - 8.dp)) {
            val w = 2.dp.toPx()
            drawArc(
                Color(0xFF4FD1C5), -90f, 360f * ring.value, useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
                size = androidx.compose.ui.geometry.Size(this.size.width - w, this.size.height - w),
                style = androidx.compose.ui.graphics.drawscope.Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )
        }
        Icon(MoodyIcons.Lock, null, Modifier.size(size), tint = tint)
    }
}

/**
 * Advanced lock: the seek bar and times stay and work; nothing else does. A tap elsewhere shows a small lock at
 * the bottom left for 3.5 s; tapping it unlocks. Touching the seek bar doesn't show it.
 */
@Composable
private fun AdvancedLockBar(
    st: PlayerState, ab: Pair<Long?, Long?>, marks: List<SeekMark>, showUnlock: Boolean,
    onSeek: (Long) -> Unit, onScrubbing: (Boolean) -> Unit, onUnlock: () -> Unit, modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 12.dp).padding(bottom = 6.dp)) {
        // The space stays reserved, so the seek bar never jumps when the lock comes and goes.
        val shown by androidx.compose.animation.core.animateFloatAsState(if (showUnlock) 1f else 0f, Motion.base(), label = "unlock")
        Box(
            Modifier.padding(start = 4.dp, bottom = 4.dp).size(44.dp).graphicsLayer { alpha = shown }.clip(CircleShape).background(OnVideo.ink)
                .then(if (showUnlock) Modifier.clickable(role = Role.Button, onClickLabel = "Unlock", onClick = onUnlock).semantics { contentDescription = "Unlock controls" } else Modifier),
            contentAlignment = Alignment.Center,
        ) { Icon(MoodyIcons.Unlock, null, Modifier.size(20.dp), tint = OnVideo.text) }
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(clock0(st.positionMs), style = MoodyTheme.type.mono, color = OnVideo.text.copy(alpha = 0.85f))
            Spacer(Modifier.weight(1f))
            Text(formatDuration(st.durationMs), style = MoodyTheme.type.mono, color = OnVideo.text.copy(alpha = 0.85f))
        }
        SeekBar(st.positionMs, st.durationMs, onSeek, onScrubbing, Modifier.fillMaxWidth().systemGestureExclusion(), ab.first, ab.second, marks)
    }
}

/** Like formatDuration, but 0 reads "0:00". */
private fun clock0(ms: Long) = formatDuration(ms).let { if (it == "--:--") "0:00" else it }

/**
 * The A-B mini bar. With only A set it reads "A 1:23  B --:--" and offers Set B (live: B shows where it
 * would land). With both it reads "1:23 \u2013 1:45": tap the times to go back to A, bookmark the range, or
 * close to stop repeating.
 */
@Composable
internal fun AbBar(
    a: Long, b: Long?, on: Boolean, at: Long,
    onEditA: () -> Unit, onEditB: () -> Unit, onSetB: () -> Unit, onToggleOn: () -> Unit,
    onBookmark: () -> Unit, onClear: () -> Unit, modifier: Modifier = Modifier,
) {
    val accent = MoodyTheme.colors.accent
    val dim = if (b != null && !on) 0.5f else 1f
    @Composable
    fun Time(text: String, label: String, alpha: Float, onClick: () -> Unit) = Text(
        text, style = MoodyTheme.type.mono, color = OnVideo.text.copy(alpha = alpha),
        modifier = Modifier.clip(Radii.pill).clickable(role = Role.Button, onClickLabel = label, onClick = onClick).padding(horizontal = 8.dp, vertical = 12.dp),
    )
    Row(
        modifier.clip(Radii.pill).background(OnVideo.ink).padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (b != null) VideoIcon(MoodyIcons.AbRepeat, if (on) "Pause A-B repeat" else "Resume A-B repeat", onToggleOn, size = 18.dp, tint = if (on) accent else OnVideo.text.copy(alpha = 0.6f), box = 40.dp)
        else Icon(MoodyIcons.AbRepeat, null, Modifier.padding(start = 10.dp, end = 2.dp).size(18.dp), tint = OnVideo.text)
        Time("A ${clock0(a)}", "Edit A", dim, onEditA)
        Text("\u2192", style = MoodyTheme.type.mono, color = OnVideo.text.copy(alpha = 0.5f * dim))
        if (b != null) Time("B ${clock0(b)}", "Edit B", dim, onEditB) else Time("B --:--", "Set B by time", 0.6f, onEditB)
        if (b == null) {
            Text(
                "Set B", style = MoodyTheme.type.label, color = MoodyTheme.colors.accentInk,
                modifier = Modifier.padding(start = 2.dp, end = 2.dp).clip(Radii.pill).background(if (at > a + 500) accent else accent.copy(alpha = 0.45f))
                    .clickable(role = Role.Button, onClick = onSetB).padding(horizontal = 14.dp, vertical = 8.dp),
            )
        } else {
            VideoIcon(MoodyIcons.Bookmark, "Bookmark this range", onBookmark, size = 18.dp, box = 40.dp)
        }
        VideoIcon(MoodyIcons.Close, "Stop A-B repeat", onClear, size = 16.dp, box = 40.dp)
    }
}

/**
 * Sets A or B to the tenth of a second: type a time or nudge it. The video pauses and shows the frame; Save
 * keeps it, Cancel goes back to where it was.
 */
@Composable
private fun AbTimeEditor(
    editingA: Boolean, a: Long?, b: Long?, durationMs: Long, origin: Long,
    onPreview: (Long) -> Unit, onSave: (Long) -> String?, onCancel: () -> Unit, onGoToA: () -> Unit,
) {
    val c = MoodyTheme.colors
    val gap = com.moody.player.domain.AB_MIN_GAP_MS
    val lo = if (editingA) 0L else (a ?: 0L) + gap
    val hi = (if (editingA) (b?.minus(gap) ?: (durationMs - gap)) else durationMs).coerceAtLeast(lo)
    val first = ((if (editingA) a else b) ?: origin)
    var text by remember { mutableStateOf(com.moody.player.domain.formatClock(first)) }
    var error by remember { mutableStateOf<String?>(null) }
    fun set(ms: Long) {
        val v = ms.coerceIn(lo, hi)
        text = com.moody.player.domain.formatClock(v); error = null; onPreview(v)
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = onCancel) {
        Column(Modifier.fillMaxWidth().background(c.surface, Radii.xl).border(1.dp, c.line, Radii.xl).padding(24.dp)) {
            Text(if (editingA) "Edit A" else "Edit B", style = MoodyTheme.type.title, color = c.text)
            Spacer(Modifier.height(4.dp))
            Text(
                if (durationMs > 0) "Between ${com.moody.player.domain.formatClock(lo)} and ${com.moody.player.domain.formatClock(hi)}" else "The video's length isn't known yet",
                style = MoodyTheme.type.caption, color = c.text3,
            )
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier.fillMaxWidth().height(48.dp).background(c.bg, Radii.md)
                    .border(1.dp, if (error != null) c.danger else c.accent.copy(alpha = 0.55f), Radii.md).padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    text,
                    { t ->
                        text = t.filter { it.isDigit() || it == ':' || it == '.' }.take(12); error = null
                        com.moody.player.domain.parseAbTime(text)?.takeIf { it in lo..hi }?.let(onPreview)
                    },
                    singleLine = true,
                    textStyle = MoodyTheme.type.mono.copy(color = c.text),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(c.accent),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Time, hours minutes seconds" },
                )
            }
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MoodyTheme.type.caption, color = c.danger)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(-5_000L to "\u22125", -1_000L to "\u22121", -100L to "\u22120.1", 100L to "+0.1", 1_000L to "+1", 5_000L to "+5").forEach { (d, label) ->
                    Box(
                        Modifier.weight(1f).height(40.dp).clip(Radii.md).border(1.dp, c.line, Radii.md)
                            .clickable(role = Role.Button, onClickLabel = "$label seconds") { set((com.moody.player.domain.parseAbTime(text) ?: first) + d) },
                        contentAlignment = Alignment.Center,
                    ) { Text(label, style = MoodyTheme.type.mono, color = c.text, maxLines = 1) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                QuietButton("Use current time", { set(origin) })
                if (a != null) QuietButton("Go to A", onGoToA)
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                QuietButton("Cancel", onCancel)
                Spacer(Modifier.width(8.dp))
                PrimaryButton("Save", {
                    val ms = com.moody.player.domain.parseAbTime(text)
                    error = if (ms == null) "Enter a time like 16:45.0" else onSave(ms)
                }, enabled = durationMs > 0)
            }
        }
    }
}

/**
 * The bookmark a double-tap seek points at: the next one after [at] when seeking forward, the one just
 * passed when seeking back (a second's grace, so a fresh jump back still finds it). With none that way, the
 * closest one the other way.
 */
internal fun nearestBookmark(marks: List<com.moody.player.data.db.Bookmark>, at: Long, right: Boolean): com.moody.player.data.db.Bookmark? {
    if (marks.isEmpty()) return null
    val after = marks.filter { it.positionMs > at + 500 }.minByOrNull { it.positionMs }
    val before = marks.filter { it.positionMs < at - 1_000 }.maxByOrNull { it.positionMs }
    return if (right) after ?: before else before ?: after
}

/** Bottom left or bottom right, on the side that was double-tapped: "Jump to <bookmark>". */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.JumpToBookmarkTip(
    tip: Pair<Boolean, com.moody.player.data.db.Bookmark>?,
    controlsShown: Boolean,
    abPill: Boolean,
    onJump: (com.moody.player.data.db.Bookmark) -> Unit,
) {
    var last by remember { mutableStateOf(tip) }
    if (tip != null) last = tip
    val (right, b) = last ?: return
    // Above the A-B pill when it is showing (it sits bottom right at the same height).
    val lift by androidx.compose.animation.core.animateDpAsState((if (controlsShown) 148.dp else 40.dp) + (if (abPill) 56.dp else 0.dp), label = "tip-lift")
    AnimatedVisibility(
        tip != null,
        Modifier
            .align(if (right) Alignment.BottomEnd else Alignment.BottomStart)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp)
            .padding(bottom = lift),
        enter = androidx.compose.animation.fadeIn(com.moody.player.design.components.Motion.fast()) +
            androidx.compose.animation.slideInVertically(com.moody.player.design.components.Motion.base()) { it / 2 },
        exit = androidx.compose.animation.fadeOut(com.moody.player.design.components.Motion.base()),
    ) {
        val label = b.name.ifBlank { com.moody.player.domain.bookmarkLabel(b.positionMs, b.endMs) }
        Row(
            Modifier
                .widthIn(max = 240.dp)
                .clip(Radii.pill)
                .background(OnVideo.ink)
                .clickable(onClickLabel = "Jump to bookmark") { onJump(b) }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(MoodyIcons.Bookmark, null, Modifier.size(16.dp), tint = MoodyTheme.colors.accent)
            Spacer(Modifier.width(8.dp))
            Text(
                "Jump to $label", style = MoodyTheme.type.label, color = OnVideo.text,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
