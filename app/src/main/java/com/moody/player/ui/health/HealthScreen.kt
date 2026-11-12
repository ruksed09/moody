package com.moody.player.ui.health

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.moody.player.data.health.HealthRepository
import com.moody.player.data.health.HealthTarget
import com.moody.player.data.health.ScanProgress
import com.moody.player.data.media.MediaRepository
import com.moody.player.data.prefs.SettingsStore
import com.moody.player.design.MoodyTheme
import com.moody.player.design.Radii
import com.moody.player.design.components.ChoiceChip
import com.moody.player.design.components.LocalToaster
import com.moody.player.design.components.PrimaryButton
import com.moody.player.design.components.QuietButton
import com.moody.player.design.components.ReelLine
import com.moody.player.design.components.SecondaryButton
import com.moody.player.design.components.EmptyState
import com.moody.player.design.components.RoundIconAction
import com.moody.player.design.components.moodyCard
import com.moody.player.design.illustrations.HealthClearArt
import com.moody.player.design.illustrations.HealthScanArt
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.CircleShape
import com.moody.player.design.icons.MoodyIcons
import com.moody.player.domain.HealthReport
import com.moody.player.domain.MediaKind
import com.moody.player.domain.Severity
import com.moody.player.ui.settings.SettingsPage
import com.moody.player.ui.video.ConfirmDialog
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Opens Media Health for one file (from a video's or song's menu). Provided by the app's root. */
val LocalOpenHealth = androidx.compose.runtime.staticCompositionLocalOf<(Uri) -> Unit> { {} }

enum class HealthFilter(val label: String) { PROBLEMS("Problems"), CRITICAL("Critical"), WARNING("Warning"), HEALTHY("Healthy"), ALL("All") }

/** A file removed from the library. [sizeBytes] and [kind] are null when the phone no longer reports them. */
data class RemovedFile(val uri: String, val name: String, val sizeBytes: Long? = null, val kind: MediaKind? = null)

data class HealthUi(
    val reports: List<HealthReport> = emptyList(),
    val counts: Map<Severity, Int> = emptyMap(),
    val progress: ScanProgress? = null,
    val checking: Set<String> = emptySet(),
    val lastScan: Long? = null,
    val filter: HealthFilter = HealthFilter.PROBLEMS,
    val removed: List<RemovedFile> = emptyList(),
    val loaded: Boolean = false,
) {
    val shown: List<HealthReport>
        get() = reports.filter {
            when (filter) {
                HealthFilter.PROBLEMS -> it.severity != Severity.HEALTHY
                HealthFilter.CRITICAL -> it.severity == Severity.CRITICAL
                HealthFilter.WARNING -> it.severity == Severity.WARNING
                HealthFilter.HEALTHY -> it.severity == Severity.HEALTHY
                HealthFilter.ALL -> true
            }
        }
}

@HiltViewModel
class HealthViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: HealthRepository,
    media: MediaRepository,
    settings: SettingsStore,
    saved: SavedStateHandle,
) : ViewModel() {
    private val filter = MutableStateFlow(HealthFilter.PROBLEMS)
    private val names = MutableStateFlow<Map<String, RemovedFile>>(emptyMap())
    /** The report sheet that's open, by URI. */
    val open = MutableStateFlow<String?>(null)

    /** URIs the library lists right now (null until it has loaded): the same set a library scan covers. */
    private val listed = combine(media.videos, media.audio, settings.settings) { v, a, s ->
        if (v == null || a == null) null
        else (v.filter { it.relativePath !in s.hiddenFolders }.map { it.uri.toString() } + a.map { it.uri.toString() }).toHashSet()
    }

    val state = combine(repo.state, settings.settings, filter, names, listed) { r, s, f, n, l ->
        // Files deleted, hidden or removed since they were checked don't show; vault videos are never scanned at all.
        val visible = r.reports.values.filter { it.uri !in s.removedMedia && (l == null || it.uri in l) }
            .sortedWith(compareByDescending<HealthReport> { it.severity }.thenBy { it.name.lowercase() })
        HealthUi(
            reports = visible,
            counts = Severity.entries.associateWith { sev -> visible.count { it.severity == sev } },
            progress = r.progress,
            checking = r.checking,
            lastScan = r.lastScan,
            filter = f,
            removed = s.removedMedia.map { n[it] ?: RemovedFile(it, "") }.sortedBy { it.name.lowercase() },
            loaded = r.loaded,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HealthUi())

    init {
        // Opened from a video's or song's menu: check that one file and show its report.
        saved.get<String>("uri")?.let { raw ->
            val uri = Uri.parse(Uri.decode(raw))
            viewModelScope.launch {
                val t = repo.target(uri)
                if (t != null) { repo.scanOne(t); open.value = t.uri.toString() }
            }
        }
        viewModelScope.launch {
            settings.settings.collect { s ->
                val missing = s.removedMedia.filter { it !in names.value }
                if (missing.isNotEmpty()) names.value = names.value + withContext(Dispatchers.IO) { missing.associateWith { describe(it) } }
            }
        }
    }

    fun scan() = repo.scanLibrary()
    fun stop() = repo.cancel()
    fun setFilter(f: HealthFilter) { filter.value = f }

    fun retry(r: HealthReport) = viewModelScope.launch {
        val uri = Uri.parse(r.uri)
        repo.scanOne(repo.target(uri) ?: HealthTarget(uri, r.name, r.kind, null))
    }

    // Close the sheet first, so the list doesn't shift under it while it's still on screen.
    fun remove(r: HealthReport) = viewModelScope.launch { open.value = null; repo.removeFromLibrary(r.uri) }
    fun restore(uri: String) = viewModelScope.launch { repo.restoreToLibrary(uri) }

    /** Name, size and kind of a removed file, read without opening it. */
    private fun describe(uri: String): RemovedFile {
        val u = Uri.parse(uri)
        val (name, size) = runCatching {
            context.contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) to (if (c.isNull(1)) null else c.getLong(1).takeIf { it > 0 }) else null
            }
        }.getOrNull() ?: (null to null)
        val mime = runCatching { context.contentResolver.getType(u) }.getOrNull().orEmpty()
        val kind = when {
            mime.startsWith("video/") || "/video/" in uri -> MediaKind.VIDEO
            mime.startsWith("audio/") || "/audio/" in uri -> MediaKind.AUDIO
            else -> null
        }
        return RemovedFile(uri, name ?: "File no longer on this phone", size, kind)
    }
}

@Composable
fun HealthScreen(vm: HealthViewModel, onBack: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val openUri by vm.open.collectAsStateWithLifecycle()
    var about by remember { mutableStateOf(false) }
    HealthContent(
        s, onBack = onBack, onScan = vm::scan, onStop = vm::stop, onFilter = vm::setFilter,
        onOpen = { vm.open.value = it.uri }, onRestore = { vm.restore(it) }, onAbout = { about = true },
    )
    if (about) AboutSheet { about = false }
    val report = openUri?.let { u -> s.reports.firstOrNull { it.uri == u } }
    val checkingOpen = openUri != null && openUri in s.checking
    if (openUri != null && (report != null || checkingOpen)) {
        ReportSheet(report, checking = checkingOpen, onDismiss = { vm.open.value = null }, onRetry = { report?.let(vm::retry) }, onRemove = { report?.let(vm::remove) })
    }
}

/** The Media Health page without its view model, so it can be rendered with sample data. */
@Composable
fun HealthContent(
    s: HealthUi,
    onBack: () -> Unit,
    onScan: () -> Unit,
    onStop: () -> Unit,
    onFilter: (HealthFilter) -> Unit,
    onOpen: (HealthReport) -> Unit,
    onRestore: (String) -> Unit,
    onAbout: () -> Unit = {},
) {
    val c = MoodyTheme.colors
    SettingsPage("Media health", onBack, trailing = { RoundIconAction(MoodyIcons.ShieldCheck, "What Media health checks", onAbout) }) {
        item(key = "head") { Summary(s, onScan, onStop) }
        if (s.reports.isNotEmpty()) {
            item(key = "filters") {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HealthFilter.entries.forEach { f ->
                        val sev = f.severity()
                        ChoiceChip(
                            f.label, s.filter == f, { onFilter(f) },
                            icon = sev?.icon() ?: if (f == HealthFilter.PROBLEMS) MoodyIcons.Warning else null,
                            iconTint = sev?.color(),
                        )
                    }
                }
            }
            val shown = s.shown
            if (shown.isEmpty()) item(key = "none") {
                if (s.filter == HealthFilter.PROBLEMS) {
                    EmptyState({ HealthClearArt() }, "No problems found", "Every checked file looks healthy", Modifier.padding(top = 4.dp))
                } else {
                    Text(
                        if (s.filter == HealthFilter.HEALTHY) "No healthy files in the last check" else "Nothing in this group",
                        style = MoodyTheme.type.body, color = c.text2, modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                    )
                }
            }
            items(shown, key = { it.uri }) { r -> ReportRow(r, checking = r.uri in s.checking) { onOpen(r) } }
        }
        if (s.removed.isNotEmpty()) {
            item(key = "removed-h") {
                Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).background(c.raised, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(MoodyIcons.Delete, null, Modifier.size(18.dp), tint = c.text2)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Removed from library", style = MoodyTheme.type.heading, color = c.text)
                        Text("Still on your phone, just not listed in Moody", style = MoodyTheme.type.caption, color = c.text3)
                    }
                }
            }
            items(s.removed, key = { "rm-" + it.uri }) { f -> RemovedRow(f) { onRestore(f.uri) } }
        }
    }
}

private fun HealthFilter.severity(): Severity? = when (this) {
    HealthFilter.CRITICAL -> Severity.CRITICAL
    HealthFilter.WARNING -> Severity.WARNING
    HealthFilter.HEALTHY -> Severity.HEALTHY
    else -> null
}

@Composable
private fun RemovedRow(f: RemovedFile, onRestore: () -> Unit) {
    val c = MoodyTheme.colors
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().moodyCard(c, padding = 0.dp).heightIn(min = 60.dp).padding(start = 12.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).background(c.raised, Radii.md), contentAlignment = Alignment.Center) {
            Icon(
                when (f.kind) { MediaKind.VIDEO -> MoodyIcons.Film; MediaKind.AUDIO -> MoodyIcons.Music; null -> MoodyIcons.Folder },
                null, Modifier.size(18.dp), tint = c.text2,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(f.name.ifBlank { "Loading name" }, style = MoodyTheme.type.bodyStrong, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            f.sizeBytes?.let { Text(sizeLabel(it), style = MoodyTheme.type.caption, color = c.text3, maxLines = 1) }
        }
        Spacer(Modifier.width(8.dp))
        SecondaryButton("Restore", onRestore, Modifier.height(36.dp))
    }
}

private fun sizeLabel(b: Long): String = when {
    b >= 1L shl 30 -> "%.1f GB".format(b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> "%.1f MB".format(b / (1L shl 20).toDouble())
    b >= 1L shl 10 -> "${b shr 10} KB"
    else -> "$b B"
}

@Composable
private fun Summary(s: HealthUi, onScan: () -> Unit, onStop: () -> Unit) {
    val c = MoodyTheme.colors
    val p = s.progress
    Column(Modifier.padding(16.dp).fillMaxWidth().moodyCard(c)) {
        when {
            p != null -> {
                Text(if (p.total == 0) "Getting ready" else "Checking ${p.done + 1} of ${p.total}", style = MoodyTheme.type.title, color = c.text)
                Spacer(Modifier.height(4.dp))
                Text(p.current.ifBlank { " " }, style = MoodyTheme.type.caption, color = c.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(12.dp))
                ReelLine(if (p.total == 0) null else p.done / p.total.toFloat(), height = 4.dp)
                Spacer(Modifier.height(12.dp))
                SecondaryButton("Stop", onStop)
            }
            s.lastScan == null && s.reports.isEmpty() -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HealthScanArt()
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Check your library", style = MoodyTheme.type.title, color = c.text)
                        Spacer(Modifier.height(2.dp))
                        Text("Finds damaged, incomplete or unsupported videos and songs", style = MoodyTheme.type.caption, color = c.text2)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Moody only reads your files; it never changes or deletes them. Vault videos and hidden folders are skipped.",
                    style = MoodyTheme.type.caption, color = c.text3,
                )
                Spacer(Modifier.height(14.dp))
                PrimaryButton("Scan library", onScan, Modifier.fillMaxWidth(), icon = MoodyIcons.Scan)
            }
            else -> {
                Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                    Severity.entries.reversed().forEachIndexed { i, sev ->
                        if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 4.dp).background(c.line))
                        CountColumn(sev, s.counts[sev] ?: 0, Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(MoodyIcons.Clock, null, Modifier.size(14.dp), tint = c.text3)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        s.lastScan?.let { "Last full check ${agoLabel(it)}" } ?: "Some files checked; no full check yet",
                        style = MoodyTheme.type.caption, color = c.text2, modifier = Modifier.weight(1f), maxLines = 2,
                    )
                    Spacer(Modifier.width(8.dp))
                    PrimaryButton("Scan again", onScan, Modifier.height(36.dp), icon = MoodyIcons.Refresh)
                }
            }
        }
    }
}

/** One count in the summary: the number, then its status in its colour with an icon and a word. */
@Composable
private fun CountColumn(sev: Severity, n: Int, modifier: Modifier) {
    val c = MoodyTheme.colors
    Column(
        modifier.padding(vertical = 2.dp).semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("$n", style = MoodyTheme.type.display, color = c.text, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(sev.icon(), null, Modifier.size(12.dp), tint = sev.color())
            Spacer(Modifier.width(4.dp))
            Text(sev.label, style = MoodyTheme.type.caption, color = c.text2, maxLines = 1)
        }
    }
}

/** What Media health does, behind the shield in the header. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutSheet(onDismiss: () -> Unit) {
    val c = MoodyTheme.colors
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(MoodyIcons.ShieldCheck, null, Modifier.size(20.dp), tint = c.accent)
                Spacer(Modifier.width(10.dp))
                Text("What Media health checks", style = MoodyTheme.type.title, color = c.text)
            }
            Spacer(Modifier.height(12.dp))
            listOf(
                "Whether each video and song opens, and whether its sound and picture can be played",
                "Whether the file reads through to the end, so cut-off downloads and copies show up",
                "Each problem comes with what you can do about it",
            ).forEach { line ->
                Row(Modifier.padding(bottom = 8.dp)) {
                    Icon(MoodyIcons.Check, null, Modifier.padding(top = 2.dp).size(14.dp), tint = c.text3)
                    Spacer(Modifier.width(10.dp))
                    Text(line, style = MoodyTheme.type.body, color = c.text2)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Moody only reads your files. It never changes, moves or deletes them. Vault videos and hidden folders are skipped.",
                style = MoodyTheme.type.caption, color = c.text3,
            )
        }
    }
}

@Composable
private fun ReportRow(r: HealthReport, checking: Boolean, onClick: () -> Unit) {
    val c = MoodyTheme.colors
    val sev = r.severity
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).background(sev.color().copy(alpha = 0.14f), Radii.md), contentAlignment = Alignment.Center) {
            Icon(if (r.kind == MediaKind.VIDEO) MoodyIcons.Film else MoodyIcons.Music, null, Modifier.size(18.dp), tint = sev.color())
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(r.name, style = MoodyTheme.type.bodyStrong, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(sev.icon(), null, Modifier.size(12.dp), tint = sev.color())
                Spacer(Modifier.width(4.dp))
                Text(
                    if (checking) "Checking" else "${sev.label}" + (r.findings.firstOrNull()?.let { " · ${it.title}" } ?: ""),
                    style = MoodyTheme.type.caption, color = c.text2, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(MoodyIcons.ChevronRight, null, Modifier.size(16.dp), tint = c.text3)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportSheet(r: HealthReport?, checking: Boolean, onDismiss: () -> Unit, onRetry: () -> Unit, onRemove: () -> Unit) {
    val c = MoodyTheme.colors
    val toaster = LocalToaster.current
    var confirm by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surface, shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            if (r == null) {
                Text("Checking file", style = MoodyTheme.type.title, color = c.text)
                Spacer(Modifier.height(12.dp))
                ReelLine(null, height = 4.dp)
                Spacer(Modifier.height(8.dp))
                Text("Reading the file. Nothing is changed.", style = MoodyTheme.type.caption, color = c.text3)
                return@Column
            }
            Text(r.name, style = MoodyTheme.type.title, color = c.text, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SeverityPill(r.severity)
                Spacer(Modifier.width(10.dp))
                Text(if (checking) "Checking again" else "Checked ${agoLabel(r.checkedAt)}", style = MoodyTheme.type.caption, color = c.text3)
            }
            if (checking) { Spacer(Modifier.height(10.dp)); ReelLine(null, height = 3.dp) }
            Spacer(Modifier.height(16.dp))
            if (r.findings.isEmpty()) {
                Text("No problems found", style = MoodyTheme.type.heading, color = c.text)
                Spacer(Modifier.height(4.dp))
                Text(
                    "It opens, its streams can be decoded, and its data reads through to the end.",
                    style = MoodyTheme.type.body, color = c.text2,
                )
            }
            r.findings.forEach { f ->
                Column(Modifier.padding(bottom = 12.dp).fillMaxWidth().background(c.raised, Radii.md).padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(f.severity.icon(), null, Modifier.size(16.dp), tint = f.severity.color())
                        Spacer(Modifier.width(8.dp))
                        Text(f.title, style = MoodyTheme.type.heading, color = c.text)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(f.detail, style = MoodyTheme.type.body, color = c.text2)
                    if (f.fixes.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text("What you can do", style = MoodyTheme.type.label, color = c.text3)
                        f.fixes.forEach { fix ->
                            Row(Modifier.padding(top = 4.dp)) {
                                Text("•", style = MoodyTheme.type.body, color = c.text3, modifier = Modifier.width(14.dp))
                                Text(fix, style = MoodyTheme.type.body, color = c.text2)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SecondaryButton("Retry scan", { if (!checking) onRetry() }, icon = MoodyIcons.Refresh)
                QuietButton("Remove from library", { confirm = true }, color = c.danger)
            }
            Spacer(Modifier.height(8.dp))
            Text("Removing only hides it in Moody. The file stays on your phone, untouched.", style = MoodyTheme.type.caption, color = c.text3)
        }
    }
    if (confirm && r != null) ConfirmDialog(
        title = "Remove from library?",
        body = "“${r.name}” stays on your phone exactly as it is. Moody just stops listing it. You can bring it back from Media health, under Removed from library.",
        confirm = "Remove",
        onDismiss = { confirm = false },
        destructive = false,
    ) { confirm = false; onRemove(); toaster.show("Removed from library") }
}

@Composable
private fun SeverityPill(sev: Severity) {
    val col = sev.color()
    Row(
        Modifier.background(col.copy(alpha = 0.14f), Radii.pill).padding(horizontal = 10.dp, vertical = 4.dp).semantics { contentDescription = "Status: ${sev.label}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(sev.icon(), null, Modifier.size(12.dp), tint = col)
        Spacer(Modifier.width(6.dp))
        Text(sev.label, style = MoodyTheme.type.label, color = MoodyTheme.colors.text)
    }
}

private fun Severity.icon(): ImageVector = when (this) {
    Severity.HEALTHY -> MoodyIcons.Check
    Severity.WARNING -> MoodyIcons.Info
    Severity.CRITICAL -> MoodyIcons.Close
}

/** Status colours, kept apart from the accent. Always shown with an icon and a word, never colour alone. */
@Composable
private fun Severity.color(): Color {
    val c = MoodyTheme.colors
    return when (this) {
        Severity.HEALTHY -> if (c.isDark) Color(0xFF8FCB6B) else Color(0xFF3F7A2A)
        Severity.WARNING -> if (c.isDark) Color(0xFFE3B341) else Color(0xFF8A5A00)
        Severity.CRITICAL -> c.danger
    }
}

private fun agoLabel(at: Long): String {
    val m = (System.currentTimeMillis() - at) / 60_000
    return when {
        m < 1 -> "just now"
        m < 60 -> "$m min ago"
        m < 24 * 60 -> "${m / 60} h ago"
        else -> "${m / (24 * 60)} days ago"
    }
}
