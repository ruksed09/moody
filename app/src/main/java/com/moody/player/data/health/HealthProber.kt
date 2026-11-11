package com.moody.player.data.health

import android.content.Context
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.moody.player.domain.MediaKind
import com.moody.player.domain.OpenResult
import com.moody.player.domain.Probe
import com.moody.player.domain.ProbedTrack
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.interfaces.IMedia
import java.io.FileNotFoundException
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Looks inside one media file, read-only: opens it, lists its streams, checks Android has decoders for them,
 * reads a frame at the start and near the end, and asks libVLC when Android can't cope. Never writes,
 * moves or deletes anything. One instance per scan; call [close] at the end. Not thread-safe.
 */
internal class HealthProber(private val context: Context) {
    private val codecs by lazy { MediaCodecList(MediaCodecList.REGULAR_CODECS) }
    private var vlc: LibVLC? = null
    private val buffer: ByteBuffer = ByteBuffer.allocateDirect(4 shl 20)

    fun probe(uri: Uri, kind: MediaKind, name: String, libraryMs: Long?): Probe {
        val pfd: ParcelFileDescriptor = try {
            context.contentResolver.openFileDescriptor(uri, "r") ?: return Probe(kind, name, OpenResult.MISSING)
        } catch (e: FileNotFoundException) {
            return Probe(kind, name, OpenResult.MISSING)
        } catch (e: SecurityException) {
            return Probe(kind, name, OpenResult.NO_ACCESS)
        } catch (e: Exception) {
            return Probe(kind, name, OpenResult.ERROR)
        }
        pfd.use { fd ->
            val size = fd.statSize
            if (size == 0L) return Probe(kind, name, OpenResult.OK, sizeBytes = 0)

            val ex = MediaExtractor()
            var parsed = false
            val tracks = mutableListOf<ProbedTrack>()
            var containerMs: Long? = null
            var firstOk: Boolean? = null
            var until: Long? = null
            try {
                ex.setDataSource(fd.fileDescriptor)
                parsed = ex.trackCount > 0
                var main = -1
                for (i in 0 until ex.trackCount) {
                    val f = ex.getTrackFormat(i)
                    val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                    val isVideo = mime.startsWith("video/")
                    if (!isVideo && !mime.startsWith("audio/")) continue
                    if (f.containsKey(MediaFormat.KEY_DURATION)) containerMs = maxOf(containerMs ?: 0L, f.getLong(MediaFormat.KEY_DURATION) / 1000)
                    val w = f.intOr(MediaFormat.KEY_WIDTH); val h = f.intOr(MediaFormat.KEY_HEIGHT)
                    tracks += ProbedTrack(isVideo, mime, hasDecoder(f, mime, isVideo), w, h)
                    val wanted = if (kind == MediaKind.VIDEO) isVideo else !isVideo
                    if (main < 0 && wanted) main = i
                }
                if (main < 0 && tracks.isNotEmpty()) main = (0 until ex.trackCount).first { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.let { m -> m.startsWith("video/") || m.startsWith("audio/") } == true }
                if (main >= 0) {
                    ex.selectTrack(main)
                    firstOk = readAt(ex, 0)
                    val len = containerMs ?: libraryMs
                    if (firstOk == true && len != null && len > 20_000) until = readableUntil(ex, len)
                }
            } catch (e: Exception) {
                parsed = false
            } finally {
                runCatching { ex.release() }
            }

            var metaError = false
            var realFormat: String? = null
            var metaW = 0; var metaH = 0
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(fd.fileDescriptor)
                realFormat = formatOf(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE))
                metaW = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                metaH = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                if (containerMs == null) containerMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.takeIf { it > 0 }
            } catch (e: Exception) {
                metaError = true
            } finally {
                runCatching { r.release() }
            }
            val sized = tracks.map { if (it.isVideo && (it.width <= 0 || it.height <= 0) && metaW > 0) it.copy(width = metaW, height = metaH) else it }

            // libVLC only when Android can't open the file or can't decode one of its streams.
            var vlcParsed: Boolean? = null
            var vlcTracks: List<ProbedTrack> = emptyList()
            if (!parsed || sized.any { it.androidDecoder == false }) {
                val (ok, t, ms) = parseWithVlc(fd)
                vlcParsed = ok; vlcTracks = t
                if (containerMs == null && ms > 0) containerMs = ms
            }
            return Probe(
                kind, name, OpenResult.OK, size, parsed, sized, vlcParsed, vlcTracks,
                containerMs, libraryMs, firstOk, until, realFormat, metaError,
            )
        }
    }

    /** Reads one sample at or before [ms] of the selected track. */
    private fun readAt(ex: MediaExtractor, ms: Long): Boolean = try {
        ex.seekTo(ms * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        buffer.clear()
        ex.readSampleData(buffer, 0) > 0 && ex.sampleTime >= 0
    } catch (e: IllegalArgumentException) {
        true // the sample exists but is bigger than our buffer
    } catch (e: Exception) {
        false
    }

    /** How far data can be read: the full [len] when the end reads, else the last point that does. */
    private fun readableUntil(ex: MediaExtractor, len: Long): Long {
        val nearEnd = len - minOf(5_000L, len / 20)
        if (readAt(ex, nearEnd)) return len
        for (frac in doubleArrayOf(0.9, 0.75, 0.5, 0.25, 0.1)) {
            val at = (len * frac).toLong()
            if (readAt(ex, at)) return at
        }
        return 0
    }

    private fun hasDecoder(f: MediaFormat, mime: String, isVideo: Boolean): Boolean? = try {
        val q = if (isVideo) MediaFormat.createVideoFormat(mime, f.intOr(MediaFormat.KEY_WIDTH).coerceAtLeast(16), f.intOr(MediaFormat.KEY_HEIGHT).coerceAtLeast(16))
            .also { if (f.containsKey(MediaFormat.KEY_PROFILE)) it.setInteger(MediaFormat.KEY_PROFILE, f.getInteger(MediaFormat.KEY_PROFILE)) }
        else MediaFormat.createAudioFormat(mime, f.intOr(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(8_000), f.intOr(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1))
        codecs.findDecoderForFormat(q) != null
    } catch (e: Exception) {
        null
    }

    private fun parseWithVlc(fd: ParcelFileDescriptor): Triple<Boolean, List<ProbedTrack>, Long> {
        val lib = vlc ?: runCatching { LibVLC(context, arrayListOf("--no-video-title-show", "-q")) }.getOrNull()?.also { vlc = it }
            ?: return Triple(false, emptyList(), 0)
        val media = try { Media(lib, fd.fileDescriptor) } catch (e: Exception) { return Triple(false, emptyList(), 0) }
        try {
            val done = CountDownLatch(1)
            var status = IMedia.ParsedStatus.Skipped
            media.setEventListener { e -> if (e.type == IMedia.Event.ParsedChanged) { status = e.parsedStatus; done.countDown() } }
            if (!media.parseAsync(IMedia.Parse.ParseLocal, 5_000)) return Triple(false, emptyList(), 0)
            if (!done.await(6, TimeUnit.SECONDS) && !media.isParsed) return Triple(false, emptyList(), 0)
            val tracks = (0 until media.trackCount).mapNotNull { i ->
                val t = media.getTrack(i) ?: return@mapNotNull null
                when (t.type) {
                    IMedia.Track.Type.Video -> ProbedTrack(true, t.codec.orEmpty(), null, (t as? IMedia.VideoTrack)?.width ?: 0, (t as? IMedia.VideoTrack)?.height ?: 0)
                    IMedia.Track.Type.Audio -> ProbedTrack(false, t.codec.orEmpty(), null)
                    else -> null
                }
            }
            val ok = (status == IMedia.ParsedStatus.Done || media.isParsed) && tracks.isNotEmpty()
            return Triple(ok, tracks, media.duration.coerceAtLeast(0))
        } catch (e: Exception) {
            return Triple(false, emptyList(), 0)
        } finally {
            media.setEventListener(null)
            media.release()
        }
    }

    fun close() { runCatching { vlc?.release() }; vlc = null }

    private fun MediaFormat.intOr(key: String): Int = if (containsKey(key)) runCatching { getInteger(key) }.getOrDefault(0) else 0

    private fun formatOf(mime: String?): String? = when (mime?.lowercase()) {
        null -> null
        "video/mp4", "audio/mp4", "video/quicktime" -> "MP4"
        "video/x-matroska", "audio/x-matroska", "video/webm", "audio/webm" -> "MATROSKA"
        "audio/mpeg" -> "MPEG AUDIO"
        "audio/ogg", "application/ogg", "video/ogg" -> "OGG"
        "audio/flac", "audio/x-flac" -> "FLAC"
        "audio/x-wav", "audio/wav", "audio/vnd.wave" -> "WAV"
        "video/avi", "video/x-msvideo" -> "AVI"
        "video/mp2ts" -> "MPEG-TS"
        else -> null
    }
}
