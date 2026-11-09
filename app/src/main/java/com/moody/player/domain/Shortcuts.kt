package com.moody.player.domain

/** Optional player shortcuts the user can place in the upper-left shortcut row. */
enum class PlayerShortcut(val label: String) {
    LOOP("Loop"),
    SHUFFLE("Shuffle"),
    SCREENSHOT("Screenshot"),
    AB_REPEAT("A–B repeat"),
    MUTE("Mute"),
    BACKGROUND_PLAY("Background play"),
}

/** At most this many shortcuts show, so the row never crowds the title. */
const val MAX_SHORTCUTS = 4

val DEFAULT_SHORTCUTS = listOf(PlayerShortcut.LOOP, PlayerShortcut.MUTE)

fun addShortcut(selected: List<PlayerShortcut>, s: PlayerShortcut): List<PlayerShortcut> =
    if (s in selected || selected.size >= MAX_SHORTCUTS) selected else selected + s

fun removeShortcut(selected: List<PlayerShortcut>, s: PlayerShortcut): List<PlayerShortcut> = selected - s

fun moveShortcut(selected: List<PlayerShortcut>, from: Int, to: Int): List<PlayerShortcut> {
    if (from !in selected.indices || to !in selected.indices || from == to) return selected
    return selected.toMutableList().apply { add(to, removeAt(from)) }
}

fun encodeShortcuts(list: List<PlayerShortcut>): String = list.joinToString(",") { it.name }

fun decodeShortcuts(text: String): List<PlayerShortcut> =
    text.split(',').mapNotNull { n -> PlayerShortcut.entries.firstOrNull { it.name == n.trim() } }.distinct().take(MAX_SHORTCUTS)
