package bassamalim.rune.core.ui.theme

import androidx.compose.ui.unit.dp

/** The spacing scale. Anything that isn't on it is a mistake worth noticing. */
object Spacing {
    /** Hairline gaps, e.g. a label to its field. */
    val xxs = 2.dp
    /** Tight internal padding. */
    val xs = 4.dp
    /** Gap between related controls. */
    val sm = 8.dp
    /** Gap between an avatar and its text block; between stacked cards. */
    val md = 12.dp
    /** Standard card and row padding. */
    val lg = 16.dp
    /** Screen horizontal margin. */
    val screen = 20.dp
    /** Spacing between grouped sections on a screen. */
    val xl = 24.dp
    /** Spacing above a major section; empty-state padding. */
    val xxl = 32.dp
    /** Top-of-screen spacing; large empty-state padding. */
    val xxxl = 48.dp
}

/** Fixed sizes the design pins down. */
object Sizes {
    val avatar = 40.dp
    /** Buttons and fields: comfortably past the 48dp touch target. */
    val control = 52.dp
    /** The tappable box around a 24dp icon. */
    val iconButton = 48.dp
    val icon = 24.dp
    val border = 1.dp
    /** Content never grows wider than this, so tablets and landscape keep a readable measure. */
    val maxContentWidth = 560.dp
}
