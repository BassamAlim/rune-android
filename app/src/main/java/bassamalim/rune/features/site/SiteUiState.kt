package bassamalim.rune.features.site

import bassamalim.rune.core.vault.UnlockStep

data class SiteUiState(
    val isLoading: Boolean = true,
    val site: String = "",
    val login: String = "",
    /** "20 characters · counter 1 · about 125 bits" */
    val details: String = "",
    /** Null until the fingerprint or master password has been given, and again after leaving. */
    val password: String? = null,
    /** The counter has been raised, so the password before this one can be copied. */
    val hasPrevious: Boolean = false,
    val isRevealed: Boolean = false,
    val isCopied: Boolean = false,
    val isPreviousCopied: Boolean = false,
    val unlock: UnlockStep = UnlockStep.Idle,
    /** The settings can't produce a password (a hand-edited or imported file). */
    val problem: String? = null
) {
    val isLocked: Boolean get() = password == null && problem == null
}
