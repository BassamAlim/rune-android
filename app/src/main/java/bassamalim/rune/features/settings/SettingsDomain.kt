package bassamalim.rune.features.settings

import android.app.Application
import android.view.autofill.AutofillManager
import bassamalim.rune.core.data.repositories.SitesRepository
import bassamalim.rune.core.models.Site
import bassamalim.rune.core.sync.Sync
import bassamalim.rune.core.sync.SyncStatus
import bassamalim.rune.core.vault.Vault
import bassamalim.rune.core.vault.VaultState
import kotlinx.coroutines.flow.Flow
import java.text.DateFormat
import java.util.Date
import javax.crypto.Cipher
import javax.inject.Inject

class SettingsDomain @Inject constructor(
    private val application: Application,
    private val vault: Vault,
    private val sitesRepository: SitesRepository,
    private val sync: Sync
) {

    fun observeSync(): Flow<SyncStatus> = sync.status

    suspend fun sync() = sync.sync()

    fun describe(status: SyncStatus): String = when (status) {
        SyncStatus.Idle -> "Open Rune on your desktop, on this Wi-Fi, and the two site lists become one."
        SyncStatus.Syncing -> "Looking for your desktop…"
        is SyncStatus.Synced -> "Synced with your desktop at " +
                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(status.atMillis)) + "."
        SyncStatus.NoDesktop -> "No desktop found. Open Rune on your desktop, on this Wi-Fi."
        SyncStatus.NotReady -> "Open any password once and sync turns on."
        is SyncStatus.Failed -> status.reason
    }

    fun observeVault(): Flow<VaultState> = vault.state

    fun observeSites(): Flow<List<Site>> = sitesRepository.observeSites()

    fun canUseBiometrics(): Boolean = vault.canUseBiometrics()

    fun isAutofillOn(): Boolean =
        application.getSystemService(AutofillManager::class.java)
            ?.hasEnabledAutofillServices() == true

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
