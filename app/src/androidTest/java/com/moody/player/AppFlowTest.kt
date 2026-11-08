package com.moody.player

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.moody.player.data.prefs.SettingsStore
import com.moody.player.design.ThemeMode
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Launches the real app on the device and walks the main screens. */
@RunWith(AndroidJUnit4::class)
class AppFlowTest {
    init {
        // Start as a returning user with media access, so the library opens directly.
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking { SettingsStore(ctx).update { it.copy(onboarded = true, themeMode = ThemeMode.DARK, lastUpdateCheck = System.currentTimeMillis()) } }
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        listOf("android.permission.READ_MEDIA_VIDEO", "android.permission.READ_MEDIA_AUDIO", "android.permission.POST_NOTIFICATIONS")
            .forEach { runCatching { ui.executeShellCommand("pm grant ${ctx.packageName} $it").close() } }
    }

    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun waitForText(t: String) = compose.waitUntil(15_000) { compose.onAllNodesWithText(t).fetchSemanticsNodes().isNotEmpty() }

    @Test fun tabsSwitchBetweenVideoAndMusic() {
        waitForText("Videos")
        compose.onNodeWithText("Videos").assertIsDisplayed()
        compose.onNodeWithText("Music").performClick()
        waitForText("Songs")
        compose.onNodeWithText("Songs").assertIsDisplayed()
        compose.onNodeWithText("Video").performClick()
        waitForText("Videos")
    }

    @Test fun settingsOpenAndThemeSwitches() {
        waitForText("Videos")
        compose.onNodeWithContentDescription("Settings").performClick()
        waitForText("Appearance")
        compose.onNodeWithText("Light").performClick()
        compose.onNodeWithText("System font").performClick()
        compose.onNodeWithText("App font").performClick()
        compose.onNodeWithText("Dark").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        waitForText("Videos")
    }

    @Test fun vaultAsksForSetupFirst() {
        waitForText("Videos")
        compose.onNodeWithText("Vault").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Set a vault PIN").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("Enter your PIN").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
