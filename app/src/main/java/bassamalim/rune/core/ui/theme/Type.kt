package bassamalim.rune.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import bassamalim.rune.R

@OptIn(ExperimentalTextApi::class)
private fun spaceGrotesk(weight: Int) = Font(
    resId = R.font.space_grotesk,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

/** Space Grotesk, bundled as a variable font and instanced at the weights the system uses. */
val SpaceGrotesk = FontFamily(spaceGrotesk(400), spaceGrotesk(500), spaceGrotesk(600), spaceGrotesk(700))

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
 * sensible Material slot live in [RuneType].
 */
val Typography = Typography(
    // Screen titles: "Rune", "Settings".
    headlineMedium = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight(700),
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.01).em
    ),
    // Top-bar titles and the site name on its own screen.
    titleLarge = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight(600),
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    // Site names in the list; card headers.
    titleMedium = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight(600),
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    // Text the user types.
    bodyLarge = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight(400),
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    // Descriptions and explanations.
    bodyMedium = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight(400),
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    // Helper text, logins under a site name.
    bodySmall = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight(400),
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    // Buttons.
    labelLarge = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight(600),
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    // Field labels, chips.
    labelMedium = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight(500),
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelSmall = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight(500),
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)

/** Styles with no Material slot. */
object RuneType {
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
