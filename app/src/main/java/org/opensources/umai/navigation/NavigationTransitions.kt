package org.opensources.umai.navigation

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute

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

/**
 * Keeps the screen below in place while the one above rises over it. Lint takes
 * this for a clash with a member of `ExitTransition.Companion`, but that member is
 * internal to Compose: the one reached is the public extension that
 * [AnimatedContentTransitionScope] declares.
 */
@SuppressLint("MemberExtensionConflict")
internal fun AnimatedContentTransitionScope<NavBackStackEntry>.stayUnderneath(): ExitTransition =
    ExitTransition.KeepUntilTransitionsFinished

/** The same curve on both screens keeps them edge to edge for the whole slide. */
private fun slideSpec() = tween<IntOffset>(SCREEN_TRANSITION_MILLIS, easing = FastOutSlowInEasing)

/*
 * The recipe picker of the meal plan appears on its own, its search field sliding up from where the
 * sheet's field stood (see PlanRecipePickerScreen). Leaving, it sinks back over the week, which
 * stays in place underneath.
 */

internal fun sinkExit(): ExitTransition =
    fadeOut(tween(SCREEN_TRANSITION_MILLIS, easing = FastOutSlowInEasing)) +
        slideOutVertically(tween(SCREEN_TRANSITION_MILLIS, easing = FastOutSlowInEasing)) { it / SINK_FRACTION }

/** How far below its place the picker ends: a fraction of its height. */
private const val SINK_FRACTION = 5

/*
 * Some screens unfold from a place of the one that opens them (SharedContainer): the search field
 * of the home screen into the search screen, the "create a recipe" row of the profile into the
 * recipe form. Between those, the screens fade through one another while that place morphs,
 * instead of sliding, both ways.
 */

private val unfoldingScreens = listOf(
    HomeDestination::class to SearchDestination::class,
    ProfileDestination::class to RecipeCreateDestination::class,
)

/** Whether the screens of this transition are one unfolding from the other. */
internal fun AnimatedContentTransitionScope<NavBackStackEntry>.unfolds(): Boolean {
    val from = initialState.destination
    val to = targetState.destination
    return unfoldingScreens.any { (opener, opened) ->
        (from.hasRoute(opener) && to.hasRoute(opened)) || (from.hasRoute(opened) && to.hasRoute(opener))
    }
}

internal fun unfoldEnter(): EnterTransition = fadeIn(tween(SCREEN_TRANSITION_MILLIS, easing = FastOutSlowInEasing))

internal fun unfoldExit(): ExitTransition = fadeOut(tween(SCREEN_TRANSITION_MILLIS, easing = FastOutSlowInEasing))
