package com.moody.player.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.moody.player.design.components.Motion

/** First launch only: animated logo, three welcome pages, then permissions. */
@Composable
fun OnboardingFlow(onFinished: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    AnimatedContent(step, transitionSpec = { fadeIn(Motion.enter()) togetherWith fadeOut(Motion.base()) }, label = "onboarding") { s ->
        when (s) {
            0 -> LogoAnimation { step = 1 }
            1 -> WelcomePager { step = 2 }
            else -> PermissionsScreen(onContinue = onFinished)
        }
    }
}
