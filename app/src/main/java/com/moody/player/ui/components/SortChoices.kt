package com.moody.player.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moody.player.design.MoodyTheme
import com.moody.player.design.components.ChoiceChip
import com.moody.player.domain.SortKey
import com.moody.player.domain.SortSpec

/** The four sorts every media list offers. */
val FileSorts: List<Pair<SortKey, String>> = listOf(
    SortKey.DATE to "Date modified",
    SortKey.NAME to "File name",
    SortKey.SIZE to "File size",
    SortKey.DURATION to "Duration",
)

/** Names sort A to Z first; dates, sizes and lengths newest, largest and longest first. */
fun defaultAscending(k: SortKey): Boolean = k == SortKey.NAME || k == SortKey.TITLE

fun orderLabels(k: SortKey): Pair<String, String> = when (k) {
    SortKey.NAME, SortKey.TITLE -> "A to Z" to "Z to A"
    SortKey.DATE -> "Oldest first" to "Newest first"
    SortKey.SIZE -> "Smallest first" to "Largest first"
    SortKey.DURATION -> "Shortest first" to "Longest first"
}

/** "Sort by" chips, then the two orders for the picked sort. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SortChoices(keys: List<Pair<SortKey, String>>, sort: SortSpec, onSort: (SortSpec) -> Unit) {
    val c = MoodyTheme.colors
    val t = MoodyTheme.type
    Column {
        Text("Sort by", style = t.section, color = c.text3)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            keys.forEach { (k, label) -> ChoiceChip(label, sort.key == k, { if (sort.key != k) onSort(SortSpec(k, defaultAscending(k))) }) }
        }
        Spacer(Modifier.height(16.dp))
        Text("Order", style = t.section, color = c.text3)
        Spacer(Modifier.height(8.dp))
        val (asc, desc) = orderLabels(sort.key)
        val first = defaultAscending(sort.key)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // The usual order for this sort comes first.
            if (first) {
                ChoiceChip(asc, sort.ascending, { onSort(sort.copy(ascending = true)) })
                ChoiceChip(desc, !sort.ascending, { onSort(sort.copy(ascending = false)) })
            } else {
                ChoiceChip(desc, !sort.ascending, { onSort(sort.copy(ascending = false)) })
                ChoiceChip(asc, sort.ascending, { onSort(sort.copy(ascending = true)) })
            }
        }
    }
}
