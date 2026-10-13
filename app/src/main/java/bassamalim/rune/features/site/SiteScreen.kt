package bassamalim.rune.features.site

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import bassamalim.rune.core.ui.theme.Surface100
import bassamalim.rune.core.ui.theme.Ink
import bassamalim.rune.core.ui.components.RuneTextField
import bassamalim.rune.core.ui.components.ConfirmDialog
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bassamalim.rune.R
import bassamalim.rune.core.Globals
import bassamalim.rune.core.ui.components.Avatar
import bassamalim.rune.core.ui.components.rememberSiteName
import bassamalim.rune.core.ui.components.Card
import bassamalim.rune.core.ui.components.ErrorNote
import bassamalim.rune.core.ui.components.FieldLabel
import bassamalim.rune.core.ui.components.IconAction
import bassamalim.rune.core.ui.components.Note
import bassamalim.rune.core.ui.components.PrimaryButton
import bassamalim.rune.core.ui.components.ScreenScaffold
import bassamalim.rune.core.ui.components.SecondaryButton
import bassamalim.rune.core.ui.components.TextAction
import bassamalim.rune.core.ui.components.TopBar
import bassamalim.rune.core.ui.components.UnlockPrompts
import bassamalim.rune.core.ui.theme.Accent
import bassamalim.rune.core.ui.theme.RuneType
import bassamalim.rune.core.ui.theme.InkMuted
import bassamalim.rune.core.ui.theme.Spacing
import bassamalim.rune.core.ui.theme.Surface0

/**
 * One site's password, worked out on arrival from the key that unlocked the app (or asked for,
 * when there isn't one). It exists only while you are looking at it.
 */
@Composable
fun SiteScreen(viewModel: SiteViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onUnlockClick() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onHidden() }

    SiteScreen(
        state = state,
        onBackClick = viewModel::onBackClick,
        onEditClick = viewModel::onEditClick,
        onUnlockClick = viewModel::onUnlockClick,
        onUsePasswordClick = viewModel::onUsePasswordClick,
        onRevealToggle = viewModel::onRevealToggle,
        onCopyClick = viewModel::onCopyClick,
        onCopyPreviousClick = viewModel::onCopyPreviousClick,
        onKeepClick = viewModel::onKeepClick,
        onSwitchClick = viewModel::onSwitchClick
    )

    if (state.isKeeping) {
        KeepDialog(onKeep = viewModel::onKeepConfirm, onDismiss = viewModel::onKeepDismiss)
    }
    if (state.isConfirmingSwitch) {
        ConfirmDialog(
            title = "Switch to Rune's password?",
            text = "Do this once the site has Rune's password. Rune forgets the old one and shows " +
                    "its own from now on.",
            confirmLabel = "Switch",
            onConfirm = viewModel::onSwitchConfirm,
            onDismiss = viewModel::onSwitchDismiss
        )
    }

    UnlockPrompts(
        step = state.unlock,
        reason = "Unlock ${state.site}",
        onBiometricSucceeded = { viewModel.onBiometricSucceeded(it) },
        onUsePassword = viewModel::onUsePasswordClick,
        onPassword = { viewModel.onMasterPassword(it) },
        onSkipPractice = viewModel::onSkipPracticeClick,
        onCancel = viewModel::onUnlockCancelled
    )
}

@Composable
internal fun SiteScreen(
    state: SiteUiState,
    onBackClick: () -> Unit,
    onEditClick: () -> Unit,
    onUnlockClick: () -> Unit,
    onUsePasswordClick: () -> Unit,
    onRevealToggle: () -> Unit,
    onCopyClick: () -> Unit,
    onCopyPreviousClick: () -> Unit,
    onKeepClick: () -> Unit,
    onSwitchClick: () -> Unit
) {
    ScreenScaffold(
        topBar = {
            TopBar(
                title = "",
                onBack = onBackClick,
                actions = { IconAction(R.drawable.ic_edit, "Edit site", onEditClick) }
            )
        }
    ) {
        Row(
            modifier = Modifier.padding(bottom = Spacing.xl),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val name = rememberSiteName(state.site)
            val subtitle = state.login
            Avatar(state.site)
            Column(Modifier.padding(start = Spacing.md)) {
                Text(name, style = MaterialTheme.typography.titleLarge)
                if (subtitle.isNotEmpty()) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = InkMuted)
                }
                if (state.note.isNotEmpty()) {
                    // Selectable, so a username in it can be long-pressed and copied.
                    SelectionContainer(Modifier.padding(top = Spacing.xs)) {
                        Text(state.note, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        when {
            state.problem != null -> {
                ErrorNote(state.problem)
                // A kept password that can't be opened mustn't trap the site.
                if (state.hasSealed) {
                    TextAction(label = "Forget the kept password", onClick = onSwitchClick)
                }
            }

            state.password == null -> Locked(onUnlockClick, onUsePasswordClick)

            else -> Unlocked(
                state, state.password, onRevealToggle, onCopyClick, onCopyPreviousClick,
                onKeepClick, onSwitchClick
            )
        }
    }
}

@Composable
private fun Unlocked(
    state: SiteUiState,
    password: String,
    onRevealToggle: () -> Unit,
    onCopyClick: () -> Unit,
    onCopyPreviousClick: () -> Unit,
    onKeepClick: () -> Unit,
    onSwitchClick: () -> Unit
) {
    Card {
        FieldLabel(if (state.isKept) "Your old password" else "Password")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(Surface0)
                .heightIn(min = 56.dp)
                .padding(start = Spacing.lg, end = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (state.isRevealed) password else "•".repeat(password.length),
                style = RuneType.Secret,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = Spacing.md)
                    .semantics {
                        // A screen reader shouldn't read out a row of bullets, or the password
                        // while it's hidden.
                        if (!state.isRevealed) contentDescription = "Password hidden"
                    }
            )
            IconAction(
                icon = if (state.isRevealed) R.drawable.ic_eye_off else R.drawable.ic_eye,
                label = if (state.isRevealed) "Hide password" else "Show password",
                onClick = onRevealToggle,
                tint = InkMuted
            )
        }

        PrimaryButton(
            label = if (state.isCopied) "Copied" else "Copy password",
            icon = if (state.isCopied) R.drawable.ic_check else R.drawable.ic_copy,
            onClick = onCopyClick,
            modifier = Modifier.padding(top = Spacing.md)
        )

        if (state.hasPrevious) {
            val second = if (state.isKept) "Copy Rune's password" else "Copy previous password"
            SecondaryButton(
                label = if (state.isPreviousCopied) "Copied" else second,
                icon = if (state.isPreviousCopied) R.drawable.ic_check else R.drawable.ic_copy,
                onClick = onCopyPreviousClick,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)
            )
        }

        Text(
            text =
                if (state.isCopied || state.isPreviousCopied) "The clipboard is cleared after " +
                        "${Globals.CLIPBOARD_CLEAR_MILLIS / 1000} seconds."
                else state.details,
            style = MaterialTheme.typography.bodySmall,
            color = InkMuted,
            modifier = Modifier.padding(top = Spacing.md)
        )
    }

    if (state.isKept) {
        Note(
            text = "Change it on the site to Rune's password, then switch.",
            modifier = Modifier.padding(top = Spacing.lg)
        )
        TextAction(label = "Switch to Rune's password", onClick = onSwitchClick)
    } else {
        TextAction(
            label = "Use my old password for now",
            onClick = onKeepClick,
            modifier = Modifier.padding(top = Spacing.sm)
        )
    }

    Note(
        text = "Autofill types the password for you and skips the clipboard. Turn it on in Settings.",
        icon = R.drawable.ic_autofill,
        modifier = Modifier.padding(top = Spacing.lg)
    )
}

@Composable
private fun Locked(onUnlockClick: () -> Unit, onUsePasswordClick: () -> Unit) {
    Card {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(R.drawable.ic_lock),
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.padding(top = Spacing.sm).size(32.dp)
            )
            Text(
                text = "Locked",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = Spacing.md)
            )
            Text(
                text = "Rune works the password out when you unlock. It isn't stored anywhere.",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.lg)
            )
            PrimaryButton(
                label = "Unlock",
                icon = R.drawable.ic_fingerprint,
                onClick = onUnlockClick
            )
            TextAction(
                label = "Use master password",
                onClick = onUsePasswordClick,
                color = InkMuted,
                modifier = Modifier.padding(top = Spacing.xs)
            )
        }
    }
}

/** The site's current password, typed in to keep until it's changed to Rune's. */
@Composable
private fun KeepDialog(onKeep: (String) -> Unit, onDismiss: () -> Unit) {
    // Not saveable: a password doesn't belong in saved instance state.
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Use my old password", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text(
                    "Rune keeps it encrypted under your master password and shows it here until " +
                            "you switch to Rune's.",
                    style = MaterialTheme.typography.bodyMedium
                )
                RuneTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Current password on this site",
                    textStyle = RuneType.Secret,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        autoCorrectEnabled = false
                    ),
                    modifier = Modifier.padding(top = Spacing.lg)
                )
            }
        },
        confirmButton = {
            TextAction(label = "Keep it", onClick = { onKeep(password) })
        },
        dismissButton = { TextAction(label = "Cancel", onClick = onDismiss, color = InkMuted) },
        containerColor = Surface100,
        titleContentColor = Ink,
        textContentColor = InkMuted
    )
}
