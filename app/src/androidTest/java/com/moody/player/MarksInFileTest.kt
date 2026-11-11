package com.moody.player

import android.content.ContentValues
import android.content.Context
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
import com.moody.player.data.media.FileMark
import com.moody.player.data.media.MarksInFile
import com.moody.player.data.media.MarksWrite
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.sin

/**
 * Bookmarks written into real MediaStore MP4 files the test makes itself (Music/MoodyTest/, deleted after).
 * The files hold only sound, so they live in the audio collection: Android rescans a file after it is
 * written and files a sound-only MP4 as audio, which would hide it from a video URI.
 * Checks that the video bytes never change, that removing the bookmarks gives back the original file
 * exactly, that odd files are refused untouched, and that a half-done write is undone.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class MarksInFileTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val made = mutableListOf<Uri>()
    private val stamp = System.currentTimeMillis()

    @After fun cleanUp() {
        made.forEach { runCatching { MarksInFile.journalFor(context, it).delete(); context.contentResolver.delete(it, null, null) } }
    }

    private val marks = listOf(
        FileMark("0b9d1c1e-0000-4000-8000-000000000001", 1_000, null, "Intro", 10, false),
        FileMark("0b9d1c1e-0000-4000-8000-000000000002", 1_500, 2_500, "Chorus — take 2: a/b;c", 20, false),
        FileMark("0b9d1c1e-0000-4000-8000-000000000003", 500, null, "", 30, true),
    )

    @Test fun roundTrip_keepsVideoBytes_andRemovesCleanly() {
        val original = mp4()
        val uri = insert(original)
        val before = duration(uri)

        assertEquals(MarksWrite.Done, MarksInFile.write(context, uri, marks))
        val tagged = read(uri)
        assertTrue("grew", tagged.size > original.size)
        assertArrayEquals("video bytes untouched", original, tagged.copyOf(original.size))
        assertEquals(marks, MarksInFile.read(context, uri))
        assertEquals(before, duration(uri))

        // Rewrite with fewer bookmarks: the box shrinks, the video stays.
        assertEquals(MarksWrite.Done, MarksInFile.write(context, uri, marks.take(1)))
        assertEquals(marks.take(1), MarksInFile.read(context, uri))
        assertArrayEquals(original, read(uri).copyOf(original.size))

        // No bookmarks left: the file is exactly what it was.
        assertEquals(MarksWrite.Done, MarksInFile.write(context, uri, emptyList()))
        assertArrayEquals(original, read(uri))
        assertNull(MarksInFile.read(context, uri))
    }

    @Test fun trailingData_isRefusedUntouched() {
        val bytes = mp4() + ByteArray(37) { 9 }
        val uri = insert(bytes)
        assertEquals(MarksWrite.NotSupported, MarksInFile.write(context, uri, marks))
        assertArrayEquals(bytes, read(uri))
    }

    @Test fun boxToEndOfFile_isRefusedUntouched() {
        val bytes = box("ftyp", "isom".toByteArray() + ByteArray(8)) + box("moov", ByteArray(16)) +
            ByteBuffer.allocate(8).putInt(0).put("mdat".toByteArray()).array() + ByteArray(500) { 3 }
        val uri = insert(bytes)
        assertEquals(MarksWrite.NotSupported, MarksInFile.write(context, uri, marks))
        assertArrayEquals(bytes, read(uri))
    }

    @Test fun fragmented_isRefusedUntouched() {
        val bytes = box("ftyp", "isom".toByteArray() + ByteArray(8)) + box("moov", ByteArray(16)) + box("moof", ByteArray(16)) + box("mdat", ByteArray(64))
        val uri = insert(bytes)
        assertEquals(MarksWrite.NotSupported, MarksInFile.write(context, uri, marks))
        assertArrayEquals(bytes, read(uri))
    }

    @Test fun halfDoneWrite_isUndoneOnRecover() {
        val original = mp4()
        val uri = insert(original)
        assertEquals(MarksWrite.Done, MarksInFile.write(context, uri, marks))
        // Pretend Moody stopped right after writing: the journal still says the file had no box.
        val j = MarksInFile.journalFor(context, uri)
        j.parentFile?.mkdirs()
        j.writeText(JSONObject().put("uri", uri.toString()).put("base", original.size.toLong()).put("length", original.size.toLong())
            .put("old", "").put("prefix", MarksInFile.prefixHashOf(context, uri, original.size.toLong())).toString())
        MarksInFile.recover(context)
        assertArrayEquals(original, read(uri))
        assertTrue(!j.exists())
    }

    @Test fun untrustedPayload_isCleaned() {
        val raw = """{"v":1,"marks":[
            {"id":"good-id-123","s":1000,"n":"ok<script>name","u":5},
            {"id":"x","s":1000},
            {"id":"bad id with spaces","s":1000},
            {"id":"neg-start-1","s":-5},
            {"id":"bad-range-1","s":5000,"e":4000},
            {"id":"long-name-1","s":0,"n":"${"a".repeat(200)}"}
        ]}""".toByteArray()
        val parsed = MarksInFile.parse(raw)!!
        assertEquals(listOf("good-id-123", "long-name-1"), parsed.map { it.uid })
        assertEquals("okscriptname", parsed[0].name)
        assertEquals(40, parsed[1].name.length)
        assertNull(MarksInFile.parse("""{"v":2,"marks":[]}""".toByteArray()))
    }

    // ---- helpers ----

    private fun box(type: String, body: ByteArray) = ByteBuffer.allocate(8 + body.size).putInt(8 + body.size).put(type.toByteArray()).put(body).array()

    private fun insert(bytes: ByteArray): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "moody-marks-test-$stamp-${made.size}.m4a")
            put(MediaStore.MediaColumns.MIME_TYPE, "audio/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Music/MoodyTest/")
        }
        val uri = context.contentResolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)!!
        made += uri
        context.contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
        return uri
    }

    private fun read(uri: Uri) = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }

    private fun duration(uri: Uri): Long? = MediaMetadataRetriever().run {
        try { setDataSource(context, uri); extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() } finally { release() }
    }

    /** A real MP4 (3 s of AAC) made by the phone's own muxer. */
    private fun mp4(): ByteArray {
        val rate = 44100
        val samples = ShortArray(rate * 3) { (sin(2 * PI * 440 * it / rate) * 8000).toInt().toShort() }
        val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, 1).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, 64000)
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        }
        val out = File(context.cacheDir, "marks-$stamp-${made.size}.mp4")
        val muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); codec.start()
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
                    fed += n; inputDone = fed >= samples.size
                    codec.queueInputBuffer(i, 0, n * 2, pts, if (inputDone) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
                }
            }
            val o = codec.dequeueOutputBuffer(info, 10_000)
            if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { track = muxer.addTrack(codec.outputFormat); muxer.start() }
            else if (o >= 0) {
                if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0) muxer.writeSampleData(track, codec.getOutputBuffer(o)!!, info)
                codec.releaseOutputBuffer(o, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            }
        }
        codec.stop(); codec.release(); muxer.stop(); muxer.release()
        return out.readBytes().also { out.delete() }
    }
}
