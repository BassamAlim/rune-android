package bassamalim.echo.core.autofill

import android.text.InputType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormParserTest {

    private val textPassword = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    private val plainText = InputType.TYPE_CLASS_TEXT

    @Test
    fun `a screen with no password field is not a login form`() {
        assertNull(FormParser.parse(listOf(FormField(0, hint = "Search"), FormField(1, hint = "Email"))))
        assertNull(FormParser.parse(emptyList()))
    }

    @Test
    fun `native app fields are told apart by input type and hints`() {
        val form = FormParser.parse(
            listOf(
                FormField(0, inputType = plainText, hint = "Search"),
                FormField(1, inputType = plainText, idEntry = "email_input"),
                FormField(2, inputType = textPassword)
            )
        )!!

        assertEquals(1, form.username?.index)
        assertEquals(2, form.password.index)
    }

    @Test
    fun `web fields are told apart by their html attributes`() {
        val form = FormParser.parse(
            listOf(
                FormField(0, htmlType = "text", htmlAutocomplete = "username"),
                FormField(1, htmlType = "password", htmlAutocomplete = "current-password")
            )
        )!!

        assertEquals(0, form.username?.index)
        assertEquals(1, form.password.index)
    }

    @Test
    fun `an unlabelled field just before the password is taken as the username`() {
        val form = FormParser.parse(
            listOf(FormField(0, inputType = plainText), FormField(1, autofillHints = listOf("password")))
        )!!

        assertEquals(0, form.username?.index)
    }

    @Test
    fun `a password field on its own still fills`() {
        val form = FormParser.parse(listOf(FormField(0, inputType = textPassword)))!!

        assertNull(form.username)
        assertEquals(0, form.password.index)
    }

    @Test
    fun `the new-password field of a sign-up form is the one filled`() {
        val form = FormParser.parse(
            listOf(
                FormField(0, htmlType = "email"),
                FormField(1, htmlType = "password", htmlAutocomplete = "new-password"),
                FormField(2, htmlType = "password", htmlAutocomplete = "new-password")
            )
        )!!

        assertEquals(0, form.username?.index)
        assertEquals(1, form.password.index)
    }
}
