package org.opensources.umai.core.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * The first appearance of a list: the items on screen when its content first
 * shows rise into place one after the other. Items that show later — scrolled
 * to, or loaded with the next page — are simply there, and so is the whole
 * list when the screen comes back (navigating back, switching tabs), as the
 * entrance is saved with the screen's state.
 */
@Stable
class ListEntrance internal constructor(played: Boolean) {

    private var played = played

    /** When the first item showed, on the monotonic clock, `null` before. */
    private var startedAt: Long? = null

    internal val hasStarted: Boolean get() = played || startedAt != null

    /** How long item [index] waits before rising, `null` when it shows in place. */
    internal fun delayFor(index: Int): Long? {
        if (played) return null
        val now = System.nanoTime()
        val start = startedAt ?: now.also { startedAt = it }
        if (now - start > ENTRANCE_WINDOW_NANOS) {
            played = true
            return null
        }
        return min(index, MAX_STAGGERED_ITEMS) * STAGGER_MILLIS
    }

    internal companion object {
        val Saver: Saver<ListEntrance, Boolean> = Saver(save = { it.hasStarted }, restore = { ListEntrance(played = it) })
    }
}

/** The entrance of a list on this screen, kept with the screen's state. */
@Composable
fun rememberListEntrance(): ListEntrance = rememberSaveable(saver = ListEntrance.Saver) { ListEntrance(played = false) }

/** The item at [index] of a list rises into place with [entrance], when it plays; no entrance, no rise. */
fun Modifier.listEntrance(entrance: ListEntrance?, index: Int): Modifier =
    if (entrance == null) this else this then ListEntranceElement(entrance, index)

private data class ListEntranceElement(val entrance: ListEntrance, val index: Int) : ModifierNodeElement<ListEntranceNode>() {
    override fun create() = ListEntranceNode(entrance, index)

    // The entrance plays once, when the item first shows: a later change has nothing to replay.
    override fun update(node: ListEntranceNode) = Unit

    override fun InspectorInfo.inspectableProperties() {
        name = "listEntrance"
        properties["index"] = index
    }
}

private class ListEntranceNode(private val entrance: ListEntrance, private val index: Int) : Modifier.Node(), LayoutModifierNode {

    /** From `0`, hidden below its place, to `1`, in place; `null` when the item shows in place. */
    private var progress: Animatable<Float, AnimationVector1D>? = null

    override fun onAttach() {
        val wait = entrance.delayFor(index) ?: return
        val rising = Animatable(0f)
        progress = rising
        coroutineScope.launch {
            // The system animation scale applies to the wait as it does to the rise, down to none.
            val scale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
            delay((wait * scale).toLong())
            rising.animateTo(1f, tween(RISE_MILLIS, easing = FastOutSlowInEasing))
        }
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) {
                val shown = progress?.value ?: 1f
                alpha = shown
                translationY = (1f - shown) * RISE_DISTANCE.toPx()
            }
        }
    }
}

/** Items that show this soon after the first one belong to the first appearance. */
private const val ENTRANCE_WINDOW_NANOS = 500_000_000L
private const val STAGGER_MILLIS = 45L

/** The items further down rise with the last staggered one, so the list is never long to show. */
private const val MAX_STAGGERED_ITEMS = 8
private const val RISE_MILLIS = 320
private val RISE_DISTANCE = 24.dp
