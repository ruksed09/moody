package com.moody.player.domain

/** How a disc is built. PICTURE is the look Moody always had: the cover printed across the whole disc. */
enum class DiscStyle(val label: String) {
    PICTURE("Picture disc"),
    CLASSIC("Classic CD"),
    VINYL("Vinyl"),
    HOLO("Holographic"),
    MINIMAL("Minimal"),
}

/** The colour of a disc's rim, label and hub. CRIMSON is the colour Moody always had. */
enum class DiscPalette(val label: String) {
    CRIMSON("Crimson"),
    CORAL("Coral"),
    AMBER("Amber"),
    MOSS("Moss"),
    LAGOON("Lagoon"),
    COBALT("Cobalt"),
    SAND("Sand"),
    GRAPHITE("Graphite"),
}

data class DiscLook(val style: DiscStyle = DiscStyle.PICTURE, val palette: DiscPalette = DiscPalette.CRIMSON)

/**
 * The disc a mood mix gets by itself, so mixes can be told apart at a glance. Moody's own mixes have a look
 * that suits them; any other id gets one worked out from the id, the same every time.
 */
fun autoDiscLook(mixId: String): DiscLook = when (mixId) {
    "now" -> DiscLook(DiscStyle.CLASSIC, DiscPalette.AMBER)
    "repeat" -> DiscLook(DiscStyle.HOLO, DiscPalette.CORAL)
    "night" -> DiscLook(DiscStyle.VINYL, DiscPalette.COBALT)
    "focus" -> DiscLook(DiscStyle.MINIMAL, DiscPalette.GRAPHITE)
    "loved" -> DiscLook(DiscStyle.PICTURE, DiscPalette.CRIMSON)
    "rediscover" -> DiscLook(DiscStyle.VINYL, DiscPalette.MOSS)
    else -> {
        val h = mixId.fold(7) { acc, ch -> acc * 31 + ch.code } and Int.MAX_VALUE
        DiscLook(DiscStyle.entries[h % DiscStyle.entries.size], DiscPalette.entries[(h / 7) % DiscPalette.entries.size])
    }
}

fun encodeDiscLook(look: DiscLook): String = "${look.style.name}:${look.palette.name}"

/** Null when the text is not a look this version knows. */
fun decodeDiscLook(text: String): DiscLook? {
    val style = DiscStyle.entries.firstOrNull { it.name == text.substringBefore(':') } ?: return null
    val palette = DiscPalette.entries.firstOrNull { it.name == text.substringAfter(':', "") } ?: return null
    return DiscLook(style, palette)
}
