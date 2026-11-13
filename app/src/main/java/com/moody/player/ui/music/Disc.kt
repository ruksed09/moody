package com.moody.player.ui.music

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import com.moody.player.domain.DiscLook
import com.moody.player.domain.DiscPalette
import com.moody.player.domain.DiscStyle
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

/** How discs are drawn here unless one is given its own look: the style and colour picked in Settings > Appearance. */
val LocalDiscLook = compositionLocalOf { DiscLook() }

/**
 * A disc for a song: by default a picture disc, the song's cover printed on a CD with a crimson rim and hub, a
 * teal-lit spindle and a soft crimson shadow. Without a cover the label takes the palette's colour. The palette
 * is the depth, its light is the highlight; the two never share a gradient. [look] picks one of the other builds
 * (silver CD, vinyl, holographic, flat) and another palette. [spinning] keeps the [DiscClock] turning while this
 * disc is on screen (only the song that is playing should). [current] marks the disc of the song loaded now,
 * playing or paused: it always shows the clock's angle, so pausing stops it where it is and playing goes on from
 * there. [behind] is the colour showing through the spindle hole.
 */
@Composable
fun Disc(
    item: AudioItem?,
    size: Dp,
    modifier: Modifier = Modifier,
    spinning: Boolean = false,
    behind: Color = MoodyTheme.colors.bg,
    sharedKey: String? = null,
    current: Boolean = spinning,
    look: DiscLook = LocalDiscLook.current,
) {
    val c = MoodyTheme.colors
    if (spinning) {
        DisposableEffect(Unit) {
            DiscClock.viewers.intValue++
            onDispose { DiscClock.viewers.intValue-- }
        }
    }
    var failed by remember(item?.uri, item?.cover) { mutableStateOf(false) }
    val hasCover = item != null && !failed
    val style = look.style
    val tones = remember(look.palette) { look.palette.tones() }
    val small = size < 64.dp
    val grooves = grooveCount(style, size)
    val shadowAlpha = if (c.isDark) 0.32f else 0.22f
    // The label: where a disc without a cover shows its colour, and on vinyl where the cover goes.
    val labelPart = if (style == DiscStyle.VINYL) (if (small) 0.54f else 0.46f) else 0.38f
    Box(modifier.sharedDisc(sharedKey).size(size)) {
        // The shadow stays put (it falls below the disc), so it is drawn outside the turning part.
        if (!small && style != DiscStyle.MINIMAL) Box(
            Modifier.fillMaxSize().drawWithCache {
                val r = this.size.minDimension / 2f
                val at = Offset(this.size.width / 2f, this.size.height / 2f + r * 0.06f)
                val tint = if (style == DiscStyle.CLASSIC) Color.Black.copy(alpha = shadowAlpha * 0.8f) else tones.shadow.copy(alpha = shadowAlpha)
                val shadow = Brush.radialGradient(0.86f to tint, 1f to Color.Transparent, center = at, radius = r * 1.10f)
                onDrawBehind { drawCircle(shadow, r * 1.10f, at) }
            },
        )
        // Body, rim and label (or cover): this part turns. Brushes are built once per size, not per frame.
        Box(
            Modifier.fillMaxSize().graphicsLayer { rotationZ = if (current || spinning) DiscClock.angle else 0f }.drawWithCache {
                val r = this.size.minDimension / 2f
                val mid = Offset(this.size.width / 2f, this.size.height / 2f)
                val body = when (style) {
                    DiscStyle.CLASSIC -> Brush.radialGradient(0f to SilverIn, 0.55f to SilverMid, 1f to SilverOut, center = mid, radius = r)
                    DiscStyle.MINIMAL -> SolidColor(DiscBodyIn)
                    else -> Brush.radialGradient(listOf(DiscBodyIn, DiscBodyOut), center = mid, radius = r)
                }
                // Rim: a band in the palette's colour whose bright side shows the turning.
                val rimWidth = (r * (if (style == DiscStyle.MINIMAL) 0.025f else 0.04f)).coerceAtLeast(1f)
                val rim = if (style == DiscStyle.MINIMAL) SolidColor(tones.rim)
                else Brush.sweepGradient(listOf(tones.deep, tones.rim, tones.deep, tones.rim, tones.deep), center = mid)
                val rimStroke = Stroke(width = rimWidth)
                val labelR = r * labelPart
                val label = when (style) {
                    DiscStyle.MINIMAL -> SolidColor(tones.rim)
                    DiscStyle.CLASSIC -> Brush.radialGradient(listOf(tones.hubIn, tones.hubOut), center = mid, radius = labelR)
                    else -> Brush.radialGradient(listOf(tones.rim, tones.shadow), center = mid, radius = labelR)
                }
                val showLabel = !hasCover || style == DiscStyle.VINYL
                // A short mark on the label so the turning reads.
                val m = r * 0.34f
                val markAt = Offset(mid.x - m, mid.y - m)
                val markSize = Size(m * 2, m * 2)
                val markStroke = Stroke(width = r * 0.05f, cap = StrokeCap.Round)
                onDrawBehind {
                    drawCircle(body, r, mid)
                    drawCircle(rim, r - rimWidth / 2, mid, style = rimStroke)
                    if (showLabel) drawCircle(label, labelR, mid)
                    if (!hasCover) drawArc(Color.Black.copy(alpha = 0.22f), -100f, 70f, useCenter = false, topLeft = markAt, size = markSize, style = markStroke)
                }
            },
        ) {
            if (item != null && !failed) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.cover?.let { java.io.File(it) } ?: item.uri)
                        .size(coverRequestPx(size))
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    // Just inside the rim; on vinyl the cover is the label, with a band of the label's colour around it.
                    modifier = Modifier.fillMaxSize().padding(if (style == DiscStyle.VINYL) size * ((1f - labelPart + 0.05f) / 2f) else size * 0.02f).clip(CircleShape),
                    onError = { failed = true },
                )
            }
        }
        // What stays still: grooves, the cover's edge, the sheen (light doesn't turn), the hub and the hole.
        Box(
            Modifier.fillMaxSize().drawWithCache {
                val r = this.size.minDimension / 2f
                val mid = Offset(this.size.width / 2f, this.size.height / 2f)
                val px = 1.dp.toPx()
                val inner = r * 0.96f
                val hair = Stroke(width = px * 0.75f)
                val line = Stroke(width = px)
                val gap = Stroke(width = px * 1.5f)
                val labelR = r * labelPart
                val from = if (style == DiscStyle.VINYL) labelR + r * 0.03f else r * 0.36f
                val step = (inner - from) / (grooves + 1)
                val sheen = discSheen(style, tones, hasCover, mid)
                val prism = if (style == DiscStyle.HOLO) Brush.radialGradient(
                    0.40f to Color.Transparent, 0.54f to Color.White.copy(alpha = 0.07f), 0.62f to Color.Transparent,
                    0.78f to tones.light.copy(alpha = 0.09f), 0.88f to Color.Transparent, 1f to Color.Transparent, center = mid, radius = inner,
                ) else null
                val hubR = r * (if (style == DiscStyle.CLASSIC) (if (small) 0.30f else 0.25f) else if (small) 0.26f else 0.22f)
                val hole = r * when (style) {
                    DiscStyle.VINYL -> if (small) 0.09f else 0.06f
                    else -> if (small) 0.11f else 0.09f
                }
                val hub = when (style) {
                    DiscStyle.CLASSIC -> Brush.radialGradient(listOf(SilverHubIn, SilverHubOut), center = mid, radius = hubR)
                    DiscStyle.MINIMAL -> SolidColor(tones.rim)
                    else -> Brush.radialGradient(listOf(tones.hubIn, tones.hubOut), center = mid, radius = hubR)
                }
                onDrawBehind {
                    for (i in 1..grooves) {
                        val at = from + step * i
                        when (style) {
                            // Fine rings in the silver, a little darker than it.
                            DiscStyle.CLASSIC -> drawCircle(Color.Black.copy(alpha = 0.05f + 0.03f * (i % 2)), at, mid, style = hair)
                            // Pressed grooves that catch more or less light, and now and then the dark gap between two tracks.
                            DiscStyle.VINYL ->
                                if (i % 7 == 0) drawCircle(Color.Black.copy(alpha = 0.45f), at, mid, style = gap)
                                else drawCircle(Color.White.copy(alpha = 0.04f + 0.05f * ((i * 5) % 7) / 6f), at, mid, style = hair)
                            else -> drawCircle(Color.White.copy(alpha = 0.035f + 0.03f * ((i * 3) % 4) / 3f), at, mid, style = hair)
                        }
                    }
                    if (hasCover && style != DiscStyle.VINYL) drawCircle(Color.Black.copy(alpha = 0.30f), inner, mid, style = line)
                    if (prism != null) drawCircle(prism, inner, mid)
                    if (sheen != null) drawCircle(sheen, inner, mid)
                    when (style) {
                        // No hub on a record: the label's edge, and a small hole.
                        DiscStyle.VINYL -> {
                            drawCircle(Color.Black.copy(alpha = 0.35f), labelR, mid, style = line)
                            drawCircle(behind, hole, mid)
                            drawCircle(Color.Black.copy(alpha = 0.40f), hole, mid, style = line)
                        }
                        // The clear plastic middle of a CD, with its stacking ring.
                        DiscStyle.CLASSIC -> {
                            drawCircle(hub, hubR, mid)
                            drawCircle(Color.Black.copy(alpha = 0.22f), hubR, mid, style = line)
                            drawCircle(Color.White.copy(alpha = 0.70f), hubR * 0.74f, mid, style = line)
                            drawCircle(Color.Black.copy(alpha = 0.14f), hubR * 0.58f, mid, style = hair)
                            drawCircle(behind, hole, mid)
                            drawCircle(tones.light, hole, mid, style = line)
                        }
                        DiscStyle.MINIMAL -> {
                            if (hasCover) drawCircle(hub, hubR, mid)
                            drawCircle(behind, hole, mid)
                        }
                        // Hub: a ring in the palette's colour around the spindle hole, whose edge catches the light.
                        else -> {
                            drawCircle(hub, hubR, mid)
                            drawCircle(Color.Black.copy(alpha = 0.25f), hubR, mid, style = line)
                            drawCircle(behind, hole, mid)
                            drawCircle(tones.light, hole, mid, style = line)
                        }
                    }
                    // The very edge, so the disc ends cleanly against any background.
                    if (style != DiscStyle.MINIMAL) drawCircle(Color.Black.copy(alpha = 0.28f), r - px * 0.375f, mid, style = hair)
                }
            },
        )
    }
}

/** A palette's main colour, for things that should match the discs. */
internal fun discColor(palette: DiscPalette): Color = palette.tones().rim

/** Rings on the playing side: none on the smallest discs, more as the disc grows; a record has the most. */
private fun grooveCount(style: DiscStyle, size: Dp): Int = when (style) {
    DiscStyle.MINIMAL -> 0
    DiscStyle.CLASSIC -> when { size < 64.dp -> 0; size <= 160.dp -> 2; else -> 6 }
    DiscStyle.VINYL -> when { size < 64.dp -> 4; size <= 160.dp -> 10; else -> 27 }
    else -> when { size < 64.dp -> 0; size <= 160.dp -> 3; else -> 12 }
}

/** The light lying on the disc. It does not turn with it, which is what makes it read as a reflection. */
private fun discSheen(style: DiscStyle, tones: DiscTones, hasCover: Boolean, mid: Offset): Brush? {
    val clear = Color.Transparent
    return when (style) {
        DiscStyle.MINIMAL -> null
        // Two opposed wedges of light: white on one side, the palette's light on the other.
        DiscStyle.PICTURE -> Brush.sweepGradient(
            0f to clear, 0.08f to Color.White.copy(alpha = 0.10f), 0.18f to clear,
            0.50f to clear, 0.58f to tones.light.copy(alpha = 0.14f), 0.68f to clear, 1f to clear, center = mid,
        )
        // Sharper and brighter: lacquer is glossier than a printed disc.
        DiscStyle.VINYL -> Brush.sweepGradient(
            0f to clear, 0.07f to Color.White.copy(alpha = 0.17f), 0.15f to clear,
            0.50f to clear, 0.57f to tones.light.copy(alpha = 0.11f), 0.65f to clear, 1f to clear, center = mid,
        )
        // The rainbow a CD throws: two opposed fans running through the spectrum around a white core.
        DiscStyle.CLASSIC -> {
            val a = if (hasCover) 0.26f else 0.42f
            Brush.sweepGradient(
                0f to clear,
                0.02f to SpectrumRed.copy(alpha = a * 0.7f), 0.05f to SpectrumAmber.copy(alpha = a), 0.08f to Color.White.copy(alpha = a + 0.18f),
                0.11f to SpectrumGreen.copy(alpha = a), 0.14f to SpectrumCyan.copy(alpha = a), 0.17f to SpectrumBlue.copy(alpha = a * 0.7f),
                0.21f to clear, 0.50f to clear,
                0.52f to SpectrumBlue.copy(alpha = a * 0.6f), 0.55f to SpectrumCyan.copy(alpha = a * 0.8f), 0.58f to Color.White.copy(alpha = a + 0.10f),
                0.61f to SpectrumYellow.copy(alpha = a * 0.8f), 0.64f to SpectrumAmber.copy(alpha = a * 0.8f), 0.67f to SpectrumRed.copy(alpha = a * 0.6f),
                0.71f to clear, 1f to clear, center = mid,
            )
        }
        // Four narrow fans of soft colour around a white core, one per quarter, the first led by the palette's light.
        DiscStyle.HOLO -> {
            val a = if (hasCover) 0.24f else 0.36f
            val fans = listOf(tones.light to HoloSky, HoloMint to HoloAmber, HoloRose to tones.light, HoloAmber to HoloMint)
            val stops = ArrayList<Pair<Float, Color>>(22)
            stops += 0f to clear
            fans.forEachIndexed { k, (first, second) ->
                val at = 0.07f + 0.25f * k
                stops += (at - 0.055f) to clear
                stops += (at - 0.025f) to first.copy(alpha = a)
                stops += at to Color.White.copy(alpha = a * 0.9f)
                stops += (at + 0.025f) to second.copy(alpha = a)
                stops += (at + 0.055f) to clear
            }
            stops += 1f to clear
            Brush.sweepGradient(*stops.toTypedArray(), center = mid)
        }
    }
}

/** One palette's colours: the rim and label with their deep side, the hub, the shadow under the disc and the light on it. */
private class DiscTones(val rim: Color, val deep: Color, val hubIn: Color, val hubOut: Color, val shadow: Color, val light: Color)

private fun DiscPalette.tones(): DiscTones = when (this) {
    DiscPalette.CRIMSON -> DiscTones(Color(0xFFDC143C), Color(0xFF7A0F1F), Color(0xFFE0243F), Color(0xFF8B1024), Color(0xFF5A0A18), DiscTeal)
    DiscPalette.CORAL -> DiscTones(Color(0xFFEE7459), Color(0xFF8C3423), Color(0xFFF38870), Color(0xFF9E3F2B), Color(0xFF4E1C11), Color(0xFF7BE0D6))
    DiscPalette.AMBER -> DiscTones(Color(0xFFE5A23C), Color(0xFF875413), Color(0xFFEDB254), Color(0xFF996319), Color(0xFF482D09), DiscTeal)
    DiscPalette.MOSS -> DiscTones(Color(0xFF6FAB5D), Color(0xFF305927), Color(0xFF83BC70), Color(0xFF3B6A30), Color(0xFF1A3214), Color(0xFFF0D88A))
    DiscPalette.LAGOON -> DiscTones(Color(0xFF2FB5AA), Color(0xFF105E58), Color(0xFF3DC7BB), Color(0xFF13716A), Color(0xFF0A3935), Color(0xFFF0C678))
    DiscPalette.COBALT -> DiscTones(Color(0xFF3F7DD6), Color(0xFF1D3F79), Color(0xFF5992E2), Color(0xFF244B8D), Color(0xFF102346), Color(0xFFF0C678))
    DiscPalette.SAND -> DiscTones(Color(0xFFD6C4A1), Color(0xFF8A7856), Color(0xFFE5D7B7), Color(0xFF9B8862), Color(0xFF483E2A), DiscTeal)
    DiscPalette.GRAPHITE -> DiscTones(Color(0xFF98A1A9), Color(0xFF4A5158), Color(0xFFB2BAC1), Color(0xFF596169), Color(0xFF22262A), DiscTeal)
}

private val DiscBodyIn = Color(0xFF1A1C20)
private val DiscBodyOut = Color(0xFF0E0F12)
private val DiscTeal = Color(0xFF4FD1C5)
private val SilverIn = Color(0xFFD6DADE)
private val SilverMid = Color(0xFFAAB0B6)
private val SilverOut = Color(0xFFC3C8CD)
private val SilverHubIn = Color(0xFFEFF1F3)
private val SilverHubOut = Color(0xFFB6BCC2)
private val SpectrumRed = Color(0xFFFF5F5F)
private val SpectrumAmber = Color(0xFFFFB650)
private val SpectrumYellow = Color(0xFFF4EC6C)
private val SpectrumGreen = Color(0xFF6FE08A)
private val SpectrumCyan = Color(0xFF5CCFE6)
private val SpectrumBlue = Color(0xFF5C8DEE)
private val HoloAmber = Color(0xFFF0C678)
private val HoloRose = Color(0xFFF09CA3)
private val HoloMint = Color(0xFF8FE0B0)
private val HoloSky = Color(0xFF8CC6F0)

private const val DEG_PER_SEC = 36f
private const val DISC_TRAVEL_MS = 320

/** Shared key of the playing song's disc (mini player and Now Playing). */
const val NOW_PLAYING_DISC = "disc"
