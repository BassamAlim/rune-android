package bassamalim.rune.core.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry

/** Going deeper comes in from the right; going back retraces it. Exits are quicker than entries. */
val inFromRight = { _: AnimatedContentTransitionScope<NavBackStackEntry> ->
    slideInHorizontally(initialOffsetX = { it / 4 }, animationSpec = tween(250)) +
            fadeIn(animationSpec = tween(250))
}

val outToLeft = { _: AnimatedContentTransitionScope<NavBackStackEntry> ->
    slideOutHorizontally(targetOffsetX = { -it / 4 }, animationSpec = tween(180)) +
            fadeOut(animationSpec = tween(180))
}

val inFromLeft = { _: AnimatedContentTransitionScope<NavBackStackEntry> ->
    slideInHorizontally(initialOffsetX = { -it / 4 }, animationSpec = tween(250)) +
            fadeIn(animationSpec = tween(250))
}

val outToRight = { _: AnimatedContentTransitionScope<NavBackStackEntry> ->
    slideOutHorizontally(targetOffsetX = { it / 4 }, animationSpec = tween(180)) +
            fadeOut(animationSpec = tween(180))
}
