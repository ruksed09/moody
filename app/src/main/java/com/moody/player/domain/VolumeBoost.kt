package com.moody.player.domain

import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.roundToInt

/** Loudest boost: 3 = 300%. 1 means no boost. */
const val MAX_BOOST = 3f

/** Finger travel (as a share of a full volume swipe) that holds at 100% before boosting starts, so a fast swipe to full volume doesn't. */
private const val HOLD = 0.1f

fun clampBoost(b: Float): Float = if (b.isNaN()) 1f else b.coerceIn(1f, MAX_BOOST)

fun boostPercent(b: Float): Int = (b * 100).roundToInt()

/**
 * Level after a volume swipe. 0..1 is the phone's volume and 1..3 is the boost: one full swipe covers 0 to 100%,
 * another covers the boost.
 */
fun volumeSwipe(start: Float, delta: Float): Float {
    val from = if (start <= 1f) start else 1f + HOLD + (start - 1f) / (MAX_BOOST - 1f)
    val at = from + delta
    return when {
        at <= 1f -> at.coerceAtLeast(0f)
        at <= 1f + HOLD -> 1f
        else -> (1f + (at - 1f - HOLD) * (MAX_BOOST - 1f)).coerceAtMost(MAX_BOOST)
    }
}

/**
 * Boost that goes with a swipe level. A swipe that began at full volume sets the boost directly; one that began
 * below it only moves the phone's volume and never lowers a boost set from the menu.
 */
fun boostForSwipe(level: Float, startedBelowFull: Boolean, startBoost: Float): Float =
    if (startedBelowFull) clampBoost(maxOf(level, startBoost)) else clampBoost(level)

/** libVLC's volume number for a linear [gain]. It amplifies by the cube of anything above 100. */
fun vlcVolume(gain: Float): Int = if (gain <= 1f) (gain.coerceAtLeast(0f) * 100).toInt() else (cbrt(gain) * 100).roundToInt()

/** One sample (-1..1) at [gain], with loud peaks rounded off instead of cut. */
fun boostSample(x: Float, gain: Float): Float {
    if (gain <= 1f) return x
    val y = x * gain
    val a = abs(y)
    if (a <= KNEE) return y
    val t = (a - KNEE) / (1f - KNEE)
    val out = KNEE + (1f - KNEE) * t / (1f + t)
    return if (y < 0) -out else out
}

private const val KNEE = 0.7f
