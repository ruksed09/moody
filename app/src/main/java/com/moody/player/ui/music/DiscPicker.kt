package com.moody.player.ui.music

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moody.player.design.MoodyTheme
import com.moody.player.design.Radii
import com.moody.player.design.icons.MoodyIcons
import com.moody.player.domain.DiscLook
import com.moody.player.domain.DiscPalette
import com.moody.player.domain.DiscStyle

/** Every disc style as a small live disc in [look]'s colour. Meant for a flow row. */
@Composable
fun DiscStyleChoices(look: DiscLook, onPick: (DiscStyle) -> Unit, behind: Color = MoodyTheme.colors.bg) {
    DiscStyle.entries.forEach { s -> DiscOption(DiscLook(s, look.palette), s.label, s == look.style, 64.dp, 84.dp, behind) { onPick(s) } }
}

/** Every disc colour as a small live disc in [look]'s style. Meant for a flow row. */
@Composable
fun DiscPaletteChoices(look: DiscLook, onPick: (DiscPalette) -> Unit, behind: Color = MoodyTheme.colors.bg) {
    DiscPalette.entries.forEach { p -> DiscOption(DiscLook(look.style, p), p.label, p == look.palette, 40.dp, 68.dp, behind) { onPick(p) } }
}

@Composable
private fun DiscOption(look: DiscLook, label: String, selected: Boolean, disc: Dp, width: Dp, behind: Color, onClick: () -> Unit) {
    val c = MoodyTheme.colors
    Column(
        Modifier.width(width).clip(Radii.md).clickable(role = Role.RadioButton, onClick = onClick).semantics { this.selected = selected }.padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The ring marks the pick; it keeps its place when off, so nothing moves.
        Box(Modifier.size(disc + 12.dp).border(2.dp, if (selected) c.accent else Color.Transparent, CircleShape), contentAlignment = Alignment.Center) {
            Disc(null, disc, behind = behind, look = look)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MoodyTheme.type.caption, color = if (selected) c.text else c.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A mood mix's own disc: style and colour, and (once it has been changed) the way back to the look Moody gave it. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DiscLookSheet(title: String, look: DiscLook, custom: Boolean, onPick: (DiscLook) -> Unit, onReset: () -> Unit, onDismiss: () -> Unit) {
    val c = MoodyTheme.colors
    val t = MoodyTheme.type
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surface) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text(title, style = t.title, color = c.text, modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp))
            Text("Style", style = t.section, color = c.text3, modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(4.dp))
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DiscStyleChoices(look, { onPick(look.copy(style = it)) }, c.surface)
            }
            Spacer(Modifier.height(12.dp))
            Text("Colour", style = t.section, color = c.text3, modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(4.dp))
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DiscPaletteChoices(look, { onPick(look.copy(palette = it)) }, c.surface)
            }
            if (custom) {
                Spacer(Modifier.height(8.dp))
                SheetRow(MoodyIcons.Replay, "Reset to automatic") { onReset() }
            }
        }
    }
}
