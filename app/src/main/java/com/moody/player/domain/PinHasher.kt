package com.moody.player.domain

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class PinHash(val saltB64: String, val hashB64: String, val iterations: Int)

/** Vault PINs and recovery answers are stored only as salted PBKDF2-SHA256 hashes. */
object PinHasher {
    private const val KEY_BITS = 256

    fun hash(pin: String, iterations: Int = 120_000): PinHash {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return PinHash(b64(salt), b64(derive(pin, salt, iterations)), iterations)
    }

    fun verify(pin: String, h: PinHash): Boolean {
        val salt = Base64.getDecoder().decode(h.saltB64)
        val expected = Base64.getDecoder().decode(h.hashB64)
        return MessageDigest.isEqual(derive(pin, salt, h.iterations), expected)
    }

    fun isValidPin(pin: String): Boolean = pin.length in 4..6 && pin.all { it in '0'..'9' }

    private fun derive(secret: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(secret.toCharArray(), salt, iterations, KEY_BITS)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
    }

    private fun b64(b: ByteArray) = Base64.getEncoder().encodeToString(b)
}

/** Recovery answers match regardless of case and surrounding spaces. */
fun normalizeAnswer(answer: String): String = answer.trim().lowercase().replace(Regex("\\s+"), " ")

/** Wait after [failedAttempts] wrong PINs: none below 5, then 30 s doubling, capped at an hour. */
fun lockoutMs(failedAttempts: Int): Long {
    if (failedAttempts < 5) return 0
    val shift = (failedAttempts - 5).coerceAtMost(20)
    return (30_000L shl shift).coerceAtMost(3_600_000L)
}
