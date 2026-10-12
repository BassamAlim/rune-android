package bassamalim.rune.features.editor

import bassamalim.rune.core.data.repositories.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Test

class AppChoicesTest {

    private val github = InstalledApp("com.github.android", "GitHub")
    private val bank = InstalledApp("com.example.mybank", "My Bank")
    private val maps = InstalledApp("com.google.android.apps.maps", "Maps")
    private val apps = listOf(github, bank, maps)

    @Test fun suggestsAppsThatLookLikeTheSite() {
        assertEquals(listOf(github) to listOf(bank, maps), appChoices(apps, "gist.github.com", ""))
        assertEquals(listOf(bank) to listOf(github, maps), appChoices(apps, "my bank", ""))
    }

    @Test fun shortOrEmptySitesSuggestNothing() {
        assertEquals(emptyList<InstalledApp>() to apps, appChoices(apps, "", ""))
        assertEquals(emptyList<InstalledApp>() to apps, appChoices(apps, "x.io", ""))
    }

    @Test fun searchMatchesNameOrPackageAndHidesSuggestions() {
        assertEquals(emptyList<InstalledApp>() to listOf(maps), appChoices(apps, "github.com", " MAPS "))
        assertEquals(emptyList<InstalledApp>() to listOf(maps), appChoices(apps, "github.com", "google"))
    }
}
