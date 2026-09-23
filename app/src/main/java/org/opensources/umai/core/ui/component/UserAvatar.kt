package org.opensources.umai.core.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

/**
 * Profile picture of a Mealie user.
 *
 * Mealie answers with a 404 for an account that never uploaded a picture, so
 * the initials are drawn both while loading and when the request fails: the
 * avatar is never an empty hole, and it always carries a description.
 */
@Composable
fun UserAvatar(
    url: String?,
    initials: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    textStyle: TextUnit = 16.sp,
) {
    val described = if (contentDescription != null) {
        modifier.semantics {
            this.contentDescription = contentDescription
            role = Role.Image
        }
    } else {
        modifier
    }

    Surface(
        modifier = described.size(size),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        if (url == null) {
            Initials(initials, textStyle)
            return@Surface
        }
        val context = LocalContext.current
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context).data(url).crossfade(true).build(),
            // Already announced by the surface; describing it twice would make
            // the avatar appear twice to accessibility services.
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            loading = { Initials(initials, textStyle) },
            error = { Initials(initials, textStyle) },
        )
    }
}

@Composable
private fun Initials(initials: String, textStyle: TextUnit) {
    Box(contentAlignment = Alignment.Center) {
        Text(
            text = initials,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = textStyle),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
