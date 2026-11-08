package com.moody.player.player.video

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.moody.player.R
import com.moody.player.ui.player.PlayerActivity
import com.moody.player.ui.player.VideoBackgroundState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Keeps a video's sound playing after the user leaves the player (setting "Background play"). */
@AndroidEntryPoint
class VideoBackgroundService : Service() {
    @Inject lateinit var engine: VlcEngine
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watch: Job? = null
    private var title: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> engine.toggle()
            ACTION_CLOSE -> { engine.pause(); stopSelf(); return START_NOT_STICKY }
            else -> title = intent?.getStringExtra(EXTRA_TITLE).orEmpty()
        }
        running = true
        VideoBackgroundState.active = true
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification(engine.state.value.playing),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
        )
        if (watch == null) {
            watch = scope.launch {
                engine.state.map { it.playing to it.ended }.distinctUntilChanged().collect { (playing, ended) ->
                    if (ended) { stopSelf(); return@collect }
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(playing))
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun notification(playing: Boolean): Notification {
        ensureChannel(this)
        fun svc(action: String, code: Int) = PendingIntent.getService(
            this, code, Intent(this, VideoBackgroundService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, PlayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title.ifEmpty { "Moody" })
            .setContentText(if (playing) "Playing in the background" else "Paused")
            .setContentIntent(open)
            .setOngoing(playing)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(if (playing) R.drawable.ic_pip_pause else R.drawable.ic_pip_play, if (playing) "Pause" else "Play", svc(ACTION_TOGGLE, 1))
            .addAction(R.drawable.ic_close, "Stop", svc(ACTION_CLOSE, 2))
            .build()
    }

    override fun onDestroy() {
        running = false
        VideoBackgroundState.active = false
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL = "playback"
        private const val NOTIFICATION_ID = 41
        private const val EXTRA_TITLE = "title"
        private const val ACTION_TOGGLE = "toggle"
        private const val ACTION_CLOSE = "close"
        @Volatile var running = false
            private set

        fun ensureChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "Playback", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Controls for video and music playing in the background"
                    setShowBadge(false)
                })
            }
        }

        fun start(context: Context, title: String) {
            ContextCompat.startForegroundService(context, Intent(context, VideoBackgroundService::class.java).putExtra(EXTRA_TITLE, title))
        }

        fun stop(context: Context) { context.stopService(Intent(context, VideoBackgroundService::class.java)) }
    }
}
