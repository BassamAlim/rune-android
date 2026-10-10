package bassamalim.echo.features.sites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bassamalim.echo.core.data.repositories.SitesState
import bassamalim.echo.core.nav.Navigator
import bassamalim.echo.core.nav.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SitesViewModel @Inject constructor(
    private val domain: SitesDomain,
    private val navigator: Navigator
) : ViewModel() {

    private val query = MutableStateFlow("")

    val uiState: StateFlow<SitesUiState> = combine(domain.observeState(), query) { state, query ->
        when (state) {
            SitesState.Loading -> SitesUiState(query = query)

            is SitesState.Unreadable -> SitesUiState(isLoading = false, unreadable = state.reason)

            is SitesState.Loaded -> SitesUiState(
                isLoading = false,
                query = query,
                rows = domain.filter(state.list.sites, query).map { SiteRow(it.site, it.login) },
                isEmpty = state.list.sites.isEmpty()
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SitesUiState()
    )

    fun onQueryChange(text: String) {
        query.value = text
    }

    fun onSiteClick(row: SiteRow) = navigator.navigate(Screen.Site(row.site, row.login))

    fun onAddClick() = navigator.navigate(Screen.Editor())

    fun onSettingsClick() = navigator.navigate(Screen.Settings)
}
