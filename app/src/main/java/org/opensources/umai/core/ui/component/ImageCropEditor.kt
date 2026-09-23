package org.opensources.umai.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import org.opensources.umai.R
import org.opensources.umai.core.image.CropGeometry
import org.opensources.umai.core.image.CropRegion
import kotlin.math.roundToInt

/** The outline of the part that is kept: a circle for an avatar, a rectangle for a recipe. */
enum class CropFrame(val aspectRatio: Float, val round: Boolean) {
    AVATAR(aspectRatio = 1f, round = true),
    RECIPE(aspectRatio = 4f / 3f, round = false),
}

/**
 * Full-screen editor shown right after a picture is picked: pinch to zoom,
 * drag to move, or use the slider. What stays inside the frame is what gets
 * uploaded; the rest of the picture is dimmed around it.
 */
@Composable
fun ImageCropEditor(
    sourceUri: String,
    frame: CropFrame,
    onCancel: () -> Unit,
    onConfirm: (CropRegion) -> Unit,
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Text(
                text = stringResource(R.string.crop_title),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
            Text(
                text = stringResource(R.string.crop_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            var geometry by remember(sourceUri) { mutableStateOf<CropGeometry?>(null) }

            CropArea(
                sourceUri = sourceUri,
                frame = frame,
                geometry = geometry,
                onGeometryChange = { geometry = it },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )

            val current = geometry
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Outlined.ZoomIn, contentDescription = null, tint = Color.White)
                val zoomLabel = stringResource(R.string.crop_zoom)
                Slider(
                    value = current?.zoom ?: CropGeometry.MIN_ZOOM,
                    onValueChange = { value ->
                        current?.let { geometry = it.copy(zoom = value).clamped() }
                    },
                    valueRange = CropGeometry.MIN_ZOOM..CropGeometry.MAX_ZOOM,
                    enabled = current != null,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = zoomLabel },
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            ) {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.action_cancel), color = Color.White)
                }
                Button(
                    onClick = { current?.let { onConfirm(it.region()) } },
                    enabled = current != null,
                ) {
                    Text(stringResource(R.string.crop_confirm))
                }
            }
        }
    }
}

@Composable
private fun CropArea(
    sourceUri: String,
    frame: CropFrame,
    geometry: CropGeometry?,
    onGeometryChange: (CropGeometry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val painter = rememberAsyncImagePainter(
        model = remember(sourceUri) {
            ImageRequest.Builder(context)
                .data(sourceUri)
                .size(PREVIEW_MAX_SIDE)
                .build()
        },
    )
    val painterState by painter.state.collectAsState()
    val density = LocalDensity.current
    // The gesture detectors outlive a recomposition: they read the latest values here.
    val latestGeometry by rememberUpdatedState(geometry)
    val latestOnChange by rememberUpdatedState(onGeometryChange)

    // Clipped: a zoomed picture must not spill over the title and the zoom slider.
    BoxWithConstraints(modifier = modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        val areaWidth = constraints.maxWidth.toFloat()
        val areaHeight = constraints.maxHeight.toFloat()
        val margin = with(density) { FRAME_MARGIN.toPx() }
        val frameWidth = minOf(areaWidth - 2 * margin, (areaHeight - 2 * margin) * frame.aspectRatio)
        val frameHeight = frameWidth / frame.aspectRatio
        val loaded = painterState is AsyncImagePainter.State.Success

        // Starts centred and zoomed out once the picture is known, and again if
        // the area changes size (the phone was rotated).
        LaunchedEffect(loaded, frameWidth, frameHeight) {
            if (!loaded) return@LaunchedEffect
            val size = painter.intrinsicSize
            CropGeometry(size.width, size.height, frameWidth, frameHeight)
                .takeIf { it.isValid }
                ?.let(latestOnChange)
        }

        when {
            painterState is AsyncImagePainter.State.Error -> Text(
                text = stringResource(R.string.crop_unreadable),
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(24.dp),
            )

            geometry == null -> CircularProgressIndicator(color = Color.White)

            else -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoomChange, _ ->
                            latestGeometry?.let { latestOnChange(it.transformed(pan.x, pan.y, zoomChange)) }
                        }
                    }
                    .pointerInput(Unit) {
                        // A double tap zooms in, or back out when already zoomed.
                        detectTapGestures(onDoubleTap = {
                            latestGeometry?.let {
                                val zoom = if (it.zoom > DOUBLE_TAP_ZOOM / 2 + 0.5f) 1f else DOUBLE_TAP_ZOOM
                                latestOnChange(it.copy(zoom = zoom).clamped())
                            }
                        })
                    },
                contentAlignment = Alignment.Center,
            ) {
                with(density) {
                    Image(
                        painter = painter,
                        contentDescription = stringResource(R.string.crop_image),
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier
                            .requiredSize(geometry.displayedWidth.toDp(), geometry.displayedHeight.toDp())
                            .offset { IntOffset(geometry.offsetX.roundToInt(), geometry.offsetY.roundToInt()) },
                    )
                }
                FrameOverlay(frame = frame, frameWidth = frameWidth, frameHeight = frameHeight)
            }
        }
    }
}

/** Dims everything outside the frame and outlines the frame itself. */
@Composable
private fun FrameOverlay(frame: CropFrame, frameWidth: Float, frameHeight: Float) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            // Offscreen, so the frame can be punched out of the dimmed layer.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        val topLeft = Offset((size.width - frameWidth) / 2f, (size.height - frameHeight) / 2f)
        val frameSize = Size(frameWidth, frameHeight)
        drawRect(Color.Black.copy(alpha = 0.6f))
        if (frame.round) {
            drawOval(Color.Transparent, topLeft, frameSize, blendMode = BlendMode.Clear)
            drawOval(Color.White, topLeft, frameSize, style = Stroke(width = 2.dp.toPx()))
        } else {
            val radius = CornerRadius(12.dp.toPx())
            drawRoundRect(Color.Transparent, topLeft, frameSize, radius, blendMode = BlendMode.Clear)
            drawRoundRect(Color.White, topLeft, frameSize, radius, style = Stroke(width = 2.dp.toPx()))
        }
    }
}

private const val DOUBLE_TAP_ZOOM = 2f

private val FRAME_MARGIN = 24.dp

/** Large enough to stay sharp when zoomed, small enough to decode quickly. */
private const val PREVIEW_MAX_SIDE = 2048
