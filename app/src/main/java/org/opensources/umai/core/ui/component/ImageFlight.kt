package org.opensources.umai.core.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.min

/**
 * A picture flying from [from], its place in the window, to the target of an
 * [ImageFlightState]. [from] is `null` when that place is off screen: the
 * picture then rises from the middle of the screen.
 */
@Immutable
class ImageFlight(val imageUrl: String?, val from: Rect?)

/**
 * The "added to the basket" gesture of shopping apps: a copy of a picture
 * lifts off its place, shrinks, curves towards [flightTarget] and drops into
 * it, which bumps as it lands. The picture left behind does not move.
 *
 * The places are layout coordinates, read at launch and during the flight
 * only, so a target that shows up or moves meanwhile is still reached.
 */
@Stable
class ImageFlightState {
    var current: ImageFlight? by mutableStateOf(null)
        private set

    /** Where [launchFromSource] starts, marked by [placeOf]. */
    val source = ImagePlace()

    internal val progress = Animatable(0f)
    internal val landing = Animatable(0f)
    internal var target: LayoutCoordinates? = null
    internal var overlay: LayoutCoordinates? = null

    fun launch(flight: ImageFlight) {
        current = flight
    }

    /** Flies [imageUrl] from [source], as far as it is on screen. */
    fun launchFromSource(imageUrl: String?) = launch(ImageFlight(imageUrl, source.visibleBounds()))

    internal fun finish() {
        current = null
    }
}

/**
 * The place of a picture a flight may leave from, kept as the picture moves.
 * Only a reference is stored on each placement: the bounds are worked out
 * when a flight starts, not at every frame of a scroll.
 */
@Stable
class ImagePlace {
    internal var coordinates: LayoutCoordinates? = null

    /** The part of the picture the window shows, `null` when it is off screen. */
    fun visibleBounds(): Rect? = coordinates?.visibleBoundsInWindow()
}

/** Keeps [place] on this element. */
fun Modifier.placeOf(place: ImagePlace): Modifier = onPlaced { place.coordinates = it }

private fun LayoutCoordinates.visibleBoundsInWindow(): Rect? =
    takeIf { it.isAttached }?.boundsInWindow()?.takeIf { it.width >= 1f && it.height >= 1f }

/** Marks where the flights of [state] land; the element bumps when one does. */
fun Modifier.flightTarget(state: ImageFlightState): Modifier = onPlaced { state.target = it }
    .graphicsLayer {
        val bump = 1f + LANDING_BUMP * state.landing.value
        scaleX = bump
        scaleY = bump
    }

/**
 * Draws the flight of [state] while there is one, over everything below it:
 * it must be laid over the source and the target together. It takes no touch.
 */
@Composable
fun ImageFlightOverlay(state: ImageFlightState, modifier: Modifier = Modifier) {
    val flight = state.current ?: return
    val density = LocalDensity.current
    val startSize = with(density) { flight.from?.let { min(it.width, it.height) } ?: FALLBACK_SIZE.toPx() }

    LaunchedEffect(flight) {
        state.landing.snapTo(0f)
        state.progress.snapTo(0f)
        state.progress.animateTo(1f, tween(FLIGHT_MILLIS, easing = LinearEasing))
        state.landing.animateTo(1f, tween(LANDING_RISE_MILLIS))
        state.landing.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        state.finish()
    }

    Box(modifier = modifier.fillMaxSize().onPlaced { state.overlay = it }) {
        RemoteImage(
            url = flight.imageUrl,
            // The picture it copies is described already, and the result is announced.
            contentDescription = null,
            placeholderIconSize = 24.dp,
            modifier = Modifier
                .size(with(density) { startSize.toDp() })
                .graphicsLayer {
                    val overlay = state.overlay?.takeIf { it.isAttached }
                    val origin = overlay?.positionInWindow() ?: Offset.Zero
                    val start = flight.from?.center
                        ?: overlay?.let { origin + Offset(it.size.width / 2f, it.size.height / 2f) }
                        ?: origin
                    // Until the target is laid out, the picture lifts off where it is.
                    val end = state.target?.visibleBoundsInWindow()?.center ?: start
                    val pose = FlightPose.at(state.progress.value, start, end)
                    val scale = pose.scale(startSize, LIFT_SIZE.toPx(), LANDED_SIZE.toPx())
                    scaleX = scale
                    scaleY = scale
                    translationX = pose.position.x - origin.x - size.width / 2f
                    translationY = pose.position.y - origin.y - size.height / 2f
                    alpha = pose.alpha
                    shadowElevation = FLIGHT_ELEVATION.toPx()
                    shape = CircleShape
                    clip = true
                },
        )
    }
}

/**
 * Where the picture is at [progress] of its flight. It first lifts off in
 * place, shrinking, then travels on a curve that sets off sideways and turns
 * towards the target, and fades as it drops into it.
 */
private class FlightPose(val position: Offset, private val lift: Float, private val travel: Float, val alpha: Float) {

    fun scale(startSize: Float, liftSize: Float, landedSize: Float): Float {
        val lifted = min(1f, liftSize / startSize)
        return if (travel == 0f) lerp(1f, lifted, lift) else lerp(lifted, landedSize / startSize, travel)
    }

    companion object {
        fun at(progress: Float, start: Offset, end: Offset): FlightPose {
            val lift = FastOutSlowInEasing.transform((progress / LIFT_END).coerceIn(0f, 1f))
            val travel = FastOutSlowInEasing.transform(((progress - LIFT_END) / (1f - LIFT_END)).coerceIn(0f, 1f))
            // A quadratic Bézier whose control point is level with the start and in line with the end.
            val rest = 1f - travel
            val weightStart = rest * rest
            val weightControl = 2f * rest * travel
            val weightEnd = travel * travel
            val position = Offset(
                x = weightStart * start.x + weightControl * end.x + weightEnd * end.x,
                y = weightStart * start.y + weightControl * start.y + weightEnd * end.y,
            )
            val appear = (progress / APPEAR_END).coerceIn(0f, 1f)
            val vanish = 1f - ((travel - VANISH_START) / (1f - VANISH_START)).coerceIn(0f, 1f)
            return FlightPose(position, lift, travel, appear * vanish)
        }
    }
}

private const val FLIGHT_MILLIS = 800
private const val LANDING_RISE_MILLIS = 90

/** Shares of the flight: fading in, lifting off, and the end of the travel where it fades out. */
private const val APPEAR_END = 0.08f
private const val LIFT_END = 0.25f
private const val VANISH_START = 0.8f

/** How much bigger the target gets as the picture lands in it. */
private const val LANDING_BUMP = 0.25f

private val LIFT_SIZE = 112.dp
private val LANDED_SIZE = 24.dp
private val FALLBACK_SIZE = 112.dp
private val FLIGHT_ELEVATION = 8.dp
