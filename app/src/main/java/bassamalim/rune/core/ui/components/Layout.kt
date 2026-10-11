package bassamalim.rune.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import bassamalim.rune.R
import bassamalim.rune.core.ui.theme.Accent
import bassamalim.rune.core.ui.theme.AccentWash
import bassamalim.rune.core.ui.theme.Danger
import bassamalim.rune.core.ui.theme.InkMuted
import bassamalim.rune.core.ui.theme.Sizes
import bassamalim.rune.core.ui.theme.Spacing
import bassamalim.rune.core.ui.theme.Surface0
import bassamalim.rune.core.ui.theme.Surface100
import bassamalim.rune.core.ui.theme.Surface300

/**
 * The frame every screen sits in: a fixed bar on top, then content that scrolls, moves out of
 * the keyboard's way, and never grows wider than a readable column.
 */
@Composable
fun ScreenScaffold(
    topBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize().background(Surface0).imePadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(Modifier.widthIn(max = Sizes.maxContentWidth).fillMaxSize()) {
            topBar()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(horizontal = Spacing.screen)
                    .padding(bottom = Spacing.xl),
                content = content
            )
        }
    }
}

/** Back arrow (when there is somewhere to go back to), title, and up to a couple of actions. */
@Composable
fun TopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    leading: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(
                start = if (onBack != null) Spacing.sm else Spacing.screen,
                end = Spacing.sm
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconAction(icon = R.drawable.ic_back, label = "Back", onClick = onBack)
        }
        if (leading != null) {
            leading()
            Box(Modifier.size(Spacing.md))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = if (onBack != null) Spacing.xs else 0.dp)
                .semantics { heading() }
        )
        actions()
    }
}

/** A grouped block of related content. */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    border: Color = Surface300,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Surface100)
            .border(Sizes.border, border, MaterialTheme.shapes.large)
            .padding(Spacing.lg),
        content = content
    )
}

@Composable
fun CardTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(bottom = Spacing.xs).semantics { heading() }
    )
}

/** The site's first letter on a tinted tile. Decoration: the name is right beside it. */
@Composable
fun Avatar(name: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(Sizes.avatar)
            .clip(MaterialTheme.shapes.medium)
            .background(AccentWash)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleMedium,
            color = Accent
        )
    }
}

/** A line with an icon in front: an error, a warning, a confirmation. Never colour alone. */
@Composable
fun Note(
    text: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int = R.drawable.ic_warning,
    color: Color = InkMuted
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = color,
            modifier = Modifier.padding(top = 1.dp).size(16.dp)
        )
        Text(text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

@Composable
fun ErrorNote(text: String, modifier: Modifier = Modifier) =
    Note(text = text, modifier = modifier, color = Danger)
