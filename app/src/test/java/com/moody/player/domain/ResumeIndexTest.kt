package com.moody.player.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ResumeIndexTest {
    private val queue = listOf("a", "b", "c", "b")

    @Test fun nothingPlaying_keepsSavedIndex() {
        assertEquals(2, resumeIndex(queue, null, 2))
    }

    @Test fun playingVideo_winsOverStaleIndex() {
        // The queue moved on in the background after the index was stored.
        assertEquals(2, resumeIndex(queue, "c", 0))
    }

    @Test fun sameVideoTwice_keepsSavedPlace() {
        assertEquals(3, resumeIndex(queue, "b", 3))
        assertEquals(1, resumeIndex(queue, "b", 0))
    }

    @Test fun playingVideoNotInQueue_keepsSavedIndex() {
        assertEquals(1, resumeIndex(queue, "x", 1))
    }

    @Test fun indexOutOfRange_isClamped() {
        assertEquals(3, resumeIndex(queue, null, 99))
        assertEquals(0, resumeIndex(queue, null, -4))
        assertEquals(0, resumeIndex(emptyList<String>(), "a", 5))
    }
}
