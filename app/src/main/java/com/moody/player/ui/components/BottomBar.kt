package com.moody.player.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.moody.player.design.MoodyTheme
import com.moody.player.design.Radii
import com.moody.player.design.components.Motion
import com.moody.player.design.icons.MoodyIcons

enum class MainTab(val label: String, val icon: ImageVector) {
    VIDEO("Video", MoodyIcons.Film),
    MUSIC("Music", MoodyIcons.Music),
}

/** Two tabs. The active one sits on a small raised pill that slides between them. */
@Composable
fun MoodyBottomBar(current: MainTab, onSelect: (MainTab) -> Unit, modifier: Modifier = Modifier) {
    val c = MoodyTheme.colors
    Column(modifier.fillMaxWidth().background(c.bg)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        BoxWithConstraints(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 24.dp)) {
            val slot = maxWidth / MainTab.entries.size
            val pillW = 96.dp
            val x by animateDpAsState(slot * current.ordinal + (slot - pillW) / 2, Motion.base(), label = "tab-pill")
            Box(
                Modifier
                    .offset(x = x)
                    .align(Alignment.CenterStart)
                    .width(pillW)
                    .height(40.dp)
                    .clip(Radii.pill)
                    .background(c.raised),
            )
            Row(Modifier.fillMaxWidth().fillMaxHeight()) {
                MainTab.entries.forEach { tab ->
                    val active = tab == current
                    val tint by animateColorAsState(if (active) c.accent else c.text3, Motion.base(), label = "tab-tint")
                    val label by animateColorAsState(if (active) c.text else c.text3, Motion.base(), label = "tab-label")
                    Row(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Tab) { onSelect(tab) }
                            .semantics { selected = active },
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(tab.icon, null, Modifier.size(20.dp), tint = tint)
                        Spacer(Modifier.width(8.dp))
                        Text(tab.label, style = MoodyTheme.type.label, color = label)
                    }
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}
