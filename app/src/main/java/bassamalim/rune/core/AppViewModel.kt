package bassamalim.rune.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bassamalim.rune.core.nav.Screen
import bassamalim.rune.core.vault.Session
import bassamalim.rune.core.vault.UnlockStep
import bassamalim.rune.core.vault.Unlocker
import bassamalim.rune.core.vault.Vault
import bassamalim.rune.core.vault.VaultState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.crypto.Cipher
import javax.inject.Inject

/**
 * Decides which door the app opens on: set-up if there are no keys on this phone, the site list
 * otherwise. With fingerprint unlock on, the app opens locked and asks for the fingerprint first;
 * that opens the [Session], so passwords inside don't ask again until the app is left.
 *
 * Decided once, from the first value: the graph's start destination must not move under the
 * user because they finished setting up, and turning fingerprint unlock on mustn't lock them out.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    vault: Vault,
    private val unlocker: Unlocker,
    private val session: Session
) : ViewModel() {

    private val initial = flow { emit(vault.state.first()) }
    private val isUnlocked = MutableStateFlow(false)

    val startDestination: StateFlow<Screen?> = flow {
        emit(
            when (initial.first()) {
                VaultState.NotSetUp -> Screen.Setup
                is VaultState.Ready -> Screen.Sites
            }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null
    )

    /** Null until known. */
    val isLocked: StateFlow<Boolean?> = combine(initial, isUnlocked) { state, unlocked ->
        !unlocked && state is VaultState.Ready && state.quickUnlock
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null
    )

    val unlock: StateFlow<UnlockStep> = unlocker.step

    fun onUnlockClick() {
        if (isUnlocked.value || unlocker.step.value != UnlockStep.Idle) return
        viewModelScope.launch { unlocker.begin() }
    }

    fun onBiometricSucceeded(cipher: Cipher) = viewModelScope.launch {
        unlocker.onBiometricSucceeded(cipher)?.let(::onUnlocked)
    }

    fun onUsePasswordClick() = unlocker.onUsePassword()

    fun onSkipPracticeClick() = viewModelScope.launch { unlocker.skipPractice() }

    fun onMasterPassword(password: String) = viewModelScope.launch {
        unlocker.onPassword(password)?.let(::onUnlocked)
    }

    fun onUnlockCancelled() = unlocker.cancel()

    private fun onUnlocked(masterKey: ByteArray) {
        session.open(masterKey)
        isUnlocked.value = true
    }

    /** The activity is finishing for good: nothing is left to lock, but the key must go. */
    override fun onCleared() = session.close()

    /** Leaving the app forgets the key and locks it again. */
    fun lock() {
        session.close()
        unlocker.cancel()
        isUnlocked.value = false
    }
}
