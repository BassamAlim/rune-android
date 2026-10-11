package bassamalim.rune.core.autofill

import bassamalim.rune.core.crypto.Rune
import bassamalim.rune.core.models.Site

/** Decides which saved sites belong to the screen asking for a password. */
object SiteMatcher {

    /**
     * Apps trusted to say which website they are showing. Any app can claim to be displaying
     * github.com; only a real browser's claim is believed, so an ordinary app can never talk its
     * way into a site's password. For everything else, the user links the app to a site by hand.
     */
    private val BROWSERS = setOf(
        "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary",
        "org.chromium.chrome", "org.mozilla.firefox", "org.mozilla.firefox_beta",
        "org.mozilla.fenix", "org.mozilla.focus", "com.brave.browser", "com.microsoft.emmx",
        "com.opera.browser", "com.opera.mini.native", "com.duckduckgo.mobile.android",
        "com.vivaldi.browser", "com.sec.android.app.sbrowser", "com.kiwibrowser.browser",
        "app.vanadium.browser", "org.cromite.cromite", "org.bromite.bromite",
        "com.ecosia.android", "org.torproject.torbrowser"
    )

    fun isBrowser(packageName: String): Boolean = packageName in BROWSERS

    /** The website the screen is showing, if the app is one that can be believed about it. */
    fun trustedDomain(packageName: String, webDomain: String?): String? =
        webDomain?.takeIf { isBrowser(packageName) }?.let(Rune::normalize)?.takeIf { it.isNotEmpty() }

    /**
     * In a browser: sites whose name is the page's domain or a parent of it, so "github.com"
     * serves "gist.github.com" but never "notgithub.com". In an app: sites linked to it.
     */
    fun match(sites: List<Site>, packageName: String, webDomain: String?): List<Site> {
        if (isBrowser(packageName)) {
            val domain = trustedDomain(packageName, webDomain) ?: return emptyList()

            // A dot is required so a site saved as just "com" can't match the whole internet.
            return sites.filter {
                '.' in it.site && (domain == it.site || domain.endsWith(".${it.site}"))
            }
        }

        return sites.filter { packageName in it.apps }
    }

    /** What to call a new site for this page: the domain without a leading "www." */
    fun suggestedName(domain: String): String = domain.removePrefix("www.")
}
