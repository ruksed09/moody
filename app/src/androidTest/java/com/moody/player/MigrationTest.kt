package com.moody.player

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.moody.player.data.db.MoodyDb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Database upgrades on a throwaway database (never the app's own): bookmarks saved before names, ranges
 * and sharing existed must come through unchanged, with the new columns at their defaults.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val name = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), MoodyDb::class.java)

    @Test fun v2ToV3_keepsBookmarks() {
        helper.createDatabase(name, 2).use { db ->
            db.execSQL("INSERT INTO bookmarks (id, uri, positionMs, createdAt) VALUES (7, 'content://media/external/video/media/1', 61000, 1700000000000)")
            db.execSQL("INSERT INTO bookmarks (id, uri, positionMs, createdAt) VALUES (8, 'content://media/external/video/media/1', 125000, 1700000001000)")
        }
        helper.runMigrationsAndValidate(name, 3, true).use { db ->
            db.query("SELECT id, uri, positionMs, createdAt, name, endMs, uid, updatedAt, deleted FROM bookmarks ORDER BY id").use { c ->
                assertEquals(2, c.count)
                c.moveToFirst()
                assertEquals(7L, c.getLong(0))
                assertEquals("content://media/external/video/media/1", c.getString(1))
                assertEquals(61_000L, c.getLong(2))
                assertEquals(1_700_000_000_000L, c.getLong(3))
                assertEquals("", c.getString(4))
                assertTrue(c.isNull(5))
                assertEquals("", c.getString(6))
                assertEquals(0L, c.getLong(7))
                assertEquals(0L, c.getLong(8))
                c.moveToNext()
                assertEquals(125_000L, c.getLong(2))
            }
        }
    }

    @Test fun v1ToV3_opens() {
        helper.createDatabase(name, 1).close()
        helper.runMigrationsAndValidate(name, 3, true).close()
    }
}
