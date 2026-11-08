package com.moody.player.ui.music

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moody.player.design.MoodyTheme
import com.moody.player.design.components.ChoiceChip
import com.moody.player.design.components.ReelLine
import com.moody.player.player.music.MusicController

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EqualizerSheet(music: MusicController, onDismiss: () -> Unit) {
    val c = MoodyTheme.colors
    var info by remember { mutableStateOf<MusicController.EqInfo?>(null) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { music.equalizer { info = it; loaded = true } }
    fun set(preset: Int, bass: Boolean) = music.equalizer(preset to bass) { info = it }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surface) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text("Equalizer", style = MoodyTheme.type.title, color = c.text)
            Spacer(Modifier.height(16.dp))
            val i = info
            when {
                !loaded -> ReelLine(null)
                i == null || i.presets.isEmpty() -> Text("This phone does not let apps change its sound", style = MoodyTheme.type.body, color = c.text2)
                else -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceChip("Off", i.preset < 0, { set(-1, i.bass) })
                        i.presets.forEachIndexed { idx, name -> ChoiceChip(name, i.preset == idx, { set(idx, i.bass) }) }
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Bass boost", style = MoodyTheme.type.bodyStrong, color = c.text)
                            Text("Fuller low end on headphones", style = MoodyTheme.type.caption, color = c.text3)
                        }
                        Switch(i.bass, { set(i.preset, it) })
                    }
                }
            }
        }
    }
}
