package bassamalim.rune.core

object Globals {
    const val TAG = "Rune"

    /** How long a copied password stays on the clipboard. */
    const val CLIPBOARD_CLEAR_MILLIS = 30_000L

    /** How long the app can be left (a file picker, a glance at another app) before it locks. */
    const val LOCK_GRACE_MILLIS = 30_000L
}
