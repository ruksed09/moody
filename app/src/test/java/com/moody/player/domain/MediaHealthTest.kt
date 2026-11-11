package com.moody.player.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaHealthTest {
    private val h264 = ProbedTrack(true, "video/avc", true, 1920, 1080)
    private val aac = ProbedTrack(false, "audio/mp4a-latm", true)
    private fun video(vararg t: ProbedTrack, block: Probe.() -> Probe = { this }) = Probe(
        MediaKind.VIDEO, "clip.mp4", OpenResult.OK, 1_000_000, androidParsed = true, androidTracks = t.toList(),
        containerMs = 120_000, libraryMs = 120_000, firstSampleOk = true, readableUntilMs = 120_000, realFormat = "MP4",
    ).block()
    private fun codes(p: Probe) = diagnose(p, "u").findings.map { it.code }

    @Test fun goodVideo_isHealthy() {
        val r = diagnose(video(h264, aac), "u")
        assertEquals(Severity.HEALTHY, r.severity)
        assertTrue(r.findings.isEmpty())
    }

    @Test fun missingAndEmptyAndUnreadable_areCritical() {
        assertEquals(listOf("missing"), codes(Probe(MediaKind.AUDIO, "a.mp3", OpenResult.MISSING)))
        assertEquals(listOf("no_access"), codes(Probe(MediaKind.AUDIO, "a.mp3", OpenResult.NO_ACCESS)))
        assertEquals(listOf("empty"), codes(Probe(MediaKind.AUDIO, "a.mp3", OpenResult.OK, sizeBytes = 0)))
        val corrupt = diagnose(Probe(MediaKind.VIDEO, "x.mp4", OpenResult.OK, 500, androidParsed = false, vlcParsed = false), "u")
        assertEquals(Severity.CRITICAL, corrupt.severity)
        assertEquals(listOf("corrupt"), corrupt.findings.map { it.code })
    }

    @Test fun vlcOnlyContainer_isWarningNotCritical() {
        val p = Probe(MediaKind.AUDIO, "song.wma", OpenResult.OK, 4_000_000, androidParsed = false, vlcParsed = true,
            vlcTracks = listOf(ProbedTrack(false, "wma2", null)), containerMs = 200_000, libraryMs = 200_000)
        val r = diagnose(p, "u")
        assertEquals(Severity.WARNING, r.severity)
        assertTrue("backup_parse" in r.findings.map { it.code })
    }

    @Test fun missingHardwareDecoder_isWarningWhenLibVlcPlaysIt() {
        val hevc = ProbedTrack(true, "video/hevc", false, 3840, 2160)
        val p = video(hevc, aac) { copy(vlcParsed = true, vlcTracks = listOf(ProbedTrack(true, "hevc", null), ProbedTrack(false, "mp4a", null))) }
        val r = diagnose(p, "u")
        assertEquals(Severity.WARNING, r.severity)
        assertEquals(listOf("software_video"), r.findings.map { it.code })
        assertTrue(r.findings[0].title.contains("2160p"))
    }

    @Test fun undecodableEverywhere_isCritical() {
        val odd = ProbedTrack(false, "audio/x-odd", false)
        val p = Probe(MediaKind.AUDIO, "a.m4a", OpenResult.OK, 10_000, androidParsed = true, androidTracks = listOf(odd), vlcParsed = false,
            containerMs = 30_000, libraryMs = 30_000, firstSampleOk = true, readableUntilMs = 30_000)
        assertEquals(Severity.CRITICAL, diagnose(p, "u").severity)
        assertTrue("audio_codec" in codes(p))
    }

    @Test fun streamProblems() {
        assertEquals(listOf("no_audio"), codes(video(h264)))
        assertEquals(listOf("no_video"), codes(video(aac)))
        assertEquals(listOf("no_streams"), codes(video()))
        val song = Probe(MediaKind.AUDIO, "s.mp4", OpenResult.OK, 100, androidParsed = true, androidTracks = listOf(h264), containerMs = 60_000, libraryMs = 60_000, firstSampleOk = true, readableUntilMs = 60_000, realFormat = "MP4")
        assertEquals(Severity.CRITICAL, diagnose(song, "u").severity)
    }

    @Test fun truncatedFile_isCritical_andShortGapsAreIgnored() {
        assertTrue("truncated" in codes(video(h264, aac) { copy(readableUntilMs = 60_000) }))
        assertEquals(emptyList<String>(), codes(video(h264, aac) { copy(readableUntilMs = 115_000) }))
        assertTrue("no_data" in codes(video(h264, aac) { copy(firstSampleOk = false, readableUntilMs = null) }))
    }

    @Test fun lengthMismatch_andUnknownLength_areWarnings() {
        assertTrue("length_mismatch" in codes(video(h264, aac) { copy(containerMs = 30_000, libraryMs = 600_000, readableUntilMs = 30_000) }))
        assertEquals(emptyList<String>(), codes(video(h264, aac) { copy(containerMs = 120_500) }))
        assertTrue("no_length" in codes(video(h264, aac) { copy(containerMs = null, libraryMs = null, readableUntilMs = null) }))
    }

    @Test fun wrongExtension_isFlagged_butRelatedExtensionsAreFine() {
        assertTrue("wrong_extension" in codes(video(h264, aac) { copy(name = "clip.mkv") }))
        assertEquals(emptyList<String>(), codes(video(h264, aac) { copy(name = "clip.m4v") }))
        assertEquals(emptyList<String>(), codes(video(h264, aac) { copy(name = "clip.xyz") }))
    }

    @Test fun findings_areSortedWorstFirst() {
        val r = diagnose(video(h264) { copy(readableUntilMs = 30_000) }, "u")
        assertEquals(Severity.CRITICAL, r.findings.first().severity)
        assertEquals(Severity.WARNING, r.findings.last().severity)
    }

    @Test fun codecLabels() {
        assertEquals("H.264", codecLabel("video/avc"))
        assertEquals("H.265 (HEVC)", codecLabel("hevc"))
        assertEquals("WMA", codecLabel("wma2"))
        assertEquals("XYZ", codecLabel("video/xyz"))
    }
}
