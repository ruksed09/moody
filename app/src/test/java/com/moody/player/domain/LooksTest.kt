package com.moody.player.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LooksTest {
    @Test fun discLook_roundTrips() {
        for (s in DiscStyle.entries) for (p in DiscPalette.entries) {
            val look = DiscLook(s, p)
            assertEquals(look, decodeDiscLook(encodeDiscLook(look)))
        }
    }

    @Test fun discLook_unknownTextIsDropped() {
        assertNull(decodeDiscLook("LASER:CRIMSON"))
        assertNull(decodeDiscLook("VINYL:NEON"))
        assertNull(decodeDiscLook("VINYL"))
        assertNull(decodeDiscLook(""))
    }

    @Test fun discLook_defaultIsThePictureDiscInCrimson() =
        assertEquals(DiscLook(DiscStyle.PICTURE, DiscPalette.CRIMSON), DiscLook())

    @Test fun mixes_getDifferentDiscs_theSameEveryTime() {
        val ids = listOf("now", "repeat", "night", "focus", "loved", "rediscover")
        assertEquals(ids.size, ids.map { autoDiscLook(it) }.toSet().size)
        assertEquals(autoDiscLook("some-other-mix"), autoDiscLook("some-other-mix"))
        assertNotEquals(autoDiscLook("night"), autoDiscLook("focus"))
    }

    @Test fun backdrops_everyVisualHasOneToFour_andStartsOnItsFirst() {
        for (v in VizStyle.entries) {
            val options = backdropsFor(v)
            assertTrue(options.size in 1..4)
            assertTrue(VizBackdrop.NONE !in options)
            assertEquals(options.first(), backdropOf(v, emptyMap()))
        }
    }

    @Test fun backdrops_pickIsKeptPerVisual_andOneThatDoesNotFitIsIgnored() {
        val picked = mapOf(VizStyle.BARS to VizBackdrop.NONE, VizStyle.WAVE to VizBackdrop.DUST, VizStyle.RINGS to VizBackdrop.HORIZON)
        assertEquals(VizBackdrop.NONE, backdropOf(VizStyle.BARS, picked))
        assertEquals(VizBackdrop.DUST, backdropOf(VizStyle.WAVE, picked))
        assertEquals(VizBackdrop.RIPPLES, backdropOf(VizStyle.RINGS, picked))
        assertEquals(VizBackdrop.HALO, backdropOf(VizStyle.CIRCLE, picked))
    }

    @Test fun backdrops_roundTrip_andUnknownEntriesAreDropped() {
        val picked = mapOf(VizStyle.BARS to VizBackdrop.GRID, VizStyle.MINIMAL to VizBackdrop.NONE)
        assertEquals(picked, decodeBackdrops(encodeBackdrops(picked)))
        assertEquals(emptyMap<VizStyle, VizBackdrop>(), decodeBackdrops(""))
        assertEquals(mapOf(VizStyle.BARS to VizBackdrop.GLOW), decodeBackdrops("BARS=GLOW;LASERS=GLOW;WAVE=FOG"))
    }
}
