package com.moody.player.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.moody.player.design.MoodyTheme
import com.moody.player.design.Radii
import com.moody.player.design.components.Motion
import com.moody.player.design.components.PrimaryButton
import com.moody.player.design.components.QuietButton
import com.moody.player.design.icons.MoodyIcons
import com.moody.player.design.illustrations.WelcomeArt
import kotlinx.coroutines.launch

private data class Page(val eyebrow: String?, val title: String, val body: String)

private val PAGES = listOf(
    Page("Welcome to Moody", "Plays whatever you throw at it", "MKV, AVI, HEVC, DTS sound and styled subtitles. Your own videos and music, played right on your phone"),
    Page(null, "A vault for the private ones", "Hide videos behind a PIN. They leave your gallery and other apps until you bring them back"),
    Page(null, "Music that follows you", "Albums, playlists and a queue, with controls on your lock screen and headphones"),
)

@Composable
fun WelcomePager(onDone: () -> Unit) {
    val c = MoodyTheme.colors
    val pager = rememberPagerState { PAGES.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == PAGES.lastIndex

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            if (!last) QuietButton("Skip", onDone)
        }
        HorizontalPager(pager, Modifier.weight(1f)) { i ->
            val p = PAGES[i]
            Column(Modifier.fillMaxSize().padding(horizontal = 32.dp), verticalArrangement = Arrangement.Center) {
                WelcomeArt(i, Modifier.widthIn(max = 420.dp))
                Spacer(Modifier.height(32.dp))
                p.eyebrow?.let {
                    Text(it, style = MoodyTheme.type.section, color = c.accent)
                    Spacer(Modifier.height(8.dp))
                }
                Text(p.title, style = MoodyTheme.type.hero, color = c.text)
                Spacer(Modifier.height(12.dp))
                Text(p.body, style = MoodyTheme.type.body, color = c.text2, modifier = Modifier.widthIn(max = 420.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.semantics { contentDescription = "Page ${pager.currentPage + 1} of ${PAGES.size}" }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(PAGES.size) { i ->
                    val on = i == pager.currentPage
                    val w by animateDpAsState(if (on) 24.dp else 8.dp, Motion.base(), label = "dot-w")
                    val col by animateColorAsState(if (on) c.accent else c.line3, Motion.base(), label = "dot-c")
                    Box(Modifier.width(w).height(8.dp).clip(Radii.pill).background(col))
                }
            }
            Spacer(Modifier.weight(1f))
            PrimaryButton(if (last) "Get started" else "Next", {
                if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
            })
        }
    }
}
