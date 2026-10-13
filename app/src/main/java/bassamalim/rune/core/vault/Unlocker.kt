package bassamalim.rune.core.vault

import bassamalim.rune.core.crypto.Rune
import bassamalim.rune.core.di.DefaultDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.crypto.Cipher
import javax.inject.Inject

/** Where an attempt to get the master key has got to. The screen draws whatever this says. */
sealed interface UnlockStep {
    data object Idle : UnlockStep

    /** Show the system fingerprint prompt, bound to [cipher]. */
    data class Biometric(val cipher: Cipher) : UnlockStep

    /**
     * Ask for the master password instead: the fallback, the only way with no fingerprint, and
     * every two weeks as practice ([isPractice]), which can be skipped for this unlock.
     */
    data class Password(
        val isChecking: Boolean = false,
        val error: String? = null,
        val isPractice: Boolean = false
    ) : UnlockStep
}

/**
 * The one ceremony for getting the master key, shared by every screen that needs it: fingerprint
 * first when quick unlock is on, master password otherwise or on request.
 *
 * Not a singleton: each ViewModel gets its own, so one screen's prompt never shows on another.
 * Whoever receives the key is expected to use it once and zero it.
 */
class Unlocker @Inject constructor(
    private val vault: Vault,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher
) {

    private val _step = MutableStateFlow<UnlockStep>(UnlockStep.Idle)
    val step: StateFlow<UnlockStep> = _step.asStateFlow()

    suspend fun begin() {
        val cipher = runCatching { vault.openingCipher() }.getOrNull()

        _step.value = when {
            cipher == null -> UnlockStep.Password()
            vault.isPracticeDue() -> UnlockStep.Password(isPractice = true)
            else -> UnlockStep.Biometric(cipher)
        }
    }

    /** Fingerprint this time; the practice stays due, so the next unlock asks again. */
    suspend fun skipPractice() {
        val cipher = runCatching { vault.openingCipher() }.getOrNull()

        _step.value = if (cipher != null) UnlockStep.Biometric(cipher) else UnlockStep.Password()
    }

    /** The master key, or null if the authenticated cipher still couldn't open it. */
    suspend fun onBiometricSucceeded(cipher: Cipher): ByteArray? {
        val key = runCatching { vault.open(cipher) }.getOrNull()

        _step.value =
            if (key != null) UnlockStep.Idle
            else UnlockStep.Password(error = "Fingerprint unlock didn't work. Use your master password.")
        return key
    }

    fun onUsePassword() {
        _step.value = UnlockStep.Password()
    }

    /** The master key, or null (and an error on [step]) when the password is wrong. */
    suspend fun onPassword(password: String): ByteArray? {
        if (password.isEmpty()) return null
        val isPractice = (_step.value as? UnlockStep.Password)?.isPractice == true
        _step.value = UnlockStep.Password(isChecking = true, isPractice = isPractice)

        val key = withContext(dispatcher) { Rune.masterKey(password) }

        if (Rune.fingerprint(key) == vault.fingerprint()) {
            vault.onPasswordTyped()
            vault.rememberSyncKey(key)
            _step.value = UnlockStep.Idle
            return key
        }

        key.fill(0)
        _step.value = UnlockStep.Password(error = "That isn't your master password.", isPractice = isPractice)
        return null
    }

    fun cancel() {
        _step.value = UnlockStep.Idle
    }
}
