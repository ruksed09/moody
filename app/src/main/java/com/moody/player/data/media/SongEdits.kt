package com.moody.player.data.media

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** Title, artist and album the user typed for a song. Null fields keep the file's own value. */
data class SongEdit(val title: String? = null, val artist: String? = null, val album: String? = null, val cover: String? = null) {
    val isEmpty: Boolean get() = title == null && artist == null && album == null && cover == null
}

fun AudioItem.withEdit(e: SongEdit?): AudioItem =
    if (e == null) this else copy(title = e.title ?: title, artist = e.artist ?: artist, album = e.album ?: album, cover = e.cover ?: cover)

/** The edit that turns [original] into the typed values: only fields that really differ are kept. */
fun editFor(original: AudioItem, title: String, artist: String, album: String, cover: String? = null): SongEdit = SongEdit(
    title = title.trim().takeIf { it.isNotEmpty() && it != original.title },
    artist = artist.trim().takeIf { it.isNotEmpty() && it != original.artist },
    album = album.trim().takeIf { it.isNotEmpty() && it != original.album },
    cover = cover,
)

private val Context.songEditStore by preferencesDataStore("song_edits")

/**
 * Song details edited inside Moody. They live in Moody's own storage, keyed by the song's uri;
 * the audio file and its tags are never touched, so other apps keep seeing the original values.
 */
@Singleton
class SongEdits @Inject constructor(@ApplicationContext private val context: Context) {
    val all: Flow<Map<String, SongEdit>> = context.songEditStore.data.map { prefs ->
        prefs.asMap().mapNotNull { (k, v) -> (v as? String)?.let { decode(it) }?.let { k.name to it } }.toMap()
    }

    suspend fun set(uri: String, edit: SongEdit?) {
        val key = stringPreferencesKey(uri)
        context.songEditStore.edit { if (edit == null || edit.isEmpty) it.remove(key) else it[key] = encode(edit) }
    }

    private fun encode(e: SongEdit): String = JSONObject().apply {
        e.title?.let { put("t", it) }; e.artist?.let { put("a", it) }; e.album?.let { put("l", it) }; e.cover?.let { put("c", it) }
    }.toString()

    private fun decode(s: String): SongEdit? = runCatching {
        val o = JSONObject(s)
        SongEdit(o.optString("t").ifEmpty { null }, o.optString("a").ifEmpty { null }, o.optString("l").ifEmpty { null }, o.optString("c").ifEmpty { null })
    }.getOrNull()
}

private val Context.mixCoverStore by preferencesDataStore("mix_covers")
private val Context.mixPrefStore by preferencesDataStore("mix_prefs")

/** Names the user gave mood mixes, the mixes they removed and the discs they picked for them, by mix id. */
data class MixPrefs(
    val names: Map<String, String> = emptyMap(), val hidden: Set<String> = emptySet(),
    val looks: Map<String, com.moody.player.domain.DiscLook> = emptyMap(),
)

/** Renamed and removed mood mixes. Mixes are rebuilt from play history, so removing one only hides it. */
@Singleton
class MixNames @Inject constructor(@ApplicationContext private val context: Context) {
    val all: Flow<MixPrefs> = context.mixPrefStore.data.map { p ->
        val names = HashMap<String, String>()
        val hidden = HashSet<String>()
        val looks = HashMap<String, com.moody.player.domain.DiscLook>()
        p.asMap().forEach { (k, v) ->
            when {
                k.name.startsWith("name:") && v is String -> names[k.name.removePrefix("name:")] = v
                k.name.startsWith("hide:") && v == true -> hidden += k.name.removePrefix("hide:")
                k.name.startsWith("look:") && v is String -> com.moody.player.domain.decodeDiscLook(v)?.let { looks[k.name.removePrefix("look:")] = it }
            }
        }
        MixPrefs(names, hidden, looks)
    }

    /** A blank or null name goes back to Moody's own name for the mix. */
    suspend fun rename(mixId: String, name: String?) {
        val key = stringPreferencesKey("name:$mixId")
        context.mixPrefStore.edit { if (name.isNullOrBlank()) it.remove(key) else it[key] = name.trim() }
    }

    /** The disc picked for a mix; null goes back to the one Moody gives it. A removed mix keeps its disc, for when it is brought back. */
    suspend fun setLook(mixId: String, look: com.moody.player.domain.DiscLook?) {
        val key = stringPreferencesKey("look:$mixId")
        context.mixPrefStore.edit { if (look == null) it.remove(key) else it[key] = com.moody.player.domain.encodeDiscLook(look) }
    }

    suspend fun setHidden(mixId: String, hidden: Boolean) {
        val key = androidx.datastore.preferences.core.booleanPreferencesKey("hide:$mixId")
        context.mixPrefStore.edit { if (hidden) it[key] = true else it.remove(key) }
    }

    suspend fun showAll(mixIds: Collection<String>) {
        context.mixPrefStore.edit { p -> mixIds.forEach { p.remove(androidx.datastore.preferences.core.booleanPreferencesKey("hide:$it")) } }
    }
}

/** Covers the user picked for mood mixes, keyed by the mix's id: a path to Moody's own copy of the picture. */
@Singleton
class MixCovers @Inject constructor(@ApplicationContext private val context: Context) {
    val all: Flow<Map<String, String>> = context.mixCoverStore.data.map { prefs ->
        prefs.asMap().mapNotNull { (k, v) -> (v as? String)?.let { k.name to it } }.toMap()
    }

    suspend fun set(mixId: String, path: String?) {
        val key = stringPreferencesKey(mixId)
        context.mixCoverStore.edit { if (path == null) it.remove(key) else it[key] = path }
    }
}

/**
 * Copies a picked picture into Moody's own storage (scaled to at most 1024 px, JPEG), so the cover
 * keeps working after the picker's permission ends. Returns the stored file's path, or null.
 */
fun storeCover(context: Context, source: android.net.Uri): String? = runCatching {
    val resolver = context.contentResolver
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(source)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1024) sample *= 2
    val bmp = resolver.openInputStream(source)?.use {
        android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return null
    val dir = java.io.File(context.filesDir, "covers").apply { mkdirs() }
    val file = java.io.File(dir, "${java.util.UUID.randomUUID()}.jpg")
    file.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
    bmp.recycle()
    file.absolutePath
}.getOrNull()

/** Deletes a cover Moody stored, and nothing else. */
fun deleteCover(context: Context, path: String?) {
    if (path == null) return
    val dir = java.io.File(context.filesDir, "covers").canonicalPath
    val f = java.io.File(path)
    if (f.canonicalPath.startsWith(dir + java.io.File.separator)) f.delete()
}
