package bassamalim.rune.core.data

import bassamalim.rune.core.data.repositories.SiteIcons.Companion.siteTitle
import bassamalim.rune.core.data.repositories.SiteIcons.Companion.titleCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SiteNameTest {

    @Test fun siteNameWinsOverTitle() = assertEquals(
        "GitHub",
        siteTitle("""<title>GitHub · Build and ship software</title><meta property="og:site_name" content="GitHub">""")
    )

    @Test fun contentMayComeFirstAndQuotesMayNest() = assertEquals(
        "Mom's Shop",
        siteTitle("""<META content="Mom's Shop" name="application-name" />""")
    )

    @Test fun titleIsCutAtItsFirstSeparator() {
        assertEquals("Stack Overflow", siteTitle("<title>\n  Stack Overflow - Where Developers Learn\n</title>"))
        assertEquals("AT&T", siteTitle("<title>AT&amp;T | Official Site</title>"))
        assertEquals("Amazon.com", siteTitle("<title>Amazon.com. Spend less. Smile more.</title>"))
    }

    @Test fun nothingUsable() {
        assertNull(siteTitle("<html><body>hi</body></html>"))
        assertNull(siteTitle("<title>" + "x".repeat(60) + "</title>"))
        assertNull(siteTitle("<title>Just a moment...</title>"))
    }

    @Test fun plainNamesAreTitleCased() {
        assertEquals("My Bank", titleCase("my bank"))
        assertEquals("github.com", titleCase("github.com"))
    }
}
