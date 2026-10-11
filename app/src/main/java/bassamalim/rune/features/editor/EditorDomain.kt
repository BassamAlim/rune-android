package bassamalim.rune.features.editor

import bassamalim.rune.core.crypto.Rune
import bassamalim.rune.core.data.repositories.SitesRepository
import bassamalim.rune.core.models.Site
import bassamalim.rune.core.models.SiteKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import kotlin.math.roundToInt

class EditorDomain @Inject constructor(private val sitesRepository: SitesRepository) {

    suspend fun site(key: SiteKey): Site? = sitesRepository.observeSite(key).first()

    suspend fun save(site: Site, replacing: SiteKey?): Result<Unit> =
        sitesRepository.save(site, replacing)

    suspend fun delete(key: SiteKey): Result<Unit> = sitesRepository.delete(key)

    fun strength(length: Int, classes: String): String =
        "about ${Rune.entropyBits(length, classes).roundToInt()} bits"
}
