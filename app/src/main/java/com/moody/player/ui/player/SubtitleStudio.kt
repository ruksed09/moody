package com.moody.player.ui.player

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moody.player.data.prefs.Settings
import com.moody.player.design.MoodyTheme
import com.moody.player.design.OnVideo
import com.moody.player.design.Radii
import com.moody.player.design.icons.MoodyIcons
import com.moody.player.domain.Cue
import com.moody.player.domain.SubAlign
import com.moody.player.domain.SubBg
import com.moody.player.domain.SubFont
import com.moody.player.domain.SubSize
import com.moody.player.domain.SubtitleDoc
import com.moody.player.domain.activeCues
import com.moody.player.domain.formatDuration
import com.moody.player.domain.transcriptIndex
import com.moody.player.player.video.PlayerState
import kotlinx.coroutines.delay

/**
 * The video's clock between libVLC's position updates: it runs on from the last reported time at the
 * playback speed, so subtitles change on the right frame rather than up to a quarter second late.
 */
@Composable
fun rememberVideoClock(st: PlayerState): State<Long> {
    val t = remember { mutableLongStateOf(st.positionMs) }
    LaunchedEffect(st.positionMs, st.playing, st.rate) {
        val base = st.positionMs
        val t0 = SystemClock.elapsedRealtime()
        t.longValue = base
        while (st.playing) {
            delay(40)
            t.longValue = base + ((SystemClock.elapsedRealtime() - t0) * st.rate).toLong()
        }
    }
    return t
}

private fun SubFont.family(): FontFamily = when (this) {
    SubFont.DEFAULT -> FontFamily.SansSerif
    SubFont.SERIF -> FontFamily.Serif
    SubFont.MONO -> FontFamily.Monospace
    SubFont.CONDENSED -> FontFamily(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL))
}

private fun SubSize.sp(): Float = when (this) { SubSize.S -> 15f; SubSize.M -> 18f; SubSize.L -> 22f; SubSize.XL -> 27f }

/** One styled subtitle block: the same drawing for the video and the live preview. [scale] shrinks it for the preview. */
@Composable
fun StudioCaption(text: String, s: Settings, modifier: Modifier = Modifier, scale: Float = 1f) {
    val landscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp
    val size = s.subSize.sp() * scale * if (landscape) 1.15f else 1f
    val align = when (s.subAlign) { SubAlign.LEFT -> TextAlign.Left; SubAlign.CENTER -> TextAlign.Center; SubAlign.RIGHT -> TextAlign.Right }
    val fill = Color(s.subColor)
    val base = TextStyle(
        fontFamily = s.subFont.family(), fontSize = size.sp, lineHeight = (size * 1.28f).sp, fontWeight = FontWeight.Medium, textAlign = align,
        shadow = if (s.subBg == SubBg.SHADOW) Shadow(Color.Black.copy(alpha = 0.85f), blurRadius = 6f * scale) else null,
    )
    val box = when (s.subBg) { SubBg.SHADOW -> Color.Transparent; SubBg.SOFT -> Color.Black.copy(alpha = 0.5f); SubBg.SOLID -> Color.Black }
    val strokePx = with(LocalDensity.current) { (s.subOutline.widthDp * scale).dp.toPx() }
    Box(modifier.clip(Radii.sm).background(box).padding(horizontal = (8 * scale).dp, vertical = (3 * scale).dp)) {
        // Outline: the same text stroked in black underneath the fill.
        if (strokePx > 0f) Text(text, style = base.copy(color = Color.Black, drawStyle = Stroke(width = strokePx * 2f), shadow = null))
        Text(text, style = base.copy(color = fill))
    }
}

/** Places a caption on the picture as the settings say: bottom or top, a distance from the edge, left/centre/right. */
@Composable
fun CaptionFrame(s: Settings, controlsLift: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier) {
        val edge = maxHeight * (s.subLift / 100f)
        val h = when (s.subAlign) { SubAlign.LEFT -> Alignment.Start; SubAlign.CENTER -> Alignment.CenterHorizontally; SubAlign.RIGHT -> Alignment.End }
        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp)
                .padding(top = if (s.subTop) maxOf(edge, controlsLift) else 0.dp, bottom = if (s.subTop) 0.dp else maxOf(edge, controlsLift)),
            verticalArrangement = if (s.subTop) Arrangement.Top else Arrangement.Bottom,
            horizontalAlignment = h,
        ) { content() }
    }
}

/** Studio subtitles over the video. Moves up while the controls show, so the two never overlap. */
@Composable
fun SubtitleOverlay(doc: SubtitleDoc, st: PlayerState, s: Settings, controlsShown: Boolean, modifier: Modifier = Modifier) {
    val clock = rememberVideoClock(st)
    val delayMs by rememberUpdatedState(st.subDelayMs)
    val text by remember(doc) { derivedStateOf { activeCues(doc.cues, clock.value - delayMs).joinToString("\n") { doc.cues[it].text } } }
    if (text.isEmpty()) return
    CaptionFrame(s, if (controlsShown) (if (s.subTop) 110.dp else 132.dp) else 0.dp, modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
        StudioCaption(text, s, Modifier.semantics { contentDescription = text })
    }
}

/**
 * Transcript of the subtitle file: the line on screen is lit and followed, tap a line to jump there,
 * copy a line, search all lines, and step to the previous or next line.
 */
@Composable
fun TranscriptPanel(vm: PlayerViewModel, st: PlayerState, onDismiss: () -> Unit) {
    val c = MoodyTheme.colors
    val doc by vm.studio.collectAsStateWithLifecycle()
    val d = doc ?: run { onDismiss(); return }
    val clipboard = LocalClipboardManager.current
    val clock = rememberVideoClock(st)
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    val delayMs by rememberUpdatedState(st.subDelayMs)
    val current by remember(d) { derivedStateOf { transcriptIndex(d.cues, clock.value - delayMs) } }
    val shown = remember(d, q) { if (q.isEmpty()) d.cues.indices.toList() else d.cues.indices.filter { d.cues[it].text.contains(q, ignoreCase = true) } }
    val list = rememberLazyListState()
    var userScrollAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(list.isScrollInProgress) { if (list.isScrollInProgress) userScrollAt = SystemClock.elapsedRealtime() }
    // Follow the line on screen, unless the list was just moved by hand or a search is open.
    LaunchedEffect(current, q) {
        if (q.isNotEmpty() || current < 0) return@LaunchedEffect
        if (SystemClock.elapsedRealtime() - userScrollAt < 4_000) return@LaunchedEffect
        val row = shown.indexOf(current)
        if (row >= 0) list.animateScrollToItem((row - 2).coerceAtLeast(0))
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    ) {
        val narrow = maxWidth < 560.dp
        Column(
            Modifier
                .align(if (narrow) Alignment.BottomCenter else Alignment.CenterEnd)
                .then(if (narrow) Modifier else Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp))
                .widthIn(max = if (narrow) 600.dp else 380.dp)
                .fillMaxWidth()
                .then(if (narrow) Modifier.heightIn(max = maxHeight * 0.66f) else Modifier.fillMaxHeight())
                .clip(if (narrow) androidx.compose.foundation.shape.RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp) else Radii.lg)
                .background(Color(0xFC0E1013))
                .clickable(remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Row(Modifier.fillMaxWidth().height(52.dp).padding(start = 16.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Transcript", style = MoodyTheme.type.heading, color = c.text)
                    Text("${d.cues.size} lines · ${d.format.label}", style = MoodyTheme.type.caption, color = c.text3, maxLines = 1)
                }
                VideoIcon(MoodyIcons.Close, "Close transcript", onDismiss, size = 18.dp, box = 40.dp)
            }
            // Search
            Row(
                Modifier.padding(horizontal = 12.dp).fillMaxWidth().height(40.dp).clip(Radii.pill).background(c.raised).border(1.dp, c.line, Radii.pill).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(MoodyIcons.Search, null, Modifier.size(16.dp), tint = c.text3)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search lines", style = MoodyTheme.type.body, color = c.text3)
                    BasicTextField(
                        query, { query = it }, singleLine = true,
                        textStyle = MoodyTheme.type.body.copy(color = c.text), cursorBrush = SolidColor(c.accent),
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search subtitle lines" },
                    )
                }
                if (query.isNotEmpty()) VideoIcon(MoodyIcons.Close, "Clear search", { query = "" }, size = 14.dp, box = 32.dp)
            }
            // Previous / next line
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                LineStep(MoodyIcons.ChevronUp, "Previous line") { vm.stepLine(next = false) }
                LineStep(MoodyIcons.ChevronDown, "Next line") { vm.stepLine(next = true) }
                Spacer(Modifier.weight(1f))
                Text(
                    if (q.isNotEmpty()) "${shown.size} ${if (shown.size == 1) "match" else "matches"}" else if (current >= 0) "Line ${current + 1}" else "",
                    style = MoodyTheme.type.caption, color = c.text3, modifier = Modifier.padding(end = 8.dp),
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
            if (shown.isEmpty()) {
                Text("No lines match “$q”", style = MoodyTheme.type.body, color = c.text3, modifier = Modifier.padding(16.dp))
            }
            LazyColumn(state = list, contentPadding = PaddingValues(vertical = 6.dp), modifier = Modifier.fillMaxWidth()) {
                items(shown, key = { it }) { i ->
                    TranscriptRow(d.cues[i], i == current, q,
                        onJump = { vm.seekToCue(d.cues[i].startMs) },
                        onCopy = { clipboard.setText(AnnotatedString(d.cues[i].text)); vm.toast("Line copied") },
                    )
                }
            }
        }
    }
}

@Composable
private fun LineStep(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val c = MoodyTheme.colors
    Row(
        Modifier.height(36.dp).clip(Radii.pill).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = c.text2)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MoodyTheme.type.label, color = c.text2)
    }
}

@Composable
private fun TranscriptRow(cue: Cue, current: Boolean, query: String, onJump: () -> Unit, onCopy: () -> Unit) {
    val c = MoodyTheme.colors
    val text = remember(cue, query) {
        buildAnnotatedString {
            append(cue.text)
            if (query.isNotEmpty()) {
                var from = 0
                while (true) {
                    val at = cue.text.indexOf(query, from, ignoreCase = true)
                    if (at < 0) break
                    addStyle(SpanStyle(color = Color(0xFF7BE0D6), fontWeight = FontWeight.SemiBold), at, at + query.length)
                    from = at + query.length
                }
            }
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp).clip(Radii.md)
            .background(if (current) c.accentWash else Color.Transparent)
            .clickable(onClickLabel = "Jump to this line", onClick = onJump)
            .padding(start = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(formatDuration(cue.startMs), style = MoodyTheme.type.mono, color = if (current) c.accent else c.text3, modifier = Modifier.width(64.dp).padding(top = 2.dp))
        Text(text, style = MoodyTheme.type.body, color = if (current) c.text else c.text2, modifier = Modifier.weight(1f))
        VideoIcon(MoodyIcons.Copy, "Copy line", onCopy, size = 14.dp, box = 36.dp, tint = c.text3)
    }
}
