package com.moody.player.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoopGuardTest {
    private val a = 10_000L
    private val b = 20_000L

    @Test fun jumpsAtB_once_whileOldTimesStillArrive() {
        val g = LoopGuard()
        assertFalse(g.shouldJump(19_900, a, b, now = 1_000))
        assertTrue(g.shouldJump(20_000, a, b, now = 1_100))
        g.seeked(1_100)
        // The player keeps reporting times past B for a moment: no second jump.
        assertFalse(g.shouldJump(20_050, a, b, now = 1_150))
        assertFalse(g.shouldJump(20_100, a, b, now = 1_500))
    }

    @Test fun rearmsOnceBackInsideTheLoop() {
        val g = LoopGuard()
        g.seeked(0)
        assertFalse(g.shouldJump(10_000, a, b, now = 200)) // too soon to trust
        assertFalse(g.shouldJump(10_100, a, b, now = 400)) // inside: armed again
        assertTrue(g.shouldJump(20_000, a, b, now = 10_400))
    }

    @Test fun rearmsAfterTimeoutEvenIfNeverInside() {
        val g = LoopGuard()
        g.seeked(0)
        assertFalse(g.shouldJump(25_000, a, b, now = 1_000))
        assertTrue(g.shouldJump(25_000, a, b, now = 1_600))
    }

    @Test fun beforeA_playsIntoTheLoop() {
        val g = LoopGuard()
        assertFalse(g.shouldJump(5_000, a, b, now = 0))
        assertFalse(g.shouldJump(15_000, a, b, now = 5_000))
        assertTrue(g.shouldJump(20_000, a, b, now = 10_000))
    }

    @Test fun resetArmsAtOnce() {
        val g = LoopGuard()
        g.seeked(0)
        g.reset()
        assertTrue(g.shouldJump(20_000, a, b, now = 10))
    }
}
