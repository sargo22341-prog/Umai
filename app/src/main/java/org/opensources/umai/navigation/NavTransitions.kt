package org.opensources.umai.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry

/**
 * The one horizontal slide-and-fade used by every destination.
 *
 * Going forward the new screen comes in from the right, going back it leaves to
 * the right: the standard Android motion. Defining all four directions in a
 * single place is what makes the back gesture and the back button look alike —
 * the predictive-back gesture plays [popExit] and [popEnter] frame by frame, so
 * a destination overriding only one of them would animate differently
 * depending on how the user went back.
 */
object NavTransitions {

    private const val DURATION_MS = 300
    private const val FADE_MS = 200

    /** A fifth of the width, the distance Material motion uses on this axis. */
    private fun offset(width: Int): Int = width / 5

    private val slideSpec = tween<IntOffset>(DURATION_MS, easing = FastOutSlowInEasing)
    private val fadeInSpec = tween<Float>(FADE_MS, delayMillis = 60, easing = FastOutSlowInEasing)
    private val fadeOutSpec = tween<Float>(FADE_MS, easing = FastOutSlowInEasing)

    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInHorizontally(slideSpec) { offset(it) } + fadeIn(fadeInSpec)
    }

    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutHorizontally(slideSpec) { -offset(it) } + fadeOut(fadeOutSpec)
    }

    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInHorizontally(slideSpec) { -offset(it) } + fadeIn(fadeInSpec)
    }

    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutHorizontally(slideSpec) { offset(it) } + fadeOut(fadeOutSpec)
    }
}
