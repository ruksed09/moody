package com.moody.player.ui.music

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.moody.player.design.MoodyTheme
import kotlinx.coroutines.isActive

/**
 * A compact disc for songs without cover art. It turns while the song plays and stops where it is on
 * pause (no snap back). With animations switched off in Android's settings it stays still.
 */
@Composable
fun SpinningDisc(playing: Boolean, modifier: Modifier = Modifier) {
    val c = MoodyTheme.colors
    val context = LocalContext.current
    val still = remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
    var angle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing, still) {
        if (!playing || still) return@LaunchedEffect
        var last = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (last != 0L) angle = (angle + (now - last) / 1_000_000_000f * DEG_PER_SEC) % 360f
                last = now
            }
        }
    }
    val body = Color(0xFF17161B)
    val accent = c.accent
    Canvas(modifier.semantics { contentDescription = "No cover art" }) {
        val r = size.minDimension / 2f
        val o = center
        // Disc body.
        drawCircle(body, r, o)
        rotate(angle, o) {
            // The rainbow sheen of a pressed CD: two soft opposite flares, faint enough to stay calm.
            drawCircle(
                Brush.sweepGradient(
                    0f to Color.Transparent, 0.08f to Color(0x33FFD27A), 0.16f to Color(0x2263D2FF), 0.24f to Color.Transparent,
                    0.5f to Color.Transparent, 0.58f to Color(0x2EFFB37A), 0.66f to Color(0x1F8AF0C8), 0.74f to Color.Transparent,
                    1f to Color.Transparent, center = o,
                ),
                r * 0.98f, o,
            )
            // Grooves.
            var gr = r * 0.40f
            while (gr < r * 0.96f) {
                drawCircle(Color.White.copy(alpha = 0.035f), gr, o, style = Stroke(width = 1f))
                gr += r * 0.045f
            }
            // Label, with a mark so the turning reads.
            drawCircle(accent.copy(alpha = 0.9f), r * 0.34f, o)
            drawCircle(Color.Black.copy(alpha = 0.18f), r * 0.34f, o, style = Stroke(width = r * 0.012f))
            drawArc(
                Color.Black.copy(alpha = 0.22f), startAngle = -100f, sweepAngle = 70f, useCenter = false,
                topLeft = Offset(o.x - r * 0.26f, o.y - r * 0.26f), size = androidx.compose.ui.geometry.Size(r * 0.52f, r * 0.52f),
                style = Stroke(width = r * 0.035f, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )
        }
        // Clear ring and spindle hole stay still.
        drawCircle(Color.White.copy(alpha = 0.10f), r * 0.16f, o)
        drawCircle(c.bg, r * 0.07f, o)
        drawCircle(Color.White.copy(alpha = 0.08f), r, o, style = Stroke(width = 1.5f))
    }
}

private const val DEG_PER_SEC = 36f
