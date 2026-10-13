package bassamalim.rune.features.site

import bassamalim.rune.core.vault.UnlockStep

data class SiteUiState(
    val isLoading: Boolean = true,
    val site: String = "",
    val login: String = "",
    /** Kept with the login, e.g. a username; not secret. */
    val note: String = "",
    /** "16 characters · counter 1 · about 100 bits" */
    val details: String = "",
    /** Null until the fingerprint or master password has been given, and again after leaving. */
    val password: String? = null,
    /**
     * The counter has been raised, so the password before this one can be copied. With
     * [isKept], the second password is Rune's instead, for the site's change-password form.
     */
    val hasPrevious: Boolean = false,
    /** [password] is the site's own, kept until it's changed to Rune's (SPEC §3.1). */
    val isKept: Boolean = false,
    /** Typing the site's own password in, to keep it. */
    val isKeeping: Boolean = false,
    val isConfirmingSwitch: Boolean = false,
    /** The site has a kept password, whether or not it could be opened. */
    val hasSealed: Boolean = false,
    val isRevealed: Boolean = false,
    val isCopied: Boolean = false,
    val isPreviousCopied: Boolean = false,
    val unlock: UnlockStep = UnlockStep.Idle,
    /** The settings can't produce a password (a hand-edited or imported file). */
    val problem: String? = null
) {
    val isLocked: Boolean get() = password == null && problem == null
}
