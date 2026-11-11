package com.moody.player.ui.music

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
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
import com.moody.player.design.components.Motion
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

/** The shared-transition scope around the app's screens, so a disc can travel between them. */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** The enter/exit scope of the screen a disc sits on. */
val LocalNavScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Lets the disc tagged [key] on one screen grow into the disc with the same key on the next. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun Modifier.sharedDisc(key: String?): Modifier {
    val shared = LocalSharedScope.current
    val scope = LocalNavScope.current
    if (key == null || shared == null || scope == null) return this
    return with(shared) {
        this@sharedDisc.sharedElement(
            rememberSharedContentState(key),
            scope,
            boundsTransform = { _, _ -> tween(DISC_TRAVEL_MS, easing = Motion.Ease) },
        )
    }
}

/**
 * A picture disc: the song's cover printed on a CD with a crimson rim and hub, a teal-lit spindle and a
 * soft crimson shadow. Without a cover the label is crimson. Crimson is the depth, teal is the light; the
 * two never share a gradient. [spinning] makes it follow the [DiscClock] (only the song that is playing
 * should). [behind] is the colour showing through the spindle hole.
 */
@Composable
fun Disc(item: AudioItem?, size: Dp, modifier: Modifier = Modifier, spinning: Boolean = false, behind: Color = MoodyTheme.colors.bg, sharedKey: String? = null) {
    val c = MoodyTheme.colors
    if (spinning) {
        DisposableEffect(Unit) {
            DiscClock.viewers.intValue++
            onDispose { DiscClock.viewers.intValue-- }
        }
    }
    var failed by remember(item?.uri, item?.cover) { mutableStateOf(false) }
    val small = size < 64.dp
    val grooves = when { small -> 0; size <= 160.dp -> 3; else -> 12 }
    val shadowAlpha = if (c.isDark) 0.32f else 0.22f
    Box(modifier.sharedDisc(sharedKey).size(size)) {
        // The shadow stays put (it falls below the disc), so it is drawn outside the turning part.
        if (!small) Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2f
            val at = Offset(center.x, center.y + r * 0.06f)
            drawCircle(
                Brush.radialGradient(0.86f to DiscCrimsonShadow.copy(alpha = shadowAlpha), 1f to Color.Transparent, center = at, radius = r * 1.10f),
                r * 1.10f, at,
            )
        }
        // Body, rim and label (or cover): this part turns.
        Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = if (spinning) DiscClock.angle else 0f }) {
            Canvas(Modifier.fillMaxSize()) {
                val r = this.size.minDimension / 2f
                drawCircle(Brush.radialGradient(listOf(DiscBodyIn, DiscBodyOut), center = center, radius = r), r)
                // Rim: a crimson band whose bright side shows the turning.
                val rim = r * 0.04f
                drawCircle(
                    Brush.sweepGradient(listOf(DiscRimDeep, DiscRim, DiscRimDeep, DiscRim, DiscRimDeep), center = center),
                    r - rim / 2, style = Stroke(width = rim.coerceAtLeast(1f)),
                )
                if (item == null || failed) {
                    drawCircle(Brush.radialGradient(listOf(DiscRim, DiscCrimsonShadow), center = center, radius = r * 0.38f), r * 0.38f)
                }
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
                    modifier = Modifier.fillMaxSize().padding(size * 0.02f).clip(CircleShape), // just inside the rim
                    onError = { failed = true },
                )
            }
        }
        // What stays still: grooves, the cover's edge, the sheen (light doesn't turn), the hub and the hole.
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2f
            val px = 1.dp.toPx()
            val inner = r * 0.96f
            if (grooves > 0) {
                val from = r * 0.36f
                val step = (inner - from) / (grooves + 1)
                for (i in 1..grooves) drawCircle(Color.White.copy(alpha = 0.05f), from + step * i, style = Stroke(width = px * 0.75f))
            }
            if (item != null && !failed) drawCircle(Color.Black.copy(alpha = 0.30f), inner, style = Stroke(width = px))
            // Two opposed wedges of light: white on one side, the teal of the app on the other.
            drawCircle(
                Brush.sweepGradient(
                    0f to Color.Transparent, 0.08f to Color.White.copy(alpha = 0.10f), 0.18f to Color.Transparent,
                    0.50f to Color.Transparent, 0.58f to DiscTeal.copy(alpha = 0.14f), 0.68f to Color.Transparent,
                    1f to Color.Transparent, center = center,
                ),
                inner,
            )
            // Hub: a crimson ring around the spindle hole, whose edge catches the teal light.
            val hub = r * (if (small) 0.26f else 0.22f)
            val hole = r * (if (small) 0.11f else 0.09f)
            drawCircle(Brush.radialGradient(listOf(DiscHubIn, DiscHubOut), center = center, radius = hub), hub)
            drawCircle(Color.Black.copy(alpha = 0.25f), hub, style = Stroke(width = px))
            drawCircle(behind, hole)
            drawCircle(DiscTeal, hole, style = Stroke(width = px))
        }
    }
}

private val DiscBodyIn = Color(0xFF1A1C20)
private val DiscBodyOut = Color(0xFF0E0F12)
private val DiscRim = Color(0xFFDC143C)
private val DiscRimDeep = Color(0xFF7A0F1F)
private val DiscHubIn = Color(0xFFE0243F)
private val DiscHubOut = Color(0xFF8B1024)
private val DiscCrimsonShadow = Color(0xFF5A0A18)
private val DiscTeal = Color(0xFF4FD1C5)

private const val DEG_PER_SEC = 36f
private const val DISC_TRAVEL_MS = 320

/** Shared key of the playing song's disc (mini player and Now Playing). */
const val NOW_PLAYING_DISC = "disc"
