package bassamalim.echo.core.autofill

import android.text.InputType

/**
 * One input on someone else's screen, reduced to what's needed to tell a password field from a
 * username field. [index] points back at the real view the service collected it from.
 */
data class FormField(
    val index: Int,
    val autofillHints: List<String> = emptyList(),
    val inputType: Int = 0,
    /** For a web page: the input's `type` and `autocomplete` attributes. */
    val htmlType: String? = null,
    val htmlAutocomplete: String? = null,
    /** The view's resource id name, or a web input's `name`/`id`. */
    val idEntry: String? = null,
    val hint: String? = null
)

/** Where a login would go. There is always a password field; the username is a best guess. */
data class LoginForm(val username: FormField?, val password: FormField)

/** Works out, from the fields on a screen, whether it is a login form and which field is which. */
object FormParser {

    private val USERNAME_WORDS = listOf("user", "email", "e-mail", "login", "account", "identifier")

    fun parse(fields: List<FormField>): LoginForm? {
        val password = fields.firstOrNull(::isPassword) ?: return null
        val before = fields.takeWhile { it !== password }

        // A field that says it's a username wins; otherwise it's the last thing typed into
        // before the password, which is where the username is on almost every login form.
        val username = fields.firstOrNull { it !== password && isUsername(it) }
            ?: before.lastOrNull { !isPassword(it) }

        return LoginForm(username, password)
    }

    private fun isPassword(field: FormField): Boolean {
        if (field.autofillHints.any { "password" in it.lowercase() }) return true
        if (field.htmlType.equals("password", ignoreCase = true)) return true
        if (field.htmlAutocomplete?.lowercase()?.contains("password") == true) return true

        val variation = field.inputType and InputType.TYPE_MASK_VARIATION
        val inputClass = field.inputType and InputType.TYPE_MASK_CLASS
        if (inputClass == InputType.TYPE_CLASS_TEXT && (
                    variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
        ) return true

        return listOfNotNull(field.idEntry, field.hint).any { "password" in it.lowercase() }
    }

    private fun isUsername(field: FormField): Boolean {
        val declared = field.autofillHints + listOfNotNull(field.htmlAutocomplete)
        if (declared.any { hint -> USERNAME_WORDS.any { it in hint.lowercase() } }) return true
        if (field.htmlType.equals("email", ignoreCase = true)) return true

        val variation = field.inputType and InputType.TYPE_MASK_VARIATION
        val inputClass = field.inputType and InputType.TYPE_MASK_CLASS
        if (inputClass == InputType.TYPE_CLASS_TEXT && (
                    variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                            variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS)
        ) return true

        return listOfNotNull(field.idEntry, field.hint).any { text ->
            USERNAME_WORDS.any { it in text.lowercase() }
        }
    }
}
