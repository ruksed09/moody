package com.moody.player.domain

/**
 * A quiet picture behind the full-screen visualizer. Each visual has its own short list ([backdropsFor]);
 * NONE is plain black.
 */
enum class VizBackdrop(val label: String) {
    NONE("None"),
    GLOW("Glow"),
    FLOOR("Floor"),
    GRID("Grid"),
    HALO("Halo"),
    STARS("Stars"),
    ORBITS("Orbits"),
    HORIZON("Horizon"),
    TIDE("Tide"),
    DUST("Dust"),
    RIPPLES("Ripples"),
    VIGNETTE("Vignette"),
    GRAIN("Grain"),
}

/** The backdrops that suit a visual, the usual one first. */
fun backdropsFor(style: VizStyle): List<VizBackdrop> = when (style) {
    VizStyle.BARS -> listOf(VizBackdrop.FLOOR, VizBackdrop.GRID, VizBackdrop.GLOW)
    VizStyle.CIRCLE -> listOf(VizBackdrop.HALO, VizBackdrop.STARS, VizBackdrop.ORBITS)
    VizStyle.WAVE -> listOf(VizBackdrop.HORIZON, VizBackdrop.TIDE, VizBackdrop.DUST)
    VizStyle.RINGS -> listOf(VizBackdrop.RIPPLES, VizBackdrop.STARS, VizBackdrop.GLOW)
    VizStyle.MINIMAL -> listOf(VizBackdrop.VIGNETTE, VizBackdrop.GRAIN)
}

/** The backdrop to show behind [style]: the one picked for it, or its usual one. A pick that no longer fits is ignored. */
fun backdropOf(style: VizStyle, picked: Map<VizStyle, VizBackdrop>): VizBackdrop {
    val options = backdropsFor(style)
    return picked[style]?.takeIf { it == VizBackdrop.NONE || it in options } ?: options.first()
}

fun encodeBackdrops(picked: Map<VizStyle, VizBackdrop>): String = picked.entries.joinToString(";") { "${it.key.name}=${it.value.name}" }

/** Entries this version does not know are dropped. */
fun decodeBackdrops(text: String): Map<VizStyle, VizBackdrop> = text.split(';').mapNotNull { part ->
    val style = VizStyle.entries.firstOrNull { it.name == part.substringBefore('=') } ?: return@mapNotNull null
    val backdrop = VizBackdrop.entries.firstOrNull { it.name == part.substringAfter('=', "") } ?: return@mapNotNull null
    style to backdrop
}.toMap()
