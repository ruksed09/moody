package com.moody.player.data.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.moody.player.BuildConfig
import com.moody.player.R
import com.moody.player.data.prefs.SettingsStore
import com.moody.player.di.AppScope
import com.moody.player.domain.ReleaseInfo
import com.moody.player.domain.parseRelease
import com.moody.player.domain.updatePrompt
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Failed(val reason: String) : UpdateState
    data class Available(val info: ReleaseInfo) : UpdateState
    data class Downloading(val info: ReleaseInfo, val done: Long, val total: Long) : UpdateState
    data class Ready(val info: ReleaseInfo, val file: File) : UpdateState
}

private const val CHANNEL = "updates"
private const val NOTIFY_ID = 77

/**
 * Self-hosted updates: asks GitHub for the latest release of [BuildConfig.UPDATE_REPO], downloads its APK,
 * and hands it to Android's installer. The new APK must be signed with the same key as the installed one.
 */
@Singleton
class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsStore,
    @AppScope private val scope: CoroutineScope,
) {
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private var download: Job? = null

    /** The newest release known to be newer than this install. It drives the header badge and outlives a closed popup. */
    private val _available = MutableStateFlow<ReleaseInfo?>(null)
    val available: StateFlow<ReleaseInfo?> = _available.asStateFlow()

    /** Whether the update popup is on screen. */
    private val _promptOpen = MutableStateFlow(false)
    val promptOpen: StateFlow<Boolean> = _promptOpen.asStateFlow()

    private val checking = Mutex()
    /** True once this visit to the app has been checked; cleared when the whole app goes to the background. */
    @Volatile private var checkedThisVisit = false

    init {
        // The process lifecycle, not an activity's: rotating or coming back from the video player is not "opening the app".
        Handler(Looper.getMainLooper()).post {
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) { checkedThisVisit = false }
            })
        }
    }

    /** The automatic check: once per app open (a cold start, or a return from the background). Safe to call on every start. */
    fun checkOnOpen() {
        if (checkedThisVisit) return
        checkedThisVisit = true
        scope.launch { check(force = false) }
    }

    /**
     * Asks GitHub for the latest release. An automatic check ([force] false) is quiet: it shows no progress, a failure
     * changes nothing, and the popup stays closed for a version the user chose "Don't show again" for.
     * A manual check ([force] true) always opens the popup when there is an update.
     */
    suspend fun check(force: Boolean) {
        if (reopenIfBusy(force)) return
        if (force) _state.value = UpdateState.Checking
        checking.withLock {
            if (reopenIfBusy(force)) return
            if (force) _state.value = UpdateState.Checking
            val result = fetchLatest()
            if (busy()) return // a download began while GitHub was answering
            val info = (result as? UpdateState.Available)?.info
            val prompt = updatePrompt(BuildConfig.VERSION_NAME, info, settings.settings.first().dismissedUpdateVersion, manual = force)
            when {
                result is UpdateState.Failed -> if (force) _state.value = result
                info != null && prompt.showBadge -> {
                    // A popup that is already open for this version stays open.
                    val alreadyOpen = _promptOpen.value && _available.value?.version == info.version
                    _available.value = info
                    _state.value = UpdateState.Available(info)
                    _promptOpen.value = prompt.showModal || alreadyOpen
                }
                else -> {
                    _available.value = null
                    _promptOpen.value = false
                    _state.value = UpdateState.UpToDate
                }
            }
            if (result !is UpdateState.Failed) settings.update { it.copy(lastUpdateCheck = System.currentTimeMillis()) }
        }
    }

    private fun busy() = _state.value is UpdateState.Downloading || _state.value is UpdateState.Ready

    /**
     * While an update downloads or waits to be installed there is nothing to ask GitHub. The check then only brings
     * its popup back, by the same rule as a fresh one. True when that was the case.
     */
    private suspend fun reopenIfBusy(manual: Boolean): Boolean {
        val info = when (val s = _state.value) {
            is UpdateState.Downloading -> s.info
            is UpdateState.Ready -> s.info
            else -> return false
        }
        if (updatePrompt(BuildConfig.VERSION_NAME, info, settings.settings.first().dismissedUpdateVersion, manual).showModal) _promptOpen.value = true
        return true
    }

    private suspend fun fetchLatest(): UpdateState = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Moody-Android/${BuildConfig.VERSION_NAME}")
                .build()
            http.newCall(req).execute().use { r ->
                when {
                    r.code == 404 -> UpdateState.UpToDate // no releases published yet
                    !r.isSuccessful -> UpdateState.Failed("GitHub answered ${r.code}")
                    else -> {
                        // Whether it is newer than this install is decided by updatePrompt, in check().
                        parseRelease(r.body?.string().orEmpty())?.let { UpdateState.Available(it) } ?: UpdateState.UpToDate
                    }
                }
            }
        } catch (e: Exception) {
            UpdateState.Failed("Couldn't reach GitHub")
        }
    }

    /** "Don't show again": the popup stays closed for this version from now on. The header badge still leads back to it. */
    fun dontShowAgain() {
        val info = (_state.value as? UpdateState.Available)?.info ?: return
        if (!canDismiss(info)) return
        scope.launch { settings.update { it.copy(dismissedUpdateVersion = info.version) } }
        _promptOpen.value = false
    }

    /** Closes the popup for now (Back). Nothing is remembered, so the next app open shows it again. */
    fun closePrompt() {
        val info = when (val s = _state.value) {
            is UpdateState.Available -> s.info
            is UpdateState.Ready -> s.info
            else -> return
        }
        if (canDismiss(info)) _promptOpen.value = false
    }

    /** False for a required release: its popup has no "Don't show again" and Back does not close it. */
    fun canDismiss(info: ReleaseInfo) = updatePrompt(BuildConfig.VERSION_NAME, info, dismissedVersion = "", manual = true).canDismiss

    /** The header badge: opens the popup for the known update, whatever was dismissed before. */
    fun openPrompt() {
        val info = _available.value ?: return
        when (_state.value) {
            UpdateState.Checking -> return // the manual check in flight opens it itself
            UpdateState.Idle, UpdateState.UpToDate, is UpdateState.Failed -> _state.value = UpdateState.Available(info)
            else -> Unit
        }
        _promptOpen.value = true
    }

    /** Clears a finished result. After a failed download the known update comes back, so it can be tried again. */
    fun dismissResult() {
        if (_state.value is UpdateState.UpToDate || _state.value is UpdateState.Failed) {
            _state.value = _available.value?.let { UpdateState.Available(it) } ?: UpdateState.Idle
        }
    }

    fun startDownload() {
        val info = when (val s = _state.value) {
            is UpdateState.Available -> s.info
            is UpdateState.Failed -> return
            else -> return
        }
        _promptOpen.value = true
        download?.cancel()
        // Pending: the button turns at once, before the server has answered.
        _state.value = UpdateState.Downloading(info, 0, info.apkSize)
        download = scope.launch(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
            val out = File(dir, "moody-${info.version}.apk")
            try {
                val req = Request.Builder().url(info.apkUrl).header("User-Agent", "Moody-Android/${BuildConfig.VERSION_NAME}").build()
                http.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) error("Download failed (${r.code})")
                    val body = r.body ?: error("Empty download")
                    val total = body.contentLength().takeIf { it > 0 } ?: info.apkSize
                    var done = 0L
                    var lastNotify = 0L
                    body.byteStream().use { input ->
                        out.outputStream().use { o ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                coroutineContext.ensureActive()
                                val n = input.read(buf)
                                if (n < 0) break
                                o.write(buf, 0, n)
                                done += n
                                _state.value = UpdateState.Downloading(info, done, total)
                                if (System.currentTimeMillis() - lastNotify > 500) { notifyProgress(info, done, total); lastNotify = System.currentTimeMillis() }
                            }
                        }
                    }
                    if (info.apkSize > 0 && out.length() != info.apkSize) error("The download came out a different size")
                }
                coroutineContext.ensureActive() // cancelled at the last moment: stay cancelled
                cancelNotification()
                _state.value = UpdateState.Ready(info, out)
            } catch (e: Exception) {
                // A cancelled job leaves state and files alone: cancelDownload already reset the state, and a new
                // download may be writing the same file by now (the next download clears the folder anyway).
                if (e is kotlinx.coroutines.CancellationException || !isActive) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    return@launch
                }
                out.delete()
                cancelNotification()
                _state.value = UpdateState.Failed(e.message ?: "Download failed")
            }
        }
    }

    fun cancelDownload() {
        download?.cancel()
        download = null
        // Back to the choice straight away, even while the request is still connecting.
        val s = _state.value
        if (s is UpdateState.Downloading) { cancelNotification(); _state.value = UpdateState.Available(s.info) }
    }

    /** True when Android still needs the user to allow installs from Moody (Settings > Install unknown apps). */
    fun needsInstallPermission(): Boolean = Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun installIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun canNotify() = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun notifyProgress(info: ReleaseInfo, done: Long, total: Long) {
        if (!canNotify()) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) nm.createNotificationChannel(NotificationChannel(CHANNEL, "Updates", NotificationManager.IMPORTANCE_LOW))
        val pct = if (total > 0) (done * 100 / total).toInt() else 0
        nm.notify(NOTIFY_ID, NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle("Downloading Moody ${info.version}")
            .setProgress(100, pct, total <= 0)
            .setOnlyAlertOnce(true).setOngoing(true).setSilent(true)
            .build())
    }

    private fun cancelNotification() = context.getSystemService(NotificationManager::class.java).cancel(NOTIFY_ID)
}
