package bassamalim.rune.core

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import bassamalim.rune.R
import bassamalim.rune.core.data.repositories.SiteIcons
import bassamalim.rune.core.nav.Navigation
import bassamalim.rune.core.sync.Approvals
import bassamalim.rune.core.sync.Sync
import bassamalim.rune.core.nav.Navigator
import bassamalim.rune.core.ui.components.ApprovalSheet
import bassamalim.rune.core.ui.components.LocalSiteIcons
import bassamalim.rune.core.ui.components.PrimaryButton
import bassamalim.rune.core.ui.components.TextAction
import bassamalim.rune.core.ui.components.UnlockPrompts
import bassamalim.rune.core.ui.theme.Accent
import bassamalim.rune.core.ui.theme.InkMuted
import bassamalim.rune.core.ui.theme.RuneTheme
import bassamalim.rune.core.ui.theme.Spacing
import bassamalim.rune.core.ui.theme.Surface0
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A [FragmentActivity] because that's what BiometricPrompt attaches to. */
@AndroidEntryPoint
class Activity : FragmentActivity() {

    @Inject lateinit var navigator: Navigator
    @Inject lateinit var siteIcons: SiteIcons
    @Inject lateinit var sync: Sync
    @Inject lateinit var approvals: Approvals

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // The app is dark-only, so the system bars are told so rather than asked.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        super.onCreate(savedInstanceState)

        // Passwords and the secret key never appear in screenshots, recordings or the recents
        // thumbnail.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        setContent {
            RuneTheme {
                CompositionLocalProvider(LocalSiteIcons provides siteIcons) {
                    val startDestination by viewModel.startDestination.collectAsStateWithLifecycle()
                    val isLocked by viewModel.isLocked.collectAsStateWithLifecycle()

                    // safeDrawing keeps content out from under the status bar and gesture area.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Surface0)
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                    ) {
                        val start = startDestination
                        when {
                            start == null || isLocked == null -> Unit
                            isLocked == true -> Locked(viewModel)
                            else -> Navigation(navigator = navigator, startDestination = start)
                        }
                    }

                    val approval by approvals.state.collectAsStateWithLifecycle()
                    val approvalUnlock by approvals.unlock.collectAsStateWithLifecycle()
                    ApprovalSheet(
                        state = approval,
                        unlock = approvalUnlock,
                        onApprove = approvals::onApprove,
                        onDecline = approvals::onDecline,
                        onBiometricSucceeded = { approvals.onBiometricSucceeded(it) },
                        onUsePassword = approvals::onUsePassword,
                        onPassword = { approvals.onPassword(it) },
                        onSkipPractice = { approvals.onSkipPractice() },
                        onUnlockCancelled = approvals::onUnlockCancelled
                    )
                }
            }
        }
    }

    private var lockJob: Job? = null

    override fun onStart() {
        super.onStart()
        lockJob?.cancel()
        // The desktop may have changed since Rune was last in front.
        sync.request()
        sync.watch()
    }

    override fun onStop() {
        super.onStop()
        sync.stopWatching()
        // A rotation stops the activity too, but nobody left.
        if (isChangingConfigurations) return
        lockJob = lifecycleScope.launch {
            delay(Globals.LOCK_GRACE_MILLIS)
            viewModel.lock()
        }
    }

}

/** What the app opens on with fingerprint unlock on: nothing behind it is drawn until it passes. */
@Composable
private fun Locked(viewModel: AppViewModel) {
    val unlock by viewModel.unlock.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onUnlockClick() }

    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.screen),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_rune_mark),
            contentDescription = null,
            tint = Accent,
            modifier = Modifier.size(Spacing.xxxl)
        )
        Text(
            text = "Rune is locked",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.xl)
        )
        PrimaryButton(
            label = "Unlock",
            icon = R.drawable.ic_fingerprint,
            onClick = viewModel::onUnlockClick
        )
        TextAction(
            label = "Use master password",
            onClick = viewModel::onUsePasswordClick,
            color = InkMuted,
            modifier = Modifier.padding(top = Spacing.xs)
        )
    }

    UnlockPrompts(
        step = unlock,
        reason = "Unlock Rune",
        onBiometricSucceeded = { viewModel.onBiometricSucceeded(it) },
        onUsePassword = viewModel::onUsePasswordClick,
        onPassword = { viewModel.onMasterPassword(it) },
        onSkipPractice = { viewModel.onSkipPracticeClick() },
        onCancel = viewModel::onUnlockCancelled
    )
}
