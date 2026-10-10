package bassamalim.echo.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import bassamalim.echo.R
import bassamalim.echo.core.ui.theme.Accent
import bassamalim.echo.core.ui.theme.Danger
import bassamalim.echo.core.ui.theme.Ink
import bassamalim.echo.core.ui.theme.InkFaint
import bassamalim.echo.core.ui.theme.InkMuted
import bassamalim.echo.core.ui.theme.Spacing
import bassamalim.echo.core.ui.theme.Surface100
import bassamalim.echo.core.ui.theme.Surface300

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Ink,
    unfocusedTextColor = Ink,
    focusedContainerColor = Surface100,
    unfocusedContainerColor = Surface100,
    errorContainerColor = Surface100,
    focusedBorderColor = Accent,
    unfocusedBorderColor = Surface300,
    errorBorderColor = Danger,
    cursorColor = Accent,
    focusedPlaceholderColor = InkFaint,
    unfocusedPlaceholderColor = InkFaint,
    focusedLeadingIconColor = InkMuted,
    unfocusedLeadingIconColor = InkMuted,
    errorSupportingTextColor = Danger,
    focusedSupportingTextColor = InkMuted,
    unfocusedSupportingTextColor = InkMuted
)

/** The small line of text that names the control under it. */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = InkMuted,
        modifier = modifier.padding(bottom = Spacing.xs)
    )
}

/**
 * A text field with its label always visible above it: a placeholder that vanishes on the first
 * keystroke is not a label. [error] replaces [helper] and sits right under the field it's about.
 */
@Composable
fun EchoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    helper: String? = null,
    error: String? = null,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailing: @Composable (() -> Unit)? = null
) {
    Column(modifier) {
        FieldLabel(label)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = textStyle,
            isError = error != null,
            placeholder = placeholder?.let { { Text(it, style = textStyle) } },
            supportingText = (error ?: helper)?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
            trailingIcon = trailing,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            shape = MaterialTheme.shapes.medium,
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** A password field: hidden by default, with an eye to check what was typed. Never autocorrected. */
@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    helper: String? = null,
    error: String? = null,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {}
) {
    var isVisible by rememberSaveable { mutableStateOf(false) }

    EchoTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        helper = helper,
        error = error,
        visualTransformation =
            if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Password,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        trailing = {
            IconAction(
                icon = if (isVisible) R.drawable.ic_eye_off else R.drawable.ic_eye,
                label = if (isVisible) "Hide $label" else "Show $label",
                onClick = { isVisible = !isVisible },
                tint = InkMuted
            )
        }
    )
}

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyLarge) },
        leadingIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        },
        trailingIcon = if (value.isEmpty()) null else {
            {
                IconAction(
                    icon = R.drawable.ic_close,
                    label = "Clear search",
                    onClick = { onValueChange("") },
                    tint = InkMuted
                )
            }
        },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Search
        ),
        shape = MaterialTheme.shapes.medium,
        colors = fieldColors(),
        modifier = modifier.fillMaxWidth()
    )
}
