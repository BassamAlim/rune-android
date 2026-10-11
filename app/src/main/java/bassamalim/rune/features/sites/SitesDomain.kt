package bassamalim.rune.features.sites

import bassamalim.rune.core.crypto.Rune
import bassamalim.rune.core.data.repositories.SitesRepository
import bassamalim.rune.core.data.repositories.SitesState
import bassamalim.rune.core.models.Site
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class SitesDomain @Inject constructor(private val sitesRepository: SitesRepository) {

    fun observeState(): StateFlow<SitesState> = sitesRepository.state

    /** Matches the site name or the login, the way they are stored: trimmed and lowercase. */
    fun filter(sites: List<Site>, query: String): List<Site> {
        val needle = Rune.normalize(query)

        return if (needle.isEmpty()) sites
        else sites.filter { needle in it.site || needle in it.login }
    }
}
