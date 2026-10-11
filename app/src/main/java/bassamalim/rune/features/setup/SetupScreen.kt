package bassamalim.rune.features.setup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import bassamalim.rune.core.ui.components.ToggleChip
import bassamalim.rune.core.ui.components.promptWithCipher
import bassamalim.rune.core.ui.theme.Accent
import bassamalim.rune.core.ui.theme.Danger
import bassamalim.rune.core.ui.theme.RuneType
import bassamalim.rune.core.ui.theme.InkMuted
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
        onGenerateClick = viewModel::onGenerateClick,
        onOwnClick = viewModel::onOwnClick,
        onMasterPasswordChange = viewModel::onMasterPasswordChange,
        onRepeatPasswordChange = viewModel::onRepeatPasswordChange,
        onContinueClick = viewModel::onContinueClick,
        onBackToFormClick = viewModel::onBackToFormClick,
        onSaveClick = viewModel::onSaveClick
    )
}

@Composable
internal fun SetupScreen(
    state: SetupUiState,
    onGenerateClick: () -> Unit,
    onOwnClick: () -> Unit,
    onMasterPasswordChange: (String) -> Unit,
    onRepeatPasswordChange: (String) -> Unit,
    onContinueClick: () -> Unit,
    onBackToFormClick: () -> Unit,
    onSaveClick: () -> Unit
) {
    when (state.step) {
        SetupStep.Form -> Form(
            state, onGenerateClick, onOwnClick, onMasterPasswordChange, onRepeatPasswordChange,
            onContinueClick
        )

        SetupStep.Deriving -> Deriving()

        SetupStep.Confirm -> Confirm(state, onBackToFormClick, onSaveClick)
    }
}

@Composable
private fun Form(
    state: SetupUiState,
    onGenerateClick: () -> Unit,
    onOwnClick: () -> Unit,
    onMasterPasswordChange: (String) -> Unit,
    onRepeatPasswordChange: (String) -> Unit,
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

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ToggleChip("Generate one", "generate a master password", state.generated != null) {
                if (state.generated == null) onGenerateClick()
            }
            ToggleChip("Use my own", "use my own master password", state.generated == null, onOwnClick)
        }

        if (state.generated != null) {
            Card(Modifier.padding(top = Spacing.sm)) {
                FieldLabel("Your master password")
                Text(state.generated, style = RuneType.Secret, color = Accent)
                SecondaryButton(
                    label = "New words",
                    onClick = onGenerateClick,
                    modifier = Modifier.padding(top = Spacing.md)
                )
                Text(
                    text = "Write it down until you know it by heart. Forgetting it loses every " +
                            "password. ${state.generatedStrength}: out of reach even if a site " +
                            "leaks one of your passwords.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                    modifier = Modifier.padding(top = Spacing.sm)
                )
            }
            RuneTextField(
                value = state.repeatPassword,
                onValueChange = onRepeatPasswordChange,
                label = "Type it back",
                error = state.repeatError,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { onContinueClick() }),
                modifier = Modifier.padding(top = Spacing.lg)
            )
        } else {
            PasswordField(
                value = state.masterPassword,
                onValueChange = onMasterPasswordChange,
                label = "Master password",
                helper = "The one password you have to remember. Several unusual words work well.",
                error = state.passwordError,
                modifier = Modifier.padding(top = Spacing.sm)
            )
            state.strength?.let {
                Text(
                    text = it.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (it.level) {
                        StrengthLevel.Weak -> Danger
                        StrengthLevel.Fair -> InkMuted
                        StrengthLevel.Strong -> Accent
                    },
                    modifier = Modifier.padding(top = Spacing.xs)
                )
            }
            PasswordField(
                value = state.repeatPassword,
                onValueChange = onRepeatPasswordChange,
                label = "Repeat master password",
                error = state.repeatError,
                imeAction = ImeAction.Done,
                onDone = onContinueClick,
                modifier = Modifier.padding(top = Spacing.lg)
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
    onBackToFormClick: () -> Unit,
    onSaveClick: () -> Unit
) {
    ScreenScaffold(topBar = {}) {
        Text(
            text = "Check your fingerprint",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = Spacing.xxl, bottom = Spacing.lg).semantics { heading() }
        )

        Card {
            FieldLabel("Key fingerprint")
            Text(state.fingerprint, style = RuneType.Fingerprint, color = Accent)
            Text(
                text = "Every device with the same master password shows these four " +
                        "characters. If you use Rune on another device and its fingerprint " +
                        "differs, go back and retype your master password.",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
                modifier = Modifier.padding(top = Spacing.xs)
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
                isLoading = state.isSaving,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
