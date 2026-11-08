package com.moody.player.data.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.moody.player.BuildConfig
import com.moody.player.R
import com.moody.player.data.prefs.SettingsStore
import com.moody.player.di.AppScope
import com.moody.player.domain.ReleaseInfo
import com.moody.player.domain.compareVersions
import com.moody.player.domain.parseRelease
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

private const val SIX_HOURS = 6 * 60 * 60 * 1000L
private const val SNOOZE = 24 * 60 * 60 * 1000L
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

    /** When [force] is false the check is skipped if one ran in the last 6 h, and a snoozed version stays quiet. */
    suspend fun check(force: Boolean) {
        if (_state.value is UpdateState.Downloading || _state.value is UpdateState.Ready) return
        val s = settings.settings.first()
        if (!force && System.currentTimeMillis() - s.lastUpdateCheck < SIX_HOURS) return
        _state.value = UpdateState.Checking
        val result = withContext(Dispatchers.IO) {
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
                            val info = parseRelease(r.body?.string().orEmpty())
                            when {
                                info == null -> UpdateState.UpToDate
                                compareVersions(info.version, BuildConfig.VERSION_NAME) <= 0 -> UpdateState.UpToDate
                                else -> UpdateState.Available(info)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                UpdateState.Failed("Couldn't reach GitHub")
            }
        }
        if (result !is UpdateState.Failed) settings.update { it.copy(lastUpdateCheck = System.currentTimeMillis()) }
        val snoozed = result is UpdateState.Available && !force && !result.info.required &&
            s.snoozedVersion == result.info.version && System.currentTimeMillis() < s.snoozedUntil
        _state.value = if (snoozed) UpdateState.Idle else result
    }

    fun later() {
        val s = _state.value as? UpdateState.Available ?: return
        scope.launch { settings.update { it.copy(snoozedVersion = s.info.version, snoozedUntil = System.currentTimeMillis() + SNOOZE) } }
        _state.value = UpdateState.Idle
    }

    fun dismissResult() {
        if (_state.value is UpdateState.UpToDate || _state.value is UpdateState.Failed) _state.value = UpdateState.Idle
    }

    fun startDownload() {
        val info = when (val s = _state.value) {
            is UpdateState.Available -> s.info
            is UpdateState.Failed -> return
            else -> return
        }
        download?.cancel()
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
                cancelNotification()
                _state.value = UpdateState.Ready(info, out)
            } catch (e: Exception) {
                out.delete()
                cancelNotification()
                _state.value = if (e is kotlinx.coroutines.CancellationException) UpdateState.Available(info) else UpdateState.Failed(e.message ?: "Download failed")
                if (e is kotlinx.coroutines.CancellationException) throw e
            }
        }
    }

    fun cancelDownload() {
        download?.cancel()
        download = null
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
