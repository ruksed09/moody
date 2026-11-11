package com.moody.player.domain

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** One subtitle line on screen from [startMs] until [endMs]. [text] is plain text; lines are split by '\n'. */
data class Cue(val startMs: Long, val endMs: Long, val text: String)

enum class SubFormat(val label: String) { SRT("SRT"), VTT("WebVTT"), ASS("ASS/SSA") }

/** A parsed subtitle file. [cues] are sorted by start time and never empty. */
data class SubtitleDoc(val name: String, val format: SubFormat, val cues: List<Cue>)

/** Subtitle look for files Moody draws itself. */
enum class SubFont(val label: String) { DEFAULT("Sans"), SERIF("Serif"), MONO("Mono"), CONDENSED("Narrow") }
enum class SubOutline(val label: String, val widthDp: Float) { NONE("None", 0f), THIN("Thin", 1.4f), THICK("Thick", 2.6f) }
enum class SubAlign(val label: String) { LEFT("Left"), CENTER("Centre"), RIGHT("Right") }

val SUBTITLE_EXTENSIONS = listOf("srt", "vtt", "ass", "ssa")

/**
 * Turns subtitle bytes into text. Byte-order marks win; then strict UTF-8; then Windows-1252, which
 * reads most older European files and never fails.
 */
fun decodeSubtitleBytes(bytes: ByteArray): String {
    fun strip(s: String) = s.removePrefix("\uFEFF")
    if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) return strip(String(bytes, Charsets.UTF_8))
    if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return strip(String(bytes, Charsets.UTF_16LE))
    if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return strip(String(bytes, Charsets.UTF_16BE))
    return try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString().let(::strip)
    } catch (e: CharacterCodingException) {
        String(bytes, Charset.forName("windows-1252"))
    }
}

/** Works out the format from the content (the extension only breaks ties). */
fun detectFormat(text: String, ext: String? = null): SubFormat? {
    val head = text.trimStart().take(400)
    return when {
        head.startsWith("WEBVTT") -> SubFormat.VTT
        head.contains("[Script Info]", ignoreCase = true) || text.contains("\nDialogue:") || head.startsWith("Dialogue:") -> SubFormat.ASS
        Regex("""\d{1,2}:\d{2}:\d{2}[,.]\d{1,3}\s*-->""").containsMatchIn(text) -> if (ext.equals("vtt", true)) SubFormat.VTT else SubFormat.SRT
        Regex("""(\d{1,2}:)?\d{2}:\d{2}\.\d{1,3}\s*-->""").containsMatchIn(text) -> SubFormat.VTT
        else -> null
    }
}

/** Parses SRT, WebVTT or ASS/SSA. Returns null when nothing usable is found. */
fun parseSubtitles(text: String, name: String = "Subtitles", ext: String? = null): SubtitleDoc? {
    val norm = text.replace("\r\n", "\n").replace('\r', '\n')
    val format = detectFormat(norm, ext) ?: return null
    val cues = when (format) {
        SubFormat.SRT -> parseSrt(norm)
        SubFormat.VTT -> parseVtt(norm)
        SubFormat.ASS -> parseAss(norm)
    }.filter { it.text.isNotBlank() && it.endMs > it.startMs }.sortedBy { it.startMs }
    return if (cues.isEmpty()) null else SubtitleDoc(name, format, cues)
}

private val ARROW = Regex("""^\s*((?:\d+:)?\d{1,2}:\d{2}[,.]\d{1,3})\s*-->\s*((?:\d+:)?\d{1,2}:\d{2}[,.]\d{1,3})""")

/** "01:02:03,450", "02:03.450", "1:02:03.45" (ASS centiseconds) into milliseconds. */
internal fun parseClock(s: String): Long? {
    val parts = s.trim().replace(',', '.').split(':')
    if (parts.size !in 2..3) return null
    val h = if (parts.size == 3) parts[0].toLongOrNull() ?: return null else 0L
    val m = parts[parts.size - 2].toLongOrNull() ?: return null
    val secParts = parts.last().split('.')
    val sec = secParts[0].toLongOrNull() ?: return null
    val frac = secParts.getOrNull(1)?.let { f -> (f + "00").take(3).toLongOrNull() } ?: 0L
    return ((h * 60 + m) * 60 + sec) * 1000 + frac
}

private val HTML_TAG = Regex("""</?[a-zA-Z][^>]*>""")
private val VTT_TIME_TAG = Regex("""<\d{1,2}:\d{2}(:\d{2})?\.\d{3}>""")
private val BRACE_TAG = Regex("""\{\\[^}]*\}""")

private fun cleanMarkup(s: String): String = s
    .replace(VTT_TIME_TAG, "")
    .replace(HTML_TAG, "")
    .replace(BRACE_TAG, "")
    .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
    .lines().joinToString("\n") { it.trim() }.trim()

private fun parseSrt(text: String): List<Cue> = text.split(Regex("""\n\s*\n""")).mapNotNull { block ->
    val lines = block.trim('\n').lines()
    val at = lines.indexOfFirst { ARROW.containsMatchIn(it) }
    if (at < 0) return@mapNotNull null
    val m = ARROW.find(lines[at])!!
    val start = parseClock(m.groupValues[1]) ?: return@mapNotNull null
    val end = parseClock(m.groupValues[2]) ?: return@mapNotNull null
    Cue(start, end, cleanMarkup(lines.drop(at + 1).joinToString("\n")))
}

private fun parseVtt(text: String): List<Cue> = text.split(Regex("""\n\s*\n""")).mapNotNull { block ->
    val lines = block.trim('\n').lines()
    val first = lines.firstOrNull()?.trim().orEmpty()
    if (first.startsWith("WEBVTT") || first.startsWith("NOTE") || first.startsWith("STYLE") || first.startsWith("REGION")) return@mapNotNull null
    val at = lines.indexOfFirst { ARROW.containsMatchIn(it) }
    if (at < 0) return@mapNotNull null
    val m = ARROW.find(lines[at])!!
    val start = parseClock(m.groupValues[1]) ?: return@mapNotNull null
    val end = parseClock(m.groupValues[2]) ?: return@mapNotNull null
    Cue(start, end, cleanMarkup(lines.drop(at + 1).joinToString("\n")))
}

private fun parseAss(text: String): List<Cue> {
    var fields = listOf("Layer", "Start", "End", "Style", "Name", "MarginL", "MarginR", "MarginV", "Effect", "Text")
    var inEvents = false
    val out = mutableListOf<Cue>()
    for (raw in text.lines()) {
        val line = raw.trim()
        if (line.startsWith("[")) { inEvents = line.equals("[Events]", ignoreCase = true); continue }
        if (!inEvents && !line.startsWith("Dialogue:")) continue
        if (line.startsWith("Format:", ignoreCase = true)) {
            fields = line.substringAfter(':').split(',').map { it.trim() }
            continue
        }
        if (!line.startsWith("Dialogue:")) continue
        val values = line.substringAfter(':').split(',', limit = fields.size).map { it.trim() }
        if (values.size < fields.size) continue
        fun f(name: String) = fields.indexOfFirst { it.equals(name, ignoreCase = true) }.takeIf { it >= 0 }?.let { values[it] }
        val start = f("Start")?.let(::parseClock) ?: continue
        val end = f("End")?.let(::parseClock) ?: continue
        val body = assText(f("Text").orEmpty())
        if (body.isNotBlank()) out += Cue(start, end, body)
    }
    return out
}

/** ASS dialogue text to plain text: override blocks go, drawings ({\p1}...{\p0}) are dropped, \N breaks lines. */
internal fun assText(s: String): String {
    val sb = StringBuilder()
    var drawing = false
    var i = 0
    while (i < s.length) {
        val ch = s[i]
        if (ch == '{') {
            val close = s.indexOf('}', i)
            if (close < 0) break
            val block = s.substring(i + 1, close)
            Regex("""\\p(\d+)""").findAll(block).lastOrNull()?.let { drawing = it.groupValues[1] != "0" }
            i = close + 1
            continue
        }
        if (!drawing) {
            if (ch == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    'N', 'n' -> { sb.append('\n'); i += 2; continue }
                    'h' -> { sb.append(' '); i += 2; continue }
                }
            }
            sb.append(ch)
        }
        i++
    }
    return sb.toString().lines().joinToString("\n") { it.trim() }.trim()
}

/** Indices of the cues on screen at [timeMs] (overlapping cues show together). */
fun activeCues(cues: List<Cue>, timeMs: Long): List<Int> {
    // Cues are sorted by start; find the last that has started, then walk back over the ones still showing.
    var lo = 0
    var hi = cues.size - 1
    var last = -1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (cues[mid].startMs <= timeMs) { last = mid; lo = mid + 1 } else hi = mid - 1
    }
    if (last < 0) return emptyList()
    val out = ArrayList<Int>(2)
    var i = last
    // Long cues can overlap many short ones; 50 steps back is plenty for real files.
    while (i >= 0 && last - i < 50) {
        if (cues[i].endMs > timeMs) out += i
        i--
    }
    return out.reversed()
}

/** The cue to highlight in a transcript at [timeMs]: the one showing, else the last one that started. */
fun transcriptIndex(cues: List<Cue>, timeMs: Long): Int {
    activeCues(cues, timeMs).lastOrNull()?.let { return it }
    return cues.indexOfLast { it.startMs <= timeMs }
}

/** Start of the next line after [timeMs], or null at the end. */
fun nextCueStart(cues: List<Cue>, timeMs: Long): Long? = cues.firstOrNull { it.startMs > timeMs + 50 }?.startMs

/**
 * Start of the line to go back to from [timeMs]: the current line's start when it has been showing for
 * over a second and a half, otherwise the line before it.
 */
fun prevCueStart(cues: List<Cue>, timeMs: Long): Long? {
    val i = cues.indexOfLast { it.startMs <= timeMs }
    if (i < 0) return null
    return if (timeMs - cues[i].startMs > 1_500) cues[i].startMs else cues.getOrNull(i - 1)?.startMs ?: cues[i].startMs
}
