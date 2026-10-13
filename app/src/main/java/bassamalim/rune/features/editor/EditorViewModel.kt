package bassamalim.rune.features.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import bassamalim.rune.core.crypto.Rune
import bassamalim.rune.core.data.repositories.InstalledApp
import bassamalim.rune.core.models.Site
import bassamalim.rune.core.models.SiteKey
import bassamalim.rune.core.nav.Navigator
import bassamalim.rune.core.nav.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
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

    /**
     * Every launcher app, for labelling linked apps and for the picker. Slow (a label per app),
     * so it loads beside the form rather than in front of it.
     */
    private val installed = viewModelScope.async { domain.installedApps() }

    /** Carried through an edit untouched: only the site screen keeps or forgets it. */
    private var sealed = ""

    private val _uiState = MutableStateFlow(EditorUiState(isEditing = original != null))
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val site = original?.let { domain.site(it) }
            sealed = site?.sealed.orEmpty()

            _uiState.update {
                (if (site == null) it else it.copy(
                    site = site.site,
                    login = site.login,
                    note = site.note,
                    counter = site.counter,
                    length = site.length,
                    lower = 'l' in site.classes,
                    upper = 'u' in site.classes,
                    digits = 'd' in site.classes,
                    symbols = 's' in site.classes,
                    symbolSet = site.symbols,
                    // Package names until the labels arrive.
                    apps = site.apps.map { InstalledApp(it, it) }
                )).copy(isLoading = false).withStrength()
            }

            val all = installed.await().associateBy { it.packageName }
            _uiState.update { state ->
                state.copy(apps = state.apps.map { all[it.packageName] ?: it })
            }
        }
    }

    private fun EditorUiState.withStrength() =
        copy(strength = domain.strength(length, classes, symbolSet))

    fun onSiteChange(text: String) = _uiState.update { it.copy(site = text, siteError = null) }

    fun onLoginChange(text: String) = _uiState.update { it.copy(login = text, siteError = null) }

    fun onNoteChange(text: String) = _uiState.update { it.copy(note = text) }

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

    fun onSymbolSetChange(text: String) =
        _uiState.update { it.copy(symbolSet = text, symbolsError = null).withStrength() }

    fun onCounterChange(delta: Int) = _uiState.update {
        it.copy(counter = (it.counter.toLong() + delta).coerceIn(1, Int.MAX_VALUE.toLong()).toInt())
    }

    fun onLinkAppClick() {
        viewModelScope.launch {
            val all = installed.await()
            _uiState.update { state -> state.copy(appChoices = all.filter { it !in state.apps }) }
        }
    }

    fun onAppPickerDismiss() = _uiState.update { it.copy(appChoices = null) }

    /** For an app with no website, its name is a fine site name, so an empty one is filled in. */
    fun onAppPick(app: InstalledApp) = _uiState.update {
        it.copy(
            apps = it.apps + app,
            appChoices = null,
            site = it.site.ifBlank { app.label.lowercase() },
            siteError = null
        )
    }

    fun onAppUnlink(app: InstalledApp) = _uiState.update { it.copy(apps = it.apps - app) }

    fun onSaveClick() {
        val state = _uiState.value
        val siteError = "Enter a site name.".takeIf { Rune.normalize(state.site).isEmpty() }
        val classesError = "Pick at least one kind of character.".takeIf { state.classes.isEmpty() }
        val symbols = runCatching { Rune.canonicalSymbols(state.symbolSet) }.getOrNull()
        val symbolsError = when {
            symbols == null -> "Symbols can only be ASCII punctuation, like ! # - ."
            state.symbols && symbols.isEmpty() -> "Enter the symbols this site allows, or turn symbols off."
            else -> null
        }

        if (siteError != null || classesError != null || symbolsError != null) {
            _uiState.update {
                it.copy(siteError = siteError, classesError = classesError, symbolsError = symbolsError)
            }
            return
        }

        viewModelScope.launch {
            val site = Site(
                site = state.site,
                login = state.login,
                counter = state.counter,
                length = state.length,
                classes = state.classes,
                symbols = symbols.orEmpty(),
                apps = state.apps.map { it.packageName },
                note = state.note,
                sealed = sealed
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
