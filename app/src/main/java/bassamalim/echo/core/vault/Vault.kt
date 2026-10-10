package bassamalim.echo.core.vault

import android.app.Application
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import bassamalim.echo.core.crypto.Echo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

sealed interface VaultState {
    data object NotSetUp : VaultState

    /** [quickUnlock]: the master key is sealed behind a fingerprint, so no password is needed. */
    data class Ready(val fingerprint: String, val quickUnlock: Boolean) : VaultState
}

/**
 * Where the two keys live (SPEC.md §4.1).
 *
 * The **master key** is sealed with a Keystore key that demands a fresh strong biometric for
 * every single use: there is no "unlocked for five minutes". The cipher is handed to
 * BiometricPrompt as a CryptoObject, so a faked success callback decrypts nothing.
 *
 * The **secret key** is sealed with a second Keystore key that needs no authentication, because
 * the master-password fallback has to be able to read it. It is useless without the password.
 *
 * Neither Keystore key can leave the device, and enrolling a new fingerprint destroys the
 * biometric one — after which only the master password gets you back in.
 */
@Singleton
class Vault @Inject constructor(
    private val application: Application,
    private val store: DataStore<Preferences>
) {

    val state: Flow<VaultState> = store.data.map { preferences ->
        val fingerprint = preferences[FINGERPRINT]

        if (fingerprint == null || preferences[SECRET] == null) VaultState.NotSetUp
        else VaultState.Ready(fingerprint, quickUnlock = preferences[MASTER] != null)
    }

    /** Whether a strong fingerprint (or face) is enrolled, so the master key can be sealed. */
    fun canUseBiometrics(): Boolean =
        BiometricManager.from(application).canAuthenticate(BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS

    /** First set-up, or a set-up over an old one. Leaves quick unlock off until [seal]. */
    suspend fun create(secretKey: ByteArray, masterKey: ByteArray) {
        deleteKey(BIOMETRIC_ALIAS)
        deleteKey(DEVICE_ALIAS)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, generateKey(DEVICE_ALIAS, biometric = false))
        val sealedSecret = encode(cipher.iv + cipher.doFinal(secretKey))

        store.edit {
            it.clear()
            it[SECRET] = sealedSecret
            it[FINGERPRINT] = Echo.fingerprint(masterKey)
        }
    }

    /**
     * A cipher that will seal the master key once the user has authenticated with it. Makes a
     * new biometric Keystore key each time, which is also how quick unlock comes back after
     * the old key was invalidated.
     */
    fun sealingCipher(): Cipher {
        deleteKey(BIOMETRIC_ALIAS)

        return Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, generateKey(BIOMETRIC_ALIAS, biometric = true))
        }
    }

    suspend fun seal(authenticated: Cipher, masterKey: ByteArray) {
        val sealed = encode(authenticated.iv + authenticated.doFinal(masterKey))

        store.edit { it[MASTER] = sealed }
    }

    /**
     * A cipher that will open the master key once the user has authenticated with it, or null
     * when there is nothing sealed, or the fingerprints changed and Android destroyed the key.
     */
    suspend fun openingCipher(): Cipher? {
        val sealed = store.data.first()[MASTER]?.let(::decode) ?: return null

        return try {
            val key = keyStore().getKey(BIOMETRIC_ALIAS, null) as? SecretKey
                ?: return null.also { forgetSeal() }

            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
            }
        } catch (_: KeyPermanentlyInvalidatedException) {
            forgetSeal()
            null
        }
    }

    suspend fun open(authenticated: Cipher): ByteArray {
        val sealed = decode(store.data.first()[MASTER] ?: error("Nothing is sealed"))

        return authenticated.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
    }

    suspend fun secretKey(): ByteArray {
        val sealed = decode(store.data.first()[SECRET] ?: error("Echo isn't set up"))
        val key = keyStore().getKey(DEVICE_ALIAS, null) as SecretKey
        val cipher = Cipher.getInstance(TRANSFORMATION)

        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
        return cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
    }

    suspend fun fingerprint(): String? = store.data.first()[FINGERPRINT]

    /** Removes both keys from this phone. The site list is not the vault's and stays. */
    suspend fun reset() {
        deleteKey(BIOMETRIC_ALIAS)
        deleteKey(DEVICE_ALIAS)
        store.edit { it.clear() }
    }

    private suspend fun forgetSeal() {
        deleteKey(BIOMETRIC_ALIAS)
        store.edit { it.remove(MASTER) }
    }

    private fun keyStore() = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private fun deleteKey(alias: String) {
        runCatching { keyStore().deleteEntry(alias) }
    }

    /** StrongBox where the phone has one, the TEE otherwise. */
    private fun generateKey(alias: String, biometric: Boolean): SecretKey {
        fun generate(strongBox: Boolean): SecretKey {
            val spec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setIsStrongBoxBacked(strongBox)

            if (biometric) {
                spec.setUserAuthenticationRequired(true)
                // 0 seconds: every use needs its own authentication.
                spec.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                spec.setInvalidatedByBiometricEnrollment(true)
            }

            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
                .apply { init(spec.build()) }
                .generateKey()
        }

        return try {
            generate(strongBox = true)
        } catch (_: StrongBoxUnavailableException) {
            generate(strongBox = false)
        }
    }

    private fun encode(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)
    private fun decode(text: String) = Base64.getDecoder().decode(text)

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val BIOMETRIC_ALIAS = "echo_master"
        const val DEVICE_ALIAS = "echo_secret"
        const val IV_BYTES = 12
        const val TAG_BITS = 128

        /** Each value is Base64 of the 12-byte IV followed by the ciphertext. */
        val MASTER = stringPreferencesKey("master")
        val SECRET = stringPreferencesKey("secret")
        val FINGERPRINT = stringPreferencesKey("fingerprint")
    }
}
