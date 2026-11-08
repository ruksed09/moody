package com.moody.player.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultSecurityTest {
    @Test fun verify_acceptsRightPin_rejectsWrongPin() {
        val h = PinHasher.hash("1234")
        assertTrue(PinHasher.verify("1234", h))
        assertFalse(PinHasher.verify("1235", h))
    }

    @Test fun samePin_getsDifferentSalts() {
        val a = PinHasher.hash("2580")
        val b = PinHasher.hash("2580")
        assertNotEquals(a.saltB64, b.saltB64)
        assertNotEquals(a.hashB64, b.hashB64)
    }

    @Test fun hash_usesAtLeast120kIterations() {
        assertTrue(PinHasher.hash("1234").iterations >= 120_000)
    }

    @Test fun pinFormat_isFourToSixDigits() {
        assertFalse(PinHasher.isValidPin("123"))
        assertFalse(PinHasher.isValidPin("1234567"))
        assertFalse(PinHasher.isValidPin("12a4"))
        assertTrue(PinHasher.isValidPin("1234"))
        assertTrue(PinHasher.isValidPin("123456"))
    }

    @Test fun recoveryAnswer_ignoresCaseAndSpaces() {
        val h = PinHasher.hash(normalizeAnswer("  Biscuit "))
        assertTrue(PinHasher.verify(normalizeAnswer("biscuit"), h))
    }

    @Test fun lockout_schedule() {
        assertEquals(0L, lockoutMs(4))
        assertEquals(30_000L, lockoutMs(5))
        assertEquals(60_000L, lockoutMs(6))
        assertEquals(3_600_000L, lockoutMs(20))
    }
}
