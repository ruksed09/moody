package com.moody.player.ui.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.moody.player.data.media.MediaRepository
import com.moody.player.data.prefs.SettingsStore
import com.moody.player.data.stats.PlayStats
import com.moody.player.design.MoodyTheme
import com.moody.player.design.Radii
import com.moody.player.design.components.ChoiceChip
import com.moody.player.design.components.LocalToaster
import com.moody.player.design.components.Motion
import com.moody.player.design.components.QuietButton
import com.moody.player.design.components.SecondaryButton
import com.moody.player.domain.DayTotal
import com.moody.player.domain.StatsSummary
import com.moody.player.domain.TopItem
import com.moody.player.domain.formatSpent
import com.moody.player.domain.hourLabel
import com.moody.player.domain.summarize
import com.moody.player.ui.settings.SettingsPage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

enum class StatsRange(val label: String, val days: Int) { WEEK("Week", 7), MONTH("Month", 30) }

data class StatsUi(val range: StatsRange = StatsRange.WEEK, val summary: StatsSummary? = null, val keepStats: Boolean = true)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val stats: PlayStats,
    media: MediaRepository,
    private val settings: SettingsStore,
) : ViewModel() {
    val range = MutableStateFlow(StatsRange.WEEK)

    val state: StateFlow<StatsUi> = combine(range, stats.recent(31), media.videos, media.audio, settings.settings) { r, rows, videos, audio, s ->
        // Only files still in the library (and not in a hidden folder) are named in the top lists.
        val visible = buildSet {
            videos.orEmpty().filter { it.relativePath !in s.hiddenFolders }.forEach { add(it.uri.toString()) }
            audio.orEmpty().forEach { add(it.uri.toString()) }
        }
        val today = PlayStats.today()
        StatsUi(r, summarize(rows, today - r.days + 1, today, visible), s.keepStats)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUi())

    fun setKeep(on: Boolean) = viewModelScope.launch { settings.update { it.copy(keepStats = on) } }
    fun clear() = viewModelScope.launch { stats.clear() }
}

/** Series colours, checked for colour-blind separation and contrast on every theme's background. */
@Composable
private fun watchColor() = MoodyTheme.colors.accent

@Composable
private fun listenColor() = if (MoodyTheme.colors.isDark) Color(0xFF1BAF7A) else Color(0xFF2A78D6)

/** Settings > Your stats: time spent watching and listening, by day, plus what you played most. */
@Composable
fun StatsScreen(vm: StatsViewModel, onBack: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    StatsContent(s, onRange = { vm.range.value = it }, onKeep = vm::setKeep, onClear = vm::clear, onBack = onBack)
}

/** The Stats page without its view model, so it can be rendered with sample data. */
@Composable
fun StatsContent(s: StatsUi, onRange: (StatsRange) -> Unit, onKeep: (Boolean) -> Unit, onClear: () -> Unit, onBack: () -> Unit) {
    val c = MoodyTheme.colors
    val toaster = LocalToaster.current
    var confirm by remember { mutableStateOf(false) }
    val sum = s.summary

    SettingsPage("Your stats", onBack) {
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatsRange.entries.forEach { r -> ChoiceChip(r.label, s.range == r, { onRange(r) }) }
            }
        }
        if (!s.keepStats) {
            item {
                Column(Modifier.padding(16.dp).fillMaxWidth().border(1.dp, c.line2, Radii.lg).padding(16.dp)) {
                    Text("Stats are off", style = MoodyTheme.type.title, color = c.text)
                    Spacer(Modifier.height(4.dp))
                    Text("Moody isn't counting play time right now. What was counted before stays here until you clear it.", style = MoodyTheme.type.caption, color = c.text2)
                    Spacer(Modifier.height(12.dp))
                    SecondaryButton("Turn stats on", { onKeep(true) })
                }
            }
        }
        if (sum == null) return@SettingsPage
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile("Watched", formatSpent(sum.videoMs), watchColor(), Modifier.weight(1f))
                Tile("Listened", formatSpent(sum.musicMs), listenColor(), Modifier.weight(1f))
            }
        }
        if (sum.isEmpty) {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Nothing counted yet", style = MoodyTheme.type.title, color = c.text)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Play a video or a song and your time shows up here. It's counted on this phone only, and vault videos are never counted.",
                        style = MoodyTheme.type.caption, color = c.text2, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        } else {
            item { DayChart(sum.days, s.range) }
            sum.busiestHour?.let { h ->
                item {
                    Text(
                        "You play most around ${hourLabel(h)}",
                        style = MoodyTheme.type.body, color = c.text2,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            if (sum.topSongs.isNotEmpty()) {
                item { Heading("Top songs") }
                sum.topSongs.forEachIndexed { i, t -> item(key = "s" + t.uri) { TopRow(i + 1, t) } }
            }
            if (sum.topVideos.isNotEmpty()) {
                item { Heading("Top videos") }
                sum.topVideos.forEachIndexed { i, t -> item(key = "v" + t.uri) { TopRow(i + 1, t) } }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                Text(
                    "Counted on this phone only. Nothing leaves it. Vault videos, links and deleted files are never named here.",
                    style = MoodyTheme.type.caption, color = c.text3,
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    if (s.keepStats) QuietButton("Turn off", { onKeep(false); toaster.show("Stats off") }, color = c.text2)
                    QuietButton("Clear stats", { confirm = true }, color = c.danger)
                }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Clear stats?") },
            text = { Text("All counted play time is forgotten. Your files, favourites and watch history are not touched.") },
            confirmButton = { TextButton({ confirm = false; onClear(); toaster.show("Stats cleared") }) { Text("Clear", color = c.danger) } },
            dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } },
            containerColor = c.raised,
            titleContentColor = c.text,
            textContentColor = c.text2,
        )
    }
}

@Composable
private fun Tile(label: String, value: String, swatch: Color, modifier: Modifier) {
    val c = MoodyTheme.colors
    Column(modifier.background(c.surface, Radii.lg).border(1.dp, c.line, Radii.lg).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(swatch, Radii.sm))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MoodyTheme.type.label, color = c.text2)
        }
        Spacer(Modifier.height(8.dp))
        Text(value, style = MoodyTheme.type.display, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Heading(text: String) =
    Text(text, style = MoodyTheme.type.title, color = MoodyTheme.colors.text, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp))

@Composable
private fun TopRow(rank: Int, t: TopItem) {
    val c = MoodyTheme.colors
    Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$rank", style = MoodyTheme.type.mono, color = c.text3, modifier = Modifier.widthIn(min = 24.dp))
        Text(t.title.ifBlank { "Untitled" }, style = MoodyTheme.type.body, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(
            formatSpent(t.ms) + if (t.plays > 0) " · ${t.plays} ${if (t.plays == 1) "play" else "plays"}" else "",
            style = MoodyTheme.type.caption, color = c.text2,
        )
    }
}

private fun dayName(day: Long, style: TextStyle): String = LocalDate.ofEpochDay(day).dayOfWeek.getDisplayName(style, Locale.getDefault())

private fun dayLong(day: Long): String = LocalDate.ofEpochDay(day).let { "${dayName(day, TextStyle.SHORT)} ${it.dayOfMonth} ${it.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())}" }

/**
 * One stacked column per day: watching at the bottom, listening on top, a 2 dp gap between them and
 * a rounded top end. Tap a day to read its numbers. Bars grow in when the range changes.
 */
@Composable
private fun DayChart(days: List<DayTotal>, range: StatsRange) {
    val c = MoodyTheme.colors
    val watch = watchColor()
    val listen = listenColor()
    val band = c.raised
    var picked by remember(range) { mutableIntStateOf(days.lastIndex) }
    val grow = remember { Animatable(0f) }
    LaunchedEffect(range) { grow.snapTo(0f); grow.animateTo(1f, Motion.enter()) }
    val max = (days.maxOfOrNull { it.videoMs + it.musicMs } ?: 0L).coerceAtLeast(60_000L)
    val sel = days.getOrNull(picked)

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        // Legend (two series) and the picked day's numbers, in text colours; the swatches carry identity.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(watch, Radii.sm)); Spacer(Modifier.width(6.dp))
            Text("Watching", style = MoodyTheme.type.caption, color = c.text2); Spacer(Modifier.width(16.dp))
            Box(Modifier.size(10.dp).background(listen, Radii.sm)); Spacer(Modifier.width(6.dp))
            Text("Listening", style = MoodyTheme.type.caption, color = c.text2)
            Spacer(Modifier.weight(1f))
            Text(formatSpent(max), style = MoodyTheme.type.caption, color = c.text3)
        }
        Spacer(Modifier.height(10.dp))
        val desc = days.joinToString("; ") { "${dayLong(it.day)}: watched ${formatSpent(it.videoMs)}, listened ${formatSpent(it.musicMs)}" }
        Canvas(
            Modifier.fillMaxWidth().height(160.dp)
                .semantics { contentDescription = "Play time by day. $desc" }
                .pointerInput(days) {
                    detectTapGestures { o -> picked = (o.x / (size.width / days.size.toFloat())).toInt().coerceIn(0, days.lastIndex) }
                },
        ) {
            val slot = size.width / days.size
            val barW = minOf(24.dp.toPx(), slot * 0.62f)
            val gap = 2.dp.toPx()
            val r = minOf(4.dp.toPx(), barW / 2)
            // Recessive guide at the top value and the baseline.
            drawLine(c.line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
            drawLine(c.line2, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            // The picked day sits on a soft band; the bars themselves keep their full, checked colours.
            if (picked in days.indices) drawRoundRect(band, Offset(picked * slot + 1.dp.toPx(), 0f), Size(slot - 2.dp.toPx(), size.height), CornerRadius(6.dp.toPx()))
            days.forEachIndexed { i, d ->
                val x = i * slot + (slot - barW) / 2
                val vh = size.height * (d.videoMs / max.toFloat()) * grow.value
                val mh = size.height * (d.musicMs / max.toFloat()) * grow.value
                val total = vh + mh + if (vh > 0f && mh > 0f) gap else 0f
                if (total <= 0f) return@forEachIndexed
                // Square at the baseline, rounded only at the free top end.
                fun seg(top: Float, h: Float, color: Color, roundTop: Boolean) {
                    if (h <= 0f) return
                    val cr = if (roundTop) CornerRadius(r, r) else CornerRadius.Zero
                    val path = Path().apply { addRoundRect(RoundRect(x, top, x + barW, top + h, cr, cr, CornerRadius.Zero, CornerRadius.Zero)) }
                    drawPath(path, color)
                }
                seg(size.height - vh, vh, watch, roundTop = mh <= 0f)
                if (mh > 0f) seg(size.height - total, mh, listen, roundTop = true)
            }
        }
        Spacer(Modifier.height(6.dp))
        // Each label is centred under its bar at its own width, so two-digit dates never clip in narrow month slots.
        val shown = days.indices.filter { i -> range == StatsRange.WEEK || i % 7 == (days.lastIndex % 7) || i == picked }
        androidx.compose.ui.layout.Layout(
            content = {
                shown.forEach { i ->
                    val d = days[i]
                    Text(
                        if (range == StatsRange.WEEK) dayName(d.day, TextStyle.NARROW) else "${LocalDate.ofEpochDay(d.day).dayOfMonth}",
                        style = MoodyTheme.type.caption, color = if (i == picked) c.text else c.text3, maxLines = 1, softWrap = false,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { measurables, constraints ->
            val loose = constraints.copy(minWidth = 0)
            val placeables = measurables.map { it.measure(loose) }
            val slot = constraints.maxWidth / days.size.toFloat()
            val h = placeables.maxOfOrNull { it.height } ?: 0
            // A label that would sit within 4 dp of a fixed one is dropped; the picked day's label wins.
            val keep = BooleanArray(placeables.size) { true }
            val lefts = shown.mapIndexed { k, i -> ((i + 0.5f) * slot - placeables[k].width / 2f).toInt().coerceIn(0, constraints.maxWidth - placeables[k].width) }
            val pickedK = shown.indexOf(picked)
            for (k in placeables.indices) {
                if (k == pickedK || pickedK < 0) continue
                val a = lefts[k]; val b = a + placeables[k].width
                val pa = lefts[pickedK] - 4.dp.roundToPx(); val pb = lefts[pickedK] + placeables[pickedK].width + 4.dp.roundToPx()
                if (b > pa && a < pb) keep[k] = false
            }
            layout(constraints.maxWidth, h) { placeables.forEachIndexed { k, p -> if (keep[k]) p.place(lefts[k], 0) } }
        }
        Spacer(Modifier.height(12.dp))
        if (sel != null) {
            Row(Modifier.fillMaxWidth().background(c.surface, Radii.md).border(1.dp, c.line, Radii.md).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (sel.day == PlayStats.today()) "Today" else dayLong(sel.day), style = MoodyTheme.type.bodyStrong, color = c.text, modifier = Modifier.weight(1f))
                Text("${formatSpent(sel.videoMs)} watched · ${formatSpent(sel.musicMs)} listened", style = MoodyTheme.type.caption, color = c.text2)
            }
        }
    }
}
