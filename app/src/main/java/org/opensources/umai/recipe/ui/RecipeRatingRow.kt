package org.opensources.umai.recipe.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.opensources.umai.R
import org.opensources.umai.core.model.MAX_RATING_STARS
import org.opensources.umai.core.ui.motion.scaledBy
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The row between the title and the description of a recipe: the favourite
 * toggle on the start side, the five rating stars on the end side.
 *
 * The stars show the reader's own rating in the accent colour, or the average
 * of the household in a quieter tone until they rate the recipe themselves.
 * Tapping the last star of their own rating removes it.
 */
@Composable
internal fun RecipeRatingRow(
    isFavorite: Boolean,
    rating: Int,
    ratingIsOwn: Boolean,
    onToggleFavorite: () -> Unit,
    onRate: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        // The buttons' touch areas reach past their icons: pulled outwards so
        // the heart and the last star line up with the text around them.
        modifier = modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val inset = EDGE_INSET.roundToPx()
                val placeable = measurable.measure(
                    constraints.copy(
                        minWidth = constraints.minWidth + 2 * inset,
                        maxWidth = constraints.maxWidth + 2 * inset,
                    ),
                )
                layout(constraints.maxWidth, placeable.height) { placeable.place(-inset, 0) }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        FavoriteButton(isFavorite = isFavorite, onClick = onToggleFavorite)
        RatingStars(rating = rating, ratingIsOwn = ratingIsOwn, onRate = onRate)
    }
}

@Composable
private fun RatingStars(rating: Int, ratingIsOwn: Boolean, onRate: (Int) -> Unit) {
    val ratingState = when {
        ratingIsOwn -> pluralStringResource(R.plurals.recipe_rating_own, rating, rating)
        rating > 0 -> pluralStringResource(R.plurals.recipe_rating_average, rating, rating)
        else -> stringResource(R.string.recipe_rating_none)
    }
    val haptics = LocalHapticFeedback.current
    // The rating shown before this one: the stars that change fill, or empty, one after the other from it.
    val previous = remember { mutableIntStateOf(rating) }
    val from = previous.intValue
    SideEffect { previous.intValue = rating }
    Row(
        modifier = Modifier.semantics { stateDescription = ratingState },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        (1..MAX_RATING_STARS).forEach { star ->
            RatingStar(
                filled = star <= rating,
                own = ratingIsOwn,
                turn = if (star > rating) from - star else star - 1 - from,
                contentDescription = if (ratingIsOwn && star == rating) {
                    stringResource(R.string.recipe_rating_clear)
                } else {
                    pluralStringResource(R.plurals.recipe_rate, star, star)
                },
                onClick = {
                    val clears = ratingIsOwn && star == rating
                    haptics.performHapticFeedback(if (clears) HapticFeedbackType.ToggleOff else HapticFeedbackType.Confirm)
                    onRate(star)
                },
            )
        }
    }
}

/**
 * The heart pops when it fills and lets out a ring of dots, and shrinks back a
 * little when it empties; the phone ticks either way.
 */
@Composable
private fun FavoriteButton(isFavorite: Boolean, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val pop = remember { Animatable(1f) }
    // From 0, the dots leaving the heart, to 1, gone: nothing shows until the heart fills.
    val burst = remember { Animatable(1f) }
    val shown = remember { mutableStateOf(isFavorite) }
    LaunchedEffect(isFavorite) {
        if (shown.value == isFavorite) return@LaunchedEffect
        shown.value = isFavorite
        if (isFavorite) launch { burst.snapTo(0f); burst.animateTo(1f, tween(BURST_MILLIS, easing = FastOutSlowInEasing)) }
        pop.animateTo(if (isFavorite) POP_SCALE else SHRINK_SCALE, tween(POP_MILLIS, easing = FastOutSlowInEasing))
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
    }
    val burstColor = MaterialTheme.colorScheme.primary
    IconButton(
        onClick = {
            haptics.performHapticFeedback(if (isFavorite) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
            onClick()
        },
        // Read while drawing only: the dots move without the button being composed again.
        modifier = Modifier.drawBehind { drawBurst(burst.value, burstColor) },
    ) {
        Icon(
            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = stringResource(
                if (isFavorite) R.string.recipe_favorite_remove else R.string.recipe_favorite_add,
            ),
            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp).scaledBy(pop.asState()),
        )
    }
}

/** The ring of dots around the heart, [progress] of the way out from it. */
private fun DrawScope.drawBurst(progress: Float, color: Color) {
    if (progress >= 1f) return
    val distance = BURST_START.toPx() + (BURST_END.toPx() - BURST_START.toPx()) * progress
    val radius = BURST_DOT.toPx() * (1f - progress)
    repeat(BURST_DOTS) { dot ->
        val angle = 2.0 * PI * dot / BURST_DOTS
        val offset = Offset((cos(angle) * distance).toFloat(), (sin(angle) * distance).toFloat())
        drawCircle(color = color, radius = radius, center = center + offset, alpha = 1f - progress)
    }
}

/**
 * One star of the rating. When it changes, the full star grows out of the
 * outline, or shrinks back into it, after the stars before it in the
 * cascade: [turn] is its place there.
 */
@Composable
private fun RatingStar(
    filled: Boolean,
    own: Boolean,
    turn: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val fill = remember { Animatable(if (filled) 1f else 0f) }
    LaunchedEffect(filled) {
        delay(turn.coerceAtLeast(0) * STAR_STAGGER_MILLIS)
        fill.animateTo(
            targetValue = if (filled) 1f else 0f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        )
    }
    val tint by animateColorAsState(
        targetValue = if (own) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        label = "starTint",
    )
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Rounded.StarOutline,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp),
            )
            Icon(
                imageVector = Icons.Rounded.Star,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(26.dp).scaledBy(fill.asState()),
            )
        }
    }
}

/** Space between the edge of an icon button and its icon. */
private val EDGE_INSET = 10.dp

private const val POP_MILLIS = 110
private const val POP_SCALE = 1.3f
private const val SHRINK_SCALE = 0.85f
private const val BURST_MILLIS = 450
private const val BURST_DOTS = 8
private val BURST_START = 12.dp
private val BURST_END = 20.dp
private val BURST_DOT = 2.5.dp
private const val STAR_STAGGER_MILLIS = 60L
