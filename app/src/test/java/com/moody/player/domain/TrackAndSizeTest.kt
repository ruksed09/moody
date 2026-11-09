package com.moody.player.domain

import com.moody.player.player.video.displaySize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackAndSizeTest {
    @Test fun phonePortraitVideo_storedLandscapeWithRotation_isPortrait() {
        // 1920x1080 frames with 90 degree rotation metadata (libVLC orientation 6 = right_top).
        val (w, h) = displaySize(1920, 1080, 1, 1, 6)
        assertTrue(h > w)
        assertEquals(1080 to 1920, w to h)
    }

    @Test fun landscapeVideo_staysLandscape() = assertEquals(1280 to 720, displaySize(1280, 720, 1, 1, 0))

    @Test fun anamorphicPixels_areWidened() = assertEquals(1024 to 576, displaySize(720, 576, 64, 45, 0))

    @Test fun missingSar_isSquare() = assertEquals(640 to 360, displaySize(640, 360, 0, 0, 0))

    @Test fun genericName_withLanguage() {
        val t = trackLabel("Track 1 - [English]", 0)
        assertEquals("Track 1", t.name)
        assertEquals("EN", t.lang)
    }

    @Test fun realName_keptWithLanguageTag() {
        val t = trackLabel("Commentary - [English]", 1)
        assertEquals("Commentary", t.name)
        assertEquals("EN", t.lang)
    }

    @Test fun missingName_fallsBackToTrackNumber() {
        assertEquals("Track 3", trackLabel(null, 2).name)
        assertEquals("Track 2", trackLabel("   ", 1).name)
        assertNull(trackLabel(null, 2).lang)
    }

    @Test fun languageOnlyName_isRecognised() {
        assertEquals("JA", trackLabel("Japanese", 0).lang)
        assertEquals("HI", trackLabel("hin", 0).lang)
    }

    @Test fun unknownWords_getNoTag() = assertNull(trackLabel("Director's cut", 0).lang)

    @Test fun controlCharacters_andLongNames_areCleaned() {
        assertEquals("Sub title x", trackLabel("Sub\u0000 title\n  x", 0).name)
        val long = trackLabel("a".repeat(200), 0).name
        assertTrue(long.length <= 60)
        assertTrue(long.endsWith("…"))
    }
}
