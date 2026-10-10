package bassamalim.echo.core.models

import bassamalim.echo.core.crypto.Echo
import kotlinx.serialization.Serializable

/**
 * One site's settings. Not secret, but required: a lost counter or policy is a lost password.
 * The JSON shape is shared with the desktop app (SPEC.md §3).
 */
@Serializable
data class Site(
    val site: String,
    val login: String = "",
    val counter: Int = 1,
    val length: Int = Echo.DEFAULT_LENGTH,
    val classes: String = Echo.ALL_CLASSES,
    /** Android package names the user has linked to this site, for autofill inside apps. */
    val apps: List<String> = emptyList()
) {
    /** What makes two entries the same account. */
    val key: SiteKey get() = SiteKey(site, login)
}

data class SiteKey(val site: String, val login: String)

/**
 * This site's password. Uses [masterKey] once and zeroes it, whatever happens. Fails only when
 * the settings can't produce a password (a hand-edited or imported file).
 */
fun Site.password(masterKey: ByteArray): Result<String> = runCatching {
    val siteKey = Echo.siteKey(masterKey, site, login, counter)

    Echo.password(siteKey, length, classes).also { siteKey.fill(0) }
}.also { masterKey.fill(0) }

@Serializable
data class SiteList(
    val version: Int = VERSION,
    val sites: List<Site> = emptyList()
) {

    /**
     * Adds [site], or replaces the entry at [replacing]. Names are normalised, the list stays
     * sorted, and a second entry for the same site and login is refused.
     */
    fun with(site: Site, replacing: SiteKey? = null): SiteList {
        val normalised = site.copy(
            site = Echo.normalize(site.site),
            login = Echo.normalize(site.login)
        )
        require(normalised.site.isNotEmpty()) { "Enter a site name." }
        require(normalised.classes.isNotEmpty()) { "Pick at least one kind of character." }

        val others = sites.filter { it.key != replacing }
        require(others.none { it.key == normalised.key }) { "That site and login already exist." }

        return copy(sites = (others + normalised).sortedWith(ORDER))
    }

    fun without(key: SiteKey): SiteList = copy(sites = sites.filter { it.key != key })

    /** Import: merge by site and login; the higher counter wins. */
    fun merge(other: SiteList): MergeResult {
        val merged = sites.associateBy { it.key }.toMutableMap()
        var added = 0
        var updated = 0

        for (incoming in other.sites) {
            val site = incoming.copy(
                site = Echo.normalize(incoming.site),
                login = Echo.normalize(incoming.login)
            )
            val mine = merged[site.key]
            when {
                mine == null -> { merged[site.key] = site; added++ }
                site.counter > mine.counter -> { merged[site.key] = site; updated++ }
            }
        }

        return MergeResult(copy(sites = merged.values.sortedWith(ORDER)), added, updated)
    }

    companion object {
        const val VERSION = 1
        private val ORDER = compareBy<Site>({ it.site }, { it.login })
    }
}

data class MergeResult(val list: SiteList, val added: Int, val updated: Int)
