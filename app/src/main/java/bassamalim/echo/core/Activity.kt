package bassamalim.echo.core

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import bassamalim.echo.core.nav.Navigation
import bassamalim.echo.core.nav.Navigator
import bassamalim.echo.core.ui.theme.EchoTheme
import bassamalim.echo.core.ui.theme.Surface0
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** A [FragmentActivity] because that's what BiometricPrompt attaches to. */
@AndroidEntryPoint
class Activity : FragmentActivity() {

    @Inject lateinit var navigator: Navigator

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
            EchoTheme {
                val startDestination by viewModel.startDestination.collectAsStateWithLifecycle()

                // safeDrawing keeps content out from under the status bar and gesture area.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Surface0)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                ) {
                    startDestination?.let {
                        Navigation(navigator = navigator, startDestination = it)
                    }
                }
            }
        }
    }

}
