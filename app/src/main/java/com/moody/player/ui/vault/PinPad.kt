package com.moody.player.ui.vault

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.moody.player.design.MoodyTheme
import com.moody.player.design.components.Motion
import com.moody.player.design.icons.MoodyIcons
import kotlin.math.roundToInt

/** Filled dots for typed digits; [slots] dots in total. Shakes when [shake] changes. */
@Composable
fun PinDots(length: Int, slots: Int, shake: Int, error: Boolean) {
    val c = MoodyTheme.colors
    val offset = remember { Animatable(0f) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(shake) {
        if (shake == 0) return@LaunchedEffect
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        for (x in listOf(14f, -12f, 9f, -6f, 3f, 0f)) offset.animateTo(x, tween(45))
    }
    Row(
        Modifier.offset { IntOffset(offset.value.dp.roundToPx(), 0) }.semantics { contentDescription = "$length of $slots digits entered" },
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        repeat(slots) { i ->
            val filled = i < length
            val s by animateFloatAsState(if (filled) 1f else 0.7f, Motion.fast(), label = "dot")
            Box(
                Modifier.size(14.dp).scale(s).clip(CircleShape)
                    .background(if (filled) (if (error) c.danger else c.accent) else Color.Transparent)
                    .border(1.5.dp, if (filled) Color.Transparent else c.line3, CircleShape),
            )
        }
    }
}

/** Number pad: 1-9, an optional left key (e.g. "Forgot"), 0, backspace. */
@Composable
fun PinPad(onDigit: (Char) -> Unit, onBackspace: () -> Unit, enabled: Boolean = true, leftLabel: String? = null, onLeft: (() -> Unit)? = null) {
    val rows = listOf("123", "456", "789")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEach { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) { r.forEach { d -> Key(d.toString(), enabled) { onDigit(d) } } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            if (leftLabel != null && onLeft != null) TextKey(leftLabel, onLeft) else Spacer(Modifier.size(72.dp))
            Key("0", enabled) { onDigit('0') }
            Box(
                Modifier.size(72.dp).clip(CircleShape).clickable(enabled = enabled, role = Role.Button, onClick = onBackspace).semantics { contentDescription = "Delete digit" },
                contentAlignment = Alignment.Center,
            ) { Icon(MoodyIcons.Backspace, null, Modifier.size(24.dp), tint = MoodyTheme.colors.text2) }
        }
    }
}

@Composable
private fun Key(label: String, enabled: Boolean, onClick: () -> Unit) {
    val c = MoodyTheme.colors
    val haptics = LocalHapticFeedback.current
    Box(
        Modifier.size(72.dp).clip(CircleShape).background(c.surface).border(1.dp, c.line, CircleShape)
            .clickable(enabled = enabled, role = Role.Button) { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(label, style = MoodyTheme.type.display, color = if (enabled) c.text else c.text3) }
}

@Composable
private fun TextKey(label: String, onClick: () -> Unit) {
    Box(Modifier.size(72.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, style = MoodyTheme.type.caption, color = MoodyTheme.colors.text2)
    }
}

@Composable
fun Gap(h: Int) = Spacer(Modifier.height(h.dp))

@Composable
fun HGap(w: Int) = Spacer(Modifier.width(w.dp))
