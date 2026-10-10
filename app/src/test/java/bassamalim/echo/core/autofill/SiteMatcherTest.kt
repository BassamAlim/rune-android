package bassamalim.echo.core.autofill

import bassamalim.echo.core.models.Site
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SiteMatcherTest {

    private val chrome = "com.android.chrome"
    private val sites = listOf(
        Site("github.com", login = "bassam"),
        Site("github.com", login = "work"),
        Site("bank.example", apps = listOf("com.bank.app")),
        Site("com")
    )

    private fun match(packageName: String, domain: String?) =
        SiteMatcher.match(sites, packageName, domain).map { it.site to it.login }

    @Test
    fun `a browser page matches its own domain and parent domains`() {
        val both = listOf("github.com" to "bassam", "github.com" to "work")

        assertEquals(both, match(chrome, "github.com"))
        assertEquals(both, match(chrome, "gist.github.com"))
        assertEquals(both, match(chrome, "GitHub.com"))
    }

    @Test
    fun `a lookalike domain matches nothing`() {
        assertEquals(emptyList<Any>(), match(chrome, "notgithub.com"))
        assertEquals(emptyList<Any>(), match(chrome, "github.com.evil.example"))
    }

    @Test
    fun `a site saved without a dot never matches a web page`() {
        assertEquals(emptyList<Any>(), match(chrome, "anything.com"))
    }

    @Test
    fun `an ordinary app is not believed about which website it shows`() {
        assertEquals(emptyList<Any>(), match("com.evil.app", "github.com"))
        assertNull(SiteMatcher.trustedDomain("com.evil.app", "github.com"))
        assertEquals("github.com", SiteMatcher.trustedDomain(chrome, "github.com"))
    }

    @Test
    fun `an app matches only the sites the user linked to it`() {
        assertEquals(listOf("bank.example" to ""), match("com.bank.app", null))
        assertEquals(emptyList<Any>(), match("com.other.app", null))
    }

    @Test
    fun `a browser with no page matches nothing, linked or not`() {
        assertEquals(emptyList<Any>(), match(chrome, null))
    }

    @Test
    fun `a new site is named after the page without www`() {
        assertEquals("example.com", SiteMatcher.suggestedName("www.example.com"))
        assertEquals("app.example.com", SiteMatcher.suggestedName("app.example.com"))
    }
}
