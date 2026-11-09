package com.moody.player.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.moody.player.BuildConfig
import com.moody.player.design.MoodyTheme
import com.moody.player.design.Radii
import com.moody.player.design.icons.MoodyIcons

const val DEVELOPER_NAME = "Charlie"
const val DEVELOPER_URL = "https://telegram.dog/TheMaverickR"
const val TAGLINE = "Every video, every song, your mood."

/** Quiet sign-off at the end of the Videos and Music lists and in Settings: credit, version, tagline. */
@Composable
fun CreditFooter(modifier: Modifier = Modifier) {
    val c = MoodyTheme.colors
    val context = LocalContext.current
    Column(modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier
                .clip(Radii.pill)
                .clickable(role = Role.Button, onClickLabel = "Open the developer's Telegram") {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DEVELOPER_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(MoodyIcons.Captain, null, Modifier.size(16.dp), tint = c.accent)
            Spacer(Modifier.width(6.dp))
            Text("Developed by ", style = MoodyTheme.type.caption, color = c.text3)
            Text(DEVELOPER_NAME, style = MoodyTheme.type.label, color = c.accent)
        }
        Spacer(Modifier.height(2.dp))
        Text(
            "Moody ${BuildConfig.VERSION_NAME}  ·  $TAGLINE",
            style = MoodyTheme.type.caption, color = c.text3, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}
