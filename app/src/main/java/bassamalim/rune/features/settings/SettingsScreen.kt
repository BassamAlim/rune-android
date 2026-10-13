package bassamalim.rune.features.settings

import android.os.Build
import android.content.Intent
import androidx.core.net.toUri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bassamalim.rune.R
import bassamalim.rune.core.ui.components.Card
import bassamalim.rune.core.ui.components.CardTitle
import bassamalim.rune.core.ui.components.ConfirmDialog
import bassamalim.rune.core.ui.components.DangerButton
import bassamalim.rune.core.ui.components.FieldLabel
import bassamalim.rune.core.ui.components.Note
import bassamalim.rune.core.ui.components.ScreenScaffold
import bassamalim.rune.core.ui.components.SecondaryButton
import bassamalim.rune.core.ui.components.TopBar
import bassamalim.rune.core.ui.components.UnlockPrompts
import bassamalim.rune.core.ui.components.promptWithCipher
import bassamalim.rune.core.ui.theme.Accent
import bassamalim.rune.core.ui.theme.Danger
import bassamalim.rune.core.ui.theme.RuneType
import bassamalim.rune.core.ui.theme.InkFaint
import bassamalim.rune.core.ui.theme.InkMuted
import bassamalim.rune.core.ui.theme.Spacing
import bassamalim.rune.core.utils.findFragmentActivity
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context.findFragmentActivity()
    val scope = rememberCoroutineScope()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        // A cancelled picker isn't a failed export, so it says nothing.
        if (uri == null) return@rememberLauncherForActivityResult

        scope.launch {
            val json = viewModel.exportJson()
            val written = json != null && runCatching {
                context.contentResolver.openOutputStream(uri)!!.use { it.write(json.encodeToByteArray()) }
            }.isSuccess

            viewModel.onExported(written, state.siteCount)
        }
    }

    // Asked or already granted, the sync runs either way: refused, it reports no desktop found.
    val networkPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.onSyncClick() }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        viewModel.onImportPicked(
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
        )
    }

    // The last step of turning fingerprint unlock on: the sheet that seals the key.
    LaunchedEffect(state.sealing) {
        val cipher = state.sealing ?: return@LaunchedEffect
        if (activity == null) return@LaunchedEffect viewModel.onSealCancelled()

        activity.promptWithCipher(
            cipher = cipher,
            title = "Turn on fingerprint unlock",
            negativeLabel = "Cancel",
            onSuccess = { viewModel.onSealAuthenticated(it) },
            onNegative = viewModel::onSealCancelled,
            onCancel = viewModel::onSealCancelled
        )
    }

    SettingsScreen(
        state = state,
        onBackClick = viewModel::onBackClick,
        onQuickUnlockClick = viewModel::onQuickUnlockClick,
        onAutofillClick = {
            // Android's own "choose an autofill service" screen, with Rune offered.
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)
                        .setData("package:${context.packageName}".toUri())
                )
            }
        },
        onExportClick = { exportLauncher.launch("rune-sites.json") },
        onImportClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
        onSyncClick = {
            // Android 17 asks before an app may talk to the local network; older ones don't.
            if (Build.VERSION.SDK_INT >= 37) networkPermission.launch(LOCAL_NETWORK) else viewModel.onSyncClick()
        },
        onResetClick = viewModel::onResetClick
    )

    UnlockPrompts(
        step = state.unlock,
        reason = "Turn on fingerprint unlock",
        onBiometricSucceeded = {},
        onUsePassword = {},
        onPassword = { viewModel.onMasterPassword(it) },
        onSkipPractice = {},
        onCancel = viewModel::onUnlockCancelled
    )

    if (state.isConfirmingReset) {
        ConfirmDialog(
            title = "Reset Rune?",
            text = "Your keys are removed from this phone. Your site list stays. To use Rune " +
                    "here again you need your master password.",
            confirmLabel = "Remove my keys",
            onConfirm = viewModel::onResetConfirm,
            onDismiss = viewModel::onResetDismiss
        )
    }
}

@Composable
internal fun SettingsScreen(
    state: SettingsUiState,
    onBackClick: () -> Unit,
    onQuickUnlockClick: () -> Unit,
    onAutofillClick: () -> Unit,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    onSyncClick: () -> Unit,
    onResetClick: () -> Unit
) {
    ScreenScaffold(topBar = { TopBar(title = "Settings", onBack = onBackClick) }) {
        if (state.isLoading) return@ScreenScaffold

        Card {
            CardTitle("Autofill")
            Text(
                text =
                    if (state.isAutofillOn) "Rune fills passwords in apps and browsers. Each " +
                            "one still asks for your fingerprint."
                    else "Let Rune type passwords straight into apps and browsers. It skips " +
                            "the clipboard and only offers a password on the site it belongs to.",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted
            )
            if (state.isAutofillOn) {
                Note(
                    text = "Autofill is on.",
                    icon = R.drawable.ic_check,
                    color = Accent,
                    modifier = Modifier.padding(top = Spacing.md)
                )
            } else {
                SecondaryButton(
                    label = "Turn on autofill",
                    icon = R.drawable.ic_autofill,
                    onClick = onAutofillClick,
                    modifier = Modifier.padding(top = Spacing.md)
                )
            }
        }

        Card(Modifier.padding(top = Spacing.md)) {
            CardTitle("Your key")
            FieldLabel("Fingerprint", Modifier.padding(top = Spacing.xs))
            Text(state.fingerprint, style = RuneType.Secret, color = Accent)
            Text(
                text = "A new device needs only your master password. Matching fingerprints show " +
                        "it was typed the same.",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted,
                modifier = Modifier.padding(top = Spacing.sm)
            )
        }

        Card(Modifier.padding(top = Spacing.md)) {
            CardTitle("Fingerprint unlock")
            when {
                state.isQuickUnlockOn -> {
                    Text(
                        text = "Your fingerprint unlocks each password. If you add or remove a " +
                                "fingerprint on this phone, Rune asks for your master password again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted
                    )
                    Note(
                        text = "Fingerprint unlock is on.",
                        icon = R.drawable.ic_check,
                        color = Accent,
                        modifier = Modifier.padding(top = Spacing.md)
                    )
                }

                state.canUseBiometrics -> {
                    Text(
                        text = "Rune is asking for your master password each time. Turn this " +
                                "on to use your fingerprint instead.",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkMuted
                    )
                    SecondaryButton(
                        label = "Turn on fingerprint unlock",
                        icon = R.drawable.ic_fingerprint,
                        onClick = onQuickUnlockClick,
                        modifier = Modifier.padding(top = Spacing.md)
                    )
                    state.unlockError?.let {
                        Note(text = it, color = Danger, modifier = Modifier.padding(top = Spacing.md))
                    }
                }

                else -> Text(
                    text = "No fingerprint is set up on this phone, so Rune asks for your " +
                            "master password each time. Add one in the phone's settings, then " +
                            "come back here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted
                )
            }
        }

        Card(Modifier.padding(top = Spacing.md)) {
            CardTitle("Desktop sync")
            Text(
                text = "Over your Wi-Fi, between devices with the same master password. Nothing " +
                        "goes through a server. Rune syncs when you open it and after each change.",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted
            )
            Text(
                text = state.sync,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Spacing.md)
            )
            SecondaryButton(
                label = if (state.isSyncing) "Syncing…" else "Sync now",
                onClick = onSyncClick,
                modifier = Modifier.padding(top = Spacing.md)
            )
        }

        Card(Modifier.padding(top = Spacing.md)) {
            CardTitle("Site list")
            Text(
                text = "Site names and settings, never passwords. Use the file to back up, or " +
                        "to move your sites to and from the desktop app.",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                SecondaryButton(
                    label = "Export",
                    icon = R.drawable.ic_export,
                    onClick = onExportClick,
                    modifier = Modifier.weight(1f)
                )
                SecondaryButton(
                    label = "Import",
                    icon = R.drawable.ic_import,
                    onClick = onImportClick,
                    modifier = Modifier.weight(1f)
                )
            }
            state.message?.let {
                Note(
                    text = it,
                    icon = if (state.isMessageError) R.drawable.ic_warning else R.drawable.ic_check,
                    color = if (state.isMessageError) Danger else Accent,
                    modifier = Modifier.padding(top = Spacing.md)
                )
            }
        }

        Card(Modifier.padding(top = Spacing.md)) {
            CardTitle("Reset")
            Text(
                text = "Removes your keys from this phone. Your site list stays.",
                style = MaterialTheme.typography.bodySmall,
                color = InkMuted
            )
            DangerButton(
                label = "Reset Rune",
                onClick = onResetClick,
                modifier = Modifier.padding(top = Spacing.md)
            )
        }

        Text(
            text = "Rune ${state.version}",
            style = MaterialTheme.typography.labelSmall,
            color = InkFaint,
            modifier = Modifier.padding(top = Spacing.lg)
        )
    }
}

/** `Manifest.permission.ACCESS_LOCAL_NETWORK`, new in API 37. */
private const val LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
