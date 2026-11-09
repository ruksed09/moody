package com.moody.player.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class GesturesTest {
    @Test fun zonesSplitIntoThirds() {
        assertEquals(TapZone.LEFT, tapZone(10f, 1000f))
        assertEquals(TapZone.LEFT, tapZone(349f, 1000f))
        assertEquals(TapZone.CENTER, tapZone(350f, 1000f))
        assertEquals(TapZone.CENTER, tapZone(500f, 1000f))
        assertEquals(TapZone.CENTER, tapZone(650f, 1000f))
        assertEquals(TapZone.RIGHT, tapZone(651f, 1000f))
    }

    @Test fun zeroWidthIsCentre() = assertEquals(TapZone.CENTER, tapZone(5f, 0f))

    @Test fun disabledSetRoundTripsAndSkipsUnknown() {
        val off = setOf(PlayerGesture.PINCH_ZOOM, PlayerGesture.VOLUME)
        assertEquals(off, decodeGestures(encodeGestures(off)))
        assertEquals(setOf(PlayerGesture.VOLUME), decodeGestures(setOf("VOLUME", "OLD_GESTURE")))
    }
}
