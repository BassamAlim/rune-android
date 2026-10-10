package bassamalim.echo.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bassamalim.echo.BuildConfig
import bassamalim.echo.core.nav.Navigator
import bassamalim.echo.core.nav.Screen
import bassamalim.echo.core.vault.Unlocker
import bassamalim.echo.core.vault.VaultState
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
class SettingsViewModel @Inject constructor(
    private val domain: SettingsDomain,
    private val unlocker: Unlocker,
    private val navigator: Navigator
) : ViewModel() {

    /** What this screen owns rather than the vault or the site list. */
    private val localState = MutableStateFlow(
        LocalState(
            canUseBiometrics = domain.canUseBiometrics(),
            isAutofillOn = domain.isAutofillOn()
        )
    )

    /** Held between the master password and the fingerprint sheet, then zeroed. */
    private var masterKey: ByteArray? = null

    val uiState: StateFlow<SettingsUiState> = combine(
        domain.observeVault(),
        domain.observeSites(),
        unlocker.step,
        localState
    ) { vault, sites, unlock, local ->
        val ready = vault as? VaultState.Ready

        SettingsUiState(
            isLoading = false,
            fingerprint = ready?.fingerprint.orEmpty(),
            isQuickUnlockOn = ready?.quickUnlock == true,
            canUseBiometrics = local.canUseBiometrics,
            isAutofillOn = local.isAutofillOn,
            secretKey = local.secretKey,
            siteCount = sites.size,
            message = local.message,
            isMessageError = local.isMessageError,
            unlock = unlock,
            sealing = local.sealing,
            unlockError = local.unlockError,
            isConfirmingReset = local.isConfirmingReset,
            version = BuildConfig.VERSION_NAME
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState()
    )

    /** Autofill and fingerprints are changed in system settings, so they're re-read on return. */
    fun onResume() = localState.update {
        it.copy(canUseBiometrics = domain.canUseBiometrics(), isAutofillOn = domain.isAutofillOn())
    }

    // ---- Secret key ----

    /** Called once the phone has confirmed it's the owner looking. */
    fun onSecretKeyAllowed() = viewModelScope.launch {
        val text = domain.secretKeyText()

        localState.update { it.copy(secretKey = text) }
    }

    fun onSecretKeyHide() = localState.update { it.copy(secretKey = null) }

    // ---- Fingerprint unlock ----

    fun onQuickUnlockClick() {
        localState.update { it.copy(unlockError = null) }
        unlocker.onUsePassword()
    }

    fun onMasterPassword(password: String) = viewModelScope.launch {
        val key = unlocker.onPassword(password) ?: return@launch
        val cipher = domain.sealingCipher()

        if (cipher == null) {
            key.fill(0)
            localState.update { it.copy(unlockError = UNLOCK_FAILED) }
        } else {
            masterKey = key
            localState.update { it.copy(sealing = cipher) }
        }
    }

    fun onUnlockCancelled() = unlocker.cancel()

    fun onSealAuthenticated(cipher: Cipher) = viewModelScope.launch {
        val key = masterKey ?: return@launch
        val sealed = domain.seal(cipher, key)

        forgetKey()
        // Success needs no announcement: the card itself turns to "on".
        localState.update { it.copy(unlockError = UNLOCK_FAILED.takeIf { !sealed }) }
    }

    fun onSealCancelled() = forgetKey()

    private fun forgetKey() {
        masterKey?.fill(0)
        masterKey = null
        localState.update { it.copy(sealing = null) }
    }

    // ---- Site list ----

    suspend fun exportJson(): String? = domain.exportJson()

    fun onExported(succeeded: Boolean, count: Int) =
        if (succeeded) say("Exported $count sites.") else say("The file couldn't be written.", isError = true)

    /** The picked file's text, or null when it couldn't be read off the disk. */
    fun onImportPicked(text: String?) {
        if (text == null) return say("That file couldn't be opened.", isError = true)

        viewModelScope.launch {
            domain.import(text).fold(
                onSuccess = { say(it) },
                onFailure = { say("That doesn't look like an Echo site list.", isError = true) }
            )
        }
    }

    private fun say(message: String, isError: Boolean = false) =
        localState.update { it.copy(message = message, isMessageError = isError) }

    // ---- Reset ----

    fun onResetClick() = localState.update { it.copy(isConfirmingReset = true) }

    fun onResetDismiss() = localState.update { it.copy(isConfirmingReset = false) }

    fun onResetConfirm() {
        localState.update { it.copy(isConfirmingReset = false) }
        viewModelScope.launch {
            domain.reset()
            navigator.navigate(Screen.Setup) { popUpTo(Screen.Sites) { inclusive = true } }
        }
    }

    fun onBackClick() = navigator.popBackStack()

    override fun onCleared() {
        masterKey?.fill(0)
    }

    private data class LocalState(
        val canUseBiometrics: Boolean,
        val isAutofillOn: Boolean,
        val secretKey: String? = null,
        val message: String? = null,
        val isMessageError: Boolean = false,
        val sealing: Cipher? = null,
        val unlockError: String? = null,
        val isConfirmingReset: Boolean = false
    )

    private companion object {
        const val UNLOCK_FAILED = "Fingerprint unlock couldn't be turned on."
    }
}
