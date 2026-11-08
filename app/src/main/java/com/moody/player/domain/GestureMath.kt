package com.moody.player.domain

/** Horizontal seek: a full-width swipe covers min(duration, 3 minutes). */
fun seekDeltaMs(dxPx: Float, widthPx: Float, durationMs: Long): Long {
    if (widthPx <= 0f || durationMs <= 0) return 0
    val span = minOf(durationMs, 180_000L)
    return (dxPx / widthPx * span).toLong()
}

/** Vertical level change for brightness/volume: swipe up is positive, full height = 1.0. */
fun levelDelta(dyPx: Float, heightPx: Float): Float = if (heightPx <= 0f) 0f else -dyPx / heightPx
