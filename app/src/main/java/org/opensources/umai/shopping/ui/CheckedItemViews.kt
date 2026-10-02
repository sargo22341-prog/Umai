package org.opensources.umai.shopping.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.min

/*
 * How an item of a shopping list reacts when it is ticked: its box bounces,
 * a line is drawn through its name from where the reading starts, the phone
 * ticks under the finger, and the list moves it to the basket.
 */

/**
 * [text] crossed out by a line that draws itself through it, line after line,
 * when [struck] turns on, and wipes itself back when it turns off.
 */
@Composable
internal fun StrikeThroughText(text: String, struck: Boolean, style: TextStyle, modifier: Modifier = Modifier) {
    val strike = animateFloatAsState(
        targetValue = if (struck) 1f else 0f,
        animationSpec = tween(STRIKE_MILLIS, easing = FastOutSlowInEasing),
        label = "strike",
    )
    val color by animateColorAsState(
        targetValue = if (struck) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(STRIKE_MILLIS),
        label = "strikeColor",
    )
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = text,
        modifier = modifier.drawWithContent {
            drawContent()
            // Read while drawing only: the line moves without the text being composed again.
            layout?.let { drawStrike(it, strike.value, color) }
        },
        style = style,
        color = color,
        onTextLayout = { layout = it },
    )
}

/** The scale of a tick box: a short bounce each time it is ticked or unticked, `1` at rest. */
@Composable
internal fun rememberCheckBounce(checked: Boolean): State<Float> {
    val scale = remember { Animatable(1f) }
    val shown = remember { mutableStateOf(checked) }
    LaunchedEffect(checked) {
        // Not when the row first shows: only a change bounces.
        if (shown.value == checked) return@LaunchedEffect
        shown.value = checked
        scale.animateTo(BOUNCE_SCALE, tween(BOUNCE_UP_MILLIS, easing = FastOutSlowInEasing))
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
    }
    return scale.asState()
}

/** The tick felt under the finger: firmer when the item goes into the basket than when it comes out. */
internal fun HapticFeedback.tick(checked: Boolean) =
    performHapticFeedback(if (checked) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)

/** The first [progress] of the length of all the lines of [layout], crossed out from where the reading starts. */
private fun DrawScope.drawStrike(layout: TextLayoutResult, progress: Float, color: Color) {
    if (progress <= 0f) return
    var total = 0f
    for (line in 0 until layout.lineCount) total += layout.getLineRight(line) - layout.getLineLeft(line)
    var remaining = total * progress
    val thickness = STRIKE_THICKNESS.toPx()
    val fromRight = layoutDirection == LayoutDirection.Rtl
    for (line in 0 until layout.lineCount) {
        if (remaining <= 0f) return
        val left = layout.getLineLeft(line)
        val right = layout.getLineRight(line)
        val length = min(right - left, remaining)
        val y = (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f
        val start = if (fromRight) right else left
        val end = if (fromRight) right - length else left + length
        drawLine(color, Offset(start, y), Offset(end, y), strokeWidth = thickness)
        remaining -= right - left
    }
}

private const val STRIKE_MILLIS = 260
private const val BOUNCE_UP_MILLIS = 90
private const val BOUNCE_SCALE = 1.25f
private val STRIKE_THICKNESS = 1.5.dp
