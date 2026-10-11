package bassamalim.rune.features.setup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bassamalim.rune.R
import bassamalim.rune.core.ui.components.Card
import bassamalim.rune.core.ui.components.RuneTextField
import bassamalim.rune.core.ui.components.ErrorNote
import bassamalim.rune.core.ui.components.FieldLabel
import bassamalim.rune.core.ui.components.Note
import bassamalim.rune.core.ui.components.PasswordField
import bassamalim.rune.core.ui.components.PrimaryButton
import bassamalim.rune.core.ui.components.ScreenScaffold
import bassamalim.rune.core.ui.components.SecondaryButton
import bassamalim.rune.core.ui.components.promptWithCipher
import bassamalim.rune.core.ui.theme.Accent
import bassamalim.rune.core.ui.theme.AccentInk
import bassamalim.rune.core.ui.theme.RuneType
import bassamalim.rune.core.ui.theme.InkMuted
import bassamalim.rune.core.ui.theme.Sizes
import bassamalim.rune.core.ui.theme.Spacing
import bassamalim.rune.core.ui.theme.Surface300
import bassamalim.rune.core.utils.findFragmentActivity

/**
 * First run: a master password goes in, two keys come out, and the password is never needed
 * again on this phone unless the fingerprints change.
 */
@Composable
fun SetupScreen(viewModel: SetupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findFragmentActivity()

    // Sealing the master key behind the fingerprint is the last step of saving.
    LaunchedEffect(state.sealing) {
        val cipher = state.sealing ?: return@LaunchedEffect
        if (activity == null) return@LaunchedEffect viewModel.onSealSkipped()

        activity.promptWithCipher(
            cipher = cipher,
            title = "Turn on fingerprint unlock",
            negativeLabel = "Not now",
            onSuccess = viewModel::onSealAuthenticated,
            onNegative = viewModel::onSealSkipped,
            onCancel = viewModel::onSealSkipped
        )
    }

    // On the confirm step, back means "retype", not "leave with half a set-up".
    BackHandler(enabled = state.step == SetupStep.Confirm, onBack = viewModel::onBackToFormClick)

    SetupScreen(
        state = state,
        onMasterPasswordChange = viewModel::onMasterPasswordChange,
        onRepeatPasswordChange = viewModel::onRepeatPasswordChange,
        onHasSecretKeyChange = viewModel::onHasSecretKeyChange,
        onSecretKeyChange = viewModel::onSecretKeyChange,
        onContinueClick = viewModel::onContinueClick,
        onWrittenDownChange = viewModel::onWrittenDownChange,
        onBackToFormClick = viewModel::onBackToFormClick,
        onSaveClick = viewModel::onSaveClick
    )
}

@Composable
internal fun SetupScreen(
    state: SetupUiState,
    onMasterPasswordChange: (String) -> Unit,
    onRepeatPasswordChange: (String) -> Unit,
    onHasSecretKeyChange: (Boolean) -> Unit,
    onSecretKeyChange: (String) -> Unit,
    onContinueClick: () -> Unit,
    onWrittenDownChange: (Boolean) -> Unit,
    onBackToFormClick: () -> Unit,
    onSaveClick: () -> Unit
) {
    when (state.step) {
        SetupStep.Form -> Form(
            state, onMasterPasswordChange, onRepeatPasswordChange, onHasSecretKeyChange,
            onSecretKeyChange, onContinueClick
        )

        SetupStep.Deriving -> Deriving()

        SetupStep.Confirm -> Confirm(state, onWrittenDownChange, onBackToFormClick, onSaveClick)
    }
}

@Composable
private fun Form(
    state: SetupUiState,
    onMasterPasswordChange: (String) -> Unit,
    onRepeatPasswordChange: (String) -> Unit,
    onHasSecretKeyChange: (Boolean) -> Unit,
    onSecretKeyChange: (String) -> Unit,
    onContinueClick: () -> Unit
) {
    ScreenScaffold(topBar = {}) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.xxxl, bottom = Spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_rune_mark),
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(72.dp)
            )
            Text(
                text = "Set up Rune",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = Spacing.lg).semantics { heading() }
            )
            Text(
                text = "One master password becomes a different password for every site. " +
                        "Nothing is stored, and nothing leaves this phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = InkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.sm)
            )
        }

        PasswordField(
            value = state.masterPassword,
            onValueChange = onMasterPasswordChange,
            label = "Master password",
            helper = "The one password you have to remember. Make it long.",
            error = state.passwordError
        )
        PasswordField(
            value = state.repeatPassword,
            onValueChange = onRepeatPasswordChange,
            label = "Repeat master password",
            error = state.repeatError,
            imeAction = if (state.hasSecretKey) ImeAction.Next else ImeAction.Done,
            onDone = onContinueClick,
            modifier = Modifier.padding(top = Spacing.lg)
        )

        CheckRow(
            checked = state.hasSecretKey,
            onCheckedChange = onHasSecretKeyChange,
            label = "I have a secret key from another device",
            modifier = Modifier.padding(top = Spacing.sm)
        )
        if (state.hasSecretKey) {
            RuneTextField(
                value = state.secretKeyText,
                onValueChange = onSecretKeyChange,
                label = "Secret key",
                placeholder = "AAAA BBBB CCCC …",
                helper = "52 letters and digits. Spaces don't matter.",
                error = state.secretKeyError,
                textStyle = RuneType.Secret,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { onContinueClick() }),
                modifier = Modifier.padding(top = Spacing.sm)
            )
        }

        PrimaryButton(
            label = "Continue",
            onClick = onContinueClick,
            modifier = Modifier.padding(top = Spacing.xl)
        )
    }
}

@Composable
private fun Deriving() {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.screen),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = Accent, trackColor = Surface300)
        Text(
            text = "Working out your key",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = Spacing.xl)
        )
        Text(
            text = "This is slow on purpose, so that guessing your password is slow too.",
            style = MaterialTheme.typography.bodySmall,
            color = InkMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.xs)
        )
    }
}

@Composable
private fun Confirm(
    state: SetupUiState,
    onWrittenDownChange: (Boolean) -> Unit,
    onBackToFormClick: () -> Unit,
    onSaveClick: () -> Unit
) {
    val isNewKey = state.newSecretKey.isNotEmpty()

    ScreenScaffold(topBar = {}) {
        Text(
            text = if (isNewKey) "Save your secret key" else "Check your fingerprint",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = Spacing.xxl, bottom = Spacing.lg).semantics { heading() }
        )

        Card {
            FieldLabel("Key fingerprint")
            Text(state.fingerprint, style = RuneType.Fingerprint, color = Accent)
            Text(
                text =
                    if (isNewKey) "Every device set up with the same master password and " +
                            "secret key shows these four characters."
                    else "This must match the fingerprint on your other device. If it " +
                            "doesn't, go back and retype your master password.",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
                modifier = Modifier.padding(top = Spacing.xs)
            )
        }

        if (isNewKey) {
            Card(Modifier.padding(top = Spacing.md)) {
                FieldLabel("Secret key")
                Text(state.newSecretKey, style = RuneType.Secret)
                Text(
                    text = "Write this down and keep it somewhere safe. A new device needs it " +
                            "together with your master password, and it can't be recovered " +
                            "if you lose it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                    modifier = Modifier.padding(top = Spacing.sm)
                )
            }
            CheckRow(
                checked = state.isWrittenDown,
                onCheckedChange = onWrittenDownChange,
                label = "I have written down my secret key",
                modifier = Modifier.padding(top = Spacing.sm)
            )
        }

        if (!state.canUseBiometrics) {
            Note(
                text = "No fingerprint is set up on this phone, so Rune will ask for your " +
                        "master password each time. You can turn on fingerprint unlock later " +
                        "in Settings.",
                modifier = Modifier.padding(top = Spacing.md)
            )
        }
        state.saveError?.let { ErrorNote(it, Modifier.padding(top = Spacing.md)) }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.xl),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            SecondaryButton(label = "Back", onClick = onBackToFormClick)
            PrimaryButton(
                label = "Save and continue",
                onClick = onSaveClick,
                enabled = state.canSave,
                isLoading = state.isSaving,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** A checkbox whose whole row is the touch target, label included. */
@Composable
private fun CheckRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(
                checkedColor = Accent,
                checkmarkColor = AccentInk,
                uncheckedColor = InkMuted
            ),
            modifier = Modifier.size(Sizes.icon)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = Spacing.md)
        )
    }
}
