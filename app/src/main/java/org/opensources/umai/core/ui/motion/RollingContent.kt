package org.opensources.umai.core.ui.motion

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * [content] rolling like a counter when [value] changes: when it grows, the
 * old content leaves upwards and the new one comes from below; when it
 * shrinks, the other way round. Two values with the same [contentKey] show the
 * same thing, so nothing rolls between them.
 */
@Composable
fun <T : Comparable<T>> RollingContent(
    value: T,
    modifier: Modifier = Modifier,
    contentKey: (T) -> Any? = { it },
    content: @Composable (T) -> Unit,
) {
    AnimatedContent(
        targetState = value,
        modifier = modifier,
        transitionSpec = { roll(rising = targetState > initialState) },
        contentKey = contentKey,
        label = "rolling",
    ) { shown ->
        content(shown)
    }
}

private fun <T> AnimatedContentTransitionScope<T>.roll(rising: Boolean): ContentTransform {
    val direction = if (rising) 1 else -1
    val enter = slideInVertically(tween(ROLL_MILLIS, easing = FastOutSlowInEasing)) { height -> direction * height / 2 } +
        fadeIn(tween(ROLL_MILLIS))
    val exit = slideOutVertically(tween(ROLL_MILLIS, easing = FastOutSlowInEasing)) { height -> -direction * height / 2 } +
        fadeOut(tween(ROLL_MILLIS / 2))
    // Not clipped: the text keeps its line, and a wider value does not cut the narrower one.
    return (enter togetherWith exit).using(SizeTransform(clip = false))
}

private const val ROLL_MILLIS = 220
