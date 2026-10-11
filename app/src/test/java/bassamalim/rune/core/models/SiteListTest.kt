package bassamalim.rune.core.models

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

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
    fun `removing drops only that account`() {
        val list = SiteList(sites = listOf(Site("a", login = "x"), Site("a", login = "y")))

        assertEquals(listOf("y"), list.without(SiteKey("a", "x")).sites.map { it.login })
    }
}
