package bassamalim.rune.features.site

import bassamalim.rune.core.crypto.Rune
import bassamalim.rune.core.data.repositories.SitesRepository
import bassamalim.rune.core.models.Site
import bassamalim.rune.core.models.SiteKey
import bassamalim.rune.core.models.password
import bassamalim.rune.core.utils.SecretClipboard
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import kotlin.math.roundToInt

class SiteDomain @Inject constructor(
    private val sitesRepository: SitesRepository,
    private val clipboard: SecretClipboard
) {

    fun observeSite(key: SiteKey): Flow<Site?> = sitesRepository.observeSite(key)

    fun password(masterKey: ByteArray, site: Site): Result<String> = site.password(masterKey)

    fun details(site: Site): String =
        "${site.length} characters  ·  counter ${site.counter}  ·  about " +
                "${Rune.entropyBits(site.length, site.classes).roundToInt()} bits"

    fun copy(password: String) = clipboard.copy(password)
}
