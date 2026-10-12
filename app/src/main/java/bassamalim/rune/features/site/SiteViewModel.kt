package bassamalim.rune.features.site

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import bassamalim.rune.core.models.Site
import bassamalim.rune.core.models.SiteKey
import bassamalim.rune.core.nav.Navigator
import bassamalim.rune.core.nav.Screen
import bassamalim.rune.core.vault.Session
import bassamalim.rune.core.vault.UnlockStep
import bassamalim.rune.core.vault.Unlocker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.crypto.Cipher
import javax.inject.Inject

@HiltViewModel
class SiteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val domain: SiteDomain,
    private val unlocker: Unlocker,
    private val session: Session,
    private val navigator: Navigator
) : ViewModel() {

    private val route = savedStateHandle.toRoute<Screen.Site>()
    private val key = SiteKey(route.site, route.login)

    /** What this screen owns rather than the site list: the password while it's on screen. */
    private val localState = MutableStateFlow(LocalState())

    val uiState: StateFlow<SiteUiState> = combine(
        domain.observeSite(key),
        unlocker.step,
        localState
    ) { site, unlock, local ->
        SiteUiState(
            isLoading = false,
            site = site?.site ?: key.site,
            login = site?.login ?: key.login,
            note = site?.note.orEmpty(),
            details = site?.let(domain::details).orEmpty(),
            password = local.password,
            hasPrevious = local.previous != null,
            isRevealed = local.isRevealed,
            isCopied = local.isCopied,
            isPreviousCopied = local.isPreviousCopied,
            unlock = unlock,
            problem = local.problem
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SiteUiState(site = key.site, login = key.login)
    )

    /** Asked for on arrival, and again by the Unlock button if the prompt was dismissed. */
    fun onUnlockClick() {
        if (localState.value.password != null) return
        // The fingerprint that opened the app counts.
        session.key()?.let { key -> viewModelScope.launch { show(key) }; return }
        // Already asking: a second prompt on top of the first would cancel it.
        if (unlocker.step.value != UnlockStep.Idle) return

        viewModelScope.launch { unlocker.begin() }
    }

    fun onBiometricSucceeded(cipher: Cipher) = viewModelScope.launch {
        unlocker.onBiometricSucceeded(cipher)?.let { show(it) }
    }

    fun onUsePasswordClick() = unlocker.onUsePassword()

    fun onSkipPracticeClick() = viewModelScope.launch { unlocker.skipPractice() }

    fun onMasterPassword(password: String) = viewModelScope.launch {
        unlocker.onPassword(password)?.let { show(it) }
    }

    fun onUnlockCancelled() = unlocker.cancel()

    private suspend fun show(masterKey: ByteArray) {
        val site: Site = domain.observeSite(key).first() ?: return masterKey.fill(0)

        domain.passwords(masterKey, site).fold(
            onSuccess = { localState.update { _ -> LocalState(password = it.current, previous = it.previous) } },
            onFailure = { e ->
                localState.update {
                    LocalState(problem = "This site's settings are invalid: ${e.message}. Edit it to fix them.")
                }
            }
        )
    }

    fun onRevealToggle() = localState.update { it.copy(isRevealed = !it.isRevealed) }

    fun onCopyClick() {
        val password = localState.value.password ?: return

        domain.copy(password)
        localState.update { it.copy(isCopied = true, isPreviousCopied = false) }
    }

    /** For a site's change-password form, which usually asks for the old password too. */
    fun onCopyPreviousClick() {
        val previous = localState.value.previous ?: return

        domain.copy(previous)
        localState.update { it.copy(isCopied = false, isPreviousCopied = true) }
    }

    /** Leaving the screen, or the app, forgets the password: coming back asks again. */
    fun onHidden() {
        unlocker.cancel()
        localState.value = LocalState()
    }

    fun onEditClick() = navigator.navigate(Screen.Editor(key.site, key.login))

    fun onBackClick() = navigator.popBackStack()

    private data class LocalState(
        val password: String? = null,
        /** The password at the counter before this one, when the counter has been raised. */
        val previous: String? = null,
        val isRevealed: Boolean = false,
        val isCopied: Boolean = false,
        val isPreviousCopied: Boolean = false,
        val problem: String? = null
    )
}
