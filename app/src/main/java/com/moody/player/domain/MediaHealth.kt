package com.moody.player.domain

/** How worried to be about a file. Ordered: a report takes its worst finding's severity. */
enum class Severity(val label: String) { HEALTHY("Healthy"), WARNING("Warning"), CRITICAL("Critical") }

enum class MediaKind { VIDEO, AUDIO }

/** One problem found in a file, in plain words, with what can be done about it. */
data class Finding(val code: String, val severity: Severity, val title: String, val detail: String, val fixes: List<String>)

data class HealthReport(
    val uri: String,
    val name: String,
    val kind: MediaKind,
    val findings: List<Finding>,
    val checkedAt: Long,
) {
    val severity: Severity get() = findings.maxOfOrNull { it.severity } ?: Severity.HEALTHY
}

/** A stream as seen by a prober. [codec] is a MIME type ("video/avc") or libVLC's codec name. */
data class ProbedTrack(
    val isVideo: Boolean,
    val codec: String,
    /** Android has a decoder for it (hardware or software); null when unknown. */
    val androidDecoder: Boolean?,
    val width: Int = 0,
    val height: Int = 0,
)

enum class OpenResult { OK, MISSING, NO_ACCESS, ERROR }

/** What the probe learned about a file. Everything the rules need, nothing Android-specific. */
data class Probe(
    val kind: MediaKind,
    val name: String,
    val open: OpenResult,
    val sizeBytes: Long = -1,
    /** Android's extractor could open the container. */
    val androidParsed: Boolean = false,
    /** Tracks Android found; empty when it couldn't parse. */
    val androidTracks: List<ProbedTrack> = emptyList(),
    /** libVLC's verdict when it was asked: true parsed, false failed, null not asked. */
    val vlcParsed: Boolean? = null,
    val vlcTracks: List<ProbedTrack> = emptyList(),
    /** Length the container states, ms; null unknown. */
    val containerMs: Long? = null,
    /** Length the media library lists, ms; null unknown. */
    val libraryMs: Long? = null,
    /** The first frame of the main stream could be read; null not tried. */
    val firstSampleOk: Boolean? = null,
    /** How far into the file data could still be read, ms; null when not measured. */
    val readableUntilMs: Long? = null,
    /** The container the bytes really are, e.g. "MP4"; null unknown. */
    val realFormat: String? = null,
    /** Android's metadata reader failed outright. */
    val metadataError: Boolean = false,
)

private val EXT_FAMILIES = mapOf(
    "MP4" to setOf("mp4", "m4v", "m4a", "mov", "3gp", "3g2", "m4b"),
    "MATROSKA" to setOf("mkv", "mka", "webm", "mk3d"),
    "MPEG AUDIO" to setOf("mp3", "mp2", "mpga"),
    "OGG" to setOf("ogg", "oga", "opus", "ogv"),
    "FLAC" to setOf("flac"),
    "WAV" to setOf("wav", "wave"),
    "AVI" to setOf("avi"),
    "MPEG-TS" to setOf("ts", "m2ts", "mts"),
)

/** Turns a probe into findings. Never touches the file; deciding what to do stays with the person. */
fun diagnose(p: Probe, uri: String, now: Long = System.currentTimeMillis()): HealthReport {
    val f = mutableListOf<Finding>()
    fun add(code: String, s: Severity, title: String, detail: String, vararg fixes: String) { f += Finding(code, s, title, detail, fixes.toList()) }
    val noun = if (p.kind == MediaKind.VIDEO) "video" else "song"

    when (p.open) {
        OpenResult.MISSING -> {
            add("missing", Severity.CRITICAL, "File not found",
                "The library still lists this $noun, but the file isn't there any more. It may have been moved, renamed or deleted, or it's on storage that isn't connected.",
                "If it's on a memory card or USB drive, connect it and retry the scan", "Remove it from the library if it's gone for good")
            return HealthReport(uri, p.name, p.kind, f, now)
        }
        OpenResult.NO_ACCESS -> {
            add("no_access", Severity.CRITICAL, "Moody can't open this file",
                "Android didn't let Moody read it. Usually that means media access was limited or turned off.",
                "Allow Moody to access your media in Android Settings, then retry", "Remove it from the library")
            return HealthReport(uri, p.name, p.kind, f, now)
        }
        OpenResult.ERROR -> {
            add("unreadable", Severity.CRITICAL, "The file can't be read",
                "Opening the file failed. The storage may have a fault, or the file is damaged.",
                "Copy the file again from where it came from", "Retry the scan", "Remove it from the library")
            return HealthReport(uri, p.name, p.kind, f, now)
        }
        OpenResult.OK -> Unit
    }

    if (p.sizeBytes == 0L) {
        add("empty", Severity.CRITICAL, "The file is empty",
            "It's 0 bytes, so there is nothing to play. A download or copy probably failed before any data arrived.",
            "Download or copy it again", "Remove it from the library")
        return HealthReport(uri, p.name, p.kind, f, now)
    }

    val tracks = if (p.androidParsed) p.androidTracks else p.vlcTracks
    if (!p.androidParsed && p.vlcParsed != true) {
        add("corrupt", Severity.CRITICAL, "Not a playable media file",
            "Neither Android nor Moody's backup engine recognises this file's format. It's most likely damaged, incomplete, or not really a $noun.",
            "Get a fresh copy of the file", "Try repairing it on a computer, for example by re-saving it with a tool like FFmpeg", "Remove it from the library")
        return HealthReport(uri, p.name, p.kind, f, now)
    }
    if (!p.androidParsed) {
        add("backup_parse", Severity.WARNING, "Opened by the backup engine only",
            "Android couldn't read this file's container, but Moody's backup engine (libVLC) can. It should play, though some details like length may be missing.",
            "Nothing needed if it plays well", "For the most reliable playback, convert it to a common format such as MP4 or MP3")
    }

    val video = tracks.filter { it.isVideo }
    val audio = tracks.filter { !it.isVideo }
    when {
        tracks.isEmpty() -> add("no_streams", Severity.CRITICAL, "No picture or sound inside",
            "The file opens, but it holds no video or audio stream, so there is nothing to play.",
            "Get a fresh copy of the file", "Remove it from the library")
        p.kind == MediaKind.AUDIO && audio.isEmpty() -> add("no_audio_song", Severity.CRITICAL, "No sound stream",
            "This song file has no audio stream, so it plays silently or not at all.",
            "Get a fresh copy of the file", "Remove it from the library")
        p.kind == MediaKind.VIDEO && video.isEmpty() -> add("no_video", Severity.WARNING, "Sound only",
            "This video has an audio stream but no picture. It plays as sound only.",
            "If it should have a picture, get a fresh copy", "Keep it if it's meant to be audio")
        p.kind == MediaKind.VIDEO && audio.isEmpty() -> add("no_audio", Severity.WARNING, "No sound track",
            "This video has no audio stream, so it plays silently. That's normal for some screen recordings and clips.",
            "If it should have sound, get a fresh copy")
    }

    // Codecs. Videos always play through libVLC (it uses Android's decoders for speed when it can);
    // songs use Android's decoders and fall back to libVLC.
    for (t in tracks) {
        val vlcKnows = p.vlcParsed == true && p.vlcTracks.any { it.isVideo == t.isVideo && it.codec.isNotBlank() && it.codec != "undf" }
        if (t.androidDecoder != false) continue
        val what = codecLabel(t.codec)
        if (t.isVideo) {
            if (p.vlcParsed == false) add("video_codec", Severity.CRITICAL, "Video format not supported",
                "The picture is encoded as $what, which this phone and Moody's backup engine can't decode.",
                "Convert it to H.264 (MP4) on a computer", "Remove it from the library")
            else add("software_video", Severity.WARNING, "No hardware decoder for $what" + if (t.height > 0) " at ${t.height}p" else "",
                "This phone can't decode it in hardware, so Moody decodes it in software. It plays, but uses more battery and may stutter at high resolutions.",
                "Nothing needed if it plays smoothly", "Convert it to H.264 or a lower resolution for smoother, cooler playback")
        } else if (p.kind == MediaKind.AUDIO) {
            if (p.vlcParsed == false && !vlcKnows) add("audio_codec", Severity.CRITICAL, "Audio format not supported",
                "The sound is encoded as $what, which this phone and Moody's backup engine can't decode.",
                "Convert it to MP3, AAC or FLAC", "Remove it from the library")
            else add("backup_audio", Severity.WARNING, "Plays through the backup engine",
                "Android can't decode $what, so Moody plays this song with its backup engine (libVLC). It works; playback may take a moment longer to start.",
                "Nothing needed if it plays well", "Convert it to MP3, AAC or FLAC for the smoothest playback")
        }
    }

    if (p.firstSampleOk == false && tracks.isNotEmpty()) {
        add("no_data", Severity.CRITICAL, "No readable data",
            "The file describes its streams, but the media data itself can't be read. It's probably damaged or cut off right at the start.",
            "Get a fresh copy of the file", "Remove it from the library")
    }

    val length = p.containerMs ?: p.libraryMs
    val until = p.readableUntilMs
    if (p.firstSampleOk != false && length != null && until != null && length > 20_000 && until < length * 0.9 && length - until > 10_000) {
        add("truncated", Severity.CRITICAL, "The file looks incomplete",
            "It's meant to be ${formatDuration(length)} long, but readable data stops around ${formatDuration(until)}. Playback will end or freeze there. A download or copy was probably interrupted.",
            "Download or copy it again from the original", "Remove it from the library")
    }

    if (p.containerMs != null && p.libraryMs != null && p.libraryMs > 0) {
        val a = p.containerMs; val b = p.libraryMs
        val diff = kotlin.math.abs(a - b)
        if (diff > 10_000 && diff > maxOf(a, b) * 0.1) add("length_mismatch", Severity.WARNING, "Length information disagrees",
            "The file's header says ${formatDuration(a)}, but the phone's media library says ${formatDuration(b)}. The seek bar and remaining time may be wrong, and playback can stop early.",
            "Moody plays the whole file where it can", "Re-saving the file with a converter usually fixes its header")
    }
    if ((p.containerMs ?: 0L) <= 0L && (p.libraryMs ?: 0L) <= 0L && tracks.isNotEmpty()) {
        add("no_length", Severity.WARNING, "Length unknown",
            "Neither the file nor the media library knows how long it is, so seeking and the progress bar may not work.",
            "Re-saving the file with a converter usually adds its length")
    }

    if (p.kind == MediaKind.VIDEO && video.isNotEmpty() && video.all { it.width <= 0 || it.height <= 0 } && p.androidParsed) {
        add("no_size", Severity.WARNING, "Picture size missing",
            "The file doesn't state its picture size. Most players cope, but thumbnails and aspect ratio may be off.",
            "Re-saving the file with a converter usually fixes it")
    }
    if (p.metadataError && p.androidParsed) {
        add("bad_metadata", Severity.WARNING, "Details can't be read",
            "Android's metadata reader failed on this file, so title, length or artwork may be missing or wrong.",
            "Nothing needed if it plays well", "Re-saving or re-tagging the file usually fixes it")
    }

    val ext = p.name.substringAfterLast('.', "").lowercase()
    val family = p.realFormat?.let { real -> EXT_FAMILIES.entries.firstOrNull { it.key == real } }
    if (family != null && ext.isNotEmpty() && ext !in family.value && EXT_FAMILIES.values.any { ext in it }) {
        add("wrong_extension", Severity.WARNING, "Name doesn't match the format",
            "The file is named .$ext but is really ${family.key}. Some apps may refuse it or treat it wrongly. Moody reads the real format.",
            "Rename it with the right extension (.${family.value.first()})")
    }

    return HealthReport(uri, p.name, p.kind, f.sortedByDescending { it.severity }, now)
}

/** "video/avc" to "H.264"; unknown names pass through upper-cased. */
fun codecLabel(codec: String): String = when (codec.lowercase()) {
    "video/avc", "h264" -> "H.264"
    "video/hevc", "hevc", "h265" -> "H.265 (HEVC)"
    "video/x-vnd.on2.vp9", "vp9", "vp90" -> "VP9"
    "video/x-vnd.on2.vp8", "vp8", "vp80" -> "VP8"
    "video/av01", "av01" -> "AV1"
    "video/mp4v-es", "mp4v" -> "MPEG-4"
    "video/mpeg2", "mpgv" -> "MPEG-2"
    "video/3gpp", "h263" -> "H.263"
    "audio/mp4a-latm", "mp4a" -> "AAC"
    "audio/mpeg", "mpga" -> "MP3"
    "audio/flac", "flac" -> "FLAC"
    "audio/opus", "opus" -> "Opus"
    "audio/vorbis", "vorb" -> "Vorbis"
    "audio/ac3", "a52" -> "Dolby Digital (AC-3)"
    "audio/eac3", "eac3" -> "Dolby Digital Plus"
    "audio/vnd.dts", "dts" -> "DTS"
    "audio/x-ms-wma", "wma1", "wma2", "wmap" -> "WMA"
    "audio/raw", "araw", "s16l" -> "PCM"
    "audio/alac", "alac" -> "ALAC"
    "audio/3gpp", "samr" -> "AMR"
    else -> codec.substringAfter('/').uppercase()
}
