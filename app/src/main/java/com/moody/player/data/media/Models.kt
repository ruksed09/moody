package com.moody.player.data.media

import android.net.Uri

data class VideoItem(
    val uri: Uri,
    val id: Long,
    val name: String,
    /** MediaStore RELATIVE_PATH, e.g. "DCIM/Camera/". Empty for the storage root. */
    val relativePath: String,
    val sizeBytes: Long,
    val durationMs: Long?,
    val dateAdded: Long,
    val width: Int,
    val height: Int,
    val mime: String,
) {
    val folder: String get() = folderName(relativePath)
    val title: String get() = name.substringBeforeLast('.', name)
    val extension: String get() = name.substringAfterLast('.', "").uppercase()
    val resolutionLabel: String?
        get() = when {
            height >= 2000 || width >= 3800 -> "4K"
            height >= 1000 || width >= 1900 -> "1080p"
            height >= 700 || width >= 1260 -> "720p"
            height > 0 -> "${height}p"
            else -> null
        }
}

data class AudioItem(
    val uri: Uri,
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val relativePath: String,
    val mime: String,
    val trackNo: Int,
    val fileName: String,
    val dateAdded: Long,
    /** A cover picked in Moody (absolute path of Moody's own copy); null uses the file's art. */
    val cover: String? = null,
) {
    val folder: String get() = folderName(relativePath)
}

data class Folder(
    val path: String,
    val name: String,
    val count: Int,
    val totalBytes: Long,
    val latest: VideoItem?,
)

enum class AccessLevel { FULL, PARTIAL, NONE }

fun folderName(relativePath: String): String =
    relativePath.trimEnd('/').substringAfterLast('/').ifEmpty { "Internal storage" }
