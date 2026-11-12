package com.moody.player.domain

/**
 * Decides when an A-B loop jumps back to A, without ever jumping twice for one pass. After a jump the player
 * still reports old times for a moment, so the guard waits until a time inside [A, B) comes back (at least
 * 300 ms later), or 1.5 s at most, before it may jump again.
 */
class LoopGuard {
    private var armed = true
    private var jumpedAt = 0L

    /** A seek just happened (the loop's own, or one the app made): ignore stale times for a moment. */
    fun seeked(now: Long) { armed = false; jumpedAt = now }

    /** Start fresh (new loop, loop switched on, another video). */
    fun reset() { armed = true }

    /** True when the loop should jump back to A now; the caller then seeks and calls [seeked]. */
    fun shouldJump(positionMs: Long, a: Long, b: Long, now: Long): Boolean {
        val since = now - jumpedAt
        if (!armed && (since > 1_500 || (since > 300 && positionMs >= a && positionMs < b))) armed = true
        return armed && positionMs >= b
    }
}
