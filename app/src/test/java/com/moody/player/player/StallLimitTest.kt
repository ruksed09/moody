package com.moody.player.player

import com.moody.player.player.video.stallLimitMs
import org.junit.Assert.assertEquals
import org.junit.Test

class StallLimitTest {
    @Test fun firstStartAfterAFontChangeGetsMoreTime() {
        assertEquals(120_000L, stallLimitMs(network = false, fontsWarm = false))
        assertEquals(120_000L, stallLimitMs(network = true, fontsWarm = false))
    }

    @Test fun laterStartsKeepTheShortLimits() {
        assertEquals(8_000L, stallLimitMs(network = false, fontsWarm = true))
        assertEquals(20_000L, stallLimitMs(network = true, fontsWarm = true))
    }
}
