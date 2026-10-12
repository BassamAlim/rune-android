package bassamalim.rune.features.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.remember
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import bassamalim.rune.core.ui.components.SearchField
import bassamalim.rune.core.ui.theme.Surface0
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import bassamalim.rune.core.data.repositories.InstalledApp
import bassamalim.rune.core.ui.components.Avatar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
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
import bassamalim.rune.core.ui.components.ErrorNote
import bassamalim.rune.core.ui.components.FieldLabel
import bassamalim.rune.core.ui.components.IconAction
import bassamalim.rune.core.ui.components.Note
import bassamalim.rune.core.ui.components.PrimaryButton
import bassamalim.rune.core.ui.components.RuneTextField
import bassamalim.rune.core.ui.components.ScreenScaffold
import bassamalim.rune.core.ui.components.TextAction
import bassamalim.rune.core.ui.components.ToggleChip
import bassamalim.rune.core.ui.components.TopBar
import bassamalim.rune.core.ui.theme.Accent
import bassamalim.rune.core.ui.theme.Ink
import bassamalim.rune.core.ui.theme.InkFaint
import bassamalim.rune.core.ui.theme.InkMuted
import bassamalim.rune.core.ui.theme.RuneType
import bassamalim.rune.core.ui.theme.Sizes
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
        onNoteChange = viewModel::onNoteChange,
        onLengthChange = viewModel::onLengthChange,
        onClassToggle = viewModel::onClassToggle,
        onSymbolSetChange = viewModel::onSymbolSetChange,
        onCounterChange = viewModel::onCounterChange,
        onLinkAppClick = viewModel::onLinkAppClick,
        onAppUnlink = viewModel::onAppUnlink,
        onSaveClick = viewModel::onSaveClick,
        onDeleteClick = viewModel::onDeleteClick
    )

    state.appChoices?.let { choices ->
        AppPicker(choices, state.site, onPick = viewModel::onAppPick, onDismiss = viewModel::onAppPickerDismiss)
    }

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
    onNoteChange: (String) -> Unit,
    onLengthChange: (Int) -> Unit,
    onClassToggle: (CharacterClass) -> Unit,
    onSymbolSetChange: (String) -> Unit,
    onCounterChange: (Int) -> Unit,
    onLinkAppClick: () -> Unit,
    onAppUnlink: (InstalledApp) -> Unit,
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
            // A site is named or picked as an app: picking one fills in an empty name.
            RuneTextField(
                value = state.site,
                onValueChange = onSiteChange,
                label = "Site",
                placeholder = "github.com",
                helper = "The address without https:// or www. For an app, pick it below " +
                        "and Rune names it.",
                error = state.siteError,
                keyboardOptions = plainText
            )
            for (app in state.apps) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = Spacing.sm)
                ) {
                    Avatar(app.label, app = app.packageName)
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = Spacing.md)
                    )
                    IconAction(
                        icon = R.drawable.ic_close,
                        label = "Unlink ${app.label}",
                        onClick = { onAppUnlink(app) },
                        tint = InkMuted
                    )
                }
            }
            TextAction(
                label = if (state.apps.isEmpty()) "Pick an app" else "Link another app",
                onClick = onLinkAppClick
            )

            RuneTextField(
                value = state.login,
                onValueChange = onLoginChange,
                label = "Login (optional)",
                placeholder = "username or email",
                keyboardOptions = plainText.copy(keyboardType = KeyboardType.Email),
                modifier = Modifier.padding(top = Spacing.sm)
            )
            RuneTextField(
                value = state.note,
                onValueChange = onNoteChange,
                label = "Note (optional)",
                placeholder = "username: bassam_m",
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.padding(top = Spacing.lg)
            )
        }

        // Everything but site and login is rarely touched, so it folds away unless already in use.
        var isExpanded by rememberSaveable {
            mutableStateOf(
                state.length != Rune.DEFAULT_LENGTH || state.classes != "luds" ||
                        state.symbolSet != Rune.DEFAULT_SYMBOLS || state.counter != 1
            )
        }
        TextButton(
            onClick = { isExpanded = !isExpanded },
            colors = ButtonDefaults.textButtonColors(contentColor = Accent),
            modifier = Modifier.padding(vertical = Spacing.xs).heightIn(min = Sizes.iconButton)
        ) {
            Text(
                if (isExpanded) "Fewer options" else "More options",
                style = MaterialTheme.typography.labelLarge
            )
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                modifier = Modifier
                    .padding(start = Spacing.xs)
                    .size(Sizes.icon)
                    .rotate(if (isExpanded) -90f else 90f)
            )
        }

        if (isExpanded || state.classesError != null || state.symbolsError != null) Card {
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

/** Every app with a launcher icon, to link one to this site, full screen with a search. */
@Composable
private fun AppPicker(
    apps: List<InstalledApp>,
    site: String,
    onPick: (InstalledApp) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(Modifier.background(Surface0).windowInsetsPadding(WindowInsets.safeDrawing)) {
            AppPickerContent(apps, site, onPick, onDismiss)
        }
    }
}

@Composable
internal fun AppPickerContent(
    apps: List<InstalledApp>,
    site: String,
    onPick: (InstalledApp) -> Unit,
    onDismiss: () -> Unit,
    initialQuery: String = ""
) {
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    val (suggested, others) = remember(apps, site, query) { appChoices(apps, site, query) }

    ScreenScaffold(
        scrollable = false,
        topBar = {
            TopBar(
                title = "Link an app",
                actions = { IconAction(R.drawable.ic_close, "Close", onDismiss) }
            )
        }
    ) {
        SearchField(value = query, onValueChange = { query = it }, placeholder = "Search apps")
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = Spacing.sm, bottom = Spacing.xl)
        ) {
            if (suggested.isNotEmpty()) {
                item { FieldLabel("Suggested for $site", Modifier.padding(top = Spacing.sm)) }
                items(suggested, key = { "s:${it.packageName}" }) { AppRow(it, onPick) }
                item { FieldLabel("All apps", Modifier.padding(top = Spacing.lg)) }
            }
            items(others, key = { it.packageName }) { AppRow(it, onPick) }
            if (suggested.isEmpty() && others.isEmpty()) {
                item {
                    Text(
                        text = if (query.isBlank()) "Every app is already linked."
                        else "No apps match \"${query.trim()}\".",
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

/**
 * Apps matching [query] by name or package, split into those that look like [site] ("github.com"
 * suggests GitHub) and the rest. Suggestions only show before a search is typed.
 */
internal fun appChoices(
    apps: List<InstalledApp>,
    site: String,
    query: String
): Pair<List<InstalledApp>, List<InstalledApp>> {
    val needle = query.trim().lowercase()
    if (needle.isNotEmpty()) {
        return emptyList<InstalledApp>() to apps.filter {
            needle in it.label.lowercase() || needle in it.packageName.lowercase()
        }
    }

    // The name a site is known by: "github" in "gist.github.com", "my bank" as it is.
    val parts = Rune.normalize(site).substringBefore('/').split('.')
    val stem = (if (parts.size >= 2) parts[parts.size - 2] else parts[0]).replace(" ", "")
    if (stem.length < 3) return emptyList<InstalledApp>() to apps

    return apps.partition {
        stem in it.label.lowercase().replace(" ", "") || stem in it.packageName.lowercase()
    }
}

@Composable
private fun AppRow(app: InstalledApp, onPick: (InstalledApp) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button) { onPick(app) }
            .heightIn(min = 64.dp)
            .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Avatar(app.label, app = app.packageName)
        Column(Modifier.weight(1f)) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
