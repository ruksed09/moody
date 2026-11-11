package com.moody.player.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.player.design.MoodyTheme
import com.moody.player.design.Radii

/**
 * The bar at the top of Videos and Music: the Moody logo and name on the left, the screen's actions as
 * bare 48 dp icons on the right. Long-pressing the name can open something (the vault).
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
    Row(modifier.fillMaxWidth().height(height).padding(start = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .clip(Radii.md)
                .then(if (onLogoLong != null) Modifier.combinedClickable(onClick = {}, onLongClick = onLogoLong) else Modifier)
                .padding(8.dp)
                .semantics { contentDescription = "Moody" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MoodyLogo(size = 24.dp)
            Spacer(Modifier.width(8.dp))
            Text("Moody", style = MoodyTheme.type.title.copy(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold), color = c.text)
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** The screen title under the top bar: the name, and one quiet line of numbers under it. */
@Composable
fun SectionTitle(title: String, stats: String, modifier: Modifier = Modifier) {
    val c = MoodyTheme.colors
    Column(modifier.heightIn(min = 56.dp), verticalArrangement = Arrangement.Center) {
        Text(title, style = MoodyTheme.type.hero, color = c.text, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(2.dp))
        Text(stats, style = MoodyTheme.type.caption, color = c.text2)
    }
}
