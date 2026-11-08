package com.moody.player.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateTest {
    @Test fun compare_numericNotLexical() = assertTrue(compareVersions("v1.10.0", "1.9.3") > 0)
    @Test fun compare_equal() = assertEquals(0, compareVersions("1.0.0", "v1.0.0"))
    @Test fun compare_preReleaseIsOlder() = assertTrue(compareVersions("1.0.0-beta", "1.0.0") < 0)
    @Test fun compare_missingPartsAreZero() = assertEquals(0, compareVersions("1.2", "1.2.0"))
    @Test fun compare_older() = assertTrue(compareVersions("1.0.1", "1.1.0") < 0)

    private val sample = """
        {
          "tag_name": "v1.1.0",
          "name": "Moody 1.1.0",
          "draft": false,
          "prerelease": false,
          "body": "- Faster thumbnails\r\n- **Fixed** subtitle timing",
          "assets": [
            {"name": "moody-1.1.0.apk.sha256", "size": 64, "browser_download_url": "https://github.com/ruksed09/moody/releases/download/v1.1.0/moody-1.1.0.apk.sha256"},
            {"name": "moody-1.1.0.apk", "size": 36700160, "browser_download_url": "https://github.com/ruksed09/moody/releases/download/v1.1.0/moody-1.1.0.apk"}
          ]
        }
    """.trimIndent()

    @Test fun parse_readsVersionApkAndSize() {
        val r = parseRelease(sample)!!
        assertEquals("1.1.0", r.version)
        assertEquals("https://github.com/ruksed09/moody/releases/download/v1.1.0/moody-1.1.0.apk", r.apkUrl)
        assertEquals(36_700_160L, r.apkSize)
        assertFalse(r.required)
        assertTrue(r.notes.contains("Faster thumbnails"))
    }

    @Test fun parse_requiredMarker_isDetectedAndStripped() {
        val r = parseRelease(sample.replace("- Faster thumbnails", "[required] - Faster thumbnails"))!!
        assertTrue(r.required)
        assertFalse(r.notes.contains("[required]"))
    }

    @Test fun parse_noApk_returnsNull() = assertNull(parseRelease(sample.replace("moody-1.1.0.apk\"", "moody-1.1.0.zip\"")))
    @Test fun parse_garbage_returnsNull() = assertNull(parseRelease("{oops"))
    @Test fun parse_missingTag_returnsNull() = assertNull(parseRelease("""{"assets":[]}"""))
}
