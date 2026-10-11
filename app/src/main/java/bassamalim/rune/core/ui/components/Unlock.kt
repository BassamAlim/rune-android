package bassamalim.rune.core.ui.components

import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import bassamalim.rune.core.ui.theme.Ink
import bassamalim.rune.core.ui.theme.InkMuted
import bassamalim.rune.core.ui.theme.Spacing
import bassamalim.rune.core.ui.theme.Surface100
import bassamalim.rune.core.utils.findFragmentActivity
import bassamalim.rune.core.vault.UnlockStep
import javax.crypto.Cipher

/**
 * Draws whatever [step] asks for: the system fingerprint sheet, or Rune's own master-password
 * sheet. Screens put this in once and forward the callbacks to their ViewModel.
 */
@Composable
fun UnlockPrompts(
    step: UnlockStep,
    reason: String,
    onBiometricSucceeded: (Cipher) -> Unit,
    onUsePassword: () -> Unit,
    onPassword: (String) -> Unit,
    onCancel: () -> Unit
) {
    val activity = LocalContext.current.findFragmentActivity()
    val succeeded by rememberUpdatedState(onBiometricSucceeded)
    val usePassword by rememberUpdatedState(onUsePassword)
    val cancel by rememberUpdatedState(onCancel)

    LaunchedEffect(step) {
        if (step !is UnlockStep.Biometric) return@LaunchedEffect
        // No activity to attach to means no fingerprint sheet: the password still works.
        if (activity == null) return@LaunchedEffect usePassword()

        activity.promptWithCipher(
            cipher = step.cipher,
            title = reason,
            negativeLabel = "Use master password",
            onSuccess = { succeeded(it) },
            onNegative = { usePassword() },
            onCancel = { cancel() }
        )
    }

    if (step is UnlockStep.Password) {
        MasterPasswordSheet(
            reason = reason,
            isChecking = step.isChecking,
            error = step.error,
            onSubmit = onPassword,
            onDismiss = onCancel
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasterPasswordSheet(
    reason: String,
    isChecking: Boolean,
    error: String?,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Surface100,
        contentColor = Ink
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.screen)
                .padding(bottom = Spacing.xl)
        ) {
            Text(reason, style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Enter your master password. Checking it takes a few seconds.",
                style = MaterialTheme.typography.bodyMedium,
                color = InkMuted,
                modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.lg)
            )
            PasswordField(
                value = password,
                onValueChange = { password = it },
                label = "Master password",
                error = error,
                imeAction = ImeAction.Done,
                onDone = { onSubmit(password) },
                modifier = Modifier.focusRequester(focus)
            )
            PrimaryButton(
                label = if (isChecking) "Checking" else "Unlock",
                onClick = { onSubmit(password) },
                enabled = password.isNotEmpty(),
                isLoading = isChecking,
                modifier = Modifier.padding(top = Spacing.md)
            )
        }
    }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

/**
 * The system prompt, bound to [cipher]: the cipher only works after a real authentication, so
 * there is no success callback worth faking.
 */
fun FragmentActivity.promptWithCipher(
    cipher: Cipher,
    title: String,
    negativeLabel: String,
    onSuccess: (Cipher) -> Unit,
    onNegative: () -> Unit,
    onCancel: () -> Unit
) {
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            result.cryptoObject?.cipher?.let(onSuccess) ?: onNegative()
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            when (errorCode) {
                BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_CANCELED -> onCancel()
                // The button itself, a lockout, or a sensor that's gone: the password still works.
                else -> onNegative()
            }
        }
    }
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setNegativeButtonText(negativeLabel)
        .setAllowedAuthenticators(BIOMETRIC_STRONG)
        .setConfirmationRequired(false)
        .build()

    BiometricPrompt(this, ContextCompat.getMainExecutor(this), callback)
        .authenticate(info, BiometricPrompt.CryptoObject(cipher))
}

/**
 * A plain "is it you" check with no key behind it, for showing things that are already readable
 * to the app (the secret key). Fingerprint or the phone's screen lock; a phone with neither
 * passes, since there is nothing to ask.
 */
fun FragmentActivity.confirmPresence(title: String, onConfirmed: () -> Unit) {
    val authenticators = BIOMETRIC_STRONG or DEVICE_CREDENTIAL
    val available = androidx.biometric.BiometricManager.from(this)
        .canAuthenticate(authenticators) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
    if (!available) return onConfirmed()

    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) =
            onConfirmed()
    }
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setAllowedAuthenticators(authenticators)
        .build()

    BiometricPrompt(this, ContextCompat.getMainExecutor(this), callback).authenticate(info)
}
