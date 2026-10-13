package bassamalim.rune.core.ui.components

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import bassamalim.rune.R
import bassamalim.rune.core.sync.ApprovalState
import bassamalim.rune.core.ui.theme.Accent
import bassamalim.rune.core.ui.theme.Ink
import bassamalim.rune.core.ui.theme.InkMuted
import bassamalim.rune.core.ui.theme.RuneType
import bassamalim.rune.core.ui.theme.Spacing
import bassamalim.rune.core.ui.theme.Surface100
import bassamalim.rune.core.vault.UnlockStep
import javax.crypto.Cipher

/**
 * What the desktop asks for (SPEC §3.4), over whatever is on screen: what it wants, the code its
 * screen shows too, then the unlock. Dismissing declines.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApprovalSheet(
    state: ApprovalState,
    unlock: UnlockStep,
    onApprove: () -> Unit,
    onDecline: () -> Unit,
    onBiometricSucceeded: (Cipher) -> Unit,
    onUsePassword: () -> Unit,
    onPassword: (String) -> Unit,
    onSkipPractice: () -> Unit,
    onUnlockCancelled: () -> Unit
) {
    if (state !is ApprovalState.Asking) return

    ModalBottomSheet(
        onDismissRequest = onDecline,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Surface100,
        contentColor = Ink
    ) {
        ApprovalContent(state, onApprove, onDecline)
    }

    UnlockPrompts(
        step = unlock,
        reason = "Approve for your desktop",
        onBiometricSucceeded = onBiometricSucceeded,
        onUsePassword = onUsePassword,
        onPassword = onPassword,
        onSkipPractice = onSkipPractice,
        onCancel = onUnlockCancelled
    )
}

@Composable
internal fun ApprovalContent(state: ApprovalState.Asking, onApprove: () -> Unit, onDecline: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = Spacing.screen)
            .padding(bottom = Spacing.xl)
    ) {
        Text("Your desktop asks", style = MaterialTheme.typography.titleLarge)
        Text(
            text = state.what,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = Spacing.xs)
        )
        Text(
            text = "${state.code.take(3)} ${state.code.drop(3)}",
            style = RuneType.Fingerprint,
            color = Accent,
            modifier = Modifier.padding(top = Spacing.lg)
        )
        Text(
            text = "Approve only if your desktop shows the same code. If you didn't just ask " +
                    "for this on your desktop, decline.",
            style = MaterialTheme.typography.bodyMedium,
            color = InkMuted,
            modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.lg)
        )
        PrimaryButton(
            label = if (state.isWorking) "Sending" else "Approve",
            icon = R.drawable.ic_fingerprint,
            onClick = onApprove,
            isLoading = state.isWorking
        )
        TextAction(
            label = "Decline",
            onClick = onDecline,
            color = InkMuted,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = Spacing.xs)
        )
    }
}
