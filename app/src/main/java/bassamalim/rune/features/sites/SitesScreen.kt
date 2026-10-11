package bassamalim.rune.features.sites

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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import bassamalim.rune.core.ui.components.ErrorNote
import bassamalim.rune.core.ui.components.IconAction
import bassamalim.rune.core.ui.components.PrimaryButton
import bassamalim.rune.core.ui.components.ScreenScaffold
import bassamalim.rune.core.ui.components.SearchField
import bassamalim.rune.core.ui.components.TopBar
import bassamalim.rune.core.ui.theme.Accent
import bassamalim.rune.core.ui.theme.AccentInk
import bassamalim.rune.core.ui.theme.RuneType
import bassamalim.rune.core.ui.theme.InkFaint
import bassamalim.rune.core.ui.theme.InkMuted
import bassamalim.rune.core.ui.theme.Spacing
import bassamalim.rune.core.ui.theme.Surface300

/** The list of sites: names and logins only, so it needs no lock. Each password has its own. */
@Composable
fun SitesScreen(viewModel: SitesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    SitesScreen(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onSiteClick = viewModel::onSiteClick,
        onAddClick = viewModel::onAddClick,
        onSettingsClick = viewModel::onSettingsClick
    )
}

@Composable
internal fun SitesScreen(
    state: SitesUiState,
    onQueryChange: (String) -> Unit,
    onSiteClick: (SiteRow) -> Unit,
    onAddClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        ScreenScaffold(
            scrollable = false,
            topBar = {
                TopBar(
                    title = "rune",
                    leading = {
                        Icon(
                            painter = painterResource(R.drawable.ic_rune_mark),
                            contentDescription = null,
                            tint = Accent,
                            modifier = Modifier.size(32.dp)
                        )
                    },
                    actions = {
                        if (state.unreadable == null) {
                            IconAction(R.drawable.ic_settings, "Settings", onSettingsClick)
                        }
                    }
                )
            }
        ) {
            when {
                state.isLoading -> Unit

                state.unreadable != null -> Unreadable(state.unreadable)

                state.isEmpty -> Empty(onAddClick)

                else -> {
                    SearchField(
                        value = state.query,
                        onValueChange = onQueryChange,
                        placeholder = "Search sites"
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        // Room for the last row to scroll clear of the add button.
                        contentPadding = PaddingValues(top = Spacing.sm, bottom = 96.dp)
                    ) {
                        items(state.rows, key = { "${it.site}\n${it.login}" }) { row ->
                            SiteRowItem(row = row, onClick = { onSiteClick(row) })
                        }
                        if (state.rows.isEmpty()) {
                            item {
                                Text(
                                    text = "No sites match \"${state.query.trim()}\".",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = InkMuted,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.xxl)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (!state.isLoading && !state.isEmpty && state.unreadable == null) {
            FloatingActionButton(
                onClick = onAddClick,
                containerColor = Accent,
                contentColor = AccentInk,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.screen)
            ) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = "Add a site")
            }
        }
    }
}

@Composable
private fun SiteRowItem(row: SiteRow, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Avatar(row.site)
        Column(Modifier.weight(1f)) {
            Text(
                text = row.site,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (row.login.isNotEmpty()) {
                Text(
                    text = row.login,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = InkFaint,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun Empty(onAddClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(bottom = Spacing.xxxl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_rune_mark),
            contentDescription = null,
            tint = Surface300,
            modifier = Modifier.size(88.dp)
        )
        Text(
            text = "No sites yet",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = Spacing.xl)
        )
        Text(
            text = "Add a site and Rune works out its password, the same one every time.",
            style = MaterialTheme.typography.bodyMedium,
            color = InkMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xl)
        )
        PrimaryButton(label = "Add your first site", onClick = onAddClick, icon = R.drawable.ic_add)
    }
}

@Composable
private fun Unreadable(reason: String) {
    Column(Modifier.fillMaxSize().padding(top = Spacing.xxl)) {
        Text("Rune can't read your site list", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "Rune has stopped here rather than start empty and overwrite it: the list " +
                    "holds counters that can't be worked out again.",
            style = MaterialTheme.typography.bodyMedium,
            color = InkMuted,
            modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.lg)
        )
        ErrorNote(reason)
        Text(
            text = reason,
            style = RuneType.Secret.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
            color = InkFaint,
            modifier = Modifier.padding(top = Spacing.lg)
        )
    }
}
