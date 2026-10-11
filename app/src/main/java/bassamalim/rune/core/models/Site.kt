package bassamalim.rune.core.models

import bassamalim.rune.core.crypto.Rune
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
    val length: Int = Rune.DEFAULT_LENGTH,
    val classes: String = Rune.ALL_CLASSES,
    /** The symbols this site accepts; absent in files written before it existed. */
    val symbols: String = Rune.DEFAULT_SYMBOLS,
    /** Android package names the user has linked to this site, for autofill inside apps. */
    val apps: List<String> = emptyList()
) {
    /** What makes two entries the same account. */
    val key: SiteKey get() = SiteKey(site, login)
}

data class SiteKey(val site: String, val login: String)

/**
 * This site's password, and the one before it when the counter has been raised (a site's
 * change-password form usually asks for the old one). Uses [masterKey] once and zeroes it,
 * whatever happens. Fails only when the settings can't produce a password (a hand-edited or
 * imported file).
 */
fun Site.passwords(masterKey: ByteArray): Result<Passwords> = runCatching {
    fun at(counter: Int): String {
        val siteKey = Rune.siteKey(masterKey, site, login, counter)
        return Rune.password(siteKey, length, classes, symbols).also { siteKey.fill(0) }
    }
    Passwords(current = at(counter), previous = if (counter > 1) at(counter - 1) else null)
}.also { masterKey.fill(0) }

data class Passwords(val current: String, val previous: String?)

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
            site = Rune.normalize(site.site),
            login = Rune.normalize(site.login)
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
                site = Rune.normalize(incoming.site),
                login = Rune.normalize(incoming.login)
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
