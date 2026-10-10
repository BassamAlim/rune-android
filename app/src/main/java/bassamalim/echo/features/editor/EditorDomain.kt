package bassamalim.echo.features.editor

import bassamalim.echo.core.crypto.Echo
import bassamalim.echo.core.data.repositories.SitesRepository
import bassamalim.echo.core.models.Site
import bassamalim.echo.core.models.SiteKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import kotlin.math.roundToInt

class EditorDomain @Inject constructor(private val sitesRepository: SitesRepository) {

    suspend fun site(key: SiteKey): Site? = sitesRepository.observeSite(key).first()

    suspend fun save(site: Site, replacing: SiteKey?): Result<Unit> =
        sitesRepository.save(site, replacing)

    suspend fun delete(key: SiteKey): Result<Unit> = sitesRepository.delete(key)

    fun strength(length: Int, classes: String): String =
        "about ${Echo.entropyBits(length, classes).roundToInt()} bits"
}
