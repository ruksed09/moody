package com.moody.player

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.moody.player.data.media.AudioItem
import com.moody.player.data.media.CoverWrite
import com.moody.player.data.media.CoverWriter
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Writes a cover into real MediaStore files of each supported format. The test makes the files itself
 * (so the app owns them and no consent prompt shows) in Music/MoodyTest/ and deletes them afterwards.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class CoverWriterTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val made = mutableListOf<Uri>()
    private val stamp = System.currentTimeMillis()
    private val cover: File by lazy { makeCover() }

    @After fun cleanUp() {
        made.forEach { runCatching { context.contentResolver.delete(it, null, null) } }
        cover.delete()
    }

    @Test fun mp3_getsCover() = embedsInto("mp3", "audio/mpeg", silentMp3(3))
    @Test fun m4a_getsCover() = embedsInto("m4a", "audio/mp4", encode(MediaFormat.MIMETYPE_AUDIO_AAC, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4))
    /** Ogg Vorbis: Android has no Vorbis encoder, so borrow one of the phone's own system sounds. */
    @Test fun oggVorbis_getsCover() = embedsInto("ogg", "audio/ogg", systemOgg())

    /** Opus in an .ogg file: the tag library reads only Vorbis, so the file must stay untouched. */
    @Test fun oggOpus_isLeftAlone() = runBlocking {
        val bytes = encode(MediaFormat.MIMETYPE_AUDIO_OPUS, MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG)
        val (uri, item) = insert("ogg", "audio/ogg", bytes)
        assertEquals(CoverWrite.Unsupported, CoverWriter.embed(context, item, cover.path, {}, {}))
        assertArrayEquals(bytes, read(uri))
    }
    @Test fun flac_getsCover() = embedsInto("flac", "audio/flac", flac())

    @Test fun wav_isLeftAlone() = runBlocking {
        val bytes = ByteArray(4096) { 7 }
        val (uri, item) = insert("wav", "audio/x-wav", bytes)
        assertEquals(CoverWrite.Unsupported, CoverWriter.embed(context, item, cover.path, {}, {}))
        assertArrayEquals(bytes, read(uri))
    }

    @Test fun brokenMp3_isLeftAlone() = runBlocking {
        val bytes = ByteArray(64 * 1024) { (it * 31 % 253).toByte() }
        val (uri, item) = insert("mp3", "audio/mpeg", bytes)
        val r = CoverWriter.embed(context, item, cover.path, {}, {})
        assertTrue("got $r", r != CoverWrite.Done)
        assertArrayEquals(bytes, read(uri))
    }

    @Test fun playerIsReleasedAndResumed() = runBlocking {
        val (_, item) = insert("mp3", "audio/mpeg", silentMp3(3))
        val calls = mutableListOf<String>()
        val r = CoverWriter.embed(context, item, cover.path, { calls += "release" }, { calls += "resume" })
        assertEquals(CoverWrite.Done, r)
        assertEquals(listOf("release", "resume"), calls)
    }


    private fun embedsInto(ext: String, mime: String, bytes: ByteArray) = runBlocking {
        val (uri, item) = insert(ext, mime, bytes)
        val before = durationMs(uri)
        assertNotNull("$ext test file has no duration", before)
        val r = CoverWriter.embed(context, item, cover.path, {}, {})
        assertEquals("$ext: $r", CoverWrite.Done, r)
        val art = MediaMetadataRetriever().run { try { setDataSource(context, uri); embeddedPicture } finally { release() } }
        assertTrue("$ext: no embedded picture", art != null && art.isNotEmpty())
        val after = durationMs(uri)!!
        assertTrue("$ext: duration $before -> $after", abs(before!! - after) <= 1_000)
        assertTrue("$ext: no backup left in cache", !File(context.cacheDir, "cover-write").exists())
    }

    private fun insert(ext: String, mime: String, bytes: ByteArray): Pair<Uri, AudioItem> {
        val name = "moody-cover-test-$stamp.$ext"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Music/MoodyTest/")
        }
        val uri = context.contentResolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)!!
        made += uri
        context.contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
        val item = AudioItem(uri, 0, "Test", "", "", 0, 3_000, "Music/MoodyTest/", mime, 0, name, 0)
        return uri to item
    }

    private fun read(uri: Uri) = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }

    private fun durationMs(uri: Uri): Long? = MediaMetadataRetriever().run {
        try { setDataSource(context, uri); extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() } finally { release() }
    }

    private fun makeCover(): File {
        val bmp = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(Color.rgb(0x4F, 0xD1, 0xC5))
        val f = File(context.cacheDir, "cover-test-$stamp.jpg")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return f
    }

    /** MPEG-1 Layer III, 128 kbps, 44.1 kHz frames with empty side info: valid silence. */
    private fun silentMp3(seconds: Int): ByteArray {
        val frame = ByteArray(417).also { it[0] = 0xFF.toByte(); it[1] = 0xFB.toByte(); it[2] = 0x90.toByte(); it[3] = 0x64 }
        val out = ByteArrayOutputStream()
        repeat(seconds * 44100 / 1152) { out.write(frame) }
        return out.toByteArray()
    }

    private fun systemOgg(): ByteArray = listOf("/system/media/audio", "/product/media/audio")
        .flatMap { root -> File(root).walkTopDown().filter { it.isFile && it.extension == "ogg" }.toList() }
        .first { f -> f.inputStream().use { s -> String(s.readNBytes(36), Charsets.ISO_8859_1).contains("vorbis") } }
        .readBytes()

    private fun pcm(rate: Int, seconds: Int): ShortArray = ShortArray(rate * seconds) { (sin(2 * PI * 440 * it / rate) * 8000).toInt().toShort() }

    /** Encodes 3 s of a 440 Hz tone with the phone's own encoder into a container. */
    private fun encode(mime: String, container: Int): ByteArray {
        val rate = 48000
        val samples = pcm(rate, 3)
        val fmt = MediaFormat.createAudioFormat(mime, rate, 1).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, 64000)
            if (mime == MediaFormat.MIMETYPE_AUDIO_AAC) setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        }
        val out = File(context.cacheDir, "enc-$stamp-${container}")
        val muxer = MediaMuxer(out.path, container)
        var track = -1
        runCodec(fmt, samples, rate, onFormat = { track = muxer.addTrack(it); muxer.start() }) { buf, info ->
            if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0) muxer.writeSampleData(track, buf, info)
        }
        muxer.stop(); muxer.release()
        return out.readBytes().also { out.delete() }
    }

    /** FLAC: the encoder's config buffer holds the stream header; frames follow it as they come. */
    private fun flac(): ByteArray {
        val rate = 44100
        val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_FLAC, rate, 1).apply {
            setInteger(MediaFormat.KEY_FLAC_COMPRESSION_LEVEL, 5)
        }
        val head = ByteArrayOutputStream(); val body = ByteArrayOutputStream()
        runCodec(fmt, pcm(rate, 3), rate, onFormat = {}) { buf, info ->
            val b = ByteArray(info.size); buf.position(info.offset); buf.get(b)
            if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) head.write(b) else body.write(b)
        }
        val h = head.toByteArray()
        val magic = "fLaC".toByteArray()
        val start = if (h.size >= 4 && h.copyOf(4).contentEquals(magic)) h else magic + h
        return start + body.toByteArray()
    }

    private fun runCodec(fmt: MediaFormat, samples: ShortArray, rate: Int, onFormat: (MediaFormat) -> Unit, onData: (java.nio.ByteBuffer, MediaCodec.BufferInfo) -> Unit) {
        val codec = MediaCodec.createEncoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val info = MediaCodec.BufferInfo()
        var fed = 0; var inputDone = false
        while (true) {
            if (!inputDone) {
                val i = codec.dequeueInputBuffer(10_000)
                if (i >= 0) {
                    val buf = codec.getInputBuffer(i)!!.order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    val n = minOf(buf.capacity() / 2, samples.size - fed)
                    for (k in 0 until n) buf.putShort(samples[fed + k])
                    val pts = fed * 1_000_000L / rate
                    fed += n
                    inputDone = fed >= samples.size
                    codec.queueInputBuffer(i, 0, n * 2, pts, if (inputDone) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
                }
            }
            val o = codec.dequeueOutputBuffer(info, 10_000)
            if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) onFormat(codec.outputFormat)
            else if (o >= 0) {
                onData(codec.getOutputBuffer(o)!!, info)
                codec.releaseOutputBuffer(o, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            }
        }
        codec.stop(); codec.release()
    }
}
