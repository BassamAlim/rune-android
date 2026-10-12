package bassamalim.rune.features.sites

import bassamalim.rune.core.crypto.Rune
import bassamalim.rune.core.data.repositories.SitesRepository
import bassamalim.rune.core.data.repositories.SitesState
import bassamalim.rune.core.models.Site
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class SitesDomain @Inject constructor(private val sitesRepository: SitesRepository) {

    fun observeState(): StateFlow<SitesState> = sitesRepository.state

    /** Matches the site name, the login (both stored trimmed and lowercase) or the note. */
    fun filter(sites: List<Site>, query: String): List<Site> {
        val needle = Rune.normalize(query)

        return if (needle.isEmpty()) sites
        else sites.filter { needle in it.site || needle in it.login || needle in it.note.lowercase() }
    }
}
