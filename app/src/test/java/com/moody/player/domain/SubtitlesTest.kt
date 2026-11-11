package com.moody.player.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitlesTest {
    @Test fun srtWithTagsAndCrlf() {
        val text = "1\r\n00:00:01,000 --> 00:00:02,500\r\n<i>Hello</i> there\r\n\r\n2\r\n00:00:03,000 --> 00:00:04,000\r\n{\\an8}Second\r\nline\r\n"
        val d = parseSubtitles(text, "a.srt", "srt")!!
        assertEquals(SubFormat.SRT, d.format)
        assertEquals(listOf(Cue(1000, 2500, "Hello there"), Cue(3000, 4000, "Second\nline")), d.cues)
    }

    @Test fun vttSkipsHeaderNotesAndCueIds() {
        val text = "WEBVTT\n\nNOTE a comment\n\nintro\n00:01.000 --> 00:02.000 align:start\n<v Ann>Hi <00:01.500>there</v>\n\n01:00:00.000 --> 01:00:01.250\nLate &amp; done\n"
        val d = parseSubtitles(text, "a.vtt", "vtt")!!
        assertEquals(SubFormat.VTT, d.format)
        assertEquals(listOf(Cue(1000, 2000, "Hi there"), Cue(3_600_000, 3_601_250, "Late & done")), d.cues)
    }

    @Test fun assUsesFormatLineAndDropsOverridesAndDrawings() {
        val text = """
            [Script Info]
            Title: x

            [V4+ Styles]
            Format: Name, Fontname, Fontsize
            Style: Default,Arial,20

            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:01.50,0:00:03.00,Default,,0,0,0,,{\b1}Bold{\b0}, with comma\Nnext
            Dialogue: 0,0:00:04.00,0:00:05.00,Default,,0,0,0,,{\p1}m 0 0 l 100 0 100 100{\p0}
        """.trimIndent()
        val d = parseSubtitles(text, "a.ass", "ass")!!
        assertEquals(SubFormat.ASS, d.format)
        assertEquals(listOf(Cue(1500, 3000, "Bold, with comma\nnext")), d.cues)
    }

    @Test fun nonUtf8FallsBackToWindows1252() {
        val bytes = byteArrayOf('C'.code.toByte(), 'a'.code.toByte(), 'f'.code.toByte(), 0xE9.toByte())
        assertEquals("Café", decodeSubtitleBytes(bytes))
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte(), 'A'.code.toByte())
        assertEquals("A", decodeSubtitleBytes(bom))
    }

    @Test fun garbageIsNull() { assertNull(parseSubtitles("just some text", "x.txt")) }

    @Test fun activeAndNavigation() {
        val cues = listOf(Cue(0, 5_000, "long"), Cue(1_000, 2_000, "a"), Cue(3_000, 4_000, "b"), Cue(6_000, 7_000, "c"))
        assertEquals(listOf(0, 1), activeCues(cues, 1_500))
        assertEquals(emptyList<Int>(), activeCues(cues, 5_500))
        assertEquals(2, transcriptIndex(cues, 5_500))
        assertEquals(6_000L, nextCueStart(cues, 3_500))
        assertEquals(1_000L, prevCueStart(cues, 3_200))
        assertEquals(3_000L, prevCueStart(cues, 4_600))
        assertNull(nextCueStart(cues, 6_500))
    }
}

class VisualizerTest {
    @Test fun sineLandsInItsBand() {
        val a = SpectrumAnalyzer(1024, 32)
        val sr = 44_100
        val samples = FloatArray(2048) { i -> (0.8 * kotlin.math.sin(2 * Math.PI * 1_000 * i / sr)).toFloat() }
        a.analyze(samples, sr, BandGains())
        val edges = bandEdges(32, highHz = 16_000f)
        val band = (0 until 32).first { edges[it] <= 1_000f && edges[it + 1] > 1_000f }
        val loudest = a.levels.indices.maxByOrNull { a.levels[it] }!!
        assertTrue("loudest band $loudest, expected near $band", kotlin.math.abs(loudest - band) <= 1)
        assertTrue(a.levels[band] > 0.7f)
        assertTrue(a.levels[0] < 0.4f)
        assertTrue(a.level > 0.5f)
    }

    @Test fun silenceIsZeroAndGainsApply() {
        val a = SpectrumAnalyzer(512, 16)
        a.analyze(FloatArray(512), 48_000, BandGains())
        assertTrue(a.levels.all { it == 0f })
        assertEquals(2f, gainAt(100f, BandGains(bass = 2f)), 0.001f)
        assertEquals(0.5f, gainAt(10_000f, BandGains(treble = 0.5f)), 0.001f)
    }

    @Test fun smoothingRisesFastFallsSlow() {
        val shown = floatArrayOf(0f, 1f)
        smoothInto(shown, floatArrayOf(1f, 0f), 1f)
        assertTrue(shown[0] >= 0.59f)
        assertTrue(shown[1] in 0.85f..0.9f)
    }
}
