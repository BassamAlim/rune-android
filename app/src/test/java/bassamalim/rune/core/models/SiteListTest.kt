package bassamalim.rune.core.models

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import kotlinx.serialization.json.Json
import org.junit.Test
import bassamalim.rune.core.crypto.Seal

class SiteListTest {

    @Test
    fun `the previous password is the one at the counter before, and the key is zeroed`() {
        val site = Site(site = "github.com", login = "bassam", counter = 2)
        val key = ByteArray(32) { 7 }

        val now = site.passwords(key).getOrThrow()
        assertArrayEquals(ByteArray(32), key)

        assertEquals(site.copy(counter = 1).passwords(ByteArray(32) { 7 }).getOrThrow().current, now.previous)
        assertNull(site.copy(counter = 1).passwords(ByteArray(32) { 7 }).getOrThrow().previous)
    }

    @Test
    fun `a note is kept as typed, survives older files and never touches the password`() {
        val list = SiteList().with(Site(site = "github.com", note = "  username: Bassam_M "))
        assertEquals("username: Bassam_M", list.sites[0].note)

        val old = Json.decodeFromString<SiteList>(
            """{"version":1,"sites":[{"site":"a","counter":1,"length":16,"classes":"l"}]}"""
        )
        assertEquals("", old.sites[0].note)

        val key = { ByteArray(32) { 7 } }
        assertEquals(
            Site(site = "github.com").passwords(key()).getOrThrow(),
            Site(site = "github.com", note = "anything").passwords(key()).getOrThrow()
        )
    }

    @Test
    fun `a kept password is the current one, with Rune's to change it to`() {
        val master = { ByteArray(32) { 7 } }
        val plain = Site(site = "github.com")
        val sealed = plain.copy(sealed = Seal.seal(Seal.key(master()), "Hunter2!"))

        val kept = sealed.passwords(master()).getOrThrow()
        assertEquals("Hunter2!", kept.current)
        assertEquals(plain.passwords(master()).getOrThrow().current, kept.rune)
        assertNull(kept.previous)

        assertThrows(IllegalArgumentException::class.java) {
            sealed.passwords(ByteArray(32) { 8 }).getOrThrow()
        }
    }

    @Test
    fun `adding normalises, sorts and refuses duplicates`() {
        val list = SiteList()
            .with(Site(site = "  GitHub.COM", login = "Bassam "))
            .with(Site(site = "bank.example"))

        assertEquals(listOf("bank.example", "github.com"), list.sites.map { it.site })
        assertEquals("bassam", list.sites[1].login)

        assertThrows(IllegalArgumentException::class.java) {
            list.with(Site(site = "github.com", login = "BASSAM"))
        }
        assertThrows(IllegalArgumentException::class.java) { list.with(Site(site = "   ")) }
        assertThrows(IllegalArgumentException::class.java) {
            list.with(Site(site = "x.example", classes = ""))
        }
    }

    @Test
    fun `editing replaces the entry it started from`() {
        val list = SiteList().with(Site(site = "github.com", login = "bassam"))
        val edited = list.with(
            Site(site = "github.com", login = "bassam", counter = 2),
            replacing = SiteKey("github.com", "bassam")
        )

        assertEquals(1, edited.sites.size)
        assertEquals(2, edited.sites[0].counter)
    }

    @Test
    fun `import merges by site and login and the higher counter wins`() {
        val mine = SiteList(sites = listOf(Site("a", counter = 3), Site("b", counter = 1)))
        val theirs = SiteList(sites = listOf(Site("a", counter = 2), Site(" B", counter = 2), Site("c")))

        val result = mine.merge(theirs)

        assertEquals(1, result.added)
        assertEquals(1, result.updated)
        assertEquals(listOf("a" to 3, "b" to 2, "c" to 1), result.list.sites.map { it.site to it.counter })
    }

    @Test
    fun `the newer change wins but a counter never goes down, and both devices settle`() {
        val mine = SiteList(sites = listOf(Site("a", counter = 3, note = "old", modified = 10)))
        val theirs = SiteList(sites = listOf(Site("a", counter = 1, note = "new", modified = 20)))

        val result = mine.merge(theirs)
        assertEquals(0 to 1, result.added to result.updated)
        assertEquals(listOf(Site("a", counter = 3, note = "new", modified = 20)), result.list.sites)

        // The other device, merging the other way, lands on the same list.
        assertEquals(result.list, theirs.merge(mine).list)
        assertEquals(0 to 0, result.list.merge(mine).let { it.added to it.updated })
    }

    @Test
    fun `deletes and renames travel`() {
        val start = SiteList(sites = listOf(Site("a", modified = 10), Site("b", modified = 10)))
        val phone = start
            .without(SiteKey("a", ""), now = 20)
            .with(Site("c"), replacing = SiteKey("b", ""), now = 20) // "b" renamed to "c"
        assertEquals(listOf("c"), phone.sites.map { it.site })

        val desktop = start.merge(phone).list
        assertEquals(phone, desktop)

        // Added again later, it comes back everywhere, and the delete is forgotten.
        val again = desktop.with(Site("a"), now = 15)
        assertEquals(21L, again.sites[0].modified) // later than the delete, whatever the clock says
        val synced = phone.merge(again).list
        assertEquals(again, synced)
        assertEquals(listOf("b"), synced.deleted.map { it.site })
    }

    @Test
    fun `removing drops only that account`() {
        val list = SiteList(sites = listOf(Site("a", login = "x"), Site("a", login = "y")))

        assertEquals(listOf("y"), list.without(SiteKey("a", "x")).sites.map { it.login })
    }
}
