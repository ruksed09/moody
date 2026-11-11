package com.moody.player.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

enum class ThemeMode { SYSTEM, LIGHT, DARK, MIDNIGHT }

/** Colour tokens. Names follow the Kurogane mockup (`--bg`, `--surface`, `--line`, ...). */
@Immutable
data class MoodyColors(
    val bg: Color,
    val surface: Color,
    val raised: Color,
    val line: Color,
    val line2: Color,
    val line3: Color,
    val text: Color,
    val text2: Color,
    val text3: Color,
    val accent: Color,
    val accentHi: Color,
    val accentInk: Color,
    val accentWash: Color,
    /** Stronger accent for filled controls: switch tracks, active segments. */
    val accentDeep: Color,
    val danger: Color,
    val isDark: Boolean,
)

private val White = Color(0xFFFFFFFF)

/**
 * "Readout": matte graphite faceplates and one teal like a hi-fi display at night. Surfaces stay
 * near-neutral; the teal marks the selection, the main action and what's playing.
 */
val DarkColors = MoodyColors(
    bg = Color(0xFF0C0E10),
    surface = Color(0xFF14171A),
    raised = Color(0xFF1C2024),
    line = White.copy(alpha = 0.08f),
    line2 = White.copy(alpha = 0.14f),
    line3 = White.copy(alpha = 0.26f),
    text = Color(0xFFEEF1F2),
    text2 = Color(0xFFA9B1B6),
    text3 = Color(0xFF818A90),
    accent = Color(0xFF4FD1C5),
    accentHi = Color(0xFF7BE0D6),
    accentInk = Color(0xFF04201D),
    accentWash = Color(0xFF4FD1C5).copy(alpha = 0.18f),
    accentDeep = Color(0xFF1A8F85),
    danger = Color(0xFFF2777A),
    isDark = true,
)

val MidnightColors = DarkColors.copy(
    bg = Color(0xFF000000),
    surface = Color(0xFF0D0F11),
    raised = Color(0xFF15181B),
)

private val Ink = Color(0xFF111517)

val LightColors = MoodyColors(
    bg = Color(0xFFF4F6F6),
    surface = Color(0xFFFFFFFF),
    raised = Color(0xFFE9EDEE),
    line = Ink.copy(alpha = 0.09f),
    line2 = Ink.copy(alpha = 0.16f),
    line3 = Ink.copy(alpha = 0.30f),
    text = Color(0xFF111517),
    text2 = Color(0xFF4A5358),
    text3 = Color(0xFF656E74),
    accent = Color(0xFF0B766D),
    accentHi = Color(0xFF085E57),
    accentInk = Color(0xFFFFFFFF),
    accentWash = Color(0xFF0B766D).copy(alpha = 0.10f),
    accentDeep = Color(0xFF0B766D),
    danger = Color(0xFFB4322E),
    isDark = false,
)

fun moodyColors(mode: ThemeMode, systemDark: Boolean): MoodyColors = when (mode) {
    ThemeMode.SYSTEM -> if (systemDark) DarkColors else LightColors
    ThemeMode.LIGHT -> LightColors
    ThemeMode.DARK -> DarkColors
    ThemeMode.MIDNIGHT -> MidnightColors
}

/** Fixed colours used on top of video, independent of theme. */
object OnVideo {
    val text = Color(0xFFF5F7F7)
    val ink = Color(0xE60C0E10)
    val scrim = Color(0xD6080A0C)
    val rail = Color(0x33F5F7F7)
    val buffered = Color(0x4DF5F7F7)
}
