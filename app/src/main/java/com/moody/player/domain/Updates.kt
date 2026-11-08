package com.moody.player.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Semantic version compare: "v1.10.0" > "1.9.3"; "1.0.0-beta" < "1.0.0"; missing parts count as 0. */
fun compareVersions(a: String, b: String): Int {
    fun split(v: String): Pair<List<Int>, String?> {
        val clean = v.trim().removePrefix("v").removePrefix("V")
        val core = clean.substringBefore('-')
        val pre = clean.substringAfter('-', "").ifEmpty { null }
        return core.split('.').map { it.toIntOrNull() ?: 0 } to pre
    }
    val (na, pa) = split(a)
    val (nb, pb) = split(b)
    for (i in 0 until maxOf(na.size, nb.size)) {
        val c = (na.getOrElse(i) { 0 }).compareTo(nb.getOrElse(i) { 0 })
        if (c != 0) return c
    }
    return when {
        pa == null && pb == null -> 0
        pa == null -> 1
        pb == null -> -1
        else -> pa.compareTo(pb)
    }
}

data class ReleaseInfo(val version: String, val notes: String, val apkUrl: String, val apkSize: Long, val required: Boolean)

@Serializable
private data class GhAsset(val name: String = "", val size: Long = 0, @SerialName("browser_download_url") val url: String = "")

@Serializable
private data class GhRelease(
    @SerialName("tag_name") val tag: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GhAsset> = emptyList(),
)

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

const val REQUIRED_MARKER = "[required]"

/** Reads GitHub's "latest release" JSON. Null for anything Moody can't install from. */
fun parseRelease(text: String): ReleaseInfo? {
    val r = runCatching { json.decodeFromString(GhRelease.serializer(), text) }.getOrNull() ?: return null
    val tag = r.tag?.takeIf { it.isNotBlank() } ?: return null
    if (r.draft) return null
    val apk = r.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) && it.url.startsWith("https://") } ?: return null
    val body = r.body.orEmpty()
    return ReleaseInfo(
        version = tag.removePrefix("v").removePrefix("V"),
        notes = body.replace(REQUIRED_MARKER, "").replace("\r\n", "\n").trim(),
        apkUrl = apk.url,
        apkSize = apk.size,
        required = body.contains(REQUIRED_MARKER, ignoreCase = true),
    )
}
