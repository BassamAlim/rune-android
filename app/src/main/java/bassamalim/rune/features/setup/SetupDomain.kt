package bassamalim.rune.features.setup

import bassamalim.rune.core.crypto.Rune
import bassamalim.rune.core.di.DefaultDispatcher
import bassamalim.rune.core.vault.Vault
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.inject.Inject

class SetupDomain @Inject constructor(
    private val vault: Vault,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher
) {

    fun newSecretKey(): ByteArray = ByteArray(Rune.KEY_BYTES).also(SecureRandom()::nextBytes)

    fun decodeSecretKey(text: String): ByteArray? = Rune.decodeSecretKey(text)

    fun encodeSecretKey(key: ByteArray): String = Rune.encodeSecretKey(key)

    fun fingerprint(masterKey: ByteArray): String = Rune.fingerprint(masterKey)

    /** Argon2id over 64 MiB: seconds on a phone, so never on the main thread. */
    suspend fun deriveMasterKey(password: String, secretKey: ByteArray): ByteArray =
        withContext(dispatcher) { Rune.masterKey(password, secretKey) }

    fun canUseBiometrics(): Boolean = vault.canUseBiometrics()

    /** Stores the keys with quick unlock off, and returns a cipher to seal with if it can be on. */
    suspend fun store(secretKey: ByteArray, masterKey: ByteArray): Cipher? {
        vault.create(secretKey, masterKey)

        return if (vault.canUseBiometrics()) runCatching { vault.sealingCipher() }.getOrNull()
        else null
    }

    suspend fun seal(authenticated: Cipher, masterKey: ByteArray): Boolean =
        runCatching { vault.seal(authenticated, masterKey) }.isSuccess
}
