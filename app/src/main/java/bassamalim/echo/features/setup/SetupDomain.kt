package bassamalim.echo.features.setup

import bassamalim.echo.core.crypto.Echo
import bassamalim.echo.core.di.DefaultDispatcher
import bassamalim.echo.core.vault.Vault
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.inject.Inject

class SetupDomain @Inject constructor(
    private val vault: Vault,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher
) {

    fun newSecretKey(): ByteArray = ByteArray(Echo.KEY_BYTES).also(SecureRandom()::nextBytes)

    fun decodeSecretKey(text: String): ByteArray? = Echo.decodeSecretKey(text)

    fun encodeSecretKey(key: ByteArray): String = Echo.encodeSecretKey(key)

    fun fingerprint(masterKey: ByteArray): String = Echo.fingerprint(masterKey)

    /** Argon2id over 64 MiB: seconds on a phone, so never on the main thread. */
    suspend fun deriveMasterKey(password: String, secretKey: ByteArray): ByteArray =
        withContext(dispatcher) { Echo.masterKey(password, secretKey) }

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
