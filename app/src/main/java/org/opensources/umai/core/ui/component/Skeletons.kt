package org.opensources.umai.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.opensources.umai.R
import org.opensources.umai.core.ui.motion.rememberShimmer
import org.opensources.umai.core.ui.motion.shimmer

/*
 * What shows while a screen loads: the outline of what is coming, shining,
 * rather than a spinner, so the screen does not jump when the content lands.
 */

/** A list loading: rows of a small picture and two lines of text. */
@Composable
fun LoadingView(modifier: Modifier = Modifier) = SkeletonScreen(modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
    repeat(LIST_ROWS) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).bone(MaterialTheme.shapes.small))
            Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextBone(widthFraction = 0.7f, height = 16.dp)
                TextBone(widthFraction = 0.4f, height = 12.dp)
            }
        }
    }
}

/** A grid of recipe cards loading, as on the home and search screens. */
@Composable
fun RecipeGridLoadingView(modifier: Modifier = Modifier) = SkeletonScreen(modifier.padding(16.dp)) {
    repeat(GRID_ROWS) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(GRID_COLUMNS) { CardBone(Modifier.weight(1f)) }
        }
    }
}

/** A recipe page loading: its picture, its title and the first ingredients. */
@Composable
fun RecipePageLoadingView(modifier: Modifier = Modifier) = SkeletonScreen(modifier) {
    Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).bone(MaterialTheme.shapes.large))
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextBone(widthFraction = 0.8f, height = 26.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(STARS) { Box(Modifier.size(24.dp).bone(CircleShape)) }
        }
        repeat(INGREDIENT_LINES) { line -> TextBone(widthFraction = if (line % 2 == 0) 0.6f else 0.45f, height = 14.dp) }
    }
}

@Composable
private fun SkeletonScope.CardBone(modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).bone(MaterialTheme.shapes.medium))
        TextBone(widthFraction = 0.85f, height = 14.dp)
        Box(Modifier.width(56.dp).height(12.dp).bone(MaterialTheme.shapes.extraSmall))
    }
}

@Composable
private fun SkeletonScope.TextBone(widthFraction: Float, height: Dp) {
    Box(Modifier.fillMaxWidth(widthFraction).height(height).bone(MaterialTheme.shapes.extraSmall))
}

/** The placeholders of one screen, which shine together. */
@Stable
private class SkeletonScope(private val sweep: State<Float>, private val base: Color, private val highlight: Color) {
    fun Modifier.bone(shape: Shape): Modifier = shimmer(sweep, shape, base, highlight)
}

@Composable
private fun SkeletonScreen(modifier: Modifier, content: @Composable SkeletonScope.() -> Unit) {
    val sweep = rememberShimmer()
    val colors = MaterialTheme.colorScheme
    val scope = remember(sweep, colors) {
        SkeletonScope(sweep, base = colors.surfaceContainerHigh, highlight = colors.onSurface.copy(alpha = HIGHLIGHT_ALPHA))
    }
    val loading = stringResource(R.string.loading)
    Column(
        modifier = Modifier
            .fillMaxSize()
            // A screen loading is not scrolled: what does not fit is cut at its bottom edge.
            .clipToBounds()
            .semantics(mergeDescendants = true) {
                contentDescription = loading
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
            }
            .then(modifier),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        scope.content()
    }
}

private const val LIST_ROWS = 8
private const val GRID_ROWS = 3
private const val GRID_COLUMNS = 2
private const val STARS = 5
private const val INGREDIENT_LINES = 6
private const val HIGHLIGHT_ALPHA = 0.07f
