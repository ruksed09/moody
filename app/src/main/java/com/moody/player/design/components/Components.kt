package com.moody.player.design.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moody.player.design.MoodyTheme
import com.moody.player.design.Radii
import kotlinx.coroutines.delay

/** One easing curve and three durations across the app. */
object Motion {
    val Ease = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    const val FAST = 150
    const val BASE = 220
    const val ENTER = 280
    fun <T> fast() = tween<T>(FAST, easing = Ease)
    fun <T> base() = tween<T>(BASE, easing = Ease)
    fun <T> enter() = tween<T>(ENTER, easing = Ease)
}

/* ---------- reel line: Moody's one progress indicator ---------- */

/**
 * The signature thin amber line. [progress] null = indeterminate (a short segment travels the track).
 */
@Composable
fun ReelLine(progress: Float?, modifier: Modifier = Modifier, height: Dp = 2.dp, track: Color = MoodyTheme.colors.line2) {
    val accent = MoodyTheme.colors.accent
    if (progress == null) {
        val t by rememberInfiniteTransition(label = "reel").animateFloat(
            0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "reel-t",
        )
        Canvas(modifier.fillMaxWidth().height(height)) {
            val r = CornerRadius(size.height / 2)
            drawRoundRect(track, cornerRadius = r)
            val segW = size.width * 0.28f
            val x = -segW + (size.width + segW) * t
            drawRoundRect(accent, Offset(x.coerceAtLeast(0f), 0f), Size((segW + minOf(x, 0f)).coerceAtMost(size.width - x.coerceAtLeast(0f)), size.height), r)
        }
    } else {
        val p by animateFloatAsState(progress.coerceIn(0f, 1f), Motion.base(), label = "reel-p")
        Canvas(modifier.fillMaxWidth().height(height)) {
            val r = CornerRadius(size.height / 2)
            drawRoundRect(track, cornerRadius = r)
            if (p > 0f) drawRoundRect(accent, size = Size(size.width * p, size.height), cornerRadius = r)
        }
    }
}

/* ---------- skeletons ---------- */

/** Loading block with a slow, low-contrast sheen. Shaped like the real content it stands in for. */
@Composable
fun Skeleton(modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape = Radii.sm) {
    val c = MoodyTheme.colors
    val t by rememberInfiniteTransition(label = "sk").animateFloat(
        -1f, 2f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "sk-t",
    )
    Box(
        modifier
            .clip(shape)
            .background(c.raised)
            .drawWithContent {
                val w = size.width
                drawRect(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, c.text.copy(alpha = if (c.isDark) 0.045f else 0.06f), Color.Transparent),
                        startX = w * t - w * 0.5f, endX = w * t + w * 0.5f,
                    ),
                )
            }
            .semantics { contentDescription = "Loading" },
    )
}

@Composable
fun VideoRowSkeleton(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Skeleton(Modifier.width(128.dp).aspectRatio(16f / 9f), Radii.md)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Skeleton(Modifier.fillMaxWidth(0.8f).height(14.dp))
            Skeleton(Modifier.fillMaxWidth(0.4f).height(10.dp))
        }
    }
}

@Composable
fun VideoGridSkeleton(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Skeleton(Modifier.fillMaxWidth().aspectRatio(16f / 9f), Radii.lg)
        Skeleton(Modifier.fillMaxWidth(0.85f).height(14.dp))
        Skeleton(Modifier.fillMaxWidth(0.45f).height(10.dp))
    }
}

@Composable
fun SongRowSkeleton(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Skeleton(Modifier.size(48.dp), Radii.md)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Skeleton(Modifier.fillMaxWidth(0.7f).height(14.dp))
            Skeleton(Modifier.fillMaxWidth(0.35f).height(10.dp))
        }
    }
}

/* ---------- empty state ---------- */

@Composable
fun EmptyState(
    art: @Composable () -> Unit,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val c = MoodyTheme.colors
    val t = MoodyTheme.type
    Column(modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        art()
        Spacer(Modifier.height(20.dp))
        Text(title, style = t.title, color = c.text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, style = t.body, color = c.text2, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 320.dp))
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            SecondaryButton(actionLabel, onAction)
        }
    }
}

/* ---------- buttons ---------- */

@Composable
private fun pressScale(source: MutableInteractionSource): Float {
    val pressed by source.collectIsPressedAsState()
    return animateFloatAsState(if (pressed) 0.97f else 1f, Motion.fast(), label = "press").value
}

/** The one amber action on a screen. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    val c = MoodyTheme.colors
    val src = remember { MutableInteractionSource() }
    Row(
        modifier
            .scale(pressScale(src))
            .height(40.dp)
            .clip(Radii.md)
            .background(if (enabled) c.accent else c.raised)
            .clickable(src, ripple(color = c.accentInk), enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp), tint = if (enabled) c.accentInk else c.text3)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MoodyTheme.type.bodyStrong, color = if (enabled) c.accentInk else c.text3)
    }
}

/** Hairline-outlined action, for everything that is not the main thing. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val c = MoodyTheme.colors
    val src = remember { MutableInteractionSource() }
    Row(
        modifier
            .scale(pressScale(src))
            .height(38.dp)
            .clip(Radii.md)
            .border(BorderStroke(1.dp, c.line2), Radii.md)
            .clickable(src, ripple(), role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp), tint = c.text2)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MoodyTheme.type.label, color = c.text)
    }
}

/** Plain text action: "Not now", "Skip". */
@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = MoodyTheme.colors.text2) {
    Box(
        modifier.height(44.dp).clip(Radii.md).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MoodyTheme.type.label, color = color) }
}

/** 40 dp touch target around the icon (compact, per the owner's mockups). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MoodyTheme.colors.text2,
    size: Dp = 22.dp,
    onLongClick: (() -> Unit)? = null,
) {
    Box(
        modifier
            .size(40.dp)
            .clip(Radii.pill)
            .then(
                if (onLongClick != null) Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                else Modifier.clickable(role = Role.Button, onClick = onClick)
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(size), tint = tint) }
}


/* ---------- chips ---------- */

/** Selectable option chip, as in the mockup's track desk: hairline, amber when selected. */
@Composable
fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, height: Dp = 32.dp) {
    val c = MoodyTheme.colors
    Row(
        modifier
            .height(height)
            .clip(Radii.pill)
            .background(if (selected) c.accentWash else Color.Transparent)
            .border(1.dp, if (selected) c.accent else c.line2, Radii.pill)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(16.dp), tint = if (selected) c.accent else c.text2)
            if (text.isNotEmpty()) Spacer(Modifier.width(6.dp))
        }
        if (text.isNotEmpty()) {
            Text(text, style = MoodyTheme.type.label, color = if (selected) (if (c.isDark) c.text else c.accent) else c.text2, maxLines = 1, softWrap = false)
        }
    }
}

/** Muted sentence-case section label. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, inset: Dp = 16.dp, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(start = inset, end = 8.dp, top = 24.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MoodyTheme.type.section, color = MoodyTheme.colors.text3, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/* ---------- toasts ---------- */

@Stable
class Toaster {
    var message by mutableStateOf<String?>(null)
        private set
    private var serial by mutableStateOf(0)
    internal val key get() = serial
    fun show(text: String) { message = text; serial++ }
    internal fun clear() { message = null }
}

val LocalToaster = staticCompositionLocalOf { Toaster() }

/** Short plain-language confirmations ("1.5× speed", "Subtitles off"). Auto-dismiss, no actions. */
@Composable
fun BoxScope.ToastHost(toaster: Toaster, alignment: Alignment = Alignment.BottomCenter, padding: PaddingValues = PaddingValues(bottom = 96.dp), onVideo: Boolean = false) {
    val msg = toaster.message
    LaunchedEffect(toaster.key) { if (msg != null) { delay(1800); toaster.clear() } }
    AnimatedVisibility(
        visible = msg != null,
        modifier = Modifier.align(alignment).padding(padding),
        enter = fadeIn(Motion.fast()) + slideInVertically(Motion.fast()) { it / 4 },
        exit = fadeOut(Motion.fast()) + slideOutVertically(Motion.fast()) { it / 4 },
    ) {
        val c = MoodyTheme.colors
        Text(
            msg.orEmpty(),
            style = MoodyTheme.type.label,
            color = if (onVideo) com.moody.player.design.OnVideo.text else c.bg,
            modifier = Modifier
                .clip(Radii.md)
                .background(if (onVideo) com.moody.player.design.OnVideo.ink else c.text)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}
