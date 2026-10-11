package bassamalim.rune.features.setup

import javax.crypto.Cipher

data class SetupUiState(
    val step: SetupStep = SetupStep.Form,
    val masterPassword: String = "",
    val repeatPassword: String = "",
    /** Joining a key that already exists on another device, rather than making a new one. */
    val hasSecretKey: Boolean = false,
    val secretKeyText: String = "",
    val passwordError: String? = null,
    val repeatError: String? = null,
    val secretKeyError: String? = null,

    /** Shown on the confirm step. */
    val fingerprint: String = "",
    /** The new secret key as text. Empty when the user typed in one they already had. */
    val newSecretKey: String = "",
    val isWrittenDown: Boolean = false,
    /** Whether a fingerprint is enrolled, so the master key can be sealed behind it. */
    val canUseBiometrics: Boolean = false,
    /** Set when the fingerprint sheet should be on screen, sealing the master key. */
    val sealing: Cipher? = null,
    val isSaving: Boolean = false,
    val saveError: String? = null
) {
    /** A new key has to be written down before it is the only copy. */
    val canSave: Boolean get() = (newSecretKey.isEmpty() || isWrittenDown) && !isSaving
}

enum class SetupStep { Form, Deriving, Confirm }
