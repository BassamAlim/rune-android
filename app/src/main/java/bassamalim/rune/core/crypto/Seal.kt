package bassamalim.rune.core.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * SPEC.md §3.1: a site's own password, kept until it is changed to Rune's. AES-256-GCM under a
 * key derived from the master key, stored as hex of nonce ‖ ciphertext ‖ tag. The desktop app
 * reads and writes the same field; `SealTest` holds both to one vector.
 */
object Seal {

    private val AAD = "rune/v1/password".toByteArray(Charsets.UTF_8)
    private val SYNC_AAD = "rune/v1/sync".toByteArray(Charsets.UTF_8)
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128

    /** The key that seals and opens kept passwords. Zero it after use, like the master key. */
    fun key(masterKey: ByteArray): ByteArray = hmac(masterKey, "rune/v1/seal")

    fun seal(sealKey: ByteArray, password: String, nonce: ByteArray = randomNonce()): String =
        encrypt(sealKey, AAD, password.toByteArray(Charsets.UTF_8), nonce).toHexString()

    /** Fails on a wrong key or a damaged or edited field. */
    fun open(sealKey: ByteArray, sealed: String): String {
        val bytes = runCatching { sealed.hexToByteArray() }.getOrNull()
        requireNotNull(bytes) { "not a sealed password" }

        val plain = decrypt(sealKey, AAD, bytes)
            ?: throw IllegalArgumentException("the kept password can't be opened with this master password")
        return plain.decodeToString().also { plain.fill(0) }
    }

    /** §3.3: what two devices with the same master password prove to each other, and encrypt with. */
    fun syncKey(masterKey: ByteArray): ByteArray = hmac(masterKey, "rune/v1/sync")

    /** §3.3: a site list (as JSON) as one sync message. */
    fun sealMessage(syncKey: ByteArray, json: String, nonce: ByteArray = randomNonce()): ByteArray =
        encrypt(syncKey, SYNC_AAD, json.toByteArray(Charsets.UTF_8), nonce)

    /** §3.3: the JSON in a sync message, or null when it isn't from a device with this master password. */
    fun openMessage(syncKey: ByteArray, message: ByteArray): String? =
        decrypt(syncKey, SYNC_AAD, message)?.decodeToString()

    private fun hmac(key: ByteArray, label: String): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(label.toByteArray(Charsets.UTF_8))
        }

    /** AES-256-GCM as nonce ‖ ciphertext ‖ tag. */
    private fun encrypt(key: ByteArray, aad: ByteArray, data: ByteArray, nonce: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            updateAAD(aad)
        }
        return nonce + cipher.doFinal(data)
    }

    /** Null on a wrong key or anything damaged or edited. */
    private fun decrypt(key: ByteArray, aad: ByteArray, bytes: ByteArray): ByteArray? {
        if (bytes.size < NONCE_BYTES + TAG_BITS / 8) return null

        return runCatching {
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, bytes, 0, NONCE_BYTES))
                updateAAD(aad)
            }.doFinal(bytes, NONCE_BYTES, bytes.size - NONCE_BYTES)
        }.getOrNull()
    }

    private fun randomNonce() = ByteArray(NONCE_BYTES).also(SecureRandom()::nextBytes)
}
