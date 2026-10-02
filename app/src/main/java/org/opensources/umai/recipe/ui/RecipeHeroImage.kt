package org.opensources.umai.recipe.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.opensources.umai.R
import org.opensources.umai.core.model.Recipe
import org.opensources.umai.core.ui.component.ImagePlace
import org.opensources.umai.core.ui.component.RemoteImage
import org.opensources.umai.core.ui.component.placeOf

/**
 * The picture at the top of the recipe page. A tap opens the cooking mode, and
 * badges in its corner tell what that mode holds besides the text: a video,
 * pictures of the steps.
 */
@Composable
internal fun RecipeHeroImage(
    recipe: Recipe,
    state: RecipeDetailUiState,
    imageUrl: String?,
    imagePlace: ImagePlace,
    onStartCooking: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 10f)
            .clickable(
                enabled = state.canCook,
                onClickLabel = stringResource(R.string.recipe_cook_mode),
                onClick = onStartCooking,
            ),
    ) {
        RemoteImage(
            url = imageUrl,
            contentDescription = if (recipe.summary.hasImage) {
                stringResource(R.string.cd_recipe_image, recipe.name)
            } else {
                stringResource(R.string.cd_recipe_no_image)
            },
            modifier = Modifier
                .matchParentSize()
                .placeOf(imagePlace),
            placeholderIconSize = 48.dp,
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.hasStepPictures) {
                MediaBadge(Icons.Outlined.PhotoLibrary, stringResource(R.string.cd_recipe_has_step_pictures))
            }
            if (state.hasVideo) {
                MediaBadge(Icons.Outlined.PlayArrow, stringResource(R.string.cd_recipe_has_video))
            }
        }
    }
}

@Composable
private fun MediaBadge(icon: ImageVector, description: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            modifier = Modifier
                .padding(6.dp)
                .size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}
