package bassamalim.rune.features.autofill

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bassamalim.rune.core.autofill.AutofillActivity
import bassamalim.rune.core.models.Site
import bassamalim.rune.core.models.SiteKey
import bassamalim.rune.core.vault.UnlockStep
import bassamalim.rune.core.vault.Unlocker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.crypto.Cipher
import javax.inject.Inject

@HiltViewModel
class AutofillViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val domain: AutofillDomain,
    private val unlocker: Unlocker
) : ViewModel() {

    // The activity's intent extras arrive here as the saved-state defaults.
    private val packageName: String = savedStateHandle[AutofillActivity.EXTRA_PACKAGE] ?: ""
    private val webDomain: String? = savedStateHandle[AutofillActivity.EXTRA_DOMAIN]

    /** Set when the tapped suggestion named its site: there is nothing to pick. */
    private val preset: SiteKey? = savedStateHandle.get<String>(AutofillActivity.EXTRA_SITE)?.let {
        SiteKey(it, savedStateHandle[AutofillActivity.EXTRA_LOGIN] ?: "")
    }

    private val localState = MutableStateFlow(LocalState(chosen = preset))

    val uiState: StateFlow<AutofillUiState> = combine(localState, unlocker.step) { local, unlock ->
        val sites = local.sites

        AutofillUiState(
            isLoading = sites == null && local.problem == null,
            domain = webDomain,
            chosen = local.chosen?.site,
            query = local.query,
            rows = domain.filter(sites.orEmpty(), local.query).map { AutofillRow(it.site, it.login) },
            suggestion = sites?.let { domain.suggestion(it, webDomain) },
            unlock = unlock,
            problem = local.problem,
            result = local.result,
            isCancelled = local.isCancelled
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AutofillUiState(domain = webDomain, chosen = preset?.site)
    )

    init {
        viewModelScope.launch {
            val sites = domain.sites()

            localState.update {
                if (sites == null) it.copy(problem = "Rune can't read your site list. Open Rune to see why.")
                else it.copy(sites = sites)
            }
            if (sites != null && preset != null) unlocker.begin()
        }
    }

    fun onQueryChange(text: String) = localState.update { it.copy(query = text) }

    fun onSiteClick(row: AutofillRow) = choose(SiteKey(row.site, row.login))

    /** Adds the page's domain as a new site with the default settings, then fills it. */
    fun onSuggestionClick(name: String) = viewModelScope.launch {
        domain.create(name).fold(
            onSuccess = {
                localState.update { it.copy(sites = domain.sites()) }
                choose(SiteKey(name, ""))
            },
            onFailure = { e -> localState.update { it.copy(problem = e.message) } }
        )
    }

    private fun choose(key: SiteKey) {
        localState.update { it.copy(chosen = key, problem = null) }
        viewModelScope.launch { unlocker.begin() }
    }

    fun onUnlockClick() {
        if (unlocker.step.value == UnlockStep.Idle) viewModelScope.launch { unlocker.begin() }
    }

    fun onBiometricSucceeded(cipher: Cipher) = viewModelScope.launch {
        unlocker.onBiometricSucceeded(cipher)?.let { fill(it) }
    }

    fun onUsePasswordClick() = unlocker.onUsePassword()

    fun onSkipPracticeClick() = viewModelScope.launch { unlocker.skipPractice() }

    fun onMasterPassword(password: String) = viewModelScope.launch {
        unlocker.onPassword(password)?.let { fill(it) }
    }

    /** Backing out of a named suggestion closes; backing out of a pick returns to the list. */
    fun onUnlockCancelled() {
        unlocker.cancel()
        localState.update {
            if (preset != null) it.copy(isCancelled = true) else it.copy(chosen = null)
        }
    }

    private suspend fun fill(masterKey: ByteArray) {
        val key = localState.value.chosen
        val site: Site? = localState.value.sites?.firstOrNull { it.key == key }
        if (key == null || site == null) return masterKey.fill(0)

        domain.password(masterKey, site).fold(
            onSuccess = { password ->
                // Only now, with the fingerprint given, is the choice worth remembering.
                if (preset == null) domain.link(key, packageName)
                localState.update { it.copy(result = FillResult(site.site, site.login, password)) }
            },
            onFailure = { e ->
                localState.update {
                    it.copy(chosen = null, problem = "That site's settings are invalid: ${e.message}")
                }
            }
        )
    }

    private data class LocalState(
        val sites: List<Site>? = null,
        val chosen: SiteKey? = null,
        val query: String = "",
        val problem: String? = null,
        val result: FillResult? = null,
        val isCancelled: Boolean = false
    )
}
