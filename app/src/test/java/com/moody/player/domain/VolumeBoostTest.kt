package com.moody.player.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeBoostTest {
    @Test fun swipeBelowFullIsPlainVolume() {
        assertEquals(0.7f, volumeSwipe(0.5f, 0.2f), 1e-4f)
        assertEquals(0f, volumeSwipe(0.2f, -0.9f), 1e-4f)
    }

    @Test fun swipeHoldsAtFullThenBoostsTo300() {
        assertEquals(1f, volumeSwipe(0.5f, 0.55f), 1e-4f)
        assertEquals(2f, volumeSwipe(1f, 0.6f), 1e-4f)
        assertEquals(3f, volumeSwipe(1f, 5f), 1e-4f)
    }

    @Test fun swipeStartsWhereTheBoostIs() {
        assertEquals(2f, volumeSwipe(2f, 0f), 1e-4f)
        assertEquals(1.6f, volumeSwipe(2f, -0.2f), 1e-4f)
        assertEquals(1f, volumeSwipe(2f, -0.55f), 1e-4f)
        assertEquals(0.5f, volumeSwipe(2f, -1.1f), 1e-4f)
    }

    @Test fun swipeBelowFullKeepsMenuBoost() {
        assertEquals(2f, boostForSwipe(0.4f, startedBelowFull = true, startBoost = 2f), 1e-4f)
        assertEquals(2.5f, boostForSwipe(2.5f, startedBelowFull = true, startBoost = 2f), 1e-4f)
        assertEquals(1f, boostForSwipe(0.8f, startedBelowFull = false, startBoost = 2f), 1e-4f)
    }

    @Test fun vlcVolumeUndoesTheCube() {
        assertEquals(0, vlcVolume(0f))
        assertEquals(50, vlcVolume(0.5f))
        assertEquals(100, vlcVolume(1f))
        val v = vlcVolume(3f) / 100f
        assertEquals(3f, v * v * v, 0.05f)
    }

    @Test fun sampleIsTripledWhenQuietAndNeverOverflows() {
        assertEquals(0.25f, boostSample(0.25f, 1f), 0f)
        assertEquals(0.3f, boostSample(0.1f, 3f), 1e-4f)
        assertEquals(-0.3f, boostSample(-0.1f, 3f), 1e-4f)
        var last = -1f
        for (i in -100..100) {
            val y = boostSample(i / 100f, 3f)
            assertTrue(y > -1f && y < 1f && y >= last)
            last = y
        }
    }

    @Test fun boostStaysInRange() {
        assertEquals(1f, clampBoost(0f), 0f)
        assertEquals(3f, clampBoost(9f), 0f)
        assertEquals(1f, clampBoost(Float.NaN), 0f)
    }
}
