package bassamalim.echo.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Radius tokens. [Shapes.medium] is the workhorse: inputs, buttons, list rows. */
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),      // cards
    extraLarge = RoundedCornerShape(28.dp)  // bottom sheets and dialogs
)

/** Avatars and pill-shaped chips. */
val Pill = RoundedCornerShape(percent = 50)
