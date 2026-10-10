package bassamalim.echo.features.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bassamalim.echo.core.nav.Navigator
import bassamalim.echo.core.nav.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.crypto.Cipher
import javax.inject.Inject

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val domain: SetupDomain,
    private val navigator: Navigator
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SetupUiState(canUseBiometrics = domain.canUseBiometrics())
    )
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    /** Held only between deriving and saving, then zeroed. */
    private var secretKey: ByteArray? = null
    private var masterKey: ByteArray? = null

    fun onMasterPasswordChange(text: String) =
        _uiState.update { it.copy(masterPassword = text, passwordError = null) }

    fun onRepeatPasswordChange(text: String) =
        _uiState.update { it.copy(repeatPassword = text, repeatError = null) }

    fun onHasSecretKeyChange(has: Boolean) =
        _uiState.update { it.copy(hasSecretKey = has, secretKeyError = null) }

    fun onSecretKeyChange(text: String) =
        _uiState.update { it.copy(secretKeyText = text, secretKeyError = null) }

    fun onContinueClick() {
        val state = _uiState.value
        if (state.step != SetupStep.Form) return

        val typedKey = if (state.hasSecretKey) domain.decodeSecretKey(state.secretKeyText) else null
        val passwordError = "Enter a master password.".takeIf { state.masterPassword.isEmpty() }
        val repeatError = "The two passwords don't match."
            .takeIf { passwordError == null && state.masterPassword != state.repeatPassword }
        val secretKeyError = "That isn't a valid secret key. It has 52 letters and digits."
            .takeIf { state.hasSecretKey && typedKey == null }

        if (passwordError != null || repeatError != null || secretKeyError != null) {
            _uiState.update {
                it.copy(
                    passwordError = passwordError,
                    repeatError = repeatError,
                    secretKeyError = secretKeyError
                )
            }
            return
        }

        _uiState.update { it.copy(step = SetupStep.Deriving) }
        viewModelScope.launch {
            val secret = typedKey ?: domain.newSecretKey()
            val master = domain.deriveMasterKey(state.masterPassword, secret)

            secretKey = secret
            masterKey = master
            _uiState.update {
                it.copy(
                    step = SetupStep.Confirm,
                    // The password has done its job; it isn't kept anywhere from here on.
                    masterPassword = "",
                    repeatPassword = "",
                    secretKeyText = "",
                    fingerprint = domain.fingerprint(master),
                    newSecretKey = if (typedKey == null) domain.encodeSecretKey(secret) else "",
                    isWrittenDown = false,
                    canUseBiometrics = domain.canUseBiometrics()
                )
            }
        }
    }

    fun onWrittenDownChange(checked: Boolean) =
        _uiState.update { it.copy(isWrittenDown = checked) }

    fun onBackToFormClick() {
        forgetKeys()
        _uiState.update { SetupUiState(canUseBiometrics = domain.canUseBiometrics()) }
    }

    fun onSaveClick() {
        val secret = secretKey ?: return
        val master = masterKey ?: return
        if (!_uiState.value.canSave) return

        _uiState.update { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            val cipher = runCatching { domain.store(secret, master) }.getOrElse {
                _uiState.update { state ->
                    state.copy(isSaving = false, saveError = "The keys couldn't be saved on this phone.")
                }
                return@launch
            }

            // No fingerprint enrolled: Echo works, it just asks for the password each time.
            if (cipher == null) finish()
            else _uiState.update { it.copy(sealing = cipher) }
        }
    }

    fun onSealAuthenticated(cipher: Cipher) {
        val master = masterKey ?: return

        viewModelScope.launch {
            domain.seal(cipher, master)
            finish()
        }
    }

    /** The keys are stored either way; skipping only means the password is asked for each time. */
    fun onSealSkipped() = finish()

    private fun finish() {
        forgetKeys()
        navigator.navigate(Screen.Sites) { popUpTo(Screen.Setup) { inclusive = true } }
    }

    private fun forgetKeys() {
        secretKey?.fill(0)
        masterKey?.fill(0)
        secretKey = null
        masterKey = null
    }

    override fun onCleared() = forgetKeys()
}
