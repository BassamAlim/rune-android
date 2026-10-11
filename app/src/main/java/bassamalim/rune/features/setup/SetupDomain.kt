package bassamalim.rune.features.setup

import android.app.Application
import bassamalim.rune.R
import bassamalim.rune.core.crypto.Rune
import bassamalim.rune.core.di.DefaultDispatcher
import bassamalim.rune.core.vault.Vault
import com.nulabinc.zxcvbn.Zxcvbn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.inject.Inject
import kotlin.math.roundToInt

class SetupDomain @Inject constructor(
    private val application: Application,
    private val vault: Vault,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher
) {

    /** The EFF large wordlist, the same file the desktop app uses. */
    private val words: List<String> by lazy {
        application.resources.openRawResource(R.raw.words).bufferedReader().readLines()
    }
    private val random = SecureRandom()
    private val zxcvbn by lazy { Zxcvbn() }

    fun newPassphrase(): String = Rune.passphrase(words) { random.nextInt(65536) }

    fun passphraseStrength(): String = "${Rune.passphraseBits(words.size).roundToInt()} bits"

    /**
     * How long a well-equipped attacker (~100,000 guesses a second: Argon2 on a rack of GPUs)
     * would take to guess [password] after a site leaks one of its passwords.
     */
    fun strength(password: String): Strength {
        val seconds = zxcvbn.measure(password, listOf("rune")).guesses / GUESSES_PER_SECOND
        fun about(n: Double, unit: String): String {
            val rounded = n.roundToInt().coerceAtLeast(1)
            return "about $rounded $unit${if (rounded == 1) "" else "s"}"
        }

        val (whenText, level) = when {
            seconds < HOUR -> "under an hour" to StrengthLevel.Weak
            seconds < DAY * 30 -> about(seconds / DAY, "day") to StrengthLevel.Weak
            seconds < YEAR -> about(seconds / (DAY * 30), "month") to StrengthLevel.Weak
            seconds < YEAR * 1000 -> about(seconds / YEAR, "year") to StrengthLevel.Fair
            else -> "thousands of years or more" to StrengthLevel.Strong
        }
        return Strength("If a site leaks one of your passwords, guessing this would take $whenText.", level)
    }

    fun fingerprint(masterKey: ByteArray): String = Rune.fingerprint(masterKey)

    /** Argon2id over 64 MiB: seconds on a phone, so never on the main thread. */
    suspend fun deriveMasterKey(password: String): ByteArray =
        withContext(dispatcher) { Rune.masterKey(password) }

    fun canUseBiometrics(): Boolean = vault.canUseBiometrics()

    /** Stores the key with quick unlock off, and returns a cipher to seal with if it can be on. */
    suspend fun store(masterKey: ByteArray): Cipher? {
        vault.create(masterKey)

        return if (vault.canUseBiometrics()) runCatching { vault.sealingCipher() }.getOrNull()
        else null
    }

    suspend fun seal(authenticated: Cipher, masterKey: ByteArray): Boolean =
        runCatching { vault.seal(authenticated, masterKey) }.isSuccess

    private companion object {
        const val GUESSES_PER_SECOND = 1e5
        const val HOUR = 3600.0
        const val DAY = 86400.0
        const val YEAR = 3.15e7
    }
}
