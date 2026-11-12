package com.moody.player.data.health

import android.content.Context
import android.net.Uri
import com.moody.player.data.media.MediaRepository
import com.moody.player.data.prefs.SettingsStore
import com.moody.player.di.AppScope
import com.moody.player.domain.Finding
import com.moody.player.domain.HealthReport
import com.moody.player.domain.MediaKind
import com.moody.player.domain.Severity
import com.moody.player.domain.diagnose
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** A file the scanner can check. */
data class HealthTarget(val uri: Uri, val name: String, val kind: MediaKind, val libraryMs: Long?)

data class ScanProgress(val done: Int, val total: Int, val current: String)

data class HealthState(
    val reports: Map<String, HealthReport> = emptyMap(),
    val progress: ScanProgress? = null,
    /** Files being checked one at a time (from a menu or Retry). */
    val checking: Set<String> = emptySet(),
    val lastScan: Long? = null,
    val loaded: Boolean = false,
)

/**
 * Media Health: checks videos and songs for damaged, incomplete or unsupported files. Read-only: it never
 * changes, moves or deletes a file. "Remove from library" only hides the file inside Moody (see
 * [SettingsStore] removedMedia). Vault videos and hidden folders are never scanned.
 */
@Singleton
class HealthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val media: MediaRepository,
    private val settings: SettingsStore,
    @AppScope private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(HealthState())
    val state: StateFlow<HealthState> = _state.asStateFlow()
    private val file = File(context.filesDir, "media_health.json")
    private val probeLock = Mutex()
    private var job: Job? = null

    init { scope.launch(Dispatchers.IO) { load() } }

    /** What a library scan covers: every song, and every video outside hidden folders (vault videos are never listed). */
    suspend fun libraryTargets(): List<HealthTarget> {
        if (media.videos.value == null || media.audio.value == null) media.rescan()
        val hidden = settings.settings.first().hiddenFolders
        val videos = media.videos.value.orEmpty().filter { it.relativePath !in hidden }
            .map { HealthTarget(it.uri, it.name, MediaKind.VIDEO, it.durationMs) }
        val songs = media.audio.value.orEmpty().map { HealthTarget(it.uri, it.fileName.ifBlank { it.title }, MediaKind.AUDIO, it.durationMs) }
        return videos + songs
    }

    /** Looks [uri] up in the library; null when it isn't listed (or is private). */
    suspend fun target(uri: Uri): HealthTarget? = libraryTargets().firstOrNull { it.uri == uri }

    fun scanLibrary() {
        val old = job
        if (old?.isActive == true && !old.isCancelled) return
        job = scope.launch(Dispatchers.IO) {
            old?.join() // a stopped scan may still be finishing its last file
            val targets = libraryTargets()
            _state.update { it.copy(progress = ScanProgress(0, targets.size, "")) }
            val found = mutableMapOf<String, HealthReport>()
            try {
                probeLock.withLock {
                    val prober = HealthProber(context)
                    try {
                        for ((i, t) in targets.withIndex()) {
                            if (!isActive) break
                            _state.update { it.copy(progress = ScanProgress(i, targets.size, t.name)) }
                            found[t.uri.toString()] = check(prober, t)
                            if (i % 10 == 9) _state.update { s -> s.copy(reports = s.reports + found) }
                        }
                    } finally {
                        prober.close()
                    }
                }
            } finally {
                val complete = found.size == targets.size
                _state.update { s ->
                    // A finished scan replaces the old results (files no longer listed drop out); a stopped one adds to them.
                    val reports = if (complete) found.toMap() else s.reports + found
                    s.copy(reports = reports, progress = null, lastScan = if (complete) System.currentTimeMillis() else s.lastScan)
                }
                withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) { save() }
            }
        }
    }

    fun cancel() { job?.cancel() }

    /** Checks one file now (a menu's "Check file health", or Retry). */
    fun scanOne(t: HealthTarget) {
        val key = t.uri.toString()
        if (key in _state.value.checking) return
        _state.update { it.copy(checking = it.checking + key) }
        scope.launch(Dispatchers.IO) {
            try {
                val report = probeLock.withLock {
                    val prober = HealthProber(context)
                    try { check(prober, t) } finally { prober.close() }
                }
                _state.update { it.copy(reports = it.reports + (key to report)) }
                save()
            } finally {
                _state.update { it.copy(checking = it.checking - key) }
            }
        }
    }

    /**
     * Hides [uri] from Moody's library. The file itself is not touched. Its report is kept (the screen hides
     * removed files), so Restore brings it back with its status; the next full scan drops it.
     */
    suspend fun removeFromLibrary(uri: String) {
        settings.update { it.copy(removedMedia = it.removedMedia + uri) }
    }

    suspend fun restoreToLibrary(uri: String) {
        settings.update { it.copy(removedMedia = it.removedMedia - uri) }
    }

    private fun check(prober: HealthProber, t: HealthTarget): HealthReport =
        try {
            diagnose(prober.probe(t.uri, t.kind, t.name, t.libraryMs), t.uri.toString())
        } catch (e: Throwable) {
            HealthReport(
                t.uri.toString(), t.name, t.kind,
                listOf(Finding("check_failed", Severity.WARNING, "Couldn't finish checking",
                    "Something went wrong while reading this file, so Moody couldn't judge it.", listOf("Retry the scan"))),
                System.currentTimeMillis(),
            )
        }

    // Results are kept in Moody's private storage so they survive a restart. Only names and findings, no media.
    private fun save() = runCatching {
        val s = _state.value
        val arr = JSONArray()
        s.reports.values.forEach { r ->
            arr.put(JSONObject().apply {
                put("uri", r.uri); put("name", r.name); put("kind", r.kind.name); put("at", r.checkedAt)
                put("findings", JSONArray().apply {
                    r.findings.forEach { f ->
                        put(JSONObject().apply {
                            put("code", f.code); put("severity", f.severity.name); put("title", f.title); put("detail", f.detail)
                            put("fixes", JSONArray(f.fixes))
                        })
                    }
                })
            })
        }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(JSONObject().apply { put("lastScan", s.lastScan ?: 0L); put("reports", arr) }.toString())
        tmp.renameTo(file)
    }

    private fun load() {
        val loaded = runCatching {
            if (!file.exists()) return@runCatching null
            val o = JSONObject(file.readText())
            val arr = o.getJSONArray("reports")
            val reports = (0 until arr.length()).associate { i ->
                val r = arr.getJSONObject(i)
                val fs = r.getJSONArray("findings")
                r.getString("uri") to HealthReport(
                    r.getString("uri"), r.getString("name"), MediaKind.valueOf(r.getString("kind")),
                    (0 until fs.length()).map { j ->
                        val f = fs.getJSONObject(j)
                        val fixes = f.getJSONArray("fixes")
                        Finding(f.getString("code"), Severity.valueOf(f.getString("severity")), f.getString("title"), f.getString("detail"), (0 until fixes.length()).map { fixes.getString(it) })
                    },
                    r.getLong("at"),
                )
            }
            reports to o.optLong("lastScan").takeIf { it > 0 }
        }.getOrNull()
        _state.update { s -> if (loaded == null) s.copy(loaded = true) else s.copy(reports = loaded.first + s.reports, lastScan = s.lastScan ?: loaded.second, loaded = true) }
    }
}
