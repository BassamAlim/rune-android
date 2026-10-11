package bassamalim.rune.features.autofill

import bassamalim.rune.core.autofill.SiteMatcher
import bassamalim.rune.core.crypto.Rune
import bassamalim.rune.core.data.repositories.SitesRepository
import bassamalim.rune.core.models.Site
import bassamalim.rune.core.models.SiteKey
import bassamalim.rune.core.models.passwords
import javax.inject.Inject

class AutofillDomain @Inject constructor(private val sitesRepository: SitesRepository) {

    /** Null when the site list can't be read. */
    suspend fun sites(): List<Site>? = sitesRepository.sites()

    fun filter(sites: List<Site>, query: String): List<Site> {
        val needle = Rune.normalize(query)

        return if (needle.isEmpty()) sites
        else sites.filter { needle in it.site || needle in it.login }
    }

    /** A name for a new site on this page, unless one is already saved under it. */
    fun suggestion(sites: List<Site>, domain: String?): String? =
        domain?.let(SiteMatcher::suggestedName)?.takeIf { name -> sites.none { it.site == name } }

    /** Adds a site with the default settings. */
    suspend fun create(name: String): Result<Unit> = sitesRepository.save(Site(site = name))

    /**
     * Remembers the user's choice for an app, so next time the suggestion names the site. Never
     * done for browsers: there the page's own domain decides, every time.
     */
    suspend fun link(key: SiteKey, packageName: String) {
        if (!SiteMatcher.isBrowser(packageName)) sitesRepository.linkApp(key, packageName)
    }

    fun password(masterKey: ByteArray, site: Site): Result<String> =
        site.passwords(masterKey).map { it.current }
}
