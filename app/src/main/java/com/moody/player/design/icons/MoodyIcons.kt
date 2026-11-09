package com.moody.player.design.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * One icon set, one stroke (1.6, round caps and joins) on a 24 grid, in the style of the Kurogane mockup.
 * Geometry follows Lucide (ISC licence) where an equivalent exists. Tint comes from the text role at the call site.
 */
private fun icon(name: String, vararg strokes: String, fills: List<String> = emptyList(), width: Float = 1.6f): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        strokes.forEach {
            addPath(
                pathData = addPathNodes(it), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            )
        }
        fills.forEach { addPath(pathData = addPathNodes(it), fill = SolidColor(Color.Black)) }
    }.build()

private const val CIRCLE10 = "M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20z"
private const val SPEAKER =
    "M11 4.7a.7.7 0 0 0-1.2-.5L6.4 7.6a1.4 1.4 0 0 1-1 .4H3a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h2.4a1.4 1.4 0 0 1 1 .4l3.4 3.4a.7.7 0 0 0 1.2-.5z"
private const val HEART =
    "M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7z"
private const val GEAR =
    "M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z"
private const val LOCK_BODY = "M5 11h14a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2z"
private const val SHIELD = "M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"
private const val REPEAT_A = "M17 2l4 4-4 4"
private const val REPEAT_B = "M3 11v-1a4 4 0 0 1 4-4h14"
private const val REPEAT_C = "M7 22l-4-4 4-4"
private const val REPEAT_D = "M21 13v1a4 4 0 0 1-4 4H3"

object MoodyIcons {
    val Play by lazy { icon("play", fills = listOf("M7 5.2v13.6a1 1 0 0 0 1.52.86l10.7-6.8a1 1 0 0 0 0-1.72L8.52 4.34A1 1 0 0 0 7 5.2z")) }
    val Pause by lazy {
        icon("pause", fills = listOf(
            "M7 4.5h2a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1v-13a1 1 0 0 1 1-1z",
            "M15 4.5h2a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1h-2a1 1 0 0 1-1-1v-13a1 1 0 0 1 1-1z",
        ))
    }
    val Replay by lazy { icon("replay", "M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8", "M3 3v5h5") }
    val SeekBack by lazy { icon("seek-back", "M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8", "M3 3v5h5") }
    val SeekForward by lazy { icon("seek-forward", "M21 12a9 9 0 1 1-9-9c2.52 0 4.93 1 6.74 2.74L21 8", "M21 3v5h-5") }
    val Prev by lazy { icon("prev", "M5 19V5", fills = listOf("M19 18.2V5.8a1 1 0 0 0-1.6-.8l-8.2 6.2a1 1 0 0 0 0 1.6l8.2 6.2a1 1 0 0 0 1.6-.8z")) }
    val Next by lazy { icon("next", "M19 5v14", fills = listOf("M5 5.8v12.4a1 1 0 0 0 1.6.8l8.2-6.2a1 1 0 0 0 0-1.6L6.6 5a1 1 0 0 0-1.6.8z")) }
    val VolumeHigh by lazy { icon("vol-high", SPEAKER, "M16 9a5 5 0 0 1 0 6", "M19.4 18.4a9 9 0 0 0 0-12.8") }
    val VolumeLow by lazy { icon("vol-low", SPEAKER, "M16 9a5 5 0 0 1 0 6") }
    val VolumeMute by lazy { icon("vol-mute", SPEAKER, "M22 9l-6 6M16 9l6 6") }
    val Brightness by lazy {
        icon("brightness", "M12 8a4 4 0 1 0 0 8a4 4 0 1 0 0-8z",
            "M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M6.34 17.66l-1.41 1.41M19.07 4.93l-1.41 1.41")
    }
    val Subtitles by lazy { icon("subtitles", "M5 5h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z", "M7 15h4M15 15h2M7 11h2M13 11h4") }
    val Audio by lazy { icon("audio", "M3 14h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-7a9 9 0 0 1 18 0v7a2 2 0 0 1-2 2h-1a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2h3") }
    val Settings by lazy { icon("settings", GEAR, "M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6z") }
    val Pip by lazy {
        icon("pip", "M4.5 4.5h15a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2h-15a2 2 0 0 1-2-2v-11a2 2 0 0 1 2-2z",
            fills = listOf("M13 11.5h5a1 1 0 0 1 1 1v3a1 1 0 0 1-1 1h-5a1 1 0 0 1-1-1v-3a1 1 0 0 1 1-1z"))
    }
    val Lock by lazy { icon("lock", "M7 11V7a5 5 0 0 1 10 0v4", LOCK_BODY) }
    val Unlock by lazy { icon("unlock", "M7 11V7a5 5 0 0 1 9.9-1", LOCK_BODY) }
    val Rotate by lazy { icon("rotate", "M21 12a9 9 0 1 1-2.64-6.36L21 8", "M21 3v5h-5") }
    val Aspect by lazy { icon("aspect", "M5 6h14a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z", "M7 12V9h3M17 12v3h-3") }
    val Speed by lazy { icon("speed", "M12 14l4-4", "M3.34 19a10 10 0 1 1 17.32 0") }
    val Equalizer by lazy { icon("eq", "M4 21v-7M4 10V3M12 21v-9M12 8V3M20 21v-5M20 12V3M2 14h4M10 8h4M18 16h4") }
    val Timer by lazy { icon("timer", "M10 2h4", "M12 14l3-3", "M12 6a8 8 0 1 0 0 16a8 8 0 1 0 0-16z") }
    val Repeat by lazy { icon("repeat", REPEAT_A, REPEAT_B, REPEAT_C, REPEAT_D) }
    val RepeatOne by lazy { icon("repeat-one", REPEAT_A, REPEAT_B, REPEAT_C, REPEAT_D, "M11 10h1v4") }
    val Shuffle by lazy {
        icon("shuffle", "M2 18h1.4c1.3 0 2.5-.6 3.3-1.7l6.1-8.6c.7-1.1 2-1.7 3.3-1.7H22", "M18 2l4 4-4 4",
            "M2 6h1.9c1.5 0 2.9.9 3.6 2.2", "M22 18h-5.9c-1.3 0-2.6-.7-3.3-1.8l-.5-.8", "M18 14l4 4-4 4")
    }
    val Heart by lazy { icon("heart", HEART) }
    val HeartFilled by lazy { icon("heart-filled", HEART, fills = listOf(HEART)) }
    val Folder by lazy { icon("folder", "M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2z") }
    val VideoTab by lazy { icon("video-tab", "M5 4h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z", "M10 9.2v5.6l4.8-2.8z") }
    val PlaySmall by lazy { icon("play-small", "M8 6.5v11l8.5-5.5z") }
    val Image by lazy { icon("image", "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z", "M9 7.5a1.5 1.5 0 1 0 0 3a1.5 1.5 0 1 0 0-3z", "M21 15l-5-5L5 21") }
    val Hand by lazy {
        icon("hand", "M18 11V6a2 2 0 0 0-4 0v1", "M14 10V4a2 2 0 0 0-4 0v2", "M10 10.5V6a2 2 0 0 0-4 0v8",
            "M18 8a2 2 0 1 1 4 0v6a8 8 0 0 1-8 8h-2c-2.8 0-4.5-.86-5.99-2.34l-3.6-3.6a2 2 0 0 1 2.83-2.82L7 15")
    }
    val Fullscreen by lazy { icon("fullscreen", "M8 3H5a2 2 0 0 0-2 2v3M21 8V5a2 2 0 0 0-2-2h-3M3 16v3a2 2 0 0 0 2 2h3M16 21h3a2 2 0 0 0 2-2v-3") }
    val FitScreen by lazy { icon("fit-screen", "M8 3v3a2 2 0 0 1-2 2H3M21 8h-3a2 2 0 0 1-2-2V3M3 16h3a2 2 0 0 1 2 2v3M16 21v-3a2 2 0 0 1 2-2h3") }
    val Film by lazy { icon("film", "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z", "M7 3v18M17 3v18M3 7.5h4M3 12h18M3 16.5h4M17 7.5h4M17 16.5h4") }
    val Music by lazy { icon("music", "M9 18V5l12-2v13", "M6 15a3 3 0 1 0 0 6a3 3 0 1 0 0-6z", "M18 13a3 3 0 1 0 0 6a3 3 0 1 0 0-6z") }
    val Search by lazy { icon("search", "M11 3a8 8 0 1 0 0 16a8 8 0 1 0 0-16z", "M21 21l-4.3-4.3") }
    val Sort by lazy { icon("sort", "M3 16l4 4 4-4M7 20V4", "M21 8l-4-4-4 4M17 4v16") }
    val Grid by lazy {
        icon("grid",
            "M4 3h5a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z",
            "M15 3h5a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1h-5a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z",
            "M15 14h5a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1h-5a1 1 0 0 1-1-1v-5a1 1 0 0 1 1-1z",
            "M4 14h5a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-5a1 1 0 0 1 1-1z")
    }
    val ListView by lazy { icon("list", "M8 6h13M8 12h13M8 18h13", "M3 6h.01M3 12h.01M3 18h.01") }
    val More by lazy { icon("more", "M12 5h.01M12 12h.01M12 19h.01", width = 2.6f) }
    val Share by lazy { icon("share", "M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8", "M16 6l-4-4-4 4", "M12 2v13") }
    val Rename by lazy { icon("rename", "M17 3a2.85 2.85 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5z", "M15 5l4 4") }
    val Delete by lazy { icon("delete", "M3 6h18", "M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6", "M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2") }
    val Info by lazy { icon("info", CIRCLE10, "M12 16v-4M12 8h.01") }
    val Eye by lazy { icon("eye", "M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7z", "M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6z") }
    val EyeOff by lazy {
        icon("eye-off", "M9.88 9.88a3 3 0 1 0 4.24 4.24", "M10.73 5.08A10.43 10.43 0 0 1 12 5c7 0 10 7 10 7a13.16 13.16 0 0 1-1.67 2.68",
            "M6.61 6.61A13.53 13.53 0 0 0 2 12s3 7 10 7a9.74 9.74 0 0 0 5.39-1.61", "M2 2l20 20")
    }
    val Vault by lazy { icon("vault", SHIELD, "M12 12.5v2.5", "M12 8.5a2 2 0 1 0 0 4a2 2 0 1 0 0-4z") }
    val Link by lazy {
        icon("link", "M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71", "M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71")
    }
    val Check by lazy { icon("check", "M20 6L9 17l-5-5") }
    val Close by lazy { icon("close", "M18 6L6 18M6 6l12 12") }
    val ChevronRight by lazy { icon("chev-right", "M9 18l6-6-6-6") }
    val ChevronDown by lazy { icon("chev-down", "M6 9l6 6 6-6") }
    val ChevronUp by lazy { icon("chev-up", "M18 15l-6-6-6 6") }
    val ArrowBack by lazy { icon("arrow-back", "M19 12H5M12 19l-7-7 7-7") }
    val Plus by lazy { icon("plus", "M12 5v14M5 12h14") }
    val Download by lazy { icon("download", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4", "M7 10l5 5 5-5", "M12 15V3") }
    val Queue by lazy { icon("queue", "M21 15V6", "M18.5 13a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0-5z", "M12 12H3M16 6H3M12 18H3") }
    val Album by lazy { icon("album", CIRCLE10, "M12 10a2 2 0 1 0 0 4a2 2 0 1 0 0-4z") }
    val Artist by lazy { icon("artist", "M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3z", "M19 10v2a7 7 0 0 1-14 0v-2", "M12 19v3") }
    val Playlist by lazy {
        icon("playlist", "M3 6h12M3 12h12M3 18h7", fills = listOf("M16 14.2v5.6a.6.6 0 0 0 .9.5l4.5-2.8a.6.6 0 0 0 0-1l-4.5-2.8a.6.6 0 0 0-.9.5z"))
    }
    val AbRepeat by lazy { icon("ab", "M3 18l3.5-12L10 18M4.2 14h4.6", "M14 6h3.5a3 3 0 0 1 0 6H14zM14 12h4a3 3 0 0 1 0 6h-4z") }
    val Decoder by lazy {
        icon("decoder", "M6 4h12a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z", "M9 9h6v6H9z",
            "M9 1v3M15 1v3M9 20v3M15 20v3M20 9h3M20 14h3M1 9h3M1 14h3")
    }
    val Camera by lazy { icon("camera", "M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3z", "M12 10a3 3 0 1 0 0 6a3 3 0 1 0 0-6z") }
    val Cc by lazy { icon("cc", "M5 5h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z", "M10.5 10.2a2 2 0 1 0 0 3.6", "M16.5 10.2a2 2 0 1 0 0 3.6") }
    val Moon by lazy { icon("moon", "M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9z") }
    val Refresh by lazy {
        icon("refresh", "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8", "M21 3v5h-5",
            "M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16", "M8 16H3v5")
    }
    val Bell by lazy { icon("bell", "M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9", "M10.3 21a1.94 1.94 0 0 0 3.4 0") }
    val Backspace by lazy { icon("backspace", "M21 4H8l-7 8 7 8h13a2 2 0 0 0 2-2V6a2 2 0 0 0-2-2z", "M18 9l-6 6M12 9l6 6") }
    val DragHandle by lazy { icon("drag", "M9 6h.01M9 12h.01M9 18h.01M15 6h.01M15 12h.01M15 18h.01", width = 2.6f) }
    val Palette by lazy { icon("palette", CIRCLE10, fills = listOf("M12 2a10 10 0 0 1 0 20z")) }
    val Shield by lazy { icon("shield", SHIELD) }
    val Key by lazy { icon("key", "M7.5 12a4.5 4.5 0 1 0 0 9a4.5 4.5 0 1 0 0-9z", "M10.7 13.3L21 3M17 7l3 3M14 10l2 2") }
    val Storage by lazy { icon("storage", "M7 2h10a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2z", "M11 18h2") }
    val Layers by lazy { icon("layers", "M12 2l9 5-9 5-9-5z", "M3 12l9 5 9-5", "M3 17l9 5 9-5") }
}
