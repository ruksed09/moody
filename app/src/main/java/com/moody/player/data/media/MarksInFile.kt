package com.moody.player.data.media

import android.content.Context
import android.net.Uri
import com.moody.player.domain.BookmarkNames
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest

/** One bookmark as it travels inside a video file. */
data class FileMark(val uid: String, val startMs: Long, val endMs: Long?, val name: String, val updatedAt: Long, val deleted: Boolean)

/** How saving bookmarks into a file ended. */
sealed interface MarksWrite {
    data object Done : MarksWrite
    /** Not an MP4/MOV/3GP file Moody can safely add to; the bookmarks stay in Moody. */
    data object NotSupported : MarksWrite
    /** Something didn't check out; the file was put back exactly as it was. */
    data class Failed(val why: String) : MarksWrite
}

/**
 * Bookmarks carried inside MP4, MOV and 3GP files, so a copy sent to someone else brings them along.
 *
 * They live in one top-level `uuid` box with Moody's own id, appended at the very end of the file. Players
 * skip boxes they don't know, and nothing before the box moves, so the video and its index are untouched.
 * Files Moody can't read cleanly box by box (a box running to the end of the file, data after the last box,
 * fragmented files) are refused rather than guessed at.
 *
 * Every write is journalled first: if Moody stops half-way, [recover] puts the file back on the next start.
 * Call from a background thread.
 */
object MarksInFile {
    /** Moody's box id: 16 fixed bytes after the `uuid` type. Never change it, or older files stop being read. */
    private val MOODY_ID = byteArrayOf(
        0x6d, 0x6f, 0x6f, 0x64, 0x79, 0x2d, 0x6d, 0x61, 0x72, 0x6b, 0x73, 0x2d, 0x76, 0x31, 0x00, 0x01, // "moody-marks-v1\0\1"
    )
    private const val MAX_PAYLOAD = 64 * 1024
    private const val MAX_MARKS = 500
    private const val HEAD_HASH = 1L shl 20
    private const val TAIL_HASH = 4L shl 20

    private class Box(val type: String, val offset: Long, val size: Long, val headerLen: Int)
    private class Layout(val length: Long, val boxes: List<Box>, val ours: Box?)

    /** Reads the bookmarks stored in [uri], or null when there are none or the file isn't a clean MP4. */
    fun read(context: Context, uri: Uri): List<FileMark>? = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            FileInputStream(pfd.fileDescriptor).channel.use { ch ->
                val layout = walk(ch) ?: return@use null
                val box = layout.ours ?: return@use null
                parse(readAt(ch, box.offset + box.headerLen + 16, (box.size - box.headerLen - 16).toInt()))
            }
        }
    }.getOrNull()

    /**
     * Replaces the bookmarks stored in [uri] with [marks] (an empty list removes Moody's box). Throws
     * SecurityException (on Android 10 a RecoverableSecurityException) when Moody may not change the file.
     */
    fun write(context: Context, uri: Uri, marks: List<FileMark>): MarksWrite {
        val journal = journalFile(context, uri)
        context.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
            val reader = FileInputStream(pfd.fileDescriptor).channel
            val writer = FileOutputStream(pfd.fileDescriptor).channel
            val layout = walk(reader) ?: return MarksWrite.NotSupported
            val base = layout.ours?.offset ?: layout.length
            val oldBox = layout.ours?.let { readAt(reader, it.offset, it.size.toInt()) } ?: ByteArray(0)
            val newBox = if (marks.isEmpty()) ByteArray(0) else box(encode(marks)) ?: return MarksWrite.Failed("Too many bookmarks to save in the file")
            if (oldBox.contentEquals(newBox)) return MarksWrite.Done
            val before = prefixHash(reader, base)

            // Journal: the file's length and the box it had, so a stop half-way can be undone.
            journal.parentFile?.mkdirs()
            journal.writeText(JSONObject().put("uri", uri.toString()).put("base", base).put("length", layout.length)
                .put("old", android.util.Base64.encodeToString(oldBox, android.util.Base64.NO_WRAP)).put("prefix", before).toString())

            val ok = runCatching {
                if (newBox.isNotEmpty()) writeAt(writer, base, newBox)
                writer.truncate(base + newBox.size)
                writer.force(true)
                val after = walk(reader)
                after != null && after.length == base + newBox.size &&
                    (newBox.isEmpty() && after.ours == null || after.ours != null && readAt(reader, base, newBox.size).contentEquals(newBox)) &&
                    prefixHash(reader, base) == before
            }.getOrDefault(false)
            if (!ok) {
                val back = restore(reader, writer, base, oldBox, layout.length)
                if (back) journal.delete()
                return MarksWrite.Failed(if (back) "Saving into the file failed, so it was put back" else "Saving into the file failed")
            }
            journal.delete()
            return MarksWrite.Done
        } ?: throw SecurityException("No access")
    }

    /** Puts back any file a write left half-done (Moody stopped mid-write). Safe to call at every start. */
    fun recover(context: Context) {
        val dir = File(context.filesDir, "marks-journal")
        dir.listFiles()?.forEach { f ->
            runCatching {
                val j = JSONObject(f.readText())
                val uri = Uri.parse(j.getString("uri"))
                val base = j.getLong("base"); val length = j.getLong("length")
                val old = android.util.Base64.decode(j.getString("old"), android.util.Base64.NO_WRAP)
                context.contentResolver.openFileDescriptor(uri, "rw")?.use { pfd ->
                    val reader = FileInputStream(pfd.fileDescriptor).channel
                    val writer = FileOutputStream(pfd.fileDescriptor).channel
                    // Only the bytes from base on were ever touched; the video before them is checked, not rewritten.
                    if (prefixHash(reader, base) == j.getString("prefix")) restore(reader, writer, base, old, length)
                }
            }
            f.delete()
        }
    }

    /** For tests: the check value a journal stores for the first [base] bytes of [uri]. */
    internal fun prefixHashOf(context: Context, uri: Uri, base: Long): String =
        context.contentResolver.openFileDescriptor(uri, "r")!!.use { pfd -> FileInputStream(pfd.fileDescriptor).channel.use { prefixHash(it, base) } }

    internal fun journalFor(context: Context, uri: Uri): File = journalFile(context, uri)

    private fun restore(reader: FileChannel, writer: FileChannel, base: Long, old: ByteArray, length: Long): Boolean = runCatching {
        if (old.isNotEmpty()) writeAt(writer, base, old)
        writer.truncate(length)
        writer.force(true)
        reader.size() == length && (old.isEmpty() || readAt(reader, base, old.size).contentEquals(old))
    }.getOrDefault(false)

    private fun journalFile(context: Context, uri: Uri) =
        File(File(context.filesDir, "marks-journal"), sha(uri.toString().toByteArray()).take(24) + ".json")

    /**
     * Walks the top-level boxes. Null unless every header is valid, the boxes end exactly at the end of the
     * file, there is an `ftyp` and a `moov`, nothing runs "to the end of the file", and the file isn't
     * fragmented. Moody's own box counts only as the last one.
     */
    private fun walk(ch: FileChannel): Layout? {
        val length = ch.size()
        if (length < 16) return null
        val boxes = mutableListOf<Box>()
        var at = 0L
        val head = ByteBuffer.allocate(16)
        while (at < length) {
            if (length - at < 8) return null
            head.clear(); head.limit(8)
            if (readFully(ch, head, at) < 8) return null
            head.flip()
            var size = head.int.toLong() and 0xFFFFFFFFL
            val type = String(ByteArray(4).also { head.get(it) }, Charsets.ISO_8859_1)
            var headerLen = 8
            when (size) {
                0L -> return null
                1L -> {
                    head.clear(); head.limit(8)
                    if (readFully(ch, head, at + 8) < 8) return null
                    head.flip(); size = head.long; headerLen = 16
                }
            }
            if (size < headerLen || at + size > length) return null
            if (type == "moof" || type == "mfra") return null
            if (!type.all { it.code in 0x20..0x7E }) return null
            boxes += Box(type, at, size, headerLen)
            at += size
        }
        if (at != length || boxes.none { it.type == "ftyp" } || boxes.none { it.type == "moov" }) return null
        val last = boxes.last()
        val ours = if (last.type == "uuid" && last.size >= last.headerLen + 16 && last.size <= MAX_PAYLOAD + 64 &&
            readAt(ch, last.offset + last.headerLen, 16).contentEquals(MOODY_ID)) last else null
        // Moody's id anywhere but last means something was added after it; leave such files alone.
        if (boxes.dropLast(1).any { it.type == "uuid" && it.size >= it.headerLen + 16 && readAt(ch, it.offset + it.headerLen, 16).contentEquals(MOODY_ID) }) return null
        return Layout(length, boxes, ours)
    }

    private fun box(payload: ByteArray): ByteArray? {
        if (payload.size > MAX_PAYLOAD) return null
        val size = 8 + 16 + payload.size
        return ByteBuffer.allocate(size).putInt(size).put("uuid".toByteArray(Charsets.ISO_8859_1)).put(MOODY_ID).put(payload).array()
    }

    private fun encode(marks: List<FileMark>): ByteArray {
        val arr = JSONArray()
        marks.take(MAX_MARKS).forEach { m ->
            arr.put(JSONObject().put("id", m.uid).put("s", m.startMs).apply {
                m.endMs?.let { put("e", it) }
                if (m.name.isNotEmpty()) put("n", m.name)
                put("u", m.updatedAt)
                if (m.deleted) put("d", true)
            })
        }
        return JSONObject().put("v", 1).put("marks", arr).toString().toByteArray(Charsets.UTF_8)
    }

    /** Reads a payload from someone else's file: everything is checked and cleaned, nothing trusted. */
    internal fun parse(bytes: ByteArray): List<FileMark>? = runCatching {
        if (bytes.size > MAX_PAYLOAD) return null
        val o = JSONObject(String(bytes, Charsets.UTF_8))
        if (o.optInt("v") != 1) return null
        val arr = o.getJSONArray("marks")
        (0 until minOf(arr.length(), MAX_MARKS)).mapNotNull { i ->
            val m = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = m.optString("id").takeIf { it.length in 8..64 && it.all { c -> c.isLetterOrDigit() || c == '-' } } ?: return@mapNotNull null
            val s = m.optLong("s", -1).takeIf { it in 0..(48L * 3600_000) } ?: return@mapNotNull null
            val e = if (m.has("e")) m.optLong("e", -1).takeIf { it > s && it <= 48L * 3600_000 } ?: return@mapNotNull null else null
            FileMark(id, s, e, BookmarkNames.clean(m.optString("n")), m.optLong("u").coerceAtLeast(0), m.optBoolean("d"))
        }
    }.getOrNull()

    private fun prefixHash(ch: FileChannel, base: Long): String {
        val md = MessageDigest.getInstance("SHA-256")
        fun take(from: Long, n: Long) {
            var at = from; val end = from + n
            val buf = ByteBuffer.allocate(64 * 1024)
            while (at < end) {
                buf.clear(); buf.limit(minOf(buf.capacity().toLong(), end - at).toInt())
                val r = ch.read(buf, at); if (r <= 0) break
                md.update(buf.array(), 0, r); at += r
            }
        }
        take(0, minOf(HEAD_HASH, base))
        val tailFrom = maxOf(minOf(HEAD_HASH, base), base - TAIL_HASH)
        take(tailFrom, base - tailFrom)
        md.update(base.toString().toByteArray())
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    private fun readFully(ch: FileChannel, buf: ByteBuffer, at: Long): Int {
        var n = 0
        while (buf.hasRemaining()) { val r = ch.read(buf, at + n); if (r <= 0) break; n += r }
        return n
    }

    private fun readAt(ch: FileChannel, at: Long, len: Int): ByteArray {
        val buf = ByteBuffer.allocate(len)
        readFully(ch, buf, at)
        return buf.array().copyOf(buf.position())
    }

    private fun writeAt(ch: FileChannel, at: Long, bytes: ByteArray) {
        val buf = ByteBuffer.wrap(bytes)
        var n = 0
        while (buf.hasRemaining()) { val w = ch.write(buf, at + n); if (w <= 0) error("short write"); n += w }
    }
}
