package bassamalim.echo.features.autofill

import bassamalim.echo.core.vault.UnlockStep

data class AutofillUiState(
    val isLoading: Boolean = true,
    /** The website being filled, when a browser said which. Null inside an ordinary app. */
    val domain: String? = null,
    /** The site that will be filled. Null while the user is still choosing one. */
    val chosen: String? = null,
    val query: String = "",
    val rows: List<AutofillRow> = emptyList(),
    /** A site name to offer adding, when the page's domain isn't saved yet. */
    val suggestion: String? = null,
    val unlock: UnlockStep = UnlockStep.Idle,
    val problem: String? = null,
    /** Set once: the screen hands it to the system and closes. */
    val result: FillResult? = null,
    /** The suggestion named a site and unlocking was dismissed: nothing left to do here. */
    val isCancelled: Boolean = false
)

data class AutofillRow(val site: String, val login: String)

class FillResult(val site: String, val login: String, val password: String)
