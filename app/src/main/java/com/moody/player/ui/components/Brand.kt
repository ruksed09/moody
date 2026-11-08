package com.moody.player.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moody.player.design.MoodyTheme

/** The Moody mark: rounded square, a play glyph whose back edge curves in like a crescent. Same geometry as the launcher icon. */
@Composable
fun MoodyMark(modifier: Modifier = Modifier, size: Dp = 24.dp, fill: Color = MoodyTheme.colors.accent, ink: Color = MoodyTheme.colors.accentInk) {
    Canvas(modifier.size(size)) { drawMark(fill, ink, 1f, 1f) }
}

/**
 * Draws the mark into the current size. [square] and [glyph] are 0..1 reveal amounts used by the launch animation.
 */
fun DrawScope.drawMark(fill: Color, ink: Color, square: Float, glyph: Float) {
    val s = size.minDimension
    val u = s / 48f
    if (square > 0f) {
        drawRoundRect(fill.copy(alpha = fill.alpha * square), Offset.Zero, androidx.compose.ui.geometry.Size(s, s), CornerRadius(11 * u))
    }
    if (glyph > 0f) {
        val p = Path().apply {
            moveTo(17 * u, 12.5f * u)
            lineTo(35 * u, 24 * u)
            lineTo(17 * u, 35.5f * u)
            quadraticTo(23 * u, 24 * u, 17 * u, 12.5f * u)
            close()
        }
        drawPath(p, ink.copy(alpha = ink.alpha * glyph))
    }
}
