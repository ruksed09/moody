package com.moody.player.ui.music

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.moody.player.design.MoodyTheme
import com.moody.player.data.media.AudioItem
import kotlinx.coroutines.isActive

/**
 * One turning angle shared by every disc of the song that is playing, so the mini player, its list row
 * and Now Playing stay in step. It only advances while music plays and a spinning disc is on screen;
 * pausing keeps the angle, so a disc stops where it is instead of snapping back.
 */
object DiscClock {
    private val _angle = mutableFloatStateOf(0f)
    val angle: Float get() = _angle.floatValue
    internal val viewers = mutableIntStateOf(0)
    internal fun advance(by: Float) { _angle.floatValue = (_angle.floatValue + by) % 360f }
}

/**
 * Runs the [DiscClock] while [playing]. Put it once near the root. With animations switched off in
 * Android's settings (checked again each time the app comes back) the discs stay still.
 */
@Composable
fun DriveDiscClock(playing: Boolean) {
    val context = LocalContext.current
    var still by remember { mutableStateOf(reducedMotion(context)) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) still = reducedMotion(context) }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val watched = DiscClock.viewers.intValue > 0
    LaunchedEffect(playing, still, watched) {
        if (!playing || still || !watched) return@LaunchedEffect
        var last = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (last != 0L) DiscClock.advance((now - last) / 1_000_000_000f * DEG_PER_SEC)
                last = now
            }
        }
    }
}

private fun reducedMotion(context: Context) =
    runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)

/** Cover requests come in a few sizes so Coil can reuse them: rows and the mini player, cards, Now Playing. */
fun coverRequestPx(size: Dp): Int = when {
    size <= 56.dp -> 128
    size <= 120.dp -> 256
    size <= 200.dp -> 512
    else -> 768
}

/**
 * A picture disc: the song's cover printed on a CD, with a clear hub and a spindle hole. Without a cover
 * it is a plain dark disc with a tinted label. [spinning] makes it follow the [DiscClock] (only the song
 * that is playing should). [behind] is the colour showing through the hole.
 */
@Composable
fun Disc(item: AudioItem?, size: Dp, modifier: Modifier = Modifier, spinning: Boolean = false, behind: Color = MoodyTheme.colors.bg) {
    val c = MoodyTheme.colors
    if (spinning) {
        DisposableEffect(Unit) {
            DiscClock.viewers.intValue++
            onDispose { DiscClock.viewers.intValue-- }
        }
    }
    var failed by remember(item?.uri, item?.cover) { mutableStateOf(false) }
    val labels = listOf(c.accentDeep, c.accent.copy(alpha = 0.55f), c.text3, c.line3)
    val label = labels[((item?.album?.hashCode() ?: 0) and 0x7fffffff) % labels.size]
    val detailed = size >= 96.dp
    Box(modifier.size(size)) {
        // Disc body and the label for songs without a cover; this part turns.
        Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = if (spinning) DiscClock.angle else 0f }) {
            Canvas(Modifier.fillMaxSize()) {
                val r = this.size.minDimension / 2f
                drawCircle(c.raised, r)
                drawCircle(label, r * 0.46f)
                // A short mark on the label so the turning reads.
                val m = r * 0.34f
                drawArc(
                    Color.Black.copy(alpha = 0.22f), -100f, 70f, useCenter = false,
                    topLeft = Offset(center.x - m, center.y - m), size = Size(m * 2, m * 2),
                    style = Stroke(width = r * 0.05f, cap = StrokeCap.Round),
                )
            }
            if (item != null && !failed) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.cover?.let { java.io.File(it) } ?: item.uri)
                        .size(coverRequestPx(size))
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().padding(size * 0.02f).clip(CircleShape),
                    onError = { failed = true },
                )
            }
        }
        // What stays still: grooves and sheen (both round, so turning wouldn't show), hub, hole and rim.
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2f
            if (detailed) {
                var g = r * 0.36f
                while (g < r * 0.94f) {
                    drawCircle(Color.White.copy(alpha = 0.04f), g, style = Stroke(width = 1f))
                    g += r * 0.05f
                }
            }
            drawCircle(
                Brush.sweepGradient(
                    0f to Color.Transparent, 0.10f to Color(0x1AFFFFFF), 0.20f to Color.Transparent,
                    0.50f to Color.Transparent, 0.60f to Color(0x14FFFFFF), 0.70f to Color.Transparent,
                    1f to Color.Transparent, center = center,
                ),
                r * 0.96f,
            )
            drawCircle(Color.Black.copy(alpha = 0.28f), r * 0.30f)
            drawCircle(Color.White.copy(alpha = 0.10f), r * 0.30f, style = Stroke(width = 1.dp.toPx()))
            drawCircle(behind, r * 0.12f)
            drawCircle(c.line2, r - 0.5.dp.toPx(), style = Stroke(width = 1.dp.toPx()))
        }
    }
}

private const val DEG_PER_SEC = 36f
