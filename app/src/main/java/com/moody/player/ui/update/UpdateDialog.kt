package com.moody.player.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
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
import com.moody.player.design.components.LocalToaster
import com.moody.player.design.components.PrimaryButton
import com.moody.player.design.components.QuietButton
import com.moody.player.design.components.ReelLine
import com.moody.player.design.icons.MoodyIcons
import com.moody.player.domain.formatBytes

/** The "new version" popup. Shown over everything while an update is available, downloading or ready to install. */
@Composable
fun UpdateDialogHost(manager: UpdateManager) {
    val state by manager.state.collectAsStateWithLifecycle()
    val toaster = LocalToaster.current
    var wasDownloading by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        if (state is UpdateState.Downloading) wasDownloading = true
        val s = state
        if (s is UpdateState.Failed && wasDownloading) { toaster.show(s.reason); wasDownloading = false; manager.dismissResult() }
    }
    val info = when (val s = state) {
        is UpdateState.Available -> s.info
        is UpdateState.Downloading -> s.info
        is UpdateState.Ready -> s.info
        else -> return
    }
    val context = LocalContext.current
    val c = MoodyTheme.colors
    var askedPermission by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        // Back from "Install unknown apps": install straight away if it was allowed.
        val s = manager.state.value
        if (askedPermission && s is UpdateState.Ready && !manager.needsInstallPermission()) {
            askedPermission = false
            context.startActivity(manager.installIntent(s.file))
        }
    }

    Dialog(
        onDismissRequest = { if (!info.required && state is UpdateState.Available) manager.later() },
        properties = DialogProperties(dismissOnBackPress = !info.required, dismissOnClickOutside = false),
    ) {
        Column(Modifier.fillMaxWidth().background(c.surface, Radii.xl).border(1.dp, c.line, Radii.xl).padding(24.dp)) {
            Box(Modifier.size(44.dp).background(c.accentWash, Radii.md), contentAlignment = Alignment.Center) {
                Icon(MoodyIcons.Download, null, Modifier.size(22.dp), tint = c.accent)
            }
            Spacer(Modifier.height(16.dp))
            Text(if (info.required) "Update needed" else "A new Moody is ready", style = MoodyTheme.type.title, color = c.text)
            Spacer(Modifier.height(4.dp))
            Text(
                "${BuildConfig.VERSION_NAME} → ${info.version}" + if (info.apkSize > 0) " · ${formatBytes(info.apkSize)}" else "",
                style = MoodyTheme.type.mono, color = c.text3,
            )
            if (info.notes.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) { Notes(info.notes) }
            }
            if (info.required) {
                Spacer(Modifier.height(12.dp))
                Text("This version is needed to keep using Moody.", style = MoodyTheme.type.caption, color = c.text2)
            }
            Spacer(Modifier.height(20.dp))
            when (val s = state) {
                is UpdateState.Downloading -> {
                    ReelLine(if (s.total > 0) s.done / s.total.toFloat() else null, height = 3.dp)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${formatBytes(s.done)} of ${formatBytes(s.total)}", style = MoodyTheme.type.mono, color = c.text2, modifier = Modifier.weight(1f))
                        QuietButton("Cancel", manager::cancelDownload)
                    }
                }
                is UpdateState.Ready -> {
                    if (manager.needsInstallPermission()) {
                        Text(
                            "Android asks once before Moody can install updates. Turn on “Allow from this source”, then come back.",
                            style = MoodyTheme.type.caption, color = c.text2,
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        PrimaryButton("Install", {
                            if (manager.needsInstallPermission()) { askedPermission = true; context.startActivity(manager.installPermissionIntent()) }
                            else context.startActivity(manager.installIntent(s.file))
                        })
                    }
                }
                else -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    if (!info.required) {
                        QuietButton("Later", manager::later)
                        Spacer(Modifier.width(8.dp))
                    }
                    PrimaryButton("Update", manager::startDownload, icon = MoodyIcons.Download)
                }
            }
        }
    }
}

/** Release notes with "- " bullets and **bold**, nothing more. */
@Composable
private fun Notes(md: String) {
    val c = MoodyTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        md.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.forEach { line ->
            val bullet = line.startsWith("- ") || line.startsWith("* ")
            Row {
                if (bullet) Text("•", style = MoodyTheme.type.body, color = c.accent, modifier = Modifier.width(16.dp))
                Text(bold(if (bullet) line.drop(2) else line), style = MoodyTheme.type.body, color = c.text2)
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
        if (state is UpdateState.Checking) ReelLine(null) else com.moody.player.design.components.SecondaryButton("Check for updates", onCheck, icon = MoodyIcons.Refresh)
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

