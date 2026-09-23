package org.opensources.umai.recipe.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import org.opensources.umai.R
import org.opensources.umai.core.model.MAX_RATING_STARS

/**
 * The row between the title and the description of a recipe: the favourite
 * toggle on the start side, the five rating stars on the end side.
 *
 * The stars show the reader's own rating in the accent colour, or the average
 * of the household in a quieter tone until they rate the recipe themselves.
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

        val ratingState = when {
            ratingIsOwn -> pluralStringResource(R.plurals.recipe_rating_own, rating, rating)
            rating > 0 -> pluralStringResource(R.plurals.recipe_rating_average, rating, rating)
            else -> stringResource(R.string.recipe_rating_none)
        }
        Row(
            modifier = Modifier.semantics { stateDescription = ratingState },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            (1..MAX_RATING_STARS).forEach { star ->
                RatingStar(
                    filled = star <= rating,
                    own = ratingIsOwn,
                    contentDescription = pluralStringResource(R.plurals.recipe_rate, star, star),
                    onClick = { onRate(star) },
                )
            }
        }
    }
}

@Composable
private fun FavoriteButton(isFavorite: Boolean, onClick: () -> Unit) {
    // A short bounce when the heart fills, so the tap is felt as well as seen.
    val scale by animateFloatAsState(
        targetValue = if (isFavorite) 1.15f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "favoriteScale",
    )
    IconButton(onClick = onClick) {
        Icon(
            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = stringResource(
                if (isFavorite) R.string.recipe_favorite_remove else R.string.recipe_favorite_add,
            ),
            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp).scale(scale),
        )
    }
}

@Composable
private fun RatingStar(
    filled: Boolean,
    own: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val tint by animateColorAsState(
        targetValue = when {
            !filled -> MaterialTheme.colorScheme.onSurfaceVariant
            own -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outline
        },
        label = "starTint",
    )
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(
            imageVector = if (filled) Icons.Rounded.Star else Icons.Rounded.StarOutline,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(26.dp),
        )
    }
}

/** Space between the edge of an icon button and its icon. */
private val EDGE_INSET = 10.dp
