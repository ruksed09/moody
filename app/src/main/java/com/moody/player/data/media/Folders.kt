package com.moody.player.data.media

/** Groups videos by folder, drops hidden folders, newest folder first. */
fun groupIntoFolders(videos: List<VideoItem>, hidden: Set<String>): List<Folder> =
    videos.asSequence()
        .filter { it.relativePath !in hidden }
        .groupBy { it.relativePath }
        .map { (path, items) ->
            val latest = items.maxByOrNull { it.dateAdded }
            Folder(path, folderName(path), items.size, items.sumOf { it.sizeBytes }, latest)
        }
        .sortedByDescending { it.latest?.dateAdded ?: 0 }
