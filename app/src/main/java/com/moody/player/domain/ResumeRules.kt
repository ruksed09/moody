package com.moody.player.domain

/** Where to resume: nothing under 5 s in, or once the video is past 95 %. */
fun resumePosition(savedMs: Long, durationMs: Long): Long {
    if (durationMs <= 0 || savedMs < 5_000) return 0
    if (savedMs > durationMs * 0.95) return 0
    return savedMs
}

fun isWatched(posMs: Long, durationMs: Long): Boolean = durationMs > 0 && posMs > durationMs * 0.95
