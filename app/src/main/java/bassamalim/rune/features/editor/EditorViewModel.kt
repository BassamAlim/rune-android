package bassamalim.rune.features.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import bassamalim.rune.core.crypto.Rune
import bassamalim.rune.core.models.Site
import bassamalim.rune.core.models.SiteKey
import bassamalim.rune.core.nav.Navigator
import bassamalim.rune.core.nav.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val domain: EditorDomain,
    private val navigator: Navigator
) : ViewModel() {

    private val route = savedStateHandle.toRoute<Screen.Editor>()

    /** The entry being edited, or null when adding. */
    private val original: SiteKey? = route.site?.let { SiteKey(it, route.login.orEmpty()) }

    /** Carried through an edit untouched: the editor has no control for linked apps. */
    private var apps: List<String> = emptyList()

    private val _uiState = MutableStateFlow(EditorUiState(isEditing = original != null))
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val site = original?.let { domain.site(it) }

            apps = site?.apps.orEmpty()
            _uiState.update {
                (if (site == null) it else it.copy(
                    site = site.site,
                    login = site.login,
                    counter = site.counter,
                    length = site.length,
                    lower = 'l' in site.classes,
                    upper = 'u' in site.classes,
                    digits = 'd' in site.classes,
                    symbols = 's' in site.classes
                )).copy(isLoading = false).withStrength()
            }
        }
    }

    private fun EditorUiState.withStrength() = copy(strength = domain.strength(length, classes))

    fun onSiteChange(text: String) = _uiState.update { it.copy(site = text, siteError = null) }

    fun onLoginChange(text: String) = _uiState.update { it.copy(login = text, siteError = null) }

    fun onLengthChange(length: Int) = _uiState.update {
        it.copy(length = length.coerceIn(Rune.MIN_LENGTH, Rune.MAX_LENGTH)).withStrength()
    }

    fun onClassToggle(which: CharacterClass) = _uiState.update {
        when (which) {
            CharacterClass.Lower -> it.copy(lower = !it.lower)
            CharacterClass.Upper -> it.copy(upper = !it.upper)
            CharacterClass.Digits -> it.copy(digits = !it.digits)
            CharacterClass.Symbols -> it.copy(symbols = !it.symbols)
        }.copy(classesError = null).withStrength()
    }

    fun onCounterChange(delta: Int) = _uiState.update {
        it.copy(counter = (it.counter.toLong() + delta).coerceIn(1, Int.MAX_VALUE.toLong()).toInt())
    }

    fun onSaveClick() {
        val state = _uiState.value
        val siteError = "Enter a site name.".takeIf { Rune.normalize(state.site).isEmpty() }
        val classesError = "Pick at least one kind of character.".takeIf { state.classes.isEmpty() }

        if (siteError != null || classesError != null) {
            _uiState.update { it.copy(siteError = siteError, classesError = classesError) }
            return
        }

        viewModelScope.launch {
            val site = Site(
                site = state.site,
                login = state.login,
                counter = state.counter,
                length = state.length,
                classes = state.classes,
                apps = apps
            )

            domain.save(site, replacing = original).fold(
                onSuccess = { leaveAfterSaving(site) },
                onFailure = { e ->
                    // The list's own refusals ("already exist") are about the name fields.
                    if (e is IllegalArgumentException) {
                        _uiState.update { it.copy(siteError = e.message) }
                    } else {
                        _uiState.update { it.copy(saveError = "The site list couldn't be saved.") }
                    }
                }
            )
        }
    }

    /**
     * A new site opens on its password, which is why it was added. An edit returns to where it
     * came from, unless the name changed, in which case the old screen no longer has a site.
     */
    private fun leaveAfterSaving(site: Site) {
        val saved = SiteKey(Rune.normalize(site.site), Rune.normalize(site.login))

        if (saved == original) return navigator.popBackStack()

        navigator.navigate(Screen.Site(saved.site, saved.login)) {
            popUpTo(Screen.Sites) { inclusive = false }
        }
    }

    fun onDeleteClick() = _uiState.update { it.copy(isConfirmingDelete = true) }

    fun onDeleteDismiss() = _uiState.update { it.copy(isConfirmingDelete = false) }

    fun onDeleteConfirm() {
        val key = original ?: return

        _uiState.update { it.copy(isConfirmingDelete = false) }
        viewModelScope.launch {
            domain.delete(key).fold(
                onSuccess = { navigator.popUpTo(Screen.Sites) },
                onFailure = {
                    _uiState.update { it.copy(saveError = "The site couldn't be deleted.") }
                }
            )
        }
    }

    fun onBackClick() = navigator.popBackStack()
}
