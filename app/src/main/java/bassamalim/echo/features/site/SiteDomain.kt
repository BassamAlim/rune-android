package bassamalim.echo.features.site

import bassamalim.echo.core.crypto.Echo
import bassamalim.echo.core.data.repositories.SitesRepository
import bassamalim.echo.core.models.Site
import bassamalim.echo.core.models.SiteKey
import bassamalim.echo.core.models.password
import bassamalim.echo.core.utils.SecretClipboard
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
                "${Echo.entropyBits(site.length, site.classes).roundToInt()} bits"

    fun copy(password: String) = clipboard.copy(password)
}
