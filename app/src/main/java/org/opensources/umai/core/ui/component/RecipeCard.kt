package org.opensources.umai.core.ui.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.opensources.umai.R
import org.opensources.umai.core.format.DurationText
import org.opensources.umai.core.format.currentLocale
import org.opensources.umai.core.model.RecipeSummary
import org.opensources.umai.core.ui.motion.animatePressScale
import org.opensources.umai.core.ui.motion.scaledBy

/**
 * Card used by the Home and Search grids. [onClick] receives where its
 * picture shows in the window, for a screen that animates it from there.
 * Like every recipe card, it sinks a little under the finger.
 */
@Composable
fun RecipeCard(
    recipe: RecipeSummary,
    imageUrl: String?,
    onClick: (imageBounds: Rect?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val imagePlace = remember { ImagePlace() }
    val interactions = remember { MutableInteractionSource() }
    val pressScale = animatePressScale(interactions)
    Card(
        onClick = { onClick(imagePlace.visibleBounds()) },
        modifier = modifier.fillMaxWidth().scaledBy(pressScale),
        interactionSource = interactions,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Box {
            RemoteImage(
                url = imageUrl,
                contentDescription = imageContentDescription(recipe),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(MaterialTheme.shapes.medium)
                    .placeOf(imagePlace),
            )
            recipe.rating?.let { rating ->
                RatingBadge(
                    rating = rating,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                )
            }
        }
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = recipe.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            RecipeMetaRow(recipe)
        }
    }
}

/** Compact row of the list layout; [onClick] as on [RecipeCard]. */
@Composable
fun RecipeRow(
    recipe: RecipeSummary,
    imageUrl: String?,
    onClick: (imageBounds: Rect?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val imagePlace = remember { ImagePlace() }
    val interactions = remember { MutableInteractionSource() }
    val pressScale = animatePressScale(interactions)
    Card(
        onClick = { onClick(imagePlace.visibleBounds()) },
        modifier = modifier.fillMaxWidth().scaledBy(pressScale),
        interactionSource = interactions,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RemoteImage(
                url = imageUrl,
                contentDescription = imageContentDescription(recipe),
                modifier = Modifier
                    .size(72.dp)
                    .clip(MaterialTheme.shapes.small)
                    .placeOf(imagePlace),
                placeholderIconSize = 24.dp,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = recipe.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                RecipeMetaRow(recipe)
            }
        }
    }
}

@Composable
private fun RecipeMetaRow(recipe: RecipeSummary, modifier: Modifier = Modifier) {
    val time = DurationText.format(
        raw = recipe.totalTime ?: recipe.performTime ?: recipe.prepTime,
        hourUnit = stringResource(R.string.unit_hour_short),
        minuteUnit = stringResource(R.string.unit_minute_short),
    )
    val servings = recipe.servings.takeIf { it >= 1.0 }?.toInt()

    if (time == null && servings == null) return

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (time != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = time,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        if (servings != null) {
            Text(
                text = androidx.compose.ui.res.pluralStringResource(
                    R.plurals.plural_servings,
                    servings,
                    servings,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RatingBadge(rating: Double, modifier: Modifier = Modifier) {
    val locale = currentLocale()
    val text = if (rating % 1.0 == 0.0) {
        rating.toInt().toString()
    } else {
        String.format(locale, "%.1f", rating)
    }
    val description = stringResource(R.string.cd_rating, text)
    Surface(
        modifier = modifier.semantics { contentDescription = description },
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(text = text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
internal fun imageContentDescription(recipe: RecipeSummary): String =
    if (recipe.hasImage) {
        stringResource(R.string.cd_recipe_image, recipe.name)
    } else {
        stringResource(R.string.cd_recipe_no_image)
    }
