package bassamalim.rune.features.editor

import bassamalim.rune.core.crypto.Rune

data class EditorUiState(
    val isLoading: Boolean = true,
    /** Editing an entry that exists, rather than adding one. */
    val isEditing: Boolean = false,
    val site: String = "",
    val login: String = "",
    val counter: Int = 1,
    val length: Int = Rune.DEFAULT_LENGTH,
    val lower: Boolean = true,
    val upper: Boolean = true,
    val digits: Boolean = true,
    val symbols: Boolean = true,
    /** "about 125 bits" */
    val strength: String = "",
    val siteError: String? = null,
    val classesError: String? = null,
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
