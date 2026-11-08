package com.moody.player.ui.onboarding

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.moody.player.data.media.AccessLevel
import com.moody.player.data.media.accessLevel
import com.moody.player.data.media.mediaPermissions
import com.moody.player.design.MoodyTheme
import com.moody.player.design.Radii
import com.moody.player.design.components.IconAction
import com.moody.player.design.components.PrimaryButton
import com.moody.player.design.components.QuietButton
import com.moody.player.design.components.SecondaryButton
import com.moody.player.design.icons.MoodyIcons

/**
 * Asks for media access (required) and notifications (optional), one card each, with the reason in plain words.
 * After two refusals Android stops showing the prompt, so the card offers the app's settings page instead.
 */
@Composable
fun PermissionsScreen(onContinue: () -> Unit, onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val c = MoodyTheme.colors
    var access by remember { mutableStateOf(accessLevel(context)) }
    var notifOk by remember { mutableStateOf(notificationsGranted(context)) }
    var mediaAsks by remember { mutableIntStateOf(0) }
    var notifAsks by remember { mutableIntStateOf(0) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        access = accessLevel(context)
        notifOk = notificationsGranted(context)
    }

    val mediaLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        access = accessLevel(context); mediaAsks++
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifOk = it; notifAsks++
    }
    fun openSettings() = context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    val activity = context as? Activity
    val mediaBlocked = mediaAsks >= 2 || (mediaAsks >= 1 && activity != null && mediaPermissions().none { activity.shouldShowRequestPermissionRationale(it) } && access == AccessLevel.NONE)
    val notifBlocked = notifAsks >= 2

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconAction(MoodyIcons.ArrowBack, "Back", onBack, tint = c.text)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Text(if (Build.VERSION.SDK_INT >= 33) "Two quick permissions" else "Let Moody see your library", style = MoodyTheme.type.hero, color = c.text)
            Spacer(Modifier.height(12.dp))
            Text("Moody only reads what's on your phone. Nothing is uploaded.", style = MoodyTheme.type.body, color = c.text2, modifier = Modifier.widthIn(max = 420.dp))
            Spacer(Modifier.height(32.dp))
            PermissionCard(
                icon = MoodyIcons.Film,
                title = "Videos and music",
                body = when (access) {
                    AccessLevel.FULL -> "Allowed. Your library builds itself"
                    AccessLevel.PARTIAL -> "Allowed for the videos you picked. Allow all to see everything"
                    AccessLevel.NONE -> "Needed to find your videos and songs"
                },
                required = true,
                granted = access == AccessLevel.FULL,
                actionLabel = when { access == AccessLevel.FULL -> null; mediaBlocked -> "Open settings"; access == AccessLevel.PARTIAL -> "Allow all"; else -> "Allow" },
                onAction = { if (mediaBlocked) openSettings() else mediaLauncher.launch(mediaPermissions()) },
            )
            Spacer(Modifier.height(12.dp))
            if (Build.VERSION.SDK_INT >= 33) {
                PermissionCard(
                    icon = MoodyIcons.Bell,
                    title = "Notifications",
                    body = if (notifOk) "Allowed" else "For music controls and update downloads. You can skip this",
                    required = false,
                    granted = notifOk,
                    actionLabel = when { notifOk -> null; notifBlocked -> "Open settings"; else -> "Allow" },
                    onAction = { if (notifBlocked) openSettings() else notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            if (access == AccessLevel.NONE) QuietButton("Not now", onContinue)
            Spacer(Modifier.weight(1f))
            PrimaryButton("Continue", onContinue, enabled = access != AccessLevel.NONE)
        }
    }
}

private fun notificationsGranted(context: android.content.Context) =
    Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

@Composable
private fun PermissionCard(icon: ImageVector, title: String, body: String, required: Boolean, granted: Boolean, actionLabel: String?, onAction: () -> Unit) {
    val c = MoodyTheme.colors
    Row(
        Modifier.fillMaxWidth().background(c.surface, Radii.lg).border(1.dp, if (granted) c.accent.copy(alpha = 0.4f) else c.line, Radii.lg).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).background(if (granted) c.accentWash else c.raised, Radii.md), contentAlignment = Alignment.Center) {
            Icon(if (granted) MoodyIcons.Check else icon, null, Modifier.size(20.dp), tint = if (granted) c.accent else c.text2)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MoodyTheme.type.bodyStrong, color = c.text)
                if (!required) Text("  Optional", style = MoodyTheme.type.caption, color = c.text3)
            }
            Text(body, style = MoodyTheme.type.caption, color = c.text2)
        }
        if (actionLabel != null) {
            Spacer(Modifier.width(8.dp))
            SecondaryButton(actionLabel, onAction)
        }
    }
}
