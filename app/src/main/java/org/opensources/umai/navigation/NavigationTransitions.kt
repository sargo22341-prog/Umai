package org.opensources.umai.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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

/*
 * The recipe picker of the meal plan rises from the sheet it was opened from, as if its search field
 * grew into a whole screen, and sinks back when left. The week stays in place underneath.
 */

internal fun riseEnter(): EnterTransition =
    fadeIn(tween(SCREEN_TRANSITION_MILLIS, easing = FastOutSlowInEasing)) +
        slideInVertically(tween(SCREEN_TRANSITION_MILLIS, easing = FastOutSlowInEasing)) { it / RISE_FRACTION }

internal fun sinkExit(): ExitTransition =
    fadeOut(tween(SCREEN_TRANSITION_MILLIS, easing = FastOutSlowInEasing)) +
        slideOutVertically(tween(SCREEN_TRANSITION_MILLIS, easing = FastOutSlowInEasing)) { it / RISE_FRACTION }

/** How far below its place the picker starts: a fraction of its height. */
private const val RISE_FRACTION = 5
