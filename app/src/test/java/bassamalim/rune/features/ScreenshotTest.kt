package bassamalim.rune.features

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import bassamalim.rune.core.ui.theme.RuneTheme
import bassamalim.rune.core.ui.theme.Surface0
import bassamalim.rune.features.autofill.AutofillRow
import bassamalim.rune.features.autofill.AutofillScreen
import bassamalim.rune.features.autofill.AutofillUiState
import bassamalim.rune.core.data.repositories.InstalledApp
import bassamalim.rune.features.editor.AppPickerContent
import bassamalim.rune.features.editor.EditorScreen
import bassamalim.rune.features.editor.EditorUiState
import bassamalim.rune.features.settings.SettingsScreen
import bassamalim.rune.features.settings.SettingsUiState
import bassamalim.rune.features.setup.SetupScreen
import bassamalim.rune.features.setup.SetupStep
import bassamalim.rune.features.setup.SetupUiState
import bassamalim.rune.features.setup.Strength
import bassamalim.rune.features.setup.StrengthLevel
import bassamalim.rune.features.site.SiteScreen
import bassamalim.rune.features.site.SiteUiState
import bassamalim.rune.features.sites.SiteRow
import bassamalim.rune.features.sites.SitesScreen
import bassamalim.rune.features.sites.SitesUiState
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders every screen to `app/build/screenshots/` so the design can be looked at without a
 * phone: `./gradlew :app:testDebugUnitTest --tests '*ScreenshotTest'`.
 *
 * These draw the stateless screens from hand-made state. They check nothing; they exist to be
 * looked at.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class ScreenshotTest {

    @get:Rule val compose = createComposeRule()

    private fun shoot(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            RuneTheme { Box(Modifier.fillMaxSize().background(Surface0)) { content() } }
        }
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    private val rows = listOf(
        SiteRow("bank.example", ""),
        SiteRow("github.com", "bassam"),
        SiteRow("github.com", "work"),
        SiteRow("mail.example", "bassam@example.com"),
        SiteRow("news.example", ""),
        SiteRow("shop.example", "bassam")
    )

    @Test fun setupForm() = shoot("1-setup") {
        SetupScreen(
            SetupUiState(
                generated = "velvet orbit candle mosaic frosty lantern",
                generatedStrength = "78 bits"
            ),
            {}, {}, {}, {}, {}, {}, {}
        )
    }

    @Test fun setupFormErrors() = shoot("1b-setup-own") {
        SetupScreen(
            SetupUiState(
                masterPassword = "Bassam2024!",
                repeatPassword = "Bassam2024",
                strength = Strength(
                    "If a site leaks one of your passwords, guessing this would take about 1 day.",
                    StrengthLevel.Weak
                ),
                repeatError = "The two passwords don't match."
            ),
            {}, {}, {}, {}, {}, {}, {}
        )
    }

    @Test fun setupConfirm() = shoot("2-confirm") {
        SetupScreen(
            SetupUiState(
                step = SetupStep.Confirm,
                fingerprint = "ef15",
                canUseBiometrics = true
            ),
            {}, {}, {}, {}, {}, {}, {}
        )
    }

    @Test fun sitesEmpty() = shoot("3-empty") {
        SitesScreen(SitesUiState(isLoading = false, isEmpty = true), {}, {}, {}, {})
    }

    @Test fun sites() = shoot("4-sites") {
        SitesScreen(SitesUiState(isLoading = false, rows = rows), {}, {}, {}, {})
    }

    @Test fun siteLocked() = shoot("5-site-locked") {
        SiteScreen(
            SiteUiState(isLoading = false, site = "github.com", login = "bassam"),
            {}, {}, {}, {}, {}, {}, {}
        )
    }

    @Test fun siteUnlocked() = shoot("6-site") {
        SiteScreen(
            SiteUiState(
                isLoading = false,
                site = "github.com",
                login = "bassam",
                note = "username: bassam_m",
                details = "16 characters  ·  counter 2  ·  about 100 bits",
                password = "FjKM@aRtMEp8=&cvQJDL",
                hasPrevious = true,
                isRevealed = true
            ),
            {}, {}, {}, {}, {}, {}, {}
        )
    }

    @Test fun editor() = shoot("7-editor") {
        EditorScreen(
            EditorUiState(
                isLoading = false,
                isEditing = true,
                site = "github.com",
                login = "bassam",
                symbolSet = "-_.",
                strength = "about 117 bits",
                apps = listOf(InstalledApp("com.github.android", "GitHub")),
                note = "username: bassam_m"
            ),
            {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}
        )
    }

    @Test fun appPicker() = shoot("7b-app-picker") {
        AppPickerContent(
            apps = listOf(
                InstalledApp("com.github.android", "GitHub"),
                InstalledApp("org.mozilla.firefox", "Firefox"),
                InstalledApp("com.google.android.apps.maps", "Maps"),
                InstalledApp("org.thoughtcrime.securesms", "Signal")
            ),
            site = "github.com",
            onPick = {},
            onDismiss = {}
        )
    }

    @Test fun settings() = shoot("8-settings") {
        SettingsScreen(
            SettingsUiState(
                isLoading = false,
                fingerprint = "ef15",
                isQuickUnlockOn = true,
                canUseBiometrics = true,
                siteCount = 6,
                message = "Imported: 2 new, 1 updated.",
                version = "1.0"
            ),
            {}, {}, {}, {}, {}, {}
        )
    }

    @Test fun autofillPicker() = shoot("9-autofill") {
        AutofillScreen(
            AutofillUiState(
                isLoading = false,
                domain = "gitlab.com",
                rows = rows.take(4).map { AutofillRow(it.site, it.login) },
                suggestion = "gitlab.com"
            ),
            {}, {}, {}, {}, {}
        )
    }
}
