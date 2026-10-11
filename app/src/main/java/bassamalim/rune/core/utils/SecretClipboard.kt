package bassamalim.rune.core.utils

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.os.PersistableBundle
import bassamalim.rune.core.Globals
import bassamalim.rune.core.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Copies a password the careful way: flagged sensitive, so the system's clipboard preview and
 * keyboards hide it, and cleared again after [Globals.CLIPBOARD_CLEAR_MILLIS].
 *
 * The flag is a request, not a wall: any app in the foreground can still read the clipboard
 * while the password is on it. Autofill, which never touches the clipboard, is the better path.
 */
@Singleton
class SecretClipboard @Inject constructor(
    application: Application,
    @ApplicationScope private val scope: CoroutineScope
) {

    private val clipboard = application.getSystemService(ClipboardManager::class.java)
    private var clearing: Job? = null

    fun copy(secret: String) {
        val clip = ClipData.newPlainText("Rune", secret)
        clip.description.extras = PersistableBundle().apply {
            // ClipDescription.EXTRA_IS_SENSITIVE, by its value so it also works below API 33.
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
        clipboard.setPrimaryClip(clip)

        clearing?.cancel()
        clearing = scope.launch {
            delay(Globals.CLIPBOARD_CLEAR_MILLIS)
            // Android won't let a background app read the clipboard, so this can't check that
            // the password is still what's on it: it clears whatever is there.
            runCatching { clipboard.clearPrimaryClip() }
        }
    }
}
