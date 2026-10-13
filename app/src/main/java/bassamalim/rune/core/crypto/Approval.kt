package bassamalim.rune.core.crypto

import org.bouncycastle.math.ec.rfc7748.X25519
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * SPEC.md §3.4: the desktop asks this phone for what needs the master key. A fresh X25519 key
 * each side, a code both screens show, and a key for what's secret in the answer. The desktop's
 * `approval_vector` and `ApprovalTest` hold both apps to the same values.
 */
@OptIn(ExperimentalStdlibApi::class)
object Approval {

    val REQUEST_AAD = "rune/v1/request".toByteArray(Charsets.UTF_8)
    val ANSWER_AAD = "rune/v1/answer".toByteArray(Charsets.UTF_8)
    private val BOX_AAD = "rune/v1/box".toByteArray(Charsets.UTF_8)

    /** How long a request may take, prompt included. */
    const val TIMEOUT_MILLIS = 120_000

    fun newPrivateKey(): ByteArray = ByteArray(32).also(SecureRandom()::nextBytes)

    fun publicKey(private: ByteArray): ByteArray = ByteArray(32).also { X25519.scalarMultBase(private, 0, it, 0) }

    fun commit(desktopPub: ByteArray): ByteArray = sha256("rune/v1/commit".toByteArray() + desktopPub)

    /** The six digits both screens show. */
    fun code(desktopPub: ByteArray, phonePub: ByteArray): String {
        val h = sha256("rune/v1/code".toByteArray() + desktopPub + phonePub)
        val n = ((h[0].toLong() and 0xff) shl 24) or ((h[1].toLong() and 0xff) shl 16) or
                ((h[2].toLong() and 0xff) shl 8) or (h[3].toLong() and 0xff)
        return "%06d".format(n % 1_000_000)
    }

    /** Null when the other side's key would make the shared secret all zeros. */
    fun answerKey(ownPrivate: ByteArray, otherPub: ByteArray, desktopPub: ByteArray, phonePub: ByteArray): ByteArray? {
        val shared = ByteArray(32)
        if (!X25519.calculateAgreement(ownPrivate, 0, otherPub, 0, shared, 0)) return null
        // HKDF-SHA256 (RFC 5869); one block is the 32 bytes wanted.
        val prk = hmac(desktopPub + phonePub, shared).also { shared.fill(0) }
        return hmac(prk, "rune/v1/answer".toByteArray() + byteArrayOf(1)).also { prk.fill(0) }
    }

    /** One frame's sealed bytes, without the length. [aad] says which side sent it. */
    fun sealFrame(syncKey: ByteArray, aad: ByteArray, json: String, nonce: ByteArray = Seal.randomNonce()): ByteArray =
        Seal.encrypt(syncKey, aad, json.toByteArray(Charsets.UTF_8), nonce)

    fun openFrame(syncKey: ByteArray, aad: ByteArray, bytes: ByteArray): String? =
        Seal.decrypt(syncKey, aad, bytes)?.decodeToString()

    /** A secret inside a frame, under the answer key, as hex. */
    fun seal(answerKey: ByteArray, json: String, nonce: ByteArray = Seal.randomNonce()): String {
        val plain = json.toByteArray(Charsets.UTF_8)
        return Seal.encrypt(answerKey, BOX_AAD, plain, nonce).toHexString().also { plain.fill(0) }
    }

    fun open(answerKey: ByteArray, sealed: String): String? {
        val bytes = runCatching { sealed.hexToByteArray() }.getOrNull() ?: return null
        val plain = Seal.decrypt(answerKey, BOX_AAD, bytes) ?: return null
        return plain.decodeToString().also { plain.fill(0) }
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(data)
        }
}
