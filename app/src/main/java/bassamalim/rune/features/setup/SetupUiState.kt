package bassamalim.rune.features.setup

import javax.crypto.Cipher

data class SetupUiState(
    val step: SetupStep = SetupStep.Form,
    /** The suggested passphrase. Null when the user chose their own master password. */
    val generated: String? = null,
    /** "78 bits", for the generated passphrase. */
    val generatedStrength: String = "",
    /** The user's own master password; unused while [generated] is set. */
    val masterPassword: String = "",
    /** The repeat of [masterPassword], or the generated passphrase typed back. */
    val repeatPassword: String = "",
    /** How guessable [masterPassword] is; null until something is typed. */
    val strength: Strength? = null,
    val passwordError: String? = null,
    val repeatError: String? = null,

    /** Shown on the confirm step. */
    val fingerprint: String = "",
    /** Whether a fingerprint is enrolled, so the master key can be sealed behind it. */
    val canUseBiometrics: Boolean = false,
    /** Set when the fingerprint sheet should be on screen, sealing the master key. */
    val sealing: Cipher? = null,
    val isSaving: Boolean = false,
    val saveError: String? = null
)

/** [text] is how long guessing would take after a site leak; [level] colours it. */
data class Strength(val text: String, val level: StrengthLevel)

enum class StrengthLevel { Weak, Fair, Strong }

enum class SetupStep { Form, Deriving, Confirm }
