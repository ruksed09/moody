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
import androidx.lifecycle.lifecycleScope
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
    private var inPip by mutableStateOf(false)
    /** null = follow the video; otherwise the user's choice from the rotate button. */
    private var userOrientation: Int? = null
    private var sizedFor: Uri? = null

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
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply { layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES }
        }
        if (vm.isVault) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        hideSystemBars()
        coordinator.videoStarted()
        ContextCompat.registerReceiver(this, pipReceiver, IntentFilter(ACTION_PIP), ContextCompat.RECEIVER_NOT_EXPORTED)

        if (vm.queue.isEmpty()) { finish(); return }

        setContent {
            val toaster = remember { Toaster() }
            LaunchedEffect(Unit) { vm.toasts.collect { toaster.show(it) } }
            val fontMode = vm.settings.collectAsStateWithLifecycle().value.fontMode
            MoodyTheme(ThemeMode.DARK, fontMode) {
                Box(Modifier.fillMaxSize()) {
                    PlayerScreen(
                        vm,
                        PlayerHost(
                            onBack = { finish() },
                            onPip = if (supportsPip()) ({ enterPip() }) else null,
                            onRotate = ::rotate,
                            orientationLabel = "",
                        ),
                        inPip,
                    )
                    if (!inPip) ToastHost(toaster, Alignment.TopCenter, androidx.compose.foundation.layout.PaddingValues(top = 64.dp), onVideo = true)
                }
            }
        }

        lifecycleScope.launch {
            vm.engine.state.collect { st ->
                if (st.uri != null && st.videoWidth > 0 && sizedFor != st.uri) {
                    sizedFor = st.uri
                    if (userOrientation == null) {
                        requestedOrientation = if (st.videoWidth >= st.videoHeight) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                    }
                }
                if (inPip) updatePip()
            }
        }
    }

    /** Opening a file from another app: the data uri becomes a one-item queue. */
    private fun adoptViewIntent(i: Intent) {
        if (i.action == Intent.ACTION_VIEW && i.data != null && !i.hasExtra(PlayerViewModel.EXTRA_QUEUE)) {
            i.putParcelableArrayListExtra(PlayerViewModel.EXTRA_QUEUE, arrayListOf(i.data!!))
            i.putExtra(PlayerViewModel.EXTRA_INDEX, 0)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // A new file while playing: restart with the new queue.
        adoptViewIntent(intent)
        finish()
        startActivity(intent.setClass(this, PlayerActivity::class.java))
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
        val ratio = if (st.videoWidth > 0 && st.videoHeight > 0) {
            val r = st.videoWidth.toFloat() / st.videoHeight
            when {
                r > 2.39f -> Rational(239, 100)
                r < 1 / 2.39f -> Rational(100, 239)
                else -> Rational(st.videoWidth, st.videoHeight)
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
        if (Build.VERSION.SDK_INT >= 31) b.setAutoEnterEnabled(vm.settings.value.autoPip && st.playing && !vm.isVault).setSeamlessResizeEnabled(true)
        return b.build()
    }

    private fun updatePip() { if (supportsPip()) runCatching { setPictureInPictureParams(pipParams()) } }

    private fun enterPip() {
        if (vm.isVault) { vm.toast("Picture in picture is off for vault videos"); return }
        runCatching { enterPictureInPictureMode(pipParams()) }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < 31 && vm.settings.value.autoPip && vm.engine.state.value.playing && !vm.isVault) enterPip()
    }

    override fun onResume() {
        super.onResume()
        updatePip()
        if (VideoBackgroundService.running) VideoBackgroundService.stop(this)
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
    }

    override fun onPause() {
        super.onPause()
        vm.onPauseScreen()
    }

    override fun onStop() {
        super.onStop()
        if (isFinishing || inPip) return
        val st = vm.engine.state.value
        if (vm.settings.value.backgroundPlay && st.playing && !vm.isVault) {
            VideoBackgroundService.start(this, vm.current?.title.orEmpty())
        } else {
            vm.engine.pause()
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(pipReceiver) }
        if (isFinishing && !VideoBackgroundService.running) vm.engine.stopIfCurrent(vm.current?.uri)
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
