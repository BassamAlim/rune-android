package bassamalim.rune.features.site

import bassamalim.rune.core.crypto.Rune
import bassamalim.rune.core.crypto.Seal
import bassamalim.rune.core.data.repositories.SitesRepository
import bassamalim.rune.core.models.Site
import bassamalim.rune.core.models.SiteKey
import bassamalim.rune.core.models.Passwords
import bassamalim.rune.core.models.passwords
import bassamalim.rune.core.utils.SecretClipboard
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import kotlin.math.roundToInt

class SiteDomain @Inject constructor(
    private val sitesRepository: SitesRepository,
    private val clipboard: SecretClipboard
) {

    fun observeSite(key: SiteKey): Flow<Site?> = sitesRepository.observeSite(key)

    fun passwords(masterKey: ByteArray, site: Site): Result<Passwords> = site.passwords(masterKey)

    fun details(site: Site): String =
        "${site.length} characters  ·  counter ${site.counter}  ·  about " +
                "${Rune.entropyBits(site.length, site.classes, site.symbols).roundToInt()} bits"

    fun copy(password: String) = clipboard.copy(password)

    /** Keeps the site's own password, sealed, until it's changed to Rune's (SPEC §3.1). */
    suspend fun keep(site: Site, password: String, sealKey: ByteArray): Result<Unit> =
        sitesRepository.save(site.copy(sealed = Seal.seal(sealKey, password)), replacing = site.key)

    /** The site now takes Rune's password: the kept one is forgotten. */
    suspend fun switchToRune(site: Site): Result<Unit> =
        sitesRepository.save(site.copy(sealed = ""), replacing = site.key)
}
