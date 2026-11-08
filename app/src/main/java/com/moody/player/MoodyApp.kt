package com.moody.player

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.moody.player.data.thumbs.MediaThumbFetcher
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MoodyApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components {
            add(MediaThumbFetcher.Factory(this@MoodyApp))
            add(VideoFrameDecoder.Factory())
        }
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.2).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("thumbs")).maxSizeBytes(200L * 1024 * 1024).build() }
        .crossfade(180)
        .build()
}
