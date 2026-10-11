package bassamalim.rune.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import bassamalim.rune.core.ui.theme.Accent
import bassamalim.rune.core.ui.theme.AccentInk
import bassamalim.rune.core.ui.theme.AccentWash
import bassamalim.rune.core.ui.theme.Danger
import bassamalim.rune.core.ui.theme.Ink
import bassamalim.rune.core.ui.theme.InkFaint
import bassamalim.rune.core.ui.theme.InkMuted
import bassamalim.rune.core.ui.theme.Sizes
import bassamalim.rune.core.ui.theme.Spacing
import bassamalim.rune.core.ui.theme.Surface200
import bassamalim.rune.core.ui.theme.Surface300

@Composable
private fun ButtonContent(label: String, @DrawableRes icon: Int?, isLoading: Boolean) {
    if (isLoading) {
        CircularProgressIndicator(
            color = LocalContentColor.current,
            strokeWidth = 2.dp,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(Spacing.sm))
    } else if (icon != null) {
        // Decorative: the label beside it says the same thing.
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Spacing.sm))
    }
    Text(label, style = MaterialTheme.typography.labelLarge)
}

/** The one filled, high-emphasis action on a screen. */
@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = Accent,
            contentColor = AccentInk,
            disabledContainerColor = Surface200,
            disabledContentColor = InkFaint
        ),
        contentPadding = PaddingValues(horizontal = Spacing.xl),
        modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = Sizes.control)
    ) { ButtonContent(label, icon, isLoading) }
}

/** A quieter action that sits beside or under the primary one. */
@Composable
fun SecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(Sizes.border, Surface300),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Surface200,
            contentColor = Ink,
            disabledContentColor = InkFaint
        ),
        contentPadding = PaddingValues(horizontal = Spacing.lg),
        modifier = modifier.defaultMinSize(minHeight = Sizes.control)
    ) { ButtonContent(label, icon, isLoading = false) }
}

/** Something that can't be taken back. Outlined, never filled: it should not be the easy tap. */
@Composable
fun DangerButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null
) {
    OutlinedButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(Sizes.border, Danger.copy(alpha = 0.6f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger),
        contentPadding = PaddingValues(horizontal = Spacing.lg),
        modifier = modifier.defaultMinSize(minHeight = Sizes.control)
    ) { ButtonContent(label, icon, isLoading = false) }
}

@Composable
fun TextAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Accent
) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(contentColor = color),
        modifier = modifier.defaultMinSize(minHeight = Sizes.iconButton)
    ) { Text(label, style = MaterialTheme.typography.labelLarge) }
}

/** An icon on its own. [label] is read by screen readers, since there is no text to read. */
@Composable
fun IconAction(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Ink
) {
    IconButton(onClick = onClick, modifier = modifier.size(Sizes.iconButton)) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(Sizes.icon)
        )
    }
}

/** One of a set of on/off choices: the editor's character kinds, setup's two ways to start. */
@Composable
fun ToggleChip(label: String, spoken: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Surface200,
            labelColor = InkMuted,
            selectedContainerColor = AccentWash,
            selectedLabelColor = Accent
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = Surface300,
            selectedBorderColor = Accent
        ),
        modifier = Modifier
            .padding(vertical = Spacing.xs)
            .semantics { contentDescription = spoken }
    )
}
