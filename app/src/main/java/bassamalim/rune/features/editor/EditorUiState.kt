package bassamalim.rune.features.editor

import bassamalim.rune.core.crypto.Rune
import bassamalim.rune.core.data.repositories.InstalledApp

data class EditorUiState(
    val isLoading: Boolean = true,
    /** Editing an entry that exists, rather than adding one. */
    val isEditing: Boolean = false,
    val site: String = "",
    val login: String = "",
    val note: String = "",
    val counter: Int = 1,
    val length: Int = Rune.DEFAULT_LENGTH,
    val lower: Boolean = true,
    val upper: Boolean = true,
    val digits: Boolean = true,
    val symbols: Boolean = true,
    /** The symbols this site allows, as typed; canonicalised on save. */
    val symbolSet: String = Rune.DEFAULT_SYMBOLS,
    /** Apps whose login forms autofill offers this site in. Linking one changes no password. */
    val apps: List<InstalledApp> = emptyList(),
    /** The apps to pick from while the picker is open; null when it's closed. */
    val appChoices: List<InstalledApp>? = null,
    /** "about 125 bits" */
    val strength: String = "",
    val siteError: String? = null,
    val classesError: String? = null,
    val symbolsError: String? = null,
    /** A failure that isn't about one field, e.g. the file couldn't be written. */
    val saveError: String? = null,
    val isConfirmingDelete: Boolean = false
) {
    val classes: String
        get() = buildString {
            if (lower) append('l')
            if (upper) append('u')
            if (digits) append('d')
            if (symbols) append('s')
        }
}

enum class CharacterClass { Lower, Upper, Digits, Symbols }
