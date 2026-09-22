package org.opensources.umai.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

/**
 * Recipe and step images. Recipes without a picture, and pictures that fail to
 * load, both fall back to a neutral placeholder instead of an empty hole.
 *
 * The description is attached to the outer node rather than to the loaded
 * bitmap, so a screen reader announces the same thing whether the picture is
 * loading, loaded, broken, or simply absent.
 */
@Composable
fun RemoteImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholderIconSize: Dp = 32.dp,
) {
    val described = if (contentDescription != null) {
        modifier.semantics {
            this.contentDescription = contentDescription
            role = Role.Image
        }
    } else {
        modifier
    }

    if (url == null) {
        ImagePlaceholder(Icons.Outlined.Restaurant, described, placeholderIconSize)
        return
    }

    val context = LocalContext.current
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context).data(url).crossfade(true).build(),
        // Already announced by the outer node; a second description would make
        // the picture appear twice to accessibility services.
        contentDescription = null,
        modifier = described,
        contentScale = contentScale,
        loading = {
            ImagePlaceholder(Icons.Outlined.Restaurant, Modifier.fillMaxSize(), placeholderIconSize)
        },
        error = {
            ImagePlaceholder(Icons.Outlined.BrokenImage, Modifier.fillMaxSize(), placeholderIconSize)
        },
    )
}

@Composable
private fun ImagePlaceholder(
    icon: ImageVector,
    modifier: Modifier,
    iconSize: Dp,
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
