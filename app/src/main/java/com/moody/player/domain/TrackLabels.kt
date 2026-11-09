package com.moody.player.domain

import java.util.Locale

/** A track as shown in the Audio / CC lists: a safe display name and an optional language code tag ("EN"). */
data class TrackLabel(val name: String, val lang: String?)

private const val MAX_NAME = 60

/** English language names and ISO 639-1/639-2 codes, mapped to the two-letter code shown in the tag. */
private val languages: Map<String, String> by lazy {
    val map = HashMap<String, String>()
    for (code in Locale.getISOLanguages()) {
        val loc = Locale(code)
        val tag = code.uppercase(Locale.ROOT)
        map[code.lowercase(Locale.ROOT)] = tag
        runCatching { map[loc.isO3Language.lowercase(Locale.ROOT)] = tag }
        loc.getDisplayLanguage(Locale.ENGLISH).lowercase(Locale.ROOT).takeIf { it.isNotBlank() && it != code }?.let { map[it] = tag }
    }
    // Bibliographic ISO 639-2 codes that differ from the terminology ones Java returns.
    mapOf("ger" to "DE", "fre" to "FR", "chi" to "ZH", "dut" to "NL", "cze" to "CS", "gre" to "EL", "per" to "FA", "rum" to "RO", "slo" to "SK", "alb" to "SQ", "arm" to "HY", "baq" to "EU", "bur" to "MY", "geo" to "KA", "ice" to "IS", "mac" to "MK", "mao" to "MI", "may" to "MS", "tib" to "BO", "wel" to "CY")
        .forEach { (k, v) -> map[k] = v }
    map
}

private fun languageTag(text: String): String? {
    val key = text.trim().lowercase(Locale.ROOT)
    if (key.isEmpty()) return null
    languages[key]?.let { return it }
    // "English (SDH)", "en-US", "Portuguese (Brazil)"
    val head = key.substringBefore('(').substringBefore('-').substringBefore('_').trim()
    return if (head != key) languages[head] else null
}

/**
 * Builds a display label from libVLC's raw track name (e.g. "Track 1 - [English]", "Commentary - [English]",
 * "jpn", or nothing). Control characters are stripped, long names are cut with an ellipsis, generic or empty
 * names become "Track N" ([index] is 0-based), and a recognised language becomes a short tag.
 */
fun trackLabel(raw: String?, index: Int): TrackLabel {
    val clean = raw.orEmpty()
        .filter { !it.isISOControl() || it == '\n' || it == '\t' }
        .replace(Regex("\\s+"), " ")
        .trim()
    val bracket = Regex("\\[([^\\]]+)]").find(clean)?.groupValues?.get(1)?.trim()
    var name = clean.replace(Regex("\\s*-?\\s*\\[[^\\]]*]"), "").trim().trimEnd('-').trim()
    val lang = bracket?.let(::languageTag) ?: languageTag(name)
    if (name.isEmpty() || Regex("(?i)^(track|piste|spur)\\s*\\d*$").matches(name)) {
        val n = Regex("\\d+").find(name)?.value?.toIntOrNull()
        name = "Track ${n ?: (index + 1)}"
    }
    if (name.length > MAX_NAME) name = name.take(MAX_NAME - 1).trimEnd() + "…"
    return TrackLabel(name, lang)
}
