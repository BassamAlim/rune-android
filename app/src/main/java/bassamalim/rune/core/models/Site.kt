package bassamalim.rune.core.models

import bassamalim.rune.core.crypto.Rune
import bassamalim.rune.core.crypto.Seal
import kotlinx.serialization.Serializable

/**
 * One site's settings. Not secret (a kept password is sealed), but required: a lost counter or
 * policy is a lost password.
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
    val apps: List<String> = emptyList(),
    /**
     * Free text kept with the login, e.g. the username when the login is an email. Not secret,
     * not part of the password; absent in files written before it existed.
     */
    val note: String = "",
    /**
     * SPEC §3.1: the site's own password, sealed under the master key, until it is changed to
     * Rune's. Empty for most sites.
     */
    val sealed: String = "",
    /** SPEC §3.2: when this entry was last changed, in seconds since 1970. Zero in older files. */
    val modified: Long = 0
) {
    /** What makes two entries the same account. */
    val key: SiteKey get() = SiteKey(site, login)
}

data class SiteKey(val site: String, val login: String)

/**
 * This site's password, and the one before it when the counter has been raised (a site's
 * change-password form usually asks for the old one). For a site with a kept password, that is
 * the current one and Rune's is [Passwords.rune]. Uses [masterKey] once and zeroes it, whatever
 * happens. Fails when the settings can't produce a password (a hand-edited or imported file) or
 * the kept password can't be opened.
 */
fun Site.passwords(masterKey: ByteArray): Result<Passwords> = runCatching {
    fun at(counter: Int): String {
        val siteKey = Rune.siteKey(masterKey, site, login, counter)
        return Rune.password(siteKey, length, classes, symbols).also { siteKey.fill(0) }
    }
    if (sealed.isEmpty()) {
        return@runCatching Passwords(current = at(counter), previous = if (counter > 1) at(counter - 1) else null)
    }
    val sealKey = Seal.key(masterKey)
    val kept = try { Seal.open(sealKey, sealed) } finally { sealKey.fill(0) }
    Passwords(current = kept, previous = null, rune = at(counter))
}.also { masterKey.fill(0) }

/** [rune] is set when [current] is the site's own, kept password: what to change it to. */
data class Passwords(val current: String, val previous: String?, val rune: String? = null)

/** SPEC §3.2: an entry that was deleted, remembered so the delete reaches the other device. */
@Serializable
data class Deleted(val site: String, val login: String = "", val modified: Long) {
    val key: SiteKey get() = SiteKey(site, login)
}

@Serializable
data class SiteList(
    val version: Int = VERSION,
    val sites: List<Site> = emptyList(),
    val deleted: List<Deleted> = emptyList()
) {

    /**
     * Adds [site], or replaces the entry at [replacing]. Names are normalised, the list stays
     * sorted, and a second entry for the same site and login is refused. The change is stamped
     * [now] (§3.2) so it wins over older copies on other devices; renaming leaves a delete for
     * the old name.
     */
    fun with(site: Site, replacing: SiteKey? = null, now: Long = now()): SiteList {
        val named = site.copy(
            site = Rune.normalize(site.site),
            login = Rune.normalize(site.login),
            // Kept as typed: a username's case can matter.
            note = site.note.trim()
        )
        require(named.site.isNotEmpty()) { "Enter a site name." }
        require(named.classes.isNotEmpty()) { "Pick at least one kind of character." }

        val old = sites.firstOrNull { it.key == replacing }
        val others = sites.filter { it.key != replacing }
        require(others.none { it.key == named.key }) { "That site and login already exist." }

        // Later than everything it replaces, even if this phone's clock runs behind.
        val before = listOfNotNull(old?.modified, deleted.firstOrNull { it.key == named.key }?.modified).maxOrNull()
        val stamped = named.copy(modified = if (before == null) now else maxOf(now, before + 1))
        val renamedFrom = old?.takeIf { it.key != stamped.key }

        return copy(
            sites = (others + stamped).sortedWith(ORDER),
            deleted = deleted.filter { it.key != stamped.key && it.key != renamedFrom?.key } +
                    listOfNotNull(renamedFrom?.let { forgotten(it, now) })
        )
    }

    /** Deletes the entry, remembering the delete (§3.2). */
    fun without(key: SiteKey, now: Long = now()): SiteList {
        val old = sites.firstOrNull { it.key == key } ?: return this

        return copy(
            sites = sites.filter { it.key != key },
            deleted = deleted.filter { it.key != key } + forgotten(old, now)
        )
    }

    private fun forgotten(old: Site, now: Long) =
        Deleted(old.site, old.login, maxOf(now, old.modified + 1))

    /**
     * §3.2: merges [other] in, for import and sync. Per site and login the newer change wins
     * (at a tie, the higher counter, then this list's), a counter never goes down, and a delete
     * beats anything older than it. Merging the result back the other way changes nothing, so
     * two devices settle. The desktop's `SiteList::merge` follows the same rules.
     */
    fun merge(other: SiteList): MergeResult {
        val before = sites.associateBy { it.key }
        val merged = before.toMutableMap()

        for (incoming in other.sites) {
            val theirs = incoming.copy(
                site = Rune.normalize(incoming.site),
                login = Rune.normalize(incoming.login)
            )
            val mine = merged[theirs.key]
            val newer = mine == null || theirs.modified > mine.modified ||
                    (theirs.modified == mine.modified && theirs.counter > mine.counter)

            merged[theirs.key] = (if (newer) theirs else mine!!)
                .let { it.copy(counter = maxOf(it.counter, mine?.counter ?: 0, theirs.counter)) }
        }

        val deletes = (deleted + other.deleted)
            .groupBy { SiteKey(Rune.normalize(it.site), Rune.normalize(it.login)) }
            .mapValues { (_, all) -> all.maxOf { it.modified } }
        // A site changed in the same second as its delete stays: nothing is lost to a tie.
        val kept = merged.filter { (key, site) -> (deletes[key] ?: Long.MIN_VALUE) <= site.modified }

        return MergeResult(
            list = copy(
                sites = kept.values.sortedWith(ORDER),
                deleted = deletes.filterKeys { it !in kept }
                    .map { (key, at) -> Deleted(key.site, key.login, at) }
                    .sortedWith(compareBy({ it.site }, { it.login }))
            ),
            added = kept.keys.count { it !in before },
            updated = kept.count { (key, site) -> before[key].let { it != null && it != site } }
        )
    }

    companion object {
        const val VERSION = 1
        private val ORDER = compareBy<Site>({ it.site }, { it.login })

        private fun now() = System.currentTimeMillis() / 1000
    }
}

data class MergeResult(val list: SiteList, val added: Int, val updated: Int)
