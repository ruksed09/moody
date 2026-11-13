package com.moody.player.ui.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moody.player.BuildConfig
import com.moody.player.data.update.UpdateManager
import com.moody.player.data.update.UpdateState
import com.moody.player.design.MoodyTheme
import com.moody.player.design.Radii
import com.moody.player.design.components.IconAction
import com.moody.player.design.components.LocalToaster
import com.moody.player.design.components.Motion
import com.moody.player.design.components.PrimaryButton
import com.moody.player.design.components.ReelLine
import com.moody.player.design.components.SecondaryButton
import com.moody.player.design.icons.MoodyIcons
import com.moody.player.design.illustrations.rememberReducedMotion
import com.moody.player.domain.ReleaseInfo
import com.moody.player.domain.formatBytes

/** The known update for the app headers: its version, and what a tap on the badge does. Null: nothing to show. */
class UpdateBadge(val version: String, val onOpen: () -> Unit)

val LocalUpdateBadge = compositionLocalOf<UpdateBadge?> { null }

/** Provides the header badge for everything in [content] while an update newer than this install is known. */
@Composable
fun ProvideUpdateBadge(manager: UpdateManager, content: @Composable () -> Unit) {
    val info by manager.available.collectAsStateWithLifecycle()
    val badge = remember(info?.version) { info?.let { UpdateBadge(it.version, manager::openPrompt) } }
    CompositionLocalProvider(LocalUpdateBadge provides badge, content = content)
}

/** A download icon with a red dot, for a top bar's actions. Takes no room when there is no update. */
@Composable
fun UpdateBadgeAction() {
    val badge = LocalUpdateBadge.current ?: return
    val c = MoodyTheme.colors
    Box {
        IconAction(MoodyIcons.Download, "Update available, version ${badge.version}", badge.onOpen, tint = c.text)
        // The dot sits on the icon's top right corner; the ring in the bar's colour keeps it apart from the strokes.
        Box(
            Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 10.dp).size(10.dp)
                .background(c.bg, CircleShape).padding(1.5.dp).background(c.danger, CircleShape),
        )
    }
}

/**
 * The "new version" popup. It opens by itself after a check (unless that version was dismissed) and from the header
 * badge, and stays up while the update downloads or waits to be installed.
 */
@Composable
fun UpdateDialogHost(manager: UpdateManager) {
    val state by manager.state.collectAsStateWithLifecycle()
    val open by manager.promptOpen.collectAsStateWithLifecycle()
    val toaster = LocalToaster.current
    var wasDownloading by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        if (state is UpdateState.Downloading) wasDownloading = true
        val s = state
        if (s is UpdateState.Failed && wasDownloading) { toaster.show(s.reason); wasDownloading = false; manager.dismissResult() }
    }
    val context = LocalContext.current
    var askedPermission by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        // Back from "Install unknown apps": install straight away if it was allowed.
        val s = manager.state.value
        if (askedPermission && s is UpdateState.Ready && !manager.needsInstallPermission()) {
            askedPermission = false
            context.startActivity(manager.installIntent(s.file))
        }
    }
    val info = when (val s = state) {
        is UpdateState.Available -> s.info
        is UpdateState.Downloading -> s.info
        is UpdateState.Ready -> s.info
        else -> return
    }
    if (!open) return

    Dialog(
        onDismissRequest = manager::closePrompt,
        properties = DialogProperties(dismissOnBackPress = manager.canDismiss(info), dismissOnClickOutside = false, usePlatformDefaultWidth = false),
    ) {
        // A short fade and settle; none at all when the phone's animations are off.
        val still = rememberReducedMotion()
        val shown = remember { MutableTransitionState(still) }.apply { targetState = true }
        AnimatedVisibility(shown, enter = fadeIn(Motion.enter()) + scaleIn(Motion.enter(), initialScale = 0.96f), exit = ExitTransition.None) {
            UpdateModal(
                info, state,
                canDismiss = manager.canDismiss(info),
                needsInstallPermission = state is UpdateState.Ready && manager.needsInstallPermission(),
                onUpdate = manager::startDownload,
                onDontShowAgain = manager::dontShowAgain,
                onCancelDownload = manager::cancelDownload,
                onInstall = {
                    val s = manager.state.value
                    if (s is UpdateState.Ready) {
                        if (manager.needsInstallPermission()) { askedPermission = true; context.startActivity(manager.installPermissionIntent()) }
                        else context.startActivity(manager.installIntent(s.file))
                    }
                },
            )
        }
    }
}

/** The popup's card with its margins, as it sits in the dialog window. Stateless, so it can be drawn in a test. */
@Composable
fun UpdateModal(
    info: ReleaseInfo,
    state: UpdateState,
    /** False for a required release: "Don't show again" is not offered. */
    canDismiss: Boolean,
    needsInstallPermission: Boolean,
    onUpdate: () -> Unit,
    onDontShowAgain: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MoodyTheme.colors
    val t = MoodyTheme.type
    Box(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 360.dp).fillMaxWidth().background(c.surface, Radii.xl).border(1.dp, c.line, Radii.xl).padding(18.dp)) {
            val title = when {
                state is UpdateState.Downloading -> "Downloading update"
                state is UpdateState.Ready -> "Ready to install"
                info.required -> "Update needed"
                else -> "Update available"
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(MoodyIcons.Download, null, Modifier.size(20.dp), tint = c.accent)
                Spacer(Modifier.width(10.dp))
                Text(title, style = t.title, color = c.text, modifier = Modifier.semantics { heading() })
            }
            Spacer(Modifier.height(6.dp))
            // One quiet line: from, to and the download size.
            val meta = "${BuildConfig.VERSION_NAME} \u2192 ${info.version}" + if (info.apkSize > 0) "  \u00b7  ${formatBytes(info.apkSize)}" else ""
            Text(meta, style = t.monoSmall, color = c.text3)
            if (info.notes.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                // Capped, and it gives way first when the screen is short (a phone on its side). A thin bar on the
                // right shows there is more whenever the notes run longer than the box.
                val scroll = rememberScrollState()
                val thumb = c.text3
                val rail = c.line
                Column(
                    Modifier.weight(1f, fill = false).heightIn(max = 148.dp)
                        .background(c.raised, Radii.md)
                        .drawWithContent {
                            drawContent()
                            if (scroll.maxValue > 0) {
                                val w = 3.dp.toPx()
                                val inset = 8.dp.toPx()
                                val x = size.width - w - 4.dp.toPx()
                                val railH = size.height - inset * 2
                                val thumbH = (railH * size.height / (size.height + scroll.maxValue)).coerceAtLeast(20.dp.toPx()).coerceAtMost(railH)
                                val y = inset + (railH - thumbH) * scroll.value / scroll.maxValue
                                val r = CornerRadius(w / 2)
                                drawRoundRect(rail, Offset(x, inset), Size(w, railH), r)
                                drawRoundRect(thumb, Offset(x, y), Size(w, thumbH), r)
                            }
                        }
                        .verticalScroll(scroll)
                        .padding(start = 12.dp, end = 18.dp, top = 10.dp, bottom = 10.dp),
                ) { Notes(info.notes) }
            }
            Spacer(Modifier.height(18.dp))
            when (state) {
                is UpdateState.Downloading -> DownloadRow(state, onCancelDownload)
                is UpdateState.Ready -> {
                    if (needsInstallPermission) {
                        Text(
                            "Android asks once before Moody can install updates. Turn on \u201cAllow from this source\u201d, then come back.",
                            style = t.caption, color = c.text2,
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                    PrimaryButton("Install", onInstall, Modifier.fillMaxWidth())
                }
                else -> if (canDismiss) ChoiceButtons(onDontShowAgain, onUpdate) else PrimaryButton("Update now", onUpdate, Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * "Don't show again" and "Update now" in one row: equal widths when both labels fit, else the quiet one takes the
 * width its label needs and "Update now" the rest. Only when even that can't fit (very large text) do they stack.
 */
@Composable
private fun ChoiceButtons(onDontShowAgain: () -> Unit, onUpdate: () -> Unit) {
    val t = MoodyTheme.type
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val quiet = "Don\u2019t show again"
    val main = "Update now"
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 8.dp
        val pad = 32.dp // a button's own side padding, both sides
        val quietW = with(density) { measurer.measure(quiet, t.label, maxLines = 1).size.width.toDp() } + pad + 2.dp
        val mainW = with(density) { measurer.measure(main, t.bodyStrong, maxLines = 1).size.width.toDp() } + pad + 2.dp
        when {
            maxOf(quietW, mainW) * 2 + gap <= maxWidth -> Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                SecondaryButton(quiet, onDontShowAgain, Modifier.weight(1f))
                PrimaryButton(main, onUpdate, Modifier.weight(1f))
            }
            quietW + mainW + gap <= maxWidth -> Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                SecondaryButton(quiet, onDontShowAgain, Modifier.width(quietW))
                PrimaryButton(main, onUpdate, Modifier.weight(1f))
            }
            else -> Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                PrimaryButton(main, onUpdate, Modifier.fillMaxWidth())
                SecondaryButton(quiet, onDontShowAgain, Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * What "Update now" turns into once tapped: "Pending" until the first bytes arrive, then the same pill filling with
 * the progress, and a close icon on the same line that cancels the download.
 */
@Composable
private fun DownloadRow(state: UpdateState.Downloading, onCancel: () -> Unit) {
    val c = MoodyTheme.colors
    val t = MoodyTheme.type
    val pending = state.done <= 0
    val target = if (!pending && state.total > 0) (state.done / state.total.toFloat()).coerceIn(0f, 1f) else 0f
    val still = rememberReducedMotion()
    val progress by animateFloatAsState(target, if (still) snap() else tween(250), label = "update-progress")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.weight(1f).height(40.dp).clip(Radii.md).background(c.raised).border(1.dp, c.line, Radii.md)
                .semantics(mergeDescendants = true) {},
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(c.accentWash))
            Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                if (pending) {
                    Text("Pending\u2026", style = t.bodyStrong, color = c.text2, modifier = Modifier.weight(1f))
                } else {
                    Text(
                        // The title already says "Downloading update"; the percent alone always fits.
                        "${(target * 100).toInt()}%", style = t.bodyStrong, color = c.text, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    if (state.total > 0) {
                        Spacer(Modifier.width(8.dp))
                        Text("${formatBytes(state.done)} / ${formatBytes(state.total)}", style = t.monoSmall, color = c.text2, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
        Spacer(Modifier.width(4.dp))
        IconAction(MoodyIcons.Close, "Cancel download", onCancel, tint = c.text2)
    }
}

/** Release notes with "- " bullets and **bold**, nothing more. */
@Composable
private fun Notes(md: String) {
    val c = MoodyTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        md.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.forEach { line ->
            val bullet = line.startsWith("- ") || line.startsWith("* ")
            Row {
                if (bullet) Text("•", style = MoodyTheme.type.caption, color = c.text3, modifier = Modifier.width(14.dp))
                Text(bold(if (bullet) line.drop(2) else line), style = MoodyTheme.type.caption, color = c.text2)
            }
        }
    }
}

private fun bold(s: String): AnnotatedString = buildAnnotatedString {
    val parts = s.split("**")
    parts.forEachIndexed { i, p -> if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(p) } else append(p) }
}

/** The "Check for updates" row in Settings. */
@Composable
fun UpdateSettingsRow(manager: UpdateManager, lastCheck: Long, onCheck: () -> Unit) {
    val c = MoodyTheme.colors
    val state by manager.state.collectAsStateWithLifecycle()
    val status = when (val s = state) {
        UpdateState.Checking -> "Checking"
        UpdateState.UpToDate -> "You have the latest version"
        is UpdateState.Failed -> "Couldn't check for updates. ${s.reason}"
        is UpdateState.Available -> "Version ${s.info.version} is ready"
        is UpdateState.Downloading -> "Downloading ${s.info.version}"
        is UpdateState.Ready -> "Version ${s.info.version} is ready to install"
        UpdateState.Idle -> if (lastCheck > 0) "Last checked ${ago(lastCheck)}" else "Not checked yet"
    }
    Column(Modifier.fillMaxWidth().padding(start = 56.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)) {
        Text("Moody ${BuildConfig.VERSION_NAME}", style = MoodyTheme.type.body, color = c.text)
        Text(status, style = MoodyTheme.type.caption, color = if (state is UpdateState.Failed) c.danger else c.text3)
        Spacer(Modifier.height(10.dp))
        if (state is UpdateState.Checking) ReelLine(null) else SecondaryButton("Check for updates", onCheck, icon = MoodyIcons.Refresh)
    }
}

private fun ago(t: Long): String {
    val mins = (System.currentTimeMillis() - t) / 60_000
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "$mins min ago"
        mins < 60 * 24 -> "${mins / 60} h ago"
        else -> "${mins / 1440} days ago"
    }
}

