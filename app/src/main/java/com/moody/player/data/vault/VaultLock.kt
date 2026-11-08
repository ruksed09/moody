package com.moody.player.data.vault

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.moody.player.data.prefs.SettingsStore
import com.moody.player.di.AppScope
import com.moody.player.domain.PinHash
import com.moody.player.domain.PinHasher
import com.moody.player.domain.lockoutMs
import com.moody.player.domain.normalizeAnswer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private val Context.vaultPrefs by preferencesDataStore("vault_prefs")

private object VK {
    val pinSalt = stringPreferencesKey("pin_salt")
    val pinHash = stringPreferencesKey("pin_hash")
    val pinIter = intPreferencesKey("pin_iter")
    val pinLength = intPreferencesKey("pin_length")
    val question = stringPreferencesKey("question")
    val ansSalt = stringPreferencesKey("ans_salt")
    val ansHash = stringPreferencesKey("ans_hash")
    val ansIter = intPreferencesKey("ans_iter")
    val failed = intPreferencesKey("failed")
    val lockedUntil = longPreferencesKey("locked_until")
}

sealed interface UnlockResult {
    data object Ok : UnlockResult
    data class Wrong(val attemptsLeft: Int) : UnlockResult
    data class LockedFor(val ms: Long) : UnlockResult
}

data class VaultInfo(val setUp: Boolean, val pinLength: Int, val question: String)

/** The vault's PIN, recovery question, lockout and auto-lock. Nothing secret is stored in plain text. */
@Singleton
class VaultLock @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsStore,
    @AppScope private val scope: CoroutineScope,
) {
    private val store = context.vaultPrefs
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()
    @Volatile private var lastTouch = 0L

    val info: Flow<VaultInfo> = store.data.map { VaultInfo(it[VK.pinHash] != null, it[VK.pinLength] ?: 4, it[VK.question].orEmpty()) }

    init {
        scope.launch(Dispatchers.Main) {
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) = lock()
            })
        }
        scope.launch {
            while (isActive) {
                delay(10_000)
                if (_unlocked.value) {
                    val limit = settings.settings.first().vaultAutoLockSec * 1000L
                    if (System.currentTimeMillis() - lastTouch > limit) lock()
                }
            }
        }
    }

    fun touch() { lastTouch = System.currentTimeMillis() }
    fun lock() { _unlocked.value = false }

    suspend fun setUp(pin: String, question: String, answer: String) = withContext(Dispatchers.Default) {
        require(PinHasher.isValidPin(pin))
        val p = PinHasher.hash(pin)
        val a = PinHasher.hash(normalizeAnswer(answer))
        store.edit {
            it[VK.pinSalt] = p.saltB64; it[VK.pinHash] = p.hashB64; it[VK.pinIter] = p.iterations; it[VK.pinLength] = pin.length
            it[VK.question] = question.trim(); it[VK.ansSalt] = a.saltB64; it[VK.ansHash] = a.hashB64; it[VK.ansIter] = a.iterations
            it[VK.failed] = 0; it[VK.lockedUntil] = 0
        }
        touch()
        _unlocked.value = true
    }

    /** Remaining lockout, or 0. */
    suspend fun lockedForMs(): Long = (store.data.first()[VK.lockedUntil] ?: 0L).minus(System.currentTimeMillis()).coerceAtLeast(0)

    suspend fun unlock(pin: String): UnlockResult = withContext(Dispatchers.Default) {
        val prefs = store.data.first()
        val wait = (prefs[VK.lockedUntil] ?: 0L) - System.currentTimeMillis()
        if (wait > 0) return@withContext UnlockResult.LockedFor(wait)
        val h = PinHash(prefs[VK.pinSalt] ?: return@withContext UnlockResult.Wrong(0), prefs[VK.pinHash]!!, prefs[VK.pinIter] ?: 120_000)
        if (PinHasher.verify(pin, h)) {
            store.edit { it[VK.failed] = 0; it[VK.lockedUntil] = 0 }
            touch()
            _unlocked.value = true
            UnlockResult.Ok
        } else {
            val failed = (prefs[VK.failed] ?: 0) + 1
            val lockMs = lockoutMs(failed)
            store.edit { it[VK.failed] = failed; it[VK.lockedUntil] = if (lockMs > 0) System.currentTimeMillis() + lockMs else 0 }
            if (lockMs > 0) UnlockResult.LockedFor(lockMs) else UnlockResult.Wrong(5 - failed)
        }
    }

    suspend fun checkAnswer(answer: String): Boolean = withContext(Dispatchers.Default) {
        val prefs = store.data.first()
        val h = PinHash(prefs[VK.ansSalt] ?: return@withContext false, prefs[VK.ansHash]!!, prefs[VK.ansIter] ?: 120_000)
        PinHasher.verify(normalizeAnswer(answer), h)
    }

    /** Recovery: a right answer sets a new PIN and clears the lockout. */
    suspend fun recover(answer: String, newPin: String): Boolean {
        if (!checkAnswer(answer) || !PinHasher.isValidPin(newPin)) return false
        setPin(newPin)
        touch()
        _unlocked.value = true
        return true
    }

    suspend fun setPin(newPin: String) = withContext(Dispatchers.Default) {
        val p = PinHasher.hash(newPin)
        store.edit {
            it[VK.pinSalt] = p.saltB64; it[VK.pinHash] = p.hashB64; it[VK.pinIter] = p.iterations; it[VK.pinLength] = newPin.length
            it[VK.failed] = 0; it[VK.lockedUntil] = 0
        }
    }

    suspend fun setRecovery(question: String, answer: String) = withContext(Dispatchers.Default) {
        val a = PinHasher.hash(normalizeAnswer(answer))
        store.edit { it[VK.question] = question.trim(); it[VK.ansSalt] = a.saltB64; it[VK.ansHash] = a.hashB64; it[VK.ansIter] = a.iterations }
    }
}
