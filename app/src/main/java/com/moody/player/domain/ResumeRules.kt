package com.moody.player.domain

/** Where to resume: nothing under 5 s in, or once the video is past 95 %. */
fun resumePosition(savedMs: Long, durationMs: Long): Long {
    if (durationMs <= 0 || savedMs < 5_000) return 0
    if (savedMs > durationMs * 0.95) return 0
    return savedMs
}

/**
 * Which queue entry a screen should show when it joins playback that is already running: the one that is
 * [playing] (the [saved] index when it is that video, else its first place in the queue), or [saved] when the
 * playing video isn't in the queue. Always a valid index; 0 for an empty queue.
 */
fun <T> resumeIndex(queue: List<T>, playing: T?, saved: Int): Int {
    val fallback = saved.coerceIn(0, (queue.size - 1).coerceAtLeast(0))
    if (playing == null || queue.getOrNull(fallback) == playing) return fallback
    val at = queue.indexOf(playing)
    return if (at >= 0) at else fallback
}

fun isWatched(posMs: Long, durationMs: Long): Boolean = durationMs > 0 && posMs > durationMs * 0.95

/**
 * Smart resume: step back a few seconds from a saved spot so the line that was cut off plays again.
 * A start of 0 (from the beginning) stays 0.
 */
fun smartResume(startMs: Long, rewindSec: Int): Long =
    if (startMs <= 0 || rewindSec <= 0) startMs else (startMs - rewindSec * 1_000L).coerceAtLeast(0)
