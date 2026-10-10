package bassamalim.echo.features.autofill

import bassamalim.echo.core.autofill.SiteMatcher
import bassamalim.echo.core.crypto.Echo
import bassamalim.echo.core.data.repositories.SitesRepository
import bassamalim.echo.core.models.Site
import bassamalim.echo.core.models.SiteKey
import bassamalim.echo.core.models.password
import javax.inject.Inject

class AutofillDomain @Inject constructor(private val sitesRepository: SitesRepository) {

    /** Null when the site list can't be read. */
    suspend fun sites(): List<Site>? = sitesRepository.sites()

    fun filter(sites: List<Site>, query: String): List<Site> {
        val needle = Echo.normalize(query)

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

    fun password(masterKey: ByteArray, site: Site): Result<String> = site.password(masterKey)
}
