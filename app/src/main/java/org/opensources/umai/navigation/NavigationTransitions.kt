package org.opensources.umai.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry

/*
 * Horizontal push between screens: the new screen slides in from the end edge while the previous one
 * slides out towards the start edge, at the same speed. Both stay fully opaque and edge to edge, so
 * the window background behind them (it follows the system theme, not the theme chosen in the app)
 * never shows through: no flash in any theme. Going back, predictive back gesture included, plays it
 * in reverse. Directions follow the layout direction; the system animation scale applies.
 */

internal const val SCREEN_TRANSITION_MILLIS = 350

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.screenEnter(): EnterTransition =
    slideIntoContainer(SlideDirection.Start, slideSpec())

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.screenExit(): ExitTransition =
    slideOutOfContainer(SlideDirection.Start, slideSpec())

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.screenPopEnter(): EnterTransition =
    slideIntoContainer(SlideDirection.End, slideSpec())

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.screenPopExit(): ExitTransition =
    slideOutOfContainer(SlideDirection.End, slideSpec())

/** The same curve on both screens keeps them edge to edge for the whole slide. */
private fun slideSpec() = tween<IntOffset>(SCREEN_TRANSITION_MILLIS, easing = FastOutSlowInEasing)
