package com.moody.player.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.moody.player.data.db.MoodyDb
import com.moody.player.data.db.VaultItem
import com.moody.player.data.db.VaultState
import com.moody.player.data.vault.recoverMoves
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VaultRecoveryTest {
    @Test fun interruptedMoves_neverLoseAFile() = runTest {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MoodyDb::class.java).allowMainThreadQueries().build()
        val dir = Files.createTempDirectory("vault").toFile()
        fun row(id: String, state: VaultState) = VaultItem(id, "$id.bin", "$id.mp4", "DCIM/Camera/", "video/mp4", 4, 1000, state, 1, "content://media/external/video/media/$id")
        File(dir, "copying.bin").writeText("half")
        File(dir, "restoring.bin").writeText("full")
        File(dir, "done.bin").writeText("full")
        db.vault().upsert(row("copying", VaultState.COPYING))     // crash while copying in: original untouched
        db.vault().upsert(row("restoring", VaultState.RESTORING)) // crash while writing out: vault copy still there
        db.vault().upsert(row("gone", VaultState.RESTORING))      // finished writing out, vault copy already removed
        db.vault().upsert(row("done", VaultState.DONE))

        recoverMoves(db.vault()) { File(dir, it.fileName) }

        assertFalse("half copy removed", File(dir, "copying.bin").exists())
        assertTrue("restoring copy kept", File(dir, "restoring.bin").exists())
        assertEquals(setOf("restoring", "done"), db.vault().done().first().map { it.id }.toSet())
        assertTrue(db.vault().unfinished().isEmpty())
        db.close()
    }
}
