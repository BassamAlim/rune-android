package bassamalim.echo.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Echo is dark-only by design: no light scheme, and no dynamic colour: the green is the app's
 * one recognisable signal and Material You would take it away.
 */
private val EchoColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = AccentInk,
    primaryContainer = AccentWash,
    onPrimaryContainer = Accent,
    secondary = InkMuted,
    onSecondary = Surface0,
    secondaryContainer = Surface200,
    onSecondaryContainer = Ink,
    tertiary = Accent,
    onTertiary = AccentInk,
    background = Surface0,
    onBackground = Ink,
    surface = Surface0,
    onSurface = Ink,
    surfaceVariant = Surface200,
    onSurfaceVariant = InkMuted,
    surfaceContainerLowest = Surface0,
    surfaceContainerLow = Surface100,
    surfaceContainer = Surface100,
    surfaceContainerHigh = Surface200,
    surfaceContainerHighest = Surface200,
    inverseSurface = Ink,
    inverseOnSurface = Surface0,
    outline = Surface300,
    outlineVariant = Surface300,
    error = Danger,
    onError = Surface0,
    errorContainer = DangerWash,
    onErrorContainer = Danger,
    scrim = Overlay
)

@Composable
fun EchoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EchoColorScheme,
        typography = Typography,
        shapes = Shapes
    ) {
        // The whole app sits on one Surface so that text with no explicit colour inherits [Ink].
        Surface(color = Surface0, contentColor = Ink, content = content)
    }
}
