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

    /** Ask for the master password instead: the fallback, and the only way with no fingerprint. */
    data class Password(val isChecking: Boolean = false, val error: String? = null) : UnlockStep
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
        _step.value = UnlockStep.Password(isChecking = true)

        val key = withContext(dispatcher) {
            runCatching { Rune.masterKey(password, vault.secretKey()) }.getOrNull()
        }

        if (key != null && Rune.fingerprint(key) == vault.fingerprint()) {
            _step.value = UnlockStep.Idle
            return key
        }

        key?.fill(0)
        _step.value = UnlockStep.Password(error = "That isn't your master password.")
        return null
    }

    fun cancel() {
        _step.value = UnlockStep.Idle
    }
}
