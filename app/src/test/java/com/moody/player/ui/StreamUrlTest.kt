package com.moody.player.ui

import com.moody.player.ui.video.parseStreamUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StreamUrlTest {
    @Test fun acceptsCommonStreamSchemes() {
        assertEquals("https://cdn.example.com/live/index.m3u8", parseStreamUrl(" https://cdn.example.com/live/index.m3u8 ").toString())
        assertEquals("rtsp", parseStreamUrl("rtsp://192.168.1.20:554/cam")?.scheme)
    }

    @Test fun bareHostGetsHttps() {
        assertEquals("https://example.com/movie.mp4", parseStreamUrl("example.com/movie.mp4").toString())
    }

    @Test fun rejectsNonsenseAndUnsupportedSchemes() {
        assertNull(parseStreamUrl(""))
        assertNull(parseStreamUrl("not a link"))
        assertNull(parseStreamUrl("file:///sdcard/a.mp4"))
        assertNull(parseStreamUrl("javascript://alert(1)"))
    }
}
