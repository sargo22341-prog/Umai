package org.opensources.umai.home.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import org.opensources.umai.R
import org.opensources.umai.core.format.DurationText
import org.opensources.umai.core.format.currentLocale
import org.opensources.umai.core.model.RecipeSummary
import org.opensources.umai.core.ui.component.RemoteImage
import kotlin.math.absoluteValue

/**
 * A few random recipes shown large, one after the other, to suggest something
 * the reader would not have searched for. The carousel moves on by itself
 * until the reader swipes it: from then on, it stays where they left it.
 *
 * [edgeBleed] is the padding of the list around it, which the carousel spans
 * so the neighbouring cards peek in from the screen edges.
 */
@Composable
fun DiscoveryCarousel(
    recipes: List<RecipeSummary>,
    imageUrl: (RecipeSummary) -> String?,
    onRecipeClick: (RecipeSummary) -> Unit,
    modifier: Modifier = Modifier,
    edgeBleed: Dp = 0.dp,
) {
    // A new draw fades in over the previous one instead of popping.
    AnimatedContent(
        targetState = recipes,
        modifier = modifier.bleedHorizontally(edgeBleed),
        transitionSpec = {
            (fadeIn(tween(DRAW_TRANSITION_MS)) + scaleIn(tween(DRAW_TRANSITION_MS), initialScale = 0.94f))
                .togetherWith(fadeOut(tween(DRAW_TRANSITION_MS / 2)))
        },
        label = "discovery-draw",
    ) { draw ->
        val pagerState = rememberPagerState { draw.size }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = edgeBleed + PEEK),
                pageSpacing = 8.dp,
                key = { draw[it].id },
            ) { page ->
                val recipe = draw[page]
                DiscoveryCard(
                    recipe = recipe,
                    imageUrl = imageUrl(recipe),
                    position = stringResource(R.string.cd_discovery_position, page + 1, draw.size),
                    offset = { pagerState.offsetOf(page) },
                    onClick = { onRecipeClick(recipe) },
                )
            }
            if (draw.size > 1) {
                PageIndicator(
                    count = draw.size,
                    current = pagerState.currentPage,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
        AutoAdvance(pagerState)
    }
}

/**
 * Moves to the next card after a pause, back to the first after the last.
 * The timer restarts after every move, and stops for good once the reader
 * drags the carousel: content that moves on its own must be stoppable.
 */
@Composable
private fun AutoAdvance(pagerState: PagerState) {
    if (pagerState.pageCount < 2) return
    var stopped by remember { mutableStateOf(false) }
    LaunchedEffect(pagerState) {
        pagerState.interactionSource.interactions.filterIsInstance<DragInteraction.Start>().first()
        stopped = true
    }

    LaunchedEffect(pagerState.settledPage, stopped) {
        if (stopped) return@LaunchedEffect
        delay(AUTO_ADVANCE_MS)
        pagerState.animateScrollToPage(
            page = (pagerState.currentPage + 1) % pagerState.pageCount,
            animationSpec = tween(PAGE_TRANSITION_MS, easing = FastOutSlowInEasing),
        )
    }
}

/** How far [page] is from the centre, in pages: 0 when it is the one in view. */
private fun PagerState.offsetOf(page: Int): Float =
    ((currentPage - page) + currentPageOffsetFraction).coerceIn(-1f, 1f)

/**
 * [offset] is read in the drawing phase only, so following the finger
 * redraws the cards without recomposing them.
 */
@Composable
private fun DiscoveryCard(
    recipe: RecipeSummary,
    imageUrl: String?,
    position: String,
    offset: () -> Float,
    onClick: () -> Unit,
) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
        Card(
            onClick = onClick,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier
                .heightIn(max = MAX_CARD_HEIGHT)
                .aspectRatio(CARD_RATIO, matchHeightConstraintsFirst = true)
                .graphicsLayer {
                    // The card in view is full size; its neighbours shrink and dim.
                    val focus = 1f - offset().absoluteValue
                    val scale = lerp(SIDE_CARD_SCALE, 1f, focus)
                    scaleX = scale
                    scaleY = scale
                    alpha = lerp(SIDE_CARD_ALPHA, 1f, focus)
                }
                .semantics { stateDescription = position },
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                RemoteImage(
                    url = imageUrl,
                    contentDescription = if (recipe.hasImage) {
                        stringResource(R.string.cd_recipe_image, recipe.name)
                    } else {
                        stringResource(R.string.cd_recipe_no_image)
                    },
                    placeholderIconSize = 56.dp,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // Parallax: the picture slides slower than its card.
                            // It is enlarged just enough never to uncover an edge.
                            scaleX = PARALLAX_SCALE
                            scaleY = PARALLAX_SCALE
                            translationX = offset() * size.width * PARALLAX_SHIFT
                        },
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.45f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.85f),
                            ),
                        ),
                )
                CardCaption(
                    recipe = recipe,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(20.dp)
                        .graphicsLayer {
                            // The title rises into place as its card arrives.
                            val away = offset().absoluteValue
                            translationY = away * CAPTION_RISE.toPx()
                            alpha = 1f - away
                        },
                )
            }
        }
    }
}

/** White on the dark end of the gradient, whatever the theme. */
@Composable
private fun CardCaption(recipe: RecipeSummary, modifier: Modifier = Modifier) {
    val time = DurationText.format(
        raw = recipe.totalTime ?: recipe.performTime ?: recipe.prepTime,
        hourUnit = stringResource(R.string.unit_hour_short),
        minuteUnit = stringResource(R.string.unit_minute_short),
    )
    val rating = recipe.rating?.let { value ->
        if (value % 1.0 == 0.0) value.toInt().toString() else String.format(currentLocale(), "%.1f", value)
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = recipe.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (time != null || rating != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (time != null) {
                    CaptionIcon(Icons.Outlined.Schedule)
                    CaptionText(time)
                }
                if (rating != null) {
                    CaptionIcon(Icons.Filled.Star)
                    val description = stringResource(R.string.cd_rating, rating)
                    CaptionText(rating, Modifier.clearAndSetSemantics { contentDescription = description })
                }
            }
        }
    }
}

@Composable
private fun CaptionIcon(icon: ImageVector) {
    Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
}

@Composable
private fun CaptionText(text: String, modifier: Modifier = Modifier) {
    Text(text = text, style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1, modifier = modifier)
}

/** The current dot stretches into a bar; the others stay small. */
@Composable
private fun PageIndicator(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val selected = index == current
            val width by animateDpAsState(if (selected) 24.dp else 8.dp, tween(PAGE_TRANSITION_MS), label = "dot-width")
            val color by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                tween(PAGE_TRANSITION_MS),
                label = "dot-color",
            )
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

/** Widens the element by [bleed] on each side, over the padding of its parent. */
private fun Modifier.bleedHorizontally(bleed: Dp): Modifier = layout { measurable, constraints ->
    val extra = if (constraints.hasBoundedWidth) (bleed * 2).roundToPx() else 0
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.minWidth + extra,
            maxWidth = constraints.maxWidth + extra,
        ),
    )
    layout(placeable.width - extra, placeable.height) {
        placeable.place(-extra / 2, 0)
    }
}

private val PEEK = 28.dp
private val MAX_CARD_HEIGHT = 440.dp
private val CAPTION_RISE = 32.dp
private const val CARD_RATIO = 4f / 5f
private const val SIDE_CARD_SCALE = 0.86f
private const val SIDE_CARD_ALPHA = 0.55f
private const val PARALLAX_SCALE = 1.2f

/** At most (1.2 - 1) / 2 of the width, so the enlarged picture always covers its card. */
private const val PARALLAX_SHIFT = 0.09f
private const val AUTO_ADVANCE_MS = 5_000L
private const val PAGE_TRANSITION_MS = 700
private const val DRAW_TRANSITION_MS = 500
