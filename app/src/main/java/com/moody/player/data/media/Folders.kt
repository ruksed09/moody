package com.moody.player.data.media

import com.moody.player.domain.SortSpec
import com.moody.player.domain.sortedBy

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

/** Orders the Folders list. A folder's date is its newest video; ties keep the order they came in. */
fun sortFolders(folders: List<Folder>, spec: SortSpec): List<Folder> =
    folders.sortedBy(spec, { it.name }, { it.latest?.dateAdded ?: 0L }, { it.totalBytes }, { 0L }, count = { it.count.toLong() })
