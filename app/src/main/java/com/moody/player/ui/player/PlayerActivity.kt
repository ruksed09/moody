package com.moody.player.ui.player

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moody.player.R
import com.moody.player.design.MoodyTheme
import com.moody.player.design.ThemeMode
import com.moody.player.design.components.ToastHost
import com.moody.player.design.components.Toaster
import com.moody.player.player.video.VideoBackgroundService
import com.moody.player.player.PlaybackCoordinator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {
    private val vm: PlayerViewModel by viewModels()
    @Inject lateinit var coordinator: PlaybackCoordinator
    /** How the rest of Moody closes this screen (see [PlaybackCoordinator.closeVideo]). */
    private val closeVideo: () -> Unit = { vm.engine.pause(); finishAndRemoveTask() }
    private var inPip by mutableStateOf(false)
    /** null = follow the video; otherwise the user's choice from the rotate button. */
    private var userOrientation: Int? = null
    private var sizedFor: Uri? = null
    private var orientationBeforePip: Int? = null
    /**
     * The Play in background button was used and this screen has not been seen since: it leaves to background
     * sound (never picture-in-picture), and onStop keeps the video playing whatever the Background play setting says.
     */
    private var backgroundRequested = false

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(EXTRA_PIP, 0)) {
                PIP_TOGGLE -> vm.engine.toggle()
                PIP_BACK -> vm.engine.seekBy(-10_000)
                PIP_FWD -> vm.engine.seekBy(10_000)
            }
            updatePip()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        adoptViewIntent(intent)
        adoptBackgroundSession(intent)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply { layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES }
        }
        if (vm.isVault) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        hideSystemBars()
        coordinator.videoStarted()
        coordinator.closeVideo = closeVideo
        ContextCompat.registerReceiver(this, pipReceiver, IntentFilter(ACTION_PIP), ContextCompat.RECEIVER_NOT_EXPORTED)

        if (vm.queue.isEmpty()) { finish(); return }

        setContent {
            val toaster = remember { Toaster() }
            LaunchedEffect(Unit) { vm.toasts.collect { toaster.show(it) } }
            val fontMode = vm.settings.collectAsStateWithLifecycle().value.fontMode
            MoodyTheme(ThemeMode.DARK, fontMode) {
                androidx.compose.runtime.CompositionLocalProvider(com.moody.player.design.components.LocalToaster provides toaster) {
                Box(Modifier.fillMaxSize()) {
                    PlayerScreen(
                        vm,
                        PlayerHost(
                            onBack = { finish() },
                            onPip = if (supportsPip()) ({ enterPip() }) else null,
                            onBackground = ::playInBackground,
                            onRotate = ::rotate,
                            orientationLabel = "",
                        ),
                        inPip,
                    )
                    if (!inPip) ToastHost(toaster, Alignment.TopCenter, androidx.compose.foundation.layout.PaddingValues(top = 64.dp), onVideo = true)
                }
                }
            }
        }

        lifecycleScope.launch {
            vm.engine.state.collect { st ->
                // Orientation follows how the video is meant to be seen (rotation metadata applied),
                // so phone-recorded portrait clips and Reels stay portrait.
                if (st.uri != null && st.displayWidth > 0 && st.displayHeight > 0 && sizedFor != st.uri) {
                    sizedFor = st.uri
                    // A manual rotation applies to one video; each new video follows its own shape.
                    userOrientation = null
                    requestedOrientation = if (st.displayWidth > st.displayHeight) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                }
            }
        }
        // Keep the PiP params current (auto-enter needs to know the video is playing before Home is pressed).
        lifecycleScope.launch {
            vm.engine.state
                .map { Triple(it.playing, it.displayWidth to it.displayHeight, it.videoWidth to it.videoHeight) }
                .distinctUntilChanged()
                .collect { updatePip() }
        }
    }

    /** Opening a file from another app: the data uri becomes a one-item queue. */
    private fun adoptViewIntent(i: Intent) {
        if (i.action == Intent.ACTION_VIEW && i.data != null && !i.hasExtra(PlayerViewModel.EXTRA_QUEUE)) {
            i.putParcelableArrayListExtra(PlayerViewModel.EXTRA_QUEUE, arrayListOf(i.data!!))
            i.putExtra(PlayerViewModel.EXTRA_INDEX, 0)
        }
    }

    /**
     * Opened without a queue (the background-play notification) after the screen that started the video was
     * closed: take the queue the service is playing from. With no service running the queue stays empty and
     * the screen closes, as before.
     */
    private fun adoptBackgroundSession(i: Intent) {
        if (i.hasExtra(PlayerViewModel.EXTRA_QUEUE) || !VideoBackgroundService.running) return
        val s = VideoBackgroundState.session?.takeIf { it.queue.isNotEmpty() } ?: return
        i.putParcelableArrayListExtra(PlayerViewModel.EXTRA_QUEUE, ArrayList(s.queue.map { it.uri }))
        i.putStringArrayListExtra(PlayerViewModel.EXTRA_TITLES, ArrayList(s.queue.map { it.title }))
        i.putExtra(PlayerViewModel.EXTRA_INDEX, s.index)
        i.putExtra(PlayerViewModel.EXTRA_VAULT, false)
        i.putExtra(PlayerViewModel.EXTRA_REJOIN, true)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // A new video while this screen is open: load it here instead of restarting the activity,
        // so no closing screen can stop the playback that replaced it.
        adoptViewIntent(intent)
        setIntent(intent)
        @Suppress("DEPRECATION")
        val uris = intent.getParcelableArrayListExtra<Uri>(PlayerViewModel.EXTRA_QUEUE).orEmpty()
        val vault = intent.getBooleanExtra(PlayerViewModel.EXTRA_VAULT, false)
        if (vault) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        sizedFor = null
        userOrientation = null
        vm.load(uris, intent.getIntExtra(PlayerViewModel.EXTRA_INDEX, 0), intent.getStringArrayListExtra(PlayerViewModel.EXTRA_TITLES), vault)
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun rotate() {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val next = if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        userOrientation = next
        requestedOrientation = next
        vm.toast(if (landscape) "Portrait" else "Landscape")
    }

    private fun supportsPip() = packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private fun pipParams(): PictureInPictureParams {
        val st = vm.engine.state.value
        val vw = if (st.displayWidth > 0) st.displayWidth else st.videoWidth
        val vh = if (st.displayHeight > 0) st.displayHeight else st.videoHeight
        val ratio = if (vw > 0 && vh > 0) {
            val r = vw.toFloat() / vh
            when {
                r > 2.39f -> Rational(239, 100)
                r < 1 / 2.39f -> Rational(100, 239)
                else -> Rational(vw, vh)
            }
        } else Rational(16, 9)
        fun action(code: Int, icon: Int, title: String) = RemoteAction(
            Icon.createWithResource(this, icon), title, title,
            PendingIntent.getBroadcast(this, code, Intent(ACTION_PIP).setPackage(packageName).putExtra(EXTRA_PIP, code), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
        )
        val b = PictureInPictureParams.Builder()
            .setAspectRatio(ratio)
            .setActions(listOf(
                action(PIP_BACK, R.drawable.ic_pip_back, "Back 10 seconds"),
                action(PIP_TOGGLE, if (st.playing) R.drawable.ic_pip_pause else R.drawable.ic_pip_play, if (st.playing) "Pause" else "Play"),
                action(PIP_FWD, R.drawable.ic_pip_forward, "Forward 10 seconds"),
            ))
        if (Build.VERSION.SDK_INT >= 31) b.setAutoEnterEnabled(vm.settings.value.autoPip && st.playing && !vm.isVault && !backgroundRequested).setSeamlessResizeEnabled(true)
        return b.build()
    }

    private fun updatePip() { if (supportsPip()) runCatching { setPictureInPictureParams(pipParams()) } }

    private fun enterPip() {
        if (vm.isVault) { vm.toast("Picture in picture is off for vault videos"); return }
        runCatching { enterPictureInPictureMode(pipParams()) }
    }

    private fun shouldAutoPip() = supportsPip() && vm.settings.value.autoPip && vm.engine.state.value.playing && !vm.isVault && !backgroundRequested

    /**
     * The Play in background button: the sound goes on under the same service and notification as the Background
     * play setting, and Moody leaves the screen. This screen is only stopped, not closed, so the sleep timer, A-B
     * repeat, stats and the queue keep working, and the notification brings it back with the picture. A paused
     * video starts playing: that is what was asked for. Vault videos never play in the background.
     */
    private fun playInBackground() {
        if (vm.isVault) { vm.toast("Background play is off for vault videos"); return }
        val st = vm.engine.state.value
        if (st.uri == null || st.error != null) { vm.toast("Nothing to play in the background yet"); return }
        backgroundRequested = true
        // Before leaving: "Picture-in-picture on Home" must not turn this into a small window.
        updatePip()
        if (!st.playing) {
            coordinator.videoStarted()
            vm.dismissResume()
            vm.engine.play()
        }
        // Started here, while this screen is still in front, so Android cannot refuse the foreground service.
        VideoBackgroundState.session = BackgroundSession(vm.queue, vm.index.value)
        val started = runCatching { VideoBackgroundService.start(this, vm.current?.title.orEmpty()) }.isSuccess
        if (started && moveTaskToBack(true)) return
        // Still on screen: back to normal. The service is given a moment to finish starting before it is stopped.
        backgroundRequested = false
        updatePip()
        vm.toast("Couldn't switch to background play")
        if (started) window.decorView.postDelayed({
            if (!backgroundRequested && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) VideoBackgroundService.stop(this)
        }, 1_000)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Android 12+ enters on its own through setAutoEnterEnabled; older versions (and OEM launchers that
        // skip auto-enter) are covered here. Both Home gesture and the three-button Home send this hint.
        if (!isInPictureInPictureMode && shouldAutoPip()) enterPip()
    }

    override fun onStart() {
        super.onStart()
        vm.engine.resumeViews()
    }

    override fun onResume() {
        super.onResume()
        // Seen again: the next Home press follows the settings (picture-in-picture, Background play) as usual.
        backgroundRequested = false
        updatePip()
        if (VideoBackgroundService.running) VideoBackgroundService.stop(this)
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
        if (isInPictureInPictureMode) {
            // The small window must not keep the phone in landscape: the screen behind it follows the user.
            orientationBeforePip = requestedOrientation
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            orientationBeforePip?.let { requestedOrientation = it }
            orientationBeforePip = null
        }
        // The surface was resized; a paused video would otherwise show black until the next frame.
        vm.engine.redrawIfPaused()
        // Leaving PiP while not coming back to the foreground means the user closed the window.
        if (!isInPictureInPictureMode && !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            vm.engine.pause()
            vm.onPauseScreen()
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        vm.onPauseScreen()
    }

    override fun onStop() {
        super.onStop()
        if (isFinishing || isInPictureInPictureMode) return
        vm.engine.suspendViews()
        val st = vm.engine.state.value
        if (backgroundRequested && !vm.isVault) {
            // The Play in background button already started the service; it works with the setting off, and a video
            // it has just un-paused may not report "playing" yet. Nothing to start, and nothing to pause.
        } else if (vm.settings.value.backgroundPlay && st.playing && !vm.isVault) {
            // Android 12+ can refuse a foreground start once the app is in the background; pause rather than crash.
            VideoBackgroundState.session = BackgroundSession(vm.queue, vm.index.value)
            runCatching { VideoBackgroundService.start(this, vm.current?.title.orEmpty()) }.onFailure { vm.engine.pause() }
        } else {
            vm.engine.pause()
        }
    }

    override fun onDestroy() {
        // A newer player screen may have taken over already: only take back what is still ours.
        if (coordinator.closeVideo === closeVideo) coordinator.closeVideo = null
        runCatching { unregisterReceiver(pipReceiver) }
        if (isFinishing && !VideoBackgroundService.running) vm.engine.stopIfOwner(vm.token)
        super.onDestroy()
    }

    companion object {
        const val ACTION_PIP = "com.moody.player.PIP"
        const val EXTRA_PIP = "pip"
        const val PIP_TOGGLE = 1
        const val PIP_BACK = 2
        const val PIP_FWD = 3

        /** At most this many videos either side of the chosen one travel in the Intent (Binder limit ~1 MB). */
        private const val WINDOW = 100

        fun intent(context: Context, uris: List<Uri>, index: Int, vault: Boolean = false, titles: List<String>? = null): Intent {
            val from = (index - WINDOW).coerceAtLeast(0)
            val to = (index + WINDOW + 1).coerceAtMost(uris.size)
            return Intent(context, PlayerActivity::class.java)
                .putParcelableArrayListExtra(PlayerViewModel.EXTRA_QUEUE, ArrayList(uris.subList(from, to)))
                .putExtra(PlayerViewModel.EXTRA_INDEX, index - from)
                .putExtra(PlayerViewModel.EXTRA_VAULT, vault)
                .apply { titles?.let { putStringArrayListExtra(PlayerViewModel.EXTRA_TITLES, ArrayList(it.subList(from, to))) } }
        }
    }
}

fun Context.openVideo(uris: List<Uri>, start: Int, fromVault: Boolean = false, titles: List<String>? = null) {
    startActivity(PlayerActivity.intent(this, uris, start, fromVault, titles))
}
