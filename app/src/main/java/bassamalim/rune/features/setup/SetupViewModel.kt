package bassamalim.rune.features.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import bassamalim.rune.core.nav.Navigator
import bassamalim.rune.core.nav.Screen
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
    private var masterKey: ByteArray? = null

    init {
        onGenerateClick()
    }

    /** Back to a generated passphrase, a fresh one each time. */
    fun onGenerateClick() = _uiState.update {
        it.copy(
            generated = domain.newPassphrase(),
            generatedStrength = domain.passphraseStrength(),
            masterPassword = "",
            repeatPassword = "",
            strength = null,
            passwordError = null,
            repeatError = null
        )
    }

    fun onOwnClick() = _uiState.update {
        it.copy(generated = null, repeatPassword = "", passwordError = null, repeatError = null)
    }

    fun onMasterPasswordChange(text: String) = _uiState.update {
        it.copy(
            masterPassword = text,
            strength = if (text.isEmpty()) null else domain.strength(text),
            passwordError = null
        )
    }

    fun onRepeatPasswordChange(text: String) =
        _uiState.update { it.copy(repeatPassword = text, repeatError = null) }

    fun onContinueClick() {
        val state = _uiState.value
        if (state.step != SetupStep.Form) return

        // Typing a generated passphrase back: case and spacing don't matter.
        fun words(text: String) = text.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val password = state.generated ?: state.masterPassword
        val passwordError = "Enter a master password.".takeIf { password.isEmpty() }
        val repeatError = when {
            passwordError != null -> null
            state.generated != null && words(state.generated) != words(state.repeatPassword) ->
                "That doesn't match the words above."
            state.generated == null && state.masterPassword != state.repeatPassword ->
                "The two passwords don't match."
            else -> null
        }

        if (passwordError != null || repeatError != null) {
            _uiState.update { it.copy(passwordError = passwordError, repeatError = repeatError) }
            return
        }

        _uiState.update { it.copy(step = SetupStep.Deriving) }
        viewModelScope.launch {
            val master = domain.deriveMasterKey(password)

            masterKey = master
            _uiState.update {
                it.copy(
                    step = SetupStep.Confirm,
                    // The password has done its job; it isn't kept anywhere from here on.
                    generated = null,
                    masterPassword = "",
                    repeatPassword = "",
                    strength = null,
                    fingerprint = domain.fingerprint(master),
                    canUseBiometrics = domain.canUseBiometrics()
                )
            }
        }
    }

    fun onBackToFormClick() {
        forgetKeys()
        _uiState.update { SetupUiState(canUseBiometrics = domain.canUseBiometrics()) }
        onGenerateClick()
    }

    fun onSaveClick() {
        val master = masterKey ?: return
        if (_uiState.value.isSaving) return

        _uiState.update { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            val cipher = runCatching { domain.store(master) }.getOrElse {
                _uiState.update { state ->
                    state.copy(isSaving = false, saveError = "The key couldn't be saved on this phone.")
                }
                return@launch
            }

            // No fingerprint enrolled: Rune works, it just asks for the password each time.
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
        masterKey?.fill(0)
        masterKey = null
    }

    override fun onCleared() = forgetKeys()
}
