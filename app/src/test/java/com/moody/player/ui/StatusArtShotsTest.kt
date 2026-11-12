package com.moody.player.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.moody.player.design.MoodyTheme
import com.moody.player.design.Radii
import com.moody.player.design.ThemeMode
import com.moody.player.design.illustrations.HealthClearArt
import com.moody.player.design.illustrations.HealthScanArt
import com.moody.player.design.illustrations.StatsEmptyArt
import com.moody.player.design.illustrations.StatsOffArt
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the status drawings to app/build/screenshots (status-art-*.png): the final frame in each theme,
 * on the page and on a card, plus a few moments of the running animation. Fails only if drawing crashes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h560dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StatusArtShotsTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val out = File("build/screenshots").apply { mkdirs() }

    private fun shot(name: String, mode: ThemeMode, ms: Long, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent { MoodyTheme(mode) { Box(Modifier.fillMaxSize().background(MoodyTheme.colors.bg)) { content() } } }
        compose.mainClock.advanceTimeBy(ms)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        compose.mainClock.advanceTimeBy(16)
        val v = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(android.graphics.Canvas(bmp))
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Composable private fun sheet(still: Boolean) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // On the page, at the sizes the screens use.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                HealthClearArt(still = still)
                StatsEmptyArt(still = still)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                HealthScanArt(still = still)
                StatsOffArt(still = still)
                HealthClearArt(size = 64.dp, still = still)
                StatsEmptyArt(size = 64.dp, still = still)
            }
            // On a card, small ones enlarged.
            Row(
                Modifier.fillMaxWidth().background(MoodyTheme.colors.surface, Radii.lg).border(1.dp, MoodyTheme.colors.line, Radii.lg).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                HealthScanArt(size = 140.dp, still = still)
                StatsOffArt(size = 140.dp, still = still)
            }
            Row(
                Modifier.fillMaxWidth().background(MoodyTheme.colors.surface, Radii.lg).border(1.dp, MoodyTheme.colors.line, Radii.lg).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                HealthClearArt(size = 96.dp, still = still)
                StatsEmptyArt(size = 96.dp, still = still)
                HealthScanArt(still = still)
            }
        }
    }

    @Test fun dark() = shot("status-art-dark", ThemeMode.DARK, 100) { sheet(still = true) }
    @Test fun light() = shot("status-art-light", ThemeMode.LIGHT, 100) { sheet(still = true) }
    @Test fun midnight() = shot("status-art-midnight", ThemeMode.MIDNIGHT, 100) { sheet(still = true) }

    // The running animation: early, midway, and after everything has landed (should match the still sheet,
    // except the scan shield, which keeps looping).
    @Test fun motionEarly() = shot("status-art-motion-0300", ThemeMode.DARK, 300) { sheet(still = false) }
    @Test fun motionMid() = shot("status-art-motion-0650", ThemeMode.DARK, 650) { sheet(still = false) }
    @Test fun motionLate() = shot("status-art-motion-0950", ThemeMode.LIGHT, 950) { sheet(still = false) }
    @Test fun motionEnd() = shot("status-art-motion-3000", ThemeMode.DARK, 3000) { sheet(still = false) }
}
