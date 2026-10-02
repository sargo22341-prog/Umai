package org.opensources.umai.core.ui.motion

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.translate

/**
 * Where the sheen of the placeholders stands, from `0` to `1`, sweeping on
 * and on. One per screen of placeholders, so they all shine together.
 */
@Composable
fun rememberShimmer(): State<Float> = rememberInfiniteTransition(label = "shimmer").animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(SWEEP_MILLIS, easing = LinearEasing)),
    label = "shimmerSweep",
)

/**
 * A placeholder shape filled with [base], a band of [highlight] sweeping
 * across it as [sweep] goes. The band is built once per size and only moved
 * frame after frame, so the sweep allocates nothing.
 */
fun Modifier.shimmer(sweep: State<Float>, shape: Shape, base: Color, highlight: Color): Modifier =
    clip(shape).drawWithCache {
        val band = size.width * BAND_FRACTION
        val sheen = Brush.linearGradient(
            colors = listOf(Color.Transparent, highlight, Color.Transparent),
            start = Offset.Zero,
            end = Offset(band, size.height),
        )
        onDrawBehind {
            drawRect(base)
            // From fully before the start edge to fully past the end edge.
            val left = -band + sweep.value * (size.width + band)
            translate(left = left) { drawRect(sheen, size = size.copy(width = band)) }
        }
    }

private const val SWEEP_MILLIS = 1_300
private const val BAND_FRACTION = 0.6f
