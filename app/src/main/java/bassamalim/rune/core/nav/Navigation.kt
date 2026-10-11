package bassamalim.rune.core.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import bassamalim.rune.core.ui.inFromLeft
import bassamalim.rune.core.ui.inFromRight
import bassamalim.rune.core.ui.outToLeft
import bassamalim.rune.core.ui.outToRight
import bassamalim.rune.features.editor.EditorScreen
import bassamalim.rune.features.settings.SettingsScreen
import bassamalim.rune.features.setup.SetupScreen
import bassamalim.rune.features.site.SiteScreen
import bassamalim.rune.features.sites.SitesScreen

@Composable
fun Navigation(navigator: Navigator, startDestination: Screen) {
    val navController = rememberNavController()

    LaunchedEffect(navController) {
        navigator.events.collect { command ->
            when (command) {
                is NavCommand.To -> navController.navigate(command.destination, command.options)
                is NavCommand.Back -> navController.popBackStack()
                is NavCommand.BackTo ->
                    navController.popBackStack(command.destination, command.inclusive)
            }
        }
    }

    NavGraph(navController = navController, startDestination = startDestination)
}

@Composable
fun NavGraph(navController: NavHostController, startDestination: Screen) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = inFromRight,
        exitTransition = outToLeft,
        popEnterTransition = inFromLeft,
        popExitTransition = outToRight
    ) {
        composable<Screen.Setup> { SetupScreen() }

        composable<Screen.Sites> { SitesScreen() }

        composable<Screen.Site> { SiteScreen() }

        composable<Screen.Editor> { EditorScreen() }

        composable<Screen.Settings> { SettingsScreen() }
    }
}
