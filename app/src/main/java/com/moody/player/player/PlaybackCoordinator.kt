package com.moody.player.player

import android.content.Context
import com.moody.player.player.video.VideoBackgroundService
import com.moody.player.player.video.VlcEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Keeps the two engines from playing over each other: starting one pauses the other. */
@Singleton
class PlaybackCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val video: VlcEngine,
) {
    /** Set by the music controller once it is connected. */
    @Volatile var pauseMusic: (() -> Unit)? = null

    fun videoStarted() { pauseMusic?.invoke() }

    fun musicStarted() {
        if (VideoBackgroundService.running) VideoBackgroundService.stop(context)
        video.pause()
    }
}
