package com.moody.player.design

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/** WCAG 2.x relative luminance. */
fun relativeLuminance(c: Color): Double {
    fun ch(v: Float): Double = if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    return 0.2126 * ch(c.red) + 0.7152 * ch(c.green) + 0.0722 * ch(c.blue)
}

/** WCAG contrast ratio between two opaque colours, 1.0..21.0. */
fun contrastRatio(a: Color, b: Color): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}
