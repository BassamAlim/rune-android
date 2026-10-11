package bassamalim.rune.features.site

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
 * One site's password. Opening this screen asks for the fingerprint, and so does coming back to
 * it: the password exists only while you are looking at it.
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
        onCopyPreviousClick = viewModel::onCopyPreviousClick
    )

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
    onCopyPreviousClick: () -> Unit
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
            Avatar(state.site)
            Column(Modifier.padding(start = Spacing.md)) {
                Text(state.site, style = MaterialTheme.typography.titleLarge)
                if (state.login.isNotEmpty()) {
                    Text(state.login, style = MaterialTheme.typography.bodyMedium, color = InkMuted)
                }
            }
        }

        when {
            state.problem != null -> ErrorNote(state.problem)

            state.password == null -> Locked(onUnlockClick, onUsePasswordClick)

            else -> Unlocked(state, state.password, onRevealToggle, onCopyClick, onCopyPreviousClick)
        }
    }
}

@Composable
private fun Unlocked(
    state: SiteUiState,
    password: String,
    onRevealToggle: () -> Unit,
    onCopyClick: () -> Unit,
    onCopyPreviousClick: () -> Unit
) {
    Card {
        FieldLabel("Password")
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
            SecondaryButton(
                label = if (state.isPreviousCopied) "Copied" else "Copy previous password",
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
