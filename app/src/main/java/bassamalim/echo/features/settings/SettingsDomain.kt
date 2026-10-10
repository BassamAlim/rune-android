package bassamalim.echo.features.settings

import android.app.Application
import android.view.autofill.AutofillManager
import bassamalim.echo.core.crypto.Echo
import bassamalim.echo.core.data.repositories.SitesRepository
import bassamalim.echo.core.models.Site
import bassamalim.echo.core.vault.Vault
import bassamalim.echo.core.vault.VaultState
import kotlinx.coroutines.flow.Flow
import javax.crypto.Cipher
import javax.inject.Inject

class SettingsDomain @Inject constructor(
    private val application: Application,
    private val vault: Vault,
    private val sitesRepository: SitesRepository
) {

    fun observeVault(): Flow<VaultState> = vault.state

    fun observeSites(): Flow<List<Site>> = sitesRepository.observeSites()

    fun canUseBiometrics(): Boolean = vault.canUseBiometrics()

    fun isAutofillOn(): Boolean =
        application.getSystemService(AutofillManager::class.java)
            ?.hasEnabledAutofillServices() == true

    suspend fun secretKeyText(): String? =
        runCatching { vault.secretKey() }.getOrNull()?.let { key ->
            Echo.encodeSecretKey(key).also { key.fill(0) }
        }

    fun sealingCipher(): Cipher? = runCatching { vault.sealingCipher() }.getOrNull()

    suspend fun seal(authenticated: Cipher, masterKey: ByteArray): Boolean =
        runCatching { vault.seal(authenticated, masterKey) }.isSuccess

    /** The site list as the JSON file the desktop app also reads. */
    suspend fun exportJson(): String? = sitesRepository.export()

    /** "Imported: 2 new, 1 updated." or why the file couldn't be used. */
    suspend fun import(text: String): Result<String> =
        sitesRepository.parse(text)
            .mapCatching { sitesRepository.merge(it).getOrThrow() }
            .map { "Imported: ${it.added} new, ${it.updated} updated." }

    suspend fun reset() = vault.reset()
}
