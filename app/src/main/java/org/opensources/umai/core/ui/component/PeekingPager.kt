package org.opensources.umai.core.ui.component

import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.util.lerp
import kotlin.math.absoluteValue

/** How far [page] is from the centre, in pages: 0 when it is the one in view, negative on its right. */
fun PagerState.offsetOf(page: Int): Float =
    ((currentPage - page) + currentPageOffsetFraction).coerceIn(-1f, 1f)

/**
 * The page in view is full size; its neighbours shrink and dim. A shrunk
 * neighbour slides towards the page in view by what it lost on that side, so
 * the gap between the two stays the spacing of the pager whatever the scale.
 *
 * [offset] is read in the drawing phase only, so following the finger redraws
 * the pages without recomposing them.
 */
fun Modifier.peekingPage(offset: () -> Float, sideScale: Float, sideAlpha: Float): Modifier = graphicsLayer {
    val away = offset()
    val focus = 1f - away.absoluteValue
    val scale = lerp(sideScale, 1f, focus)
    scaleX = scale
    scaleY = scale
    alpha = lerp(sideAlpha, 1f, focus)
    translationX = away * size.width * (1f - sideScale) / 2f
}
