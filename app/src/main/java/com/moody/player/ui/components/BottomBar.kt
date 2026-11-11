package com.moody.player.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.moody.player.design.components.Motion
import com.moody.player.design.icons.MoodyIcons

enum class MainTab(val label: String, val icon: ImageVector) {
    VIDEO("Video", MoodyIcons.VideoTab),
    MUSIC("Music", MoodyIcons.Music),
}

/** Height of the pill itself; screens add the bottom margin and system navigation inset on top. */
val NavPillHeight = 40.dp
val NavPillMargin = 8.dp
private val NavPillWidth = 224.dp

/**
 * Floating navigation pill, centred: a raised segment slides under the active tab. The active icon
 * takes the accent; everything else stays muted.
 */
@Composable
fun MoodyBottomBar(current: MainTab, onSelect: (MainTab) -> Unit, modifier: Modifier = Modifier) {
    val c = MoodyTheme.colors
    val shape = RoundedCornerShape(20.dp)
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = NavPillMargin), contentAlignment = Alignment.Center) {
        BoxWithConstraints(
            Modifier
                .width(NavPillWidth)
                .height(NavPillHeight)
                .background(c.surface, shape)
                .border(1.dp, c.line2, shape)
                .clip(shape)
                .padding(4.dp),
        ) {
            val half = maxWidth / 2
            val x by animateDpAsState(half * current.ordinal, Motion.base(), label = "tab-seg")
            Box(Modifier.offset(x = x).width(half).fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(c.raised))
            Row(Modifier.fillMaxWidth().fillMaxHeight()) {
                MainTab.entries.forEach { tab ->
                    val active = tab == current
                    val icon by animateColorAsState(if (active) c.accent else c.text3, Motion.base(), label = "tab-icon")
                    val label by animateColorAsState(if (active) c.text else c.text3, Motion.base(), label = "tab-label")
                    Row(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Tab) { onSelect(tab) }
                            .semantics { selected = active },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(tab.icon, null, Modifier.size(18.dp), tint = icon)
                        Spacer(Modifier.width(6.dp))
                        Text(tab.label, style = MoodyTheme.type.label, color = label)
                    }
                }
            }
        }
    }
}
