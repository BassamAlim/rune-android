package bassamalim.rune.features.autofill

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bassamalim.rune.R
import bassamalim.rune.core.ui.components.Avatar
import bassamalim.rune.core.ui.components.rememberSiteName
import bassamalim.rune.core.ui.components.ErrorNote
import bassamalim.rune.core.ui.components.PrimaryButton
import bassamalim.rune.core.ui.components.ScreenScaffold
import bassamalim.rune.core.ui.components.SearchField
import bassamalim.rune.core.ui.components.TextAction
import bassamalim.rune.core.ui.components.TopBar
import bassamalim.rune.core.ui.components.UnlockPrompts
import bassamalim.rune.core.ui.theme.Accent
import bassamalim.rune.core.ui.theme.AccentWash
import bassamalim.rune.core.ui.theme.InkMuted
import bassamalim.rune.core.ui.theme.Sizes
import bassamalim.rune.core.ui.theme.Spacing
import androidx.compose.foundation.background

/** What a tapped autofill suggestion opens: choose the site if need be, then unlock it. */
@Composable
fun AutofillScreen(
    onResult: (FillResult) -> Unit,
    onCancel: () -> Unit,
    viewModel: AutofillViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.result) { state.result?.let(onResult) }
    LaunchedEffect(state.isCancelled) { if (state.isCancelled) onCancel() }

    AutofillScreen(
        state = state,
        onCloseClick = onCancel,
        onQueryChange = viewModel::onQueryChange,
        onSiteClick = viewModel::onSiteClick,
        onSuggestionClick = { viewModel.onSuggestionClick(it) },
        onUnlockClick = viewModel::onUnlockClick
    )

    UnlockPrompts(
        step = state.unlock,
        reason = "Fill ${state.chosen ?: "password"}",
        onBiometricSucceeded = { viewModel.onBiometricSucceeded(it) },
        onUsePassword = viewModel::onUsePasswordClick,
        onPassword = { viewModel.onMasterPassword(it) },
        onSkipPractice = viewModel::onSkipPracticeClick,
        onCancel = viewModel::onUnlockCancelled
    )
}

@Composable
internal fun AutofillScreen(
    state: AutofillUiState,
    onCloseClick: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSiteClick: (AutofillRow) -> Unit,
    onSuggestionClick: (String) -> Unit,
    onUnlockClick: () -> Unit
) {
    ScreenScaffold(
        scrollable = false,
        topBar = {
            TopBar(
                title = "Fill with Rune",
                leading = {
                    Icon(
                        painter = painterResource(R.drawable.ic_rune_mark),
                        contentDescription = null,
                        tint = Accent,
                        modifier = Modifier.size(32.dp)
                    )
                },
                actions = { TextAction("Cancel", onCloseClick, color = InkMuted) }
            )
        }
    ) {
        when {
            state.isLoading -> Unit

            state.chosen != null -> Unlocking(state.chosen, onUnlockClick)

            else -> Picker(state, onQueryChange, onSiteClick, onSuggestionClick)
        }
    }
}

@Composable
private fun Picker(
    state: AutofillUiState,
    onQueryChange: (String) -> Unit,
    onSiteClick: (AutofillRow) -> Unit,
    onSuggestionClick: (String) -> Unit
) {
    Text(
        text = state.domain?.let { "Which site's password goes into $it?" }
            ?: "Which site's password goes into this app? Rune remembers your choice.",
        style = MaterialTheme.typography.bodyMedium,
        color = InkMuted,
        modifier = Modifier.padding(bottom = Spacing.md)
    )
    state.problem?.let { ErrorNote(it, Modifier.padding(bottom = Spacing.md)) }
    SearchField(value = state.query, onValueChange = onQueryChange, placeholder = "Search sites")

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = Spacing.sm)
    ) {
        state.suggestion?.let { name ->
            item(key = "suggestion") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable(role = Role.Button) { onSuggestionClick(name) }
                        .heightIn(min = 64.dp)
                        .padding(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    Box(
                        modifier = Modifier.size(Sizes.avatar).clip(MaterialTheme.shapes.medium).background(AccentWash),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_add),
                            contentDescription = null,
                            tint = Accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text("Add $name", style = MaterialTheme.typography.titleMedium, color = Accent)
                        Text(
                            text = "A new site with the usual settings",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkMuted
                        )
                    }
                }
            }
        }
        items(state.rows, key = { "${it.site}\n${it.login}" }) { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(role = Role.Button) { onSiteClick(row) }
                    .heightIn(min = 64.dp)
                    .padding(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                val name = rememberSiteName(row.site)
                val subtitle = row.login
                Avatar(row.site)
                Column {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle.isNotEmpty()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = InkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        if (state.rows.isEmpty() && state.suggestion == null) {
            item {
                Text(
                    text = if (state.query.isBlank()) "No sites yet. Add one in Rune first."
                    else "No sites match \"${state.query.trim()}\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.xxl)
                )
            }
        }
    }
}

/** Behind the fingerprint sheet, and what's left if the sheet is dismissed. */
@Composable
private fun Unlocking(site: String, onUnlockClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(bottom = Spacing.xxxl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Avatar(site)
        Text(
            text = rememberSiteName(site),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = Spacing.md)
        )
        Text(
            text = "Unlock to fill this site's password.",
            style = MaterialTheme.typography.bodyMedium,
            color = InkMuted,
            modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.xl)
        )
        PrimaryButton(label = "Unlock", icon = R.drawable.ic_fingerprint, onClick = onUnlockClick)
    }
}
