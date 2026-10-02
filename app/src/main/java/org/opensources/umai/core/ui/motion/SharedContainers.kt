package org.opensources.umai.core.ui.motion

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape

/**
 * The places that grow into a whole screen when they are tapped: the same key
 * marks the place on the screen that opens and the screen it opens, and
 * [SharedContainer] morphs one into the other, back and forth.
 */
enum class SharedContainerKey {
    /** The search field of the home screen, which unfolds into the search screen. */
    SEARCH_FIELD,

    /** The "create a recipe" row of the profile, which unfolds into the recipe form. */
    RECIPE_CREATION,
}

/** The scope the screens morph in: provided around the navigation, absent from previews and tests. */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/** The entrance and exit of the screen being composed: provided by each destination that morphs. */
val LocalScreenVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Gives [content], a screen of the navigation, the [scope] of its entrance and exit. */
@Composable
fun ScreenVisibility(scope: AnimatedVisibilityScope, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalScreenVisibilityScope provides scope, content = content)
}

/**
 * [content] as one end of a container transform: while the screen around it
 * comes or goes, its bounds travel to or from the place marked with the same
 * [key] on the other screen, its content fading through, clipped to [shape].
 * Outside a navigation transition, or where nothing provides the scopes, it is
 * a plain box.
 */
@Composable
fun SharedContainer(
    key: SharedContainerKey,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    content: @Composable () -> Unit,
) {
    val transitionScope = LocalSharedTransitionScope.current
    val visibilityScope = LocalScreenVisibilityScope.current
    if (transitionScope == null || visibilityScope == null) {
        Box(modifier) { content() }
        return
    }
    with(transitionScope) {
        Box(
            modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key),
                animatedVisibilityScope = visibilityScope,
                enter = fadeIn(tween(CONTAINER_FADE_MILLIS, delayMillis = CONTAINER_FADE_MILLIS, easing = FastOutSlowInEasing)),
                exit = fadeOut(tween(CONTAINER_FADE_MILLIS, easing = FastOutSlowInEasing)),
                clipInOverlayDuringTransition = OverlayClip(shape),
            ),
        ) {
            content()
        }
    }
}

/** The old content fades out in the first half of the morph, the new one in during the second. */
private const val CONTAINER_FADE_MILLIS = 150
