package bassamalim.echo.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The Echo palette: vault navy and one green. There is one theme and it is dark, shared with
 * the desktop app: no light variant, no dynamic colour.
 */

/** App background: the base canvas behind every screen. */
val Surface0 = Color(0xFF0F172A)
/** Cards and list rows at rest. */
val Surface100 = Color(0xFF192134)
/** Raised surfaces: text inputs, pressed or selected rows, quiet buttons. */
val Surface200 = Color(0xFF232D45)
/** Borders and dividers. */
val Surface300 = Color(0xFF2B3654)

/** Primary text and icons on any surface colour. */
val Ink = Color(0xFFF1F5F9)
/** Secondary text: subtitles, field labels, helper text. 7:1 on [Surface0]. */
val InkMuted = Color(0xFF94A3B8)
/** Tertiary text: placeholders and disabled labels. Still 4.5:1 on [Surface0]. */
val InkFaint = Color(0xFF7C8BA1)

/**
 * The one accent: the thing you tap, and your key's fingerprint. Use it once per screen for an
 * action; it works because it's rare.
 */
val Accent = Color(0xFF34D399)
/** Text and icons sitting on an accent-filled surface. */
val AccentInk = Color(0xFF052E22)
/** Tinted background for avatars and selected chips, the accent at 16%. */
val AccentWash = Color(0x2934D399)

/** Destructive actions and error states. */
val Danger = Color(0xFFF87171)
/** Background for destructive confirmations, the danger red at 14%. */
val DangerWash = Color(0x24F87171)

/** Scrim behind a bottom sheet or dialog. */
val Overlay = Color(0x99000000)
