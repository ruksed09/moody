package com.moody.player.ui.music

import android.app.RecoverableSecurityException
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.moody.player.data.media.AudioItem
import com.moody.player.data.media.CoverWrite
import com.moody.player.data.media.CoverWriter
import com.moody.player.design.components.LocalToaster
import com.moody.player.player.music.MusicController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Returns a function that writes a cover picked in Moody into the song's file, after Android asks the
 * user to allow the change. Remember it at screen level: the info dialog that saves the cover closes
 * before Android's answer comes back. Whatever happens, the cover still shows in Moody.
 */
@Composable
fun rememberCoverWriter(music: MusicController): (AudioItem, String) -> Unit {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Pair<AudioItem, String>?>(null) }
    val run = remember { arrayOfNulls<(AudioItem, String) -> Unit>(1) }
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        val p = pending
        pending = null
        if (p == null) return@rememberLauncherForActivityResult
        if (r.resultCode == android.app.Activity.RESULT_OK) run[0]?.invoke(p.first, p.second)
        else toaster.show("File not changed. The cover stays in Moody")
    }
    run[0] = { song, path ->
        scope.launch {
            toaster.show("Saving the cover into the file")
            var resume: MusicController.Resume? = null
            val result = try {
                withContext(Dispatchers.IO) {
                    CoverWriter.embed(
                        context, song, path,
                        releasePlayer = { resume = withContext(Dispatchers.Main) { music.releaseIfCurrent(song.uri) } },
                        resumePlayer = { resume?.let { r -> withContext(Dispatchers.Main) { music.resume(r) } } },
                    )
                }
            } catch (e: RecoverableSecurityException) {
                // Android 10: ask once for this file, then try again.
                pending = song to path
                consent.launch(IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build())
                return@launch
            } catch (e: SecurityException) {
                CoverWrite.Failed("Moody isn't allowed to change this file")
            } catch (e: Exception) {
                CoverWrite.Failed("Something went wrong")
            }
            toaster.show(
                when (result) {
                    CoverWrite.Done -> "Cover saved into the file"
                    CoverWrite.Unsupported -> "This file type can't hold a cover. It stays in Moody"
                    is CoverWrite.Failed -> "${result.why}. The cover stays in Moody"
                },
            )
        }
    }
    return { song, path ->
        if (Build.VERSION.SDK_INT >= 30) {
            pending = song to path
            consent.launch(IntentSenderRequest.Builder(MediaStore.createWriteRequest(context.contentResolver, listOf(song.uri)).intentSender).build())
        } else run[0]?.invoke(song, path)
    }
}
