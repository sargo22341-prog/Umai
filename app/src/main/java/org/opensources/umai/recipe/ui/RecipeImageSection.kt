package org.opensources.umai.recipe.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.opensources.umai.R
import org.opensources.umai.core.ui.component.CropFrame
import org.opensources.umai.core.ui.component.ImagePicker
import org.opensources.umai.core.ui.component.RemoteImage
import org.opensources.umai.core.ui.component.rememberImagePickerState

/**
 * The picture of the recipe: a preview in the proportions the recipe page
 * uses, and the buttons to pick one — from the gallery, the camera or a file —
 * frame it, or remove it.
 *
 * [imageUrl] is either the picture on Mealie or the one framed on the device.
 */
@Composable
internal fun ImageSection(
    imageUrl: String?,
    processing: Boolean,
    canRemove: Boolean,
    failed: Boolean,
    actions: RecipeFormActions,
) {
    val picker = rememberImagePickerState()
    var cameraUnavailable by rememberSaveable { mutableStateOf(false) }

    ImagePicker(
        state = picker,
        frame = CropFrame.RECIPE,
        onImageReady = { source, region ->
            cameraUnavailable = false
            actions.onImagePicked(source, region)
        },
        onCameraUnavailable = { cameraUnavailable = true },
    )

    Text(
        text = stringResource(R.string.create_image_helper),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Surface(
        onClick = picker::open,
        enabled = !processing,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(CropFrame.RECIPE.aspectRatio),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Crossfade(targetState = imageUrl, label = "recipeImage") { url ->
                RemoteImage(
                    url = url,
                    contentDescription = stringResource(
                        if (url == null) R.string.cd_recipe_no_image else R.string.create_image_preview,
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(MaterialTheme.shapes.large),
                    placeholderIconSize = 48.dp,
                )
            }
            if (processing) {
                Surface(color = Color.Black.copy(alpha = 0.4f), modifier = Modifier.fillMaxSize()) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            }
        }
    }

    AnimatedVisibility(visible = failed || cameraUnavailable) {
        Text(
            text = stringResource(
                if (cameraUnavailable) R.string.image_camera_unavailable else R.string.create_image_failed,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(onClick = picker::open, enabled = !processing, modifier = Modifier.weight(1f)) {
            Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null)
            Text(
                text = stringResource(
                    if (imageUrl == null) R.string.create_image_pick else R.string.create_image_change,
                ),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        if (canRemove) {
            OutlinedButton(onClick = actions.onRemoveImage, enabled = !processing) {
                Icon(Icons.Outlined.Delete, contentDescription = null)
                Text(
                    text = stringResource(R.string.create_image_remove),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}
