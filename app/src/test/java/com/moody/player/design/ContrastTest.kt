package com.moody.player.design

import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastTest {
    private val modes = listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.MIDNIGHT)

    @Test fun primaryText_meetsAAA_onBackground() {
        for (m in modes) {
            val c = moodyColors(m, systemDark = false)
            val r = contrastRatio(c.text, c.bg)
            assertTrue("$m text/bg = $r", r >= 7.0)
        }
    }

    @Test fun secondaryText_meetsAA_onBackgroundAndSurface() {
        for (m in modes) {
            val c = moodyColors(m, systemDark = false)
            assertTrue("$m text2/bg", contrastRatio(c.text2, c.bg) >= 4.5)
            assertTrue("$m text2/surface", contrastRatio(c.text2, c.surface) >= 4.5)
        }
    }

    @Test fun accentButtonLabel_meetsAA() {
        for (m in modes) {
            val c = moodyColors(m, systemDark = false)
            val r = contrastRatio(c.accentInk, c.accent)
            assertTrue("$m accentInk/accent = $r", r >= 4.5)
        }
    }

    @Test fun system_followsSystemSetting() {
        assertTrue(moodyColors(ThemeMode.SYSTEM, systemDark = true) == moodyColors(ThemeMode.DARK, false))
        assertTrue(moodyColors(ThemeMode.SYSTEM, systemDark = false) == moodyColors(ThemeMode.LIGHT, false))
    }
}
