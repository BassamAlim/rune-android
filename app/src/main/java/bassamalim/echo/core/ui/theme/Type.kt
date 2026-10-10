package bassamalim.echo.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import bassamalim.echo.R

@OptIn(ExperimentalTextApi::class)
private fun inter(weight: Int) = Font(
    resId = R.font.inter,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

/** Inter, bundled as a variable font and instanced at the weights the system uses. */
val Inter = FontFamily(inter(400), inter(500), inter(600), inter(700))

@OptIn(ExperimentalTextApi::class)
private fun jetBrainsMono(weight: Int) = Font(
    resId = R.font.jetbrains_mono,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

/** JetBrains Mono, for passwords and keys: 0 and O, l and 1 stay apart. */
val JetBrainsMono = FontFamily(jetBrainsMono(400), jetBrainsMono(500))

/**
 * The text styles mapped onto the Material 3 slots the components read. Named styles with no
 * sensible Material slot live in [EchoType].
 */
val Typography = Typography(
    // Screen titles: "Echo", "Settings".
    headlineMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight(700),
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.01).em
    ),
    // Top-bar titles and the site name on its own screen.
    titleLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight(600),
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    // Site names in the list; card headers.
    titleMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight(600),
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    // Text the user types.
    bodyLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight(400),
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    // Descriptions and explanations.
    bodyMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight(400),
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    // Helper text, logins under a site name.
    bodySmall = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight(400),
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    // Buttons.
    labelLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight(600),
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    // Field labels, chips.
    labelMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight(500),
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelSmall = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight(500),
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)

/** Styles with no Material slot. */
object EchoType {
    /** A generated password, or a secret key. */
    val Secret = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight(500),
        fontSize = 18.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.02.em
    )
    /** The key fingerprint, large enough to compare across two screens at a glance. */
    val Fingerprint = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight(500),
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.08.em
    )
}
