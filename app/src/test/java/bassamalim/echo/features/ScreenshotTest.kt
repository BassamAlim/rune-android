package bassamalim.echo.features

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import bassamalim.echo.core.ui.theme.EchoTheme
import bassamalim.echo.core.ui.theme.Surface0
import bassamalim.echo.features.autofill.AutofillRow
import bassamalim.echo.features.autofill.AutofillScreen
import bassamalim.echo.features.autofill.AutofillUiState
import bassamalim.echo.features.editor.EditorScreen
import bassamalim.echo.features.editor.EditorUiState
import bassamalim.echo.features.settings.SettingsScreen
import bassamalim.echo.features.settings.SettingsUiState
import bassamalim.echo.features.setup.SetupScreen
import bassamalim.echo.features.setup.SetupStep
import bassamalim.echo.features.setup.SetupUiState
import bassamalim.echo.features.site.SiteScreen
import bassamalim.echo.features.site.SiteUiState
import bassamalim.echo.features.sites.SiteRow
import bassamalim.echo.features.sites.SitesScreen
import bassamalim.echo.features.sites.SitesUiState
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
            EchoTheme { Box(Modifier.fillMaxSize().background(Surface0)) { content() } }
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
        SetupScreen(SetupUiState(), {}, {}, {}, {}, {}, {}, {}, {})
    }

    @Test fun setupFormErrors() = shoot("1b-setup-errors") {
        SetupScreen(
            SetupUiState(
                masterPassword = "hunter2",
                repeatPassword = "hunter",
                hasSecretKey = true,
                secretKeyText = "AAAA",
                repeatError = "The two passwords don't match.",
                secretKeyError = "That isn't a valid secret key. It has 52 letters and digits."
            ),
            {}, {}, {}, {}, {}, {}, {}, {}
        )
    }

    @Test fun setupConfirm() = shoot("2-confirm") {
        SetupScreen(
            SetupUiState(
                step = SetupStep.Confirm,
                fingerprint = "ef15",
                newSecretKey = "AADQ 4FI4 EMVD COB7 IZGV IW3C NFYH O7UF RSJZ VINI V63L 3RGL 2LMQ",
                canUseBiometrics = true
            ),
            {}, {}, {}, {}, {}, {}, {}, {}
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
            {}, {}, {}, {}, {}, {}
        )
    }

    @Test fun siteUnlocked() = shoot("6-site") {
        SiteScreen(
            SiteUiState(
                isLoading = false,
                site = "github.com",
                login = "bassam",
                details = "20 characters  ·  counter 1  ·  about 125 bits",
                password = "FjKM@aRtMEp8=&cvQJDL",
                isRevealed = true
            ),
            {}, {}, {}, {}, {}, {}
        )
    }

    @Test fun editor() = shoot("7-editor") {
        EditorScreen(
            EditorUiState(
                isLoading = false,
                isEditing = true,
                site = "github.com",
                login = "bassam",
                symbols = false,
                strength = "about 119 bits"
            ),
            {}, {}, {}, {}, {}, {}, {}, {}
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
            {}, {}, {}, {}, {}, {}, {}, {}
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
