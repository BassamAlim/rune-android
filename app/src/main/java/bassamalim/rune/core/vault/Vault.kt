package bassamalim.rune.core.vault

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
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import bassamalim.rune.core.crypto.Rune
import bassamalim.rune.core.crypto.Seal
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
 * Where the master key lives (SPEC.md §4.1).
 *
 * It is sealed with a Keystore key that demands a fresh strong biometric for every single use:
 * there is no "unlocked for five minutes". The cipher is handed to BiometricPrompt as a
 * CryptoObject, so a faked success callback decrypts nothing. The Keystore key can't leave the
 * device, and enrolling a new fingerprint destroys it — after which only the master password
 * gets you back in. Alongside it: the key's fingerprint, and when the master password was last
 * typed, for practice prompts.
 */
@Singleton
class Vault @Inject constructor(
    private val application: Application,
    private val store: DataStore<Preferences>
) {

    val state: Flow<VaultState> = store.data.map { preferences ->
        val fingerprint = preferences[FINGERPRINT]

        if (fingerprint == null) VaultState.NotSetUp
        else VaultState.Ready(fingerprint, quickUnlock = preferences[MASTER] != null)
    }

    /** Whether a strong fingerprint (or face) is enrolled, so the master key can be sealed. */
    fun canUseBiometrics(): Boolean =
        BiometricManager.from(application).canAuthenticate(BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS

    /** First set-up, or a set-up over an old one. Leaves quick unlock off until [seal]. */
    suspend fun create(masterKey: ByteArray) {
        deleteKey(BIOMETRIC_ALIAS)

        store.edit {
            it.clear()
            it[FINGERPRINT] = Rune.fingerprint(masterKey)
            it[PASSWORD_TYPED_AT] = System.currentTimeMillis()
            it[SYNC] = encode(Seal.syncKey(masterKey))
        }
    }

    /**
     * The key for syncing with the desktop (SPEC §3.3), kept so sync needs no unlock. It is a
     * one-way derivative of the master key: it opens no password, only the site list in
     * transit, which this phone already stores in the clear. Null until the master key has
     * been used once on an install from before sync existed.
     */
    suspend fun syncKey(): ByteArray? = store.data.first()[SYNC]?.let(::decode)

    /** Called wherever the master key is in hand, so older installs pick the sync key up. */
    suspend fun rememberSyncKey(masterKey: ByteArray) {
        if (store.data.first()[SYNC] == null) store.edit { it[SYNC] = encode(Seal.syncKey(masterKey)) }
    }

    /**
     * A cipher that will seal the master key once the user has authenticated with it. Makes a
     * new biometric Keystore key each time, which is also how quick unlock comes back after
     * the old key was invalidated.
     */
    fun sealingCipher(): Cipher {
        deleteKey(BIOMETRIC_ALIAS)

        return Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, generateKey(BIOMETRIC_ALIAS))
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

        return authenticated.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES).also { rememberSyncKey(it) }
    }

    suspend fun fingerprint(): String? = store.data.first()[FINGERPRINT]

    /**
     * With fingerprint unlock the master password is rarely typed, and forgetting it loses
     * everything. So once it hasn't been typed for [PRACTICE_DAYS], the next unlock asks for it.
     */
    suspend fun isPracticeDue(): Boolean {
        val typedAt = store.data.first()[PASSWORD_TYPED_AT] ?: return true
        return System.currentTimeMillis() - typedAt > PRACTICE_DAYS * 24 * 60 * 60 * 1000L
    }

    suspend fun onPasswordTyped() {
        store.edit { it[PASSWORD_TYPED_AT] = System.currentTimeMillis() }
    }

    /** Removes the key from this phone. The site list is not the vault's and stays. */
    suspend fun reset() {
        deleteKey(BIOMETRIC_ALIAS)
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
    private fun generateKey(alias: String): SecretKey {
        fun generate(strongBox: Boolean): SecretKey {
            val spec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setIsStrongBoxBacked(strongBox)
                .setUserAuthenticationRequired(true)
                // 0 seconds: every use needs its own authentication.
                .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                .setInvalidatedByBiometricEnrollment(true)

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
        const val BIOMETRIC_ALIAS = "rune_master"
        const val IV_BYTES = 12
        const val TAG_BITS = 128

        const val PRACTICE_DAYS = 14

        /** Base64 of the 12-byte IV followed by the ciphertext. */
        val MASTER = stringPreferencesKey("master")
        val FINGERPRINT = stringPreferencesKey("fingerprint")
        val SYNC = stringPreferencesKey("sync")
        /** Epoch millis. */
        val PASSWORD_TYPED_AT = longPreferencesKey("password_typed_at")
    }
}
