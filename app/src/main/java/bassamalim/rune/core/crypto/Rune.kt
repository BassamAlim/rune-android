package bassamalim.rune.core.crypto

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.text.Normalizer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.ln

/**
 * The `rune/v1` algorithm. SPEC.md §2 in the rune-desktop repo is the contract, and this must
 * match it byte for byte: the same `vectors.json` is asserted by both apps.
 */
object Rune {

    const val KEY_BYTES = 32
    const val PASSPHRASE_WORDS = 6
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 64
    const val DEFAULT_LENGTH = 16
    const val ALL_CLASSES = "luds"

    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val DIGITS = "0123456789"
    const val DEFAULT_SYMBOLS = "!@#$%^&*-_=+?"

    /**
     * The default symbols, then the rest of ASCII punctuation: the fixed order a site's symbols
     * take in the charset (§2.3). Keeping the defaults first keeps default passwords unchanged.
     */
    private const val ALL_SYMBOLS = DEFAULT_SYMBOLS + "\"'(),./:;<>[\\]`{|}~"

    /** §2.3: a site's symbols in canonical order. Any ASCII punctuation, any order, repeats allowed. */
    fun canonicalSymbols(symbols: String): String {
        symbols.firstOrNull { it !in ALL_SYMBOLS }?.let {
            throw IllegalArgumentException("'$it' is not an ASCII symbol")
        }
        return ALL_SYMBOLS.filter { it in symbols }
    }

    /** The enabled sets, in the fixed order the charset is built in, however `classes` is written. */
    private fun charset(classes: String, symbols: String): List<String> {
        require(classes.isNotEmpty() && classes.all { it in ALL_CLASSES }) {
            "classes \"$classes\" must be a non-empty subset of \"$ALL_CLASSES\""
        }
        val canonical = canonicalSymbols(symbols)
        require('s' !in classes || canonical.isNotEmpty()) { "symbols are on but none are allowed" }
        return listOf('l' to LOWER, 'u' to UPPER, 'd' to DIGITS, 's' to canonical)
            .filter { it.first in classes }
            .map { it.second }
    }

    private fun nfkc(text: String) = Normalizer.normalize(text, Normalizer.Form.NFKC)

    private fun hmac(key: ByteArray, vararg parts: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        parts.forEach(mac::update)
        return mac.doFinal()
    }

    private fun u32(value: Int) = byteArrayOf(
        (value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte()
    )

    /** §2.2: how site and login names are canonicalised before derivation. */
    fun normalize(text: String): String = nfkc(text).trim().lowercase()

    /** §2.1: slow on purpose (64 MiB, seconds on a phone). Run once at setup, off the main thread. */
    fun masterKey(masterPassword: String): ByteArray {
        val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withMemoryAsKB(65536)
            .withIterations(3)
            .withParallelism(4)
            .withSalt("rune/v1/master".toByteArray(Charsets.UTF_8))
            .build()
        val password = nfkc(masterPassword).toByteArray(Charsets.UTF_8)
        val key = ByteArray(KEY_BYTES)

        Argon2BytesGenerator().apply { init(parameters) }.generateBytes(password, key)
        password.fill(0)
        return key
    }

    /** §2.2 */
    fun siteKey(masterKey: ByteArray, site: String, login: String, counter: Int): ByteArray {
        val siteBytes = normalize(site).toByteArray(Charsets.UTF_8)
        val loginBytes = normalize(login).toByteArray(Charsets.UTF_8)

        return hmac(
            masterKey,
            "rune/v1".toByteArray(Charsets.UTF_8),
            u32(siteBytes.size), siteBytes,
            u32(loginBytes.size), loginBytes,
            u32(counter)
        )
    }

    /**
     * §2.3: `classes` is any non-empty subset of "luds" (lower, upper, digits, symbols);
     * `symbols` is the site's symbol set, used when `classes` has `s`.
     */
    fun password(siteKey: ByteArray, length: Int, classes: String, symbols: String): String {
        require(length in MIN_LENGTH..MAX_LENGTH) { "length $length not in $MIN_LENGTH..$MAX_LENGTH" }

        val enabled = charset(classes, symbols)
        val charset = enabled.joinToString("")
        val limit = 256 - 256 % charset.length

        var block = 0
        var buffer = ByteArray(0)
        var position = 0
        fun nextByte(): Int {
            if (position == buffer.size) {
                buffer = hmac(siteKey, u32(block++))
                position = 0
            }
            return buffer[position++].toInt() and 0xFF
        }

        while (true) {
            val candidate = StringBuilder(length)
            while (candidate.length < length) {
                val byte = nextByte()
                // Rejection sampling: a plain modulo would favour the start of the charset.
                if (byte < limit) candidate.append(charset[byte % charset.length])
            }
            if (enabled.all { set -> candidate.any { it in set } }) return candidate.toString()
        }
    }

    /** §2.4: four hex digits that every device with the same key shows. */
    fun fingerprint(masterKey: ByteArray): String =
        hmac(masterKey, "rune/v1/check".toByteArray(Charsets.UTF_8))
            .take(2)
            .joinToString("") { "%02x".format(it) }

    /** Roughly how strong a policy is, in bits (ignores the small loss from requiring every class). */
    fun entropyBits(length: Int, classes: String, symbols: String): Double {
        val size = runCatching { charset(classes, symbols) }.getOrNull()?.sumOf { it.length } ?: 0
        return if (size == 0) 0.0 else length * ln(size.toDouble()) / ln(2.0)
    }

    /**
     * §1: a generated master password, [PASSPHRASE_WORDS] words from [words] (the EFF large
     * wordlist). [random] must return uniformly random values in 0..65535 (from SecureRandom).
     */
    fun passphrase(words: List<String>, random: () -> Int): String {
        val limit = 65536 - 65536 % words.size
        return List(PASSPHRASE_WORDS) {
            var r = random()
            while (r >= limit) r = random()
            words[r % words.size]
        }.joinToString(" ")
    }

    /** Strength of a generated passphrase in bits. */
    fun passphraseBits(wordCount: Int): Double = PASSPHRASE_WORDS * ln(wordCount.toDouble()) / ln(2.0)
}
