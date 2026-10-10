package bassamalim.echo.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bassamalim.echo.core.nav.Screen
import bassamalim.echo.core.vault.Vault
import bassamalim.echo.core.vault.VaultState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Decides which door the app opens on: set-up if there are no keys on this phone, the site list
 * otherwise. There is no lock screen in between, because the list holds no secrets: each
 * password asks for the fingerprint on its own.
 *
 * Decided once, from the first value: the graph's start destination must not move under the
 * user because they finished setting up.
 */
@HiltViewModel
class AppViewModel @Inject constructor(vault: Vault) : ViewModel() {

    val startDestination: StateFlow<Screen?> = flow {
        emit(
            when (vault.state.first()) {
                VaultState.NotSetUp -> Screen.Setup
                is VaultState.Ready -> Screen.Sites
            }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null
    )
}
