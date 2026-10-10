package bassamalim.echo.core.autofill

import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.Intent
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.view.View
import android.view.autofill.AutofillId
import android.widget.RemoteViews
import bassamalim.echo.R
import bassamalim.echo.core.data.repositories.SitesRepository
import bassamalim.echo.core.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Offers Echo's passwords in other apps' login forms (SPEC.md §4.3).
 *
 * This service never holds a password. Every suggestion is a locked dataset: tapping one opens
 * [AutofillActivity], which asks for the fingerprint, works the password out, and hands it back
 * to the system for that one field. Nothing is saved from forms either: Echo has nothing to save.
 */
@AndroidEntryPoint
class EchoAutofillService : AutofillService() {

    @Inject lateinit var sitesRepository: SitesRepository
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val structure = request.fillContexts.lastOrNull()?.structure
            ?: return callback.onSuccess(null)
        val packageName = structure.activityComponent.packageName
        // Echo's own master-password fields are not something to autofill.
        if (packageName == this.packageName) return callback.onSuccess(null)

        val collected = Collected()
        for (i in 0 until structure.windowNodeCount) {
            collect(structure.getWindowNodeAt(i).rootViewNode, collected)
        }
        val form = FormParser.parse(collected.fields) ?: return callback.onSuccess(null)
        val target = FillTarget(
            packageName = packageName,
            webDomain = SiteMatcher.trustedDomain(packageName, collected.webDomain),
            usernameId = form.username?.let { collected.ids[it.index] },
            passwordId = collected.ids[form.password.index]
        )

        scope.launch {
            val sites = sitesRepository.sites() ?: return@launch callback.onSuccess(null)
            val matches = SiteMatcher.match(sites, packageName, collected.webDomain)
            val response = FillResponse.Builder()

            matches.take(MAX_SUGGESTIONS).forEachIndexed { index, site ->
                response.addDataset(
                    lockedDataset(
                        target = target,
                        title = site.site,
                        subtitle = site.login.ifEmpty { "Fill password" },
                        intent = AutofillActivity.fill(this@EchoAutofillService, target, site),
                        requestCode = index + 1
                    )
                )
            }
            // Always offered, so a site that isn't matched (or isn't saved yet) is one tap away.
            response.addDataset(
                lockedDataset(
                    target = target,
                    title = if (matches.isEmpty()) "Fill with Echo" else "Another site",
                    subtitle = "Choose in Echo",
                    intent = AutofillActivity.pick(this@EchoAutofillService, target),
                    requestCode = 0
                )
            )

            callback.onSuccess(response.build())
        }
    }

    /** Echo generates passwords; it never stores what you type into a form. */
    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) = callback.onSuccess()

    /**
     * A suggestion with no values in it. The system shows the row; the values only exist after
     * [AutofillActivity] has authenticated and returned a real dataset in its place.
     */
    @Suppress("DEPRECATION") // The Presentations API replacing these is API 33+; minSdk is 30.
    private fun lockedDataset(
        target: FillTarget,
        title: String,
        subtitle: String,
        intent: Intent,
        requestCode: Int
    ): Dataset {
        val row = RemoteViews(packageName, R.layout.autofill_row).apply {
            setTextViewText(R.id.title, title)
            setTextViewText(R.id.subtitle, subtitle)
        }
        // Mutable because the system adds the result extras; cancel-current because the extras
        // differ between requests and must not be reused from an older one.
        val sender = PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE
        ).intentSender

        return Dataset.Builder(row)
            .apply { target.usernameId?.let { setValue(it, null, row) } }
            .setValue(target.passwordId, null, row)
            .setAuthentication(sender)
            .build()
    }

    private class Collected {
        val fields = mutableListOf<FormField>()
        val ids = mutableListOf<AutofillId>()
        var webDomain: String? = null
    }

    private fun collect(node: AssistStructure.ViewNode, into: Collected) {
        node.webDomain?.takeIf { it.isNotBlank() }?.let { into.webDomain = it }

        val id = node.autofillId
        if (id != null && node.autofillType == View.AUTOFILL_TYPE_TEXT && node.visibility == View.VISIBLE) {
            val html = node.htmlInfo?.attributes?.associate { it.first.lowercase() to it.second }

            into.fields += FormField(
                index = into.ids.size,
                autofillHints = node.autofillHints?.toList().orEmpty(),
                inputType = node.inputType,
                htmlType = html?.get("type"),
                htmlAutocomplete = html?.get("autocomplete"),
                idEntry = node.idEntry ?: html?.get("name") ?: html?.get("id"),
                hint = node.hint
            )
            into.ids += id
        }

        for (i in 0 until node.childCount) collect(node.getChildAt(i), into)
    }

    private companion object {
        const val MAX_SUGGESTIONS = 4
    }
}

/** The form a suggestion will fill, carried to [AutofillActivity] and back. */
data class FillTarget(
    val packageName: String,
    /** Only set when the app is a browser Echo believes about which site it's showing. */
    val webDomain: String?,
    val usernameId: AutofillId?,
    val passwordId: AutofillId
)

