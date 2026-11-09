package com.moody.player.domain

/** Touch gestures over the video. Each one can be turned off on its own in Settings > Gestures. */
enum class PlayerGesture(val label: String, val detail: String) {
    DOUBLE_TAP_SEEK("Double-tap to seek", "Double-tap the left or right side to jump back or forward."),
    DOUBLE_TAP_PLAY("Double-tap to play or pause", "Double-tap the middle of the video."),
    SWIPE_SEEK("Swipe to seek", "Slide sideways across the video to scrub."),
    BRIGHTNESS("Brightness swipe", "Slide up or down on the left half."),
    VOLUME("Volume swipe", "Slide up or down on the right half."),
    HOLD_SPEED("Hold for 2× speed", "Press and hold while playing."),
    PINCH_ZOOM("Pinch to zoom", "Pinch out to fill the screen, in to fit."),
}

enum class TapZone { LEFT, CENTER, RIGHT }

/** Left and right thirds seek; the middle third plays or pauses. */
fun tapZone(x: Float, width: Float): TapZone = when {
    width <= 0f -> TapZone.CENTER
    x < width * 0.35f -> TapZone.LEFT
    x > width * 0.65f -> TapZone.RIGHT
    else -> TapZone.CENTER
}

val SEEK_STEPS = listOf(5, 10, 15, 30)

fun encodeGestures(set: Set<PlayerGesture>): Set<String> = set.mapTo(mutableSetOf()) { it.name }

fun decodeGestures(names: Set<String>): Set<PlayerGesture> =
    names.mapNotNullTo(mutableSetOf()) { n -> PlayerGesture.entries.firstOrNull { it.name == n } }
