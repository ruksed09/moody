package com.moody.player.data

import com.moody.player.data.vault.RestoreTarget
import com.moody.player.data.vault.restoreTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class RestoreTargetTest {
    @Test fun camera_goesBackToItsFolder() = assertEquals(RestoreTarget.Video("DCIM/Camera/"), restoreTarget("DCIM/Camera/"))
    @Test fun movies_and_pictures_keepTheirPath() {
        assertEquals(RestoreTarget.Video("Movies/Anime/"), restoreTarget("Movies/Anime/"))
        assertEquals(RestoreTarget.Video("Pictures/Screenshots/"), restoreTarget("Pictures/Screenshots/"))
    }
    @Test fun downloads_goToTheDownloadsCollection() {
        assertEquals(RestoreTarget.Downloads("Download/"), restoreTarget("Download/"))
        assertEquals(RestoreTarget.Downloads("Download/Telegram/"), restoreTarget("Download/Telegram/"))
    }
    @Test fun appPrivateFolders_fallBackToMoviesMoody() {
        assertEquals(RestoreTarget.Video("Movies/Moody/"), restoreTarget("Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video/"))
        assertEquals(RestoreTarget.Video("Movies/Moody/"), restoreTarget(""))
        assertEquals(RestoreTarget.Video("Movies/Moody/"), restoreTarget("Telegram/Telegram Video/"))
    }
    @Test fun missingTrailingSlash_isAdded() = assertEquals(RestoreTarget.Video("DCIM/Camera/"), restoreTarget("DCIM/Camera"))
}
