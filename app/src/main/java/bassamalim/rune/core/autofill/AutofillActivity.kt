package bassamalim.rune.core.autofill

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.autofill.Dataset
import android.view.WindowManager
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.core.content.IntentCompat
import androidx.fragment.app.FragmentActivity
import bassamalim.rune.R
import bassamalim.rune.core.data.repositories.SiteIcons
import bassamalim.rune.core.models.Site
import bassamalim.rune.core.ui.components.LocalSiteIcons
import bassamalim.rune.core.ui.theme.RuneTheme
import bassamalim.rune.core.ui.theme.Surface0
import bassamalim.rune.features.autofill.AutofillScreen
import bassamalim.rune.features.autofill.FillResult
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Where a tapped autofill suggestion lands. It picks the site if the suggestion didn't name one,
 * asks for the fingerprint, and returns the filled-in dataset to the system, which types it into
 * the form. The password goes from here to the field and nowhere else.
 */
@AndroidEntryPoint
class AutofillActivity : FragmentActivity() {

    @Inject lateinit var siteIcons: SiteIcons

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        setContent {
            RuneTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Surface0)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                ) {
                    CompositionLocalProvider(LocalSiteIcons provides siteIcons) {
                        AutofillScreen(onResult = ::finishWith, onCancel = ::cancel)
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION") // The Presentations API replacing these is API 33+; minSdk is 30.
    private fun finishWith(result: FillResult) {
        val passwordId = IntentCompat.getParcelableExtra(intent, EXTRA_PASSWORD_ID, AutofillId::class.java)
            ?: return cancel()
        val usernameId = IntentCompat.getParcelableExtra(intent, EXTRA_USERNAME_ID, AutofillId::class.java)

        // The system fills this straight away, so the row is never shown; it still wants one.
        val row = RemoteViews(packageName, R.layout.autofill_row).apply {
            setTextViewText(R.id.title, result.site)
            setTextViewText(R.id.subtitle, result.login)
        }
        val dataset = Dataset.Builder(row)
            .setValue(passwordId, AutofillValue.forText(result.password), row)
            .apply {
                // An empty login means "this site has one account": leave the field alone.
                if (usernameId != null && result.login.isNotEmpty()) {
                    setValue(usernameId, AutofillValue.forText(result.login), row)
                }
            }
            .build()

        setResult(
            Activity.RESULT_OK,
            Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset)
        )
        finish()
    }

    private fun cancel() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    companion object {
        /** The site and login of the suggestion that was tapped; absent means "let me choose". */
        const val EXTRA_SITE = "site"
        const val EXTRA_LOGIN = "login"
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_DOMAIN = "domain"
        private const val EXTRA_USERNAME_ID = "username_id"
        private const val EXTRA_PASSWORD_ID = "password_id"

        fun fill(context: Context, target: FillTarget, site: Site): Intent =
            pick(context, target)
                .putExtra(EXTRA_SITE, site.site)
                .putExtra(EXTRA_LOGIN, site.login)

        fun pick(context: Context, target: FillTarget): Intent =
            Intent(context, AutofillActivity::class.java)
                .putExtra(EXTRA_PACKAGE, target.packageName)
                .putExtra(EXTRA_DOMAIN, target.webDomain)
                .putExtra(EXTRA_USERNAME_ID, target.usernameId)
                .putExtra(EXTRA_PASSWORD_ID, target.passwordId)
    }
}
