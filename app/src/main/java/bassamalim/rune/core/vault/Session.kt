package bassamalim.rune.core.vault

import javax.inject.Inject
import javax.inject.Singleton

/**
 * The master key from the fingerprint that opened the app, held until the app leaves the screen,
 * so passwords inside the app don't each ask again. Autofill never sees it: the app is in the
 * background whenever autofill runs, and that closes the session.
 */
@Singleton
class Session @Inject constructor() {

    private var masterKey: ByteArray? = null

    fun open(key: ByteArray) {
        close()
        masterKey = key
    }

    /** A copy, because whoever takes it zeroes it after use. */
    fun key(): ByteArray? = masterKey?.copyOf()

    fun close() {
        masterKey?.fill(0)
        masterKey = null
    }
}
