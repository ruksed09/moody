package com.moody.player.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.moody.player.design.MoodyTheme
import com.moody.player.design.components.Motion

/**
 * A top bar that slides away while the list scrolls down and comes back on any upward scroll
 * (Material "enter always"). When a scroll stops half-way the bar settles fully shown or fully hidden.
 * Scrolling also closes the keyboard.
 */
@Stable
class CollapsingBarState(val heightPx: Float, initialOffset: Float, private val keyboard: SoftwareKeyboardController?, private val focus: FocusManager) {
    /** 0 = fully shown, -heightPx = fully hidden. */
    var offset by mutableFloatStateOf(initialOffset)
        private set
    /** True while something (search, selection) needs the bar on screen. */
    var pinned = false

    val fraction: Float get() = if (heightPx == 0f) 0f else -offset / heightPx

    fun show() { offset = 0f }

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y != 0f && source == NestedScrollSource.UserInput) { keyboard?.hide(); focus.clearFocus() }
            offset = if (pinned) 0f else (offset + available.y).coerceIn(-heightPx, 0f)
            return Offset.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (offset != 0f && offset != -heightPx) {
                val target = if (offset < -heightPx / 2) -heightPx else 0f
                animate(offset, target, animationSpec = tween(Motion.BASE, easing = Motion.Ease)) { v, _ -> offset = v }
            }
            return Velocity.Zero
        }
    }
}

@Composable
fun rememberCollapsingBar(height: Dp = 64.dp, key: Any? = Unit): CollapsingBarState {
    val px = with(LocalDensity.current) { height.toPx() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    var saved by rememberSaveable(key) { mutableFloatStateOf(0f) }
    val state = remember(px, key) { CollapsingBarState(px, saved, keyboard, focus) }
    saved = state.offset
    return state
}

/** Draws the bar: opaque page colour so content scrolls beneath it, a hairline once content is under it. */
@Composable
fun CollapsingTopBar(state: CollapsingBarState, contentUnder: Boolean, height: Dp = 64.dp, content: @Composable () -> Unit) {
    val c = MoodyTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = state.offset }
            .background(c.bg)
            .statusBarsPadding(),
    ) {
        Box(Modifier.fillMaxWidth().height(height).graphicsLayer { alpha = 1f - state.fraction }) { content() }
        Box(Modifier.fillMaxWidth().height(1.dp).background(if (contentUnder) c.line else c.bg))
    }
}

/** Space the floating navigation (and mini player) take at the bottom; lists pad by this so nothing hides under it. */
val LocalBottomInset = compositionLocalOf { 0.dp }
