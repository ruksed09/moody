package com.moody.player.data.thumbs

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.util.Size
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options

/**
 * Uses MediaStore's own cached thumbnails (fast, already on disk) for content://media uris on Android 10+.
 * Returns null otherwise so Coil falls through to the video frame decoder.
 */
class MediaThumbFetcher(private val context: Context, private val uri: Uri, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val w = (options.size.width as? coil.size.Dimension.Pixels)?.px ?: 512
        val h = (options.size.height as? coil.size.Dimension.Pixels)?.px ?: 288
        val bmp = try {
            context.contentResolver.loadThumbnail(uri, Size(w.coerceAtLeast(96), h.coerceAtLeast(96)), null)
        } catch (e: Exception) {
            return null
        }
        return DrawableResult(BitmapDrawable(context.resources, bmp), isSampled = true, dataSource = DataSource.DISK)
    }

    class Factory(private val context: Context) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (Build.VERSION.SDK_INT < 29) return null
            if (data.scheme != "content" || data.authority != "media") return null
            return MediaThumbFetcher(context, data, options)
        }
    }
}
