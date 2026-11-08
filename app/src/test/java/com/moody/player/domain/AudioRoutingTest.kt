package com.moody.player.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioRoutingTest {
    @Test fun wma_goesToVlc() = assertTrue(needsVlc("audio/x-ms-wma", "a.wma"))
    @Test fun mp3_staysOnMedia3() = assertFalse(needsVlc("audio/mpeg", "a.mp3"))
    @Test fun unknownMime_usesExtension() = assertTrue(needsVlc(null, "b.APE"))
    @Test fun flacAndOpus_stayOnMedia3() {
        assertFalse(needsVlc("audio/flac", "c.flac"))
        assertFalse(needsVlc("audio/ogg", "d.opus"))
    }
    @Test fun ac3AndDts_goToVlc() {
        assertTrue(needsVlc("audio/ac3", "e.ac3"))
        assertTrue(needsVlc("audio/vnd.dts", "f.dts"))
    }
}
