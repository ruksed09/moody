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

/** Dark: near-black, cool neutral surfaces, one purple accent (owner's mockup). */
val DarkColors = MoodyColors(
    bg = Color(0xFF0B0A0F),
    surface = Color(0xFF141219),
    raised = Color(0xFF1D1A23),
    line = White.copy(alpha = 0.08f),
    line2 = White.copy(alpha = 0.14f),
    line3 = White.copy(alpha = 0.26f),
    text = Color(0xFFF4F2F8),
    text2 = Color(0xFFB3AEBC),
    text3 = Color(0xFF8A8494),
    accent = Color(0xFFA774F2),
    accentHi = Color(0xFFBC94F6),
    accentInk = Color(0xFF14091F),
    accentWash = Color(0xFFA774F2).copy(alpha = 0.24f),
    accentDeep = Color(0xFF7C3AED),
    danger = Color(0xFFF07A8A),
    isDark = true,
)

val MidnightColors = DarkColors.copy(
    bg = Color(0xFF000000),
    surface = Color(0xFF0E0D12),
    raised = Color(0xFF17151C),
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
    accentDeep = Color(0xFFB86B25),
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
    val ink = Color(0xE60B0A0F)
    val scrim = Color(0xD608070B)
    val rail = Color(0x33FBF5EE)
    val buffered = Color(0x4DFBF5EE)
}
