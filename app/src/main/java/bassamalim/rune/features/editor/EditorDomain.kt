package bassamalim.rune.features.editor

import bassamalim.rune.core.crypto.Rune
import bassamalim.rune.core.data.repositories.InstalledApp
import bassamalim.rune.core.data.repositories.SiteIcons
import bassamalim.rune.core.data.repositories.SitesRepository
import bassamalim.rune.core.models.Site
import bassamalim.rune.core.models.SiteKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import kotlin.math.roundToInt

class EditorDomain @Inject constructor(
    private val sitesRepository: SitesRepository,
    private val siteIcons: SiteIcons
) {

    suspend fun site(key: SiteKey): Site? = sitesRepository.observeSite(key).first()

    suspend fun save(site: Site, replacing: SiteKey?): Result<Unit> =
        sitesRepository.save(site, replacing)

    suspend fun delete(key: SiteKey): Result<Unit> = sitesRepository.delete(key)

    suspend fun installedApps(): List<InstalledApp> = siteIcons.installedApps()

    fun strength(length: Int, classes: String, symbols: String): String =
        "about ${Rune.entropyBits(length, classes, symbols).roundToInt()} bits"
}
