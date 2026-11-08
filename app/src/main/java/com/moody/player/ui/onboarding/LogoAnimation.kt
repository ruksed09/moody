package com.moody.player.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.moody.player.design.MoodyTheme
import com.moody.player.design.components.Motion
import com.moody.player.ui.components.drawMark
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope

/**
 * The launch moment: the mark's outline is drawn like a reel being threaded (0-900 ms), the square fills
 * and the crescent-play glyph appears (800-1200 ms), then the wordmark rises in (1100-1600 ms).
 */
@Composable
fun LogoAnimation(onDone: () -> Unit) {
    val c = MoodyTheme.colors
    val trace = remember { Animatable(0f) }
    val fill = remember { Animatable(0f) }
    val glyph = remember { Animatable(0f) }
    val word = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            val a = async { trace.animateTo(1f, tween(900, easing = Motion.Ease)) }
            val b = async { delay(800); fill.animateTo(1f, tween(300, easing = LinearEasing)) }
            val g = async { delay(900); glyph.animateTo(1f, tween(300, easing = Motion.Ease)) }
            val w = async { delay(1100); word.animateTo(1f, tween(500, easing = Motion.Ease)) }
            a.await(); b.await(); g.await(); w.await()
        }
        delay(450)
        onDone()
    }

    Box(Modifier.fillMaxSize().background(c.bg).semantics { contentDescription = "Moody" }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.size(96.dp)) {
                val s = size.minDimension
                val inset = 2.dp.toPx()
                val outline = Path().apply {
                    addRoundRect(RoundRect(inset, inset, s - inset, s - inset, CornerRadius(s * 11 / 48f)))
                }
                val measure = PathMeasure().apply { setPath(outline, false) }
                val partial = Path()
                measure.getSegment(0f, measure.length * trace.value, partial, true)
                drawPath(partial, c.accent, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                drawMark(c.accent, c.accentInk, fill.value, glyph.value)
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "Moody",
                style = MoodyTheme.type.hero,
                color = c.text,
                modifier = Modifier.alpha(word.value).offset(y = (8 * (1 - word.value)).dp),
            )
        }
    }
}
