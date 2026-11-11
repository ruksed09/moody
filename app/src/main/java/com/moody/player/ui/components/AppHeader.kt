package com.moody.player.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.player.design.MoodyTheme
import com.moody.player.design.Radii

/**
 * The bar at the top of Videos and Music: the Moody logo and name on the left, the screen's actions
 * grouped in one hairline pill on the right. Long-pressing the name can open something (the vault).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppTopBar(
    height: Dp,
    modifier: Modifier = Modifier,
    onLogoLong: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit,
) {
    val c = MoodyTheme.colors
    Row(modifier.fillMaxWidth().height(height).padding(start = 8.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .clip(Radii.md)
                .then(if (onLogoLong != null) Modifier.combinedClickable(onClick = {}, onLongClick = onLogoLong) else Modifier)
                .padding(8.dp)
                .semantics { contentDescription = "Moody" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MoodyLogo(size = 30.dp)
            Spacer(Modifier.width(10.dp))
            Text("Moody", style = MoodyTheme.type.display.copy(fontSize = 19.sp, lineHeight = 24.sp), color = c.text)
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.clip(Radii.pill).background(c.surface).border(1.dp, c.line, Radii.pill).padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

/**
 * The big screen title under the top bar: a tinted tile with the section's glyph, the name, and one
 * line of numbers. The logo stays in the top bar only.
 */
@Composable
fun SectionTitle(icon: ImageVector, title: String, stats: String, modifier: Modifier = Modifier) {
    val c = MoodyTheme.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(52.dp).clip(Radii.lg).background(c.accentWash).border(1.dp, c.accent.copy(alpha = 0.35f), Radii.lg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(24.dp), tint = c.accent)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MoodyTheme.type.hero, color = c.text, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(2.dp))
            Text(stats, style = MoodyTheme.type.caption, color = c.text2)
        }
    }
}
