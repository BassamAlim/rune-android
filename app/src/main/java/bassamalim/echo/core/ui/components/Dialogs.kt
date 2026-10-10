package bassamalim.echo.core.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import bassamalim.echo.core.ui.theme.Accent
import bassamalim.echo.core.ui.theme.Danger
import bassamalim.echo.core.ui.theme.Ink
import bassamalim.echo.core.ui.theme.InkMuted
import bassamalim.echo.core.ui.theme.Surface100

/** Asked before anything that can't be undone. The confirm button says what it will do. */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDestructive: Boolean = true
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(text, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextAction(
                label = confirmLabel,
                onClick = onConfirm,
                color = if (isDestructive) Danger else Accent
            )
        },
        dismissButton = { TextAction(label = "Cancel", onClick = onDismiss, color = InkMuted) },
        containerColor = Surface100,
        titleContentColor = Ink,
        textContentColor = InkMuted
    )
}
