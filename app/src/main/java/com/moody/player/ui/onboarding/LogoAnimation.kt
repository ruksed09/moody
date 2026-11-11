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
import com.moody.player.ui.components.MoodyLogo
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope

/**
 * The launch moment: the logo scales up into place (0-900 ms),
 * then the wordmark rises in (700-1300 ms).
 */
@Composable
fun LogoAnimation(onDone: () -> Unit) {
    val c = MoodyTheme.colors
    val logo = remember { Animatable(0f) }
    val word = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            val l = async { delay(150); logo.animateTo(1f, tween(750, easing = Motion.Ease)) }
            val w = async { delay(700); word.animateTo(1f, tween(600, easing = Motion.Ease)) }
            l.await(); w.await()
        }
        delay(450)
        onDone()
    }

    Box(Modifier.fillMaxSize().background(c.bg).semantics { contentDescription = "Moody" }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                MoodyLogo(
                    Modifier.graphicsLayer {
                        val s = 0.82f + 0.18f * logo.value
                        scaleX = s; scaleY = s; alpha = logo.value
                    },
                    size = 112.dp,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Moody",
                style = MoodyTheme.type.hero,
                color = c.text,
                modifier = Modifier.alpha(word.value).offset(y = (8 * (1 - word.value)).dp),
            )
        }
    }
}
