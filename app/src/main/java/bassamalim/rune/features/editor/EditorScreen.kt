package bassamalim.rune.features.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bassamalim.rune.R
import bassamalim.rune.core.crypto.Rune
import bassamalim.rune.core.ui.components.Card
import bassamalim.rune.core.ui.components.ConfirmDialog
import bassamalim.rune.core.ui.components.DangerButton
import bassamalim.rune.core.ui.components.RuneTextField
import bassamalim.rune.core.ui.components.TextAction
import bassamalim.rune.core.ui.components.ToggleChip
import bassamalim.rune.core.ui.components.ErrorNote
import bassamalim.rune.core.ui.components.FieldLabel
import bassamalim.rune.core.ui.components.IconAction
import bassamalim.rune.core.ui.components.Note
import bassamalim.rune.core.ui.components.PrimaryButton
import bassamalim.rune.core.ui.components.ScreenScaffold
import bassamalim.rune.core.ui.components.TopBar
import bassamalim.rune.core.ui.theme.Accent
import bassamalim.rune.core.ui.theme.Ink
import bassamalim.rune.core.ui.theme.RuneType
import bassamalim.rune.core.ui.theme.InkFaint
import bassamalim.rune.core.ui.theme.InkMuted
import bassamalim.rune.core.ui.theme.Spacing
import bassamalim.rune.core.ui.theme.Surface300

/** Adding a site, or changing the settings its password is made from. */
@Composable
fun EditorScreen(viewModel: EditorViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    EditorScreen(
        state = state,
        onBackClick = viewModel::onBackClick,
        onSiteChange = viewModel::onSiteChange,
        onLoginChange = viewModel::onLoginChange,
        onLengthChange = viewModel::onLengthChange,
        onClassToggle = viewModel::onClassToggle,
        onSymbolSetChange = viewModel::onSymbolSetChange,
        onCounterChange = viewModel::onCounterChange,
        onSaveClick = viewModel::onSaveClick,
        onDeleteClick = viewModel::onDeleteClick
    )

    if (state.isConfirmingDelete) {
        ConfirmDialog(
            title = "Delete ${state.site}?",
            text = "Rune forgets this site's settings. Adding it again with the same settings " +
                    "gives the same password.",
            confirmLabel = "Delete",
            onConfirm = viewModel::onDeleteConfirm,
            onDismiss = viewModel::onDeleteDismiss
        )
    }
}

@Composable
internal fun EditorScreen(
    state: EditorUiState,
    onBackClick: () -> Unit,
    onSiteChange: (String) -> Unit,
    onLoginChange: (String) -> Unit,
    onLengthChange: (Int) -> Unit,
    onClassToggle: (CharacterClass) -> Unit,
    onSymbolSetChange: (String) -> Unit,
    onCounterChange: (Int) -> Unit,
    onSaveClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    ScreenScaffold(
        topBar = {
            TopBar(title = if (state.isEditing) "Edit site" else "Add site", onBack = onBackClick)
        }
    ) {
        if (state.isLoading) return@ScreenScaffold

        val plainText = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Next
        )

        Card {
            RuneTextField(
                value = state.site,
                onValueChange = onSiteChange,
                label = "Site",
                placeholder = "github.com",
                helper = "The address without https:// or www. Autofill matches on it.",
                error = state.siteError,
                keyboardOptions = plainText
            )
            RuneTextField(
                value = state.login,
                onValueChange = onLoginChange,
                label = "Login (optional)",
                placeholder = "username or email",
                helper = "Two logins on one site get two different passwords.",
                keyboardOptions = plainText.copy(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.padding(top = Spacing.lg)
            )
        }

        Card(Modifier.padding(top = Spacing.md)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FieldLabel("Length", Modifier.weight(1f))
                Text(
                    text = "${state.length} characters",
                    style = MaterialTheme.typography.labelMedium,
                    color = Ink
                )
            }
            Slider(
                value = state.length.toFloat(),
                onValueChange = { onLengthChange(it.toInt()) },
                valueRange = Rune.MIN_LENGTH.toFloat()..Rune.MAX_LENGTH.toFloat(),
                steps = Rune.MAX_LENGTH - Rune.MIN_LENGTH - 1,
                colors = SliderDefaults.colors(
                    thumbColor = Accent,
                    activeTrackColor = Accent,
                    inactiveTrackColor = Surface300,
                    // Ticks are painted in the track colours: 59 visible marks would be noise.
                    activeTickColor = Accent,
                    inactiveTickColor = Surface300
                ),
                modifier = Modifier.semantics { contentDescription = "Password length" }
            )

            FieldLabel("Characters", Modifier.padding(top = Spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ToggleChip("a–z", "lowercase letters", state.lower) { onClassToggle(CharacterClass.Lower) }
                ToggleChip("A–Z", "uppercase letters", state.upper) { onClassToggle(CharacterClass.Upper) }
                ToggleChip("0–9", "digits", state.digits) { onClassToggle(CharacterClass.Digits) }
                ToggleChip("!@#", "symbols", state.symbols) { onClassToggle(CharacterClass.Symbols) }
            }
            state.classesError?.let { ErrorNote(it, Modifier.padding(top = Spacing.xs)) }

            if (state.symbols) {
                RuneTextField(
                    value = state.symbolSet,
                    onValueChange = onSymbolSetChange,
                    label = "Symbols this site allows",
                    helper = "Narrow it when a site refuses some symbols.",
                    error = state.symbolsError,
                    textStyle = RuneType.Secret,
                    keyboardOptions = plainText.copy(
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.padding(top = Spacing.lg)
                )
                if (state.symbolSet != Rune.DEFAULT_SYMBOLS) {
                    TextAction(
                        label = "Use the usual symbols",
                        onClick = { onSymbolSetChange(Rune.DEFAULT_SYMBOLS) },
                        color = InkMuted
                    )
                }
            }

            FieldLabel("Counter", Modifier.padding(top = Spacing.lg))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconAction(
                    icon = R.drawable.ic_minus,
                    label = "Lower the counter",
                    onClick = { onCounterChange(-1) },
                    tint = if (state.counter > 1) Ink else InkFaint
                )
                Text(
                    text = state.counter.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .widthIn(min = 48.dp)
                        .semantics {
                            liveRegion = LiveRegionMode.Polite
                            contentDescription = "Counter ${state.counter}"
                        }
                )
                IconAction(
                    icon = R.drawable.ic_add,
                    label = "Raise the counter",
                    onClick = { onCounterChange(1) }
                )
            }
            Text(
                text = "Raise it to get a new password for this site, for example after a breach.",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted
            )

            Text(
                text = "Strength: ${state.strength}",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
                modifier = Modifier.padding(top = Spacing.md)
            )
        }

        if (state.isEditing) {
            Note(
                text = "Changing anything here changes this site's password.",
                modifier = Modifier.padding(top = Spacing.lg)
            )
        }
        state.saveError?.let { ErrorNote(it, Modifier.padding(top = Spacing.md)) }

        PrimaryButton(
            label = if (state.isEditing) "Save changes" else "Add site",
            onClick = onSaveClick,
            modifier = Modifier.padding(top = Spacing.xl)
        )
        if (state.isEditing) {
            DangerButton(
                label = "Delete site",
                icon = R.drawable.ic_trash,
                onClick = onDeleteClick,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.md)
            )
        }
    }
}
