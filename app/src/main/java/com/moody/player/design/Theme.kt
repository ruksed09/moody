package com.moody.player.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val LocalMoodyColors = staticCompositionLocalOf { DarkColors }
val LocalMoodyType = staticCompositionLocalOf { moodyType() }

object MoodyTheme {
    val colors: MoodyColors @Composable @ReadOnlyComposable get() = LocalMoodyColors.current
    val type: MoodyType @Composable @ReadOnlyComposable get() = LocalMoodyType.current
}

object Radii {
    val sm = RoundedCornerShape(6.dp)
    val md = RoundedCornerShape(10.dp)
    val lg = RoundedCornerShape(14.dp)
    val xl = RoundedCornerShape(20.dp)
    val pill = RoundedCornerShape(50)
}

/** Theme switches crossfade every token over 250 ms. */
@Composable
private fun animated(target: MoodyColors): MoodyColors {
    val spec = tween<Color>(250)
    @Composable fun a(c: Color) = animateColorAsState(c, spec, label = "theme").value
    return target.copy(
        bg = a(target.bg), surface = a(target.surface), raised = a(target.raised),
        line = a(target.line), line2 = a(target.line2), line3 = a(target.line3),
        text = a(target.text), text2 = a(target.text2), text3 = a(target.text3),
        accent = a(target.accent), accentHi = a(target.accentHi), accentInk = a(target.accentInk),
        accentWash = a(target.accentWash), danger = a(target.danger),
    )
}

@Composable
fun MoodyTheme(mode: ThemeMode, fontMode: FontMode = FontMode.APP, content: @Composable () -> Unit) {
    val colors = animated(moodyColors(mode, isSystemInDarkTheme()))
    val type = androidx.compose.runtime.remember(fontMode) { moodyType(fontMode) }
    val scheme = (if (colors.isDark) darkColorScheme() else lightColorScheme()).copy(
        primary = colors.accent, onPrimary = colors.accentInk,
        primaryContainer = colors.accentWash, onPrimaryContainer = colors.text,
        secondary = colors.accent, onSecondary = colors.accentInk,
        secondaryContainer = colors.raised, onSecondaryContainer = colors.text,
        background = colors.bg, onBackground = colors.text,
        surface = colors.surface, onSurface = colors.text,
        surfaceVariant = colors.raised, onSurfaceVariant = colors.text2,
        surfaceContainerLowest = colors.bg, surfaceContainerLow = colors.surface,
        surfaceContainer = colors.surface, surfaceContainerHigh = colors.raised,
        surfaceContainerHighest = colors.raised, surfaceBright = colors.raised, surfaceDim = colors.bg,
        outline = colors.line3, outlineVariant = colors.line2,
        error = colors.danger, onError = colors.accentInk,
        inverseSurface = colors.text, inverseOnSurface = colors.bg, scrim = Color.Black,
    )
    val typography = Typography(
        displaySmall = type.display, headlineSmall = type.title, titleLarge = type.title,
        titleMedium = type.heading, titleSmall = type.bodyStrong,
        bodyLarge = type.body, bodyMedium = type.body, bodySmall = type.caption,
        labelLarge = type.label, labelMedium = type.caption, labelSmall = type.monoSmall,
    )
    CompositionLocalProvider(LocalMoodyColors provides colors, LocalMoodyType provides type) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography,
            shapes = Shapes(extraSmall = Radii.sm, small = Radii.md, medium = Radii.lg, large = Radii.xl, extraLarge = Radii.xl),
            content = content,
        )
    }
}
