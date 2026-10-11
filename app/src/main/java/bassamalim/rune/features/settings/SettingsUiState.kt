package bassamalim.rune.features.settings

import bassamalim.rune.core.vault.UnlockStep
import javax.crypto.Cipher

data class SettingsUiState(
    val isLoading: Boolean = true,
    val fingerprint: String = "",
    /** The master key is sealed behind a fingerprint, so passwords don't need the master password. */
    val isQuickUnlockOn: Boolean = false,
    /** A fingerprint is enrolled on the phone, so quick unlock could be turned on. */
    val canUseBiometrics: Boolean = false,
    /** Rune is the phone's autofill service. */
    val isAutofillOn: Boolean = false,
    /** The secret key as text, once the user has proved it's them. Null while hidden. */
    val secretKey: String? = null,
    val siteCount: Int = 0,
    /** What the last export or import did, or why it didn't. */
    val message: String? = null,
    val isMessageError: Boolean = false,
    /** Turning quick unlock on starts with the master password... */
    val unlock: UnlockStep = UnlockStep.Idle,
    /** ...and ends with the fingerprint sheet sealing the key. */
    val sealing: Cipher? = null,
    val unlockError: String? = null,
    val isConfirmingReset: Boolean = false,
    val version: String = ""
)
