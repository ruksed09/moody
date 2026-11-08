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
    val danger: Color,
    val isDark: Boolean,
)

private val Warm = Color(0xFFECD6BE)

val DarkColors = MoodyColors(
    bg = Color(0xFF13100E),
    surface = Color(0xFF1A1613),
    raised = Color(0xFF221D19),
    line = Warm.copy(alpha = 0.085f),
    line2 = Warm.copy(alpha = 0.16f),
    line3 = Warm.copy(alpha = 0.30f),
    text = Color(0xFFF1E9E0),
    text2 = Color(0xFFB8AB9D),
    text3 = Color(0xFF877B70),
    accent = Color(0xFFE6A766),
    accentHi = Color(0xFFEFB67C),
    accentInk = Color(0xFF1B130B),
    accentWash = Color(0xFFE6A766).copy(alpha = 0.12f),
    danger = Color(0xFFE5806B),
    isDark = true,
)

val MidnightColors = DarkColors.copy(
    bg = Color(0xFF000000),
    surface = Color(0xFF0E0B09),
    raised = Color(0xFF181411),
)

private val Ink = Color(0xFF3A2A1C)

val LightColors = MoodyColors(
    bg = Color(0xFFF6EFE6),
    surface = Color(0xFFFCF8F2),
    raised = Color(0xFFEFE5D8),
    line = Ink.copy(alpha = 0.09f),
    line2 = Ink.copy(alpha = 0.16f),
    line3 = Ink.copy(alpha = 0.30f),
    text = Color(0xFF221A14),
    text2 = Color(0xFF5C5047),
    text3 = Color(0xFF85786D),
    accent = Color(0xFFB86B25),
    accentHi = Color(0xFFA35E1F),
    accentInk = Color(0xFF1B130B),
    accentWash = Color(0xFFB86B25).copy(alpha = 0.12f),
    danger = Color(0xFFB4442E),
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
    val text = Color(0xFFFBF5EE)
    val ink = Color(0xE613100E)
    val scrim = Color(0xD60C0907)
    val rail = Color(0x33FBF5EE)
    val buffered = Color(0x4DFBF5EE)
}
