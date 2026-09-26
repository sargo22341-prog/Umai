package org.opensources.umai.planning.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import org.opensources.umai.R
import org.opensources.umai.planning.data.BarcodeFrameAnalyzer
import org.opensources.umai.planning.data.ScanWindow
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Reads a barcode live from the camera, as a barcode scanner app does: the user
 * aims with a frame, every frame of the camera is read under it, and the first
 * barcode read closes the scanner and goes to [onScanned].
 */
@Composable
internal fun BarcodeScannerDialog(onScanned: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var refused by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        granted = allowed
        refused = !allowed
    }
    LaunchedEffect(Unit) {
        if (!granted) permission.launch(Manifest.permission.CAMERA)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            var torchAvailable by remember { mutableStateOf(false) }
            var torchOn by remember { mutableStateOf(false) }
            var failed by remember { mutableStateOf(false) }
            when {
                granted && !failed -> CameraScanner(
                    torchOn = torchOn,
                    onTorchAvailable = { torchAvailable = it },
                    onFailed = { failed = true },
                    onScanned = onScanned,
                )
                refused || failed -> Text(
                    text = stringResource(if (failed) R.string.food_scanner_failed else R.string.food_scanner_permission),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().safeDrawingPadding().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_close), tint = Color.White)
                }
                if (torchAvailable && granted && !failed) {
                    IconButton(onClick = { torchOn = !torchOn }) {
                        Icon(
                            imageVector = if (torchOn) Icons.Outlined.FlashlightOff else Icons.Outlined.FlashlightOn,
                            contentDescription = stringResource(if (torchOn) R.string.food_scanner_torch_off else R.string.food_scanner_torch_on),
                            tint = Color.White,
                        )
                    }
                }
            }
        }
    }
}

/** The camera preview, the aiming frame over it, and the reading of the frames. */
@Composable
private fun CameraScanner(
    torchOn: Boolean,
    onTorchAvailable: (Boolean) -> Unit,
    onFailed: () -> Unit,
    onScanned: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val scanned by rememberUpdatedState(onScanned)
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }

    val analyzer = remember {
        BarcodeFrameAnalyzer { code ->
            context.mainExecutor.execute {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                scanned(code)
            }
        }
    }
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(executor) { onDispose { executor.shutdown() } }

    val useCases = remember {
        // Preview and analysis share one aspect ratio, so the frame drawn on the one lies over the same part of the other.
        val sameView = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
        val preview = Preview.Builder().setResolutionSelector(sameView.build()).build()
        preview.setSurfaceProvider { surfaceRequest = it }
        // Thin bars need pixels: Full HD keeps a barcode at arm's length readable, and only the frame is read.
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(
                sameView.setResolutionStrategy(
                    ResolutionStrategy(android.util.Size(1920, 1080), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                ).build(),
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysis.setAnalyzer(executor, analyzer)
        arrayOf(preview, analysis)
    }

    LaunchedEffect(lifecycleOwner) {
        val provider = try {
            ProcessCameraProvider.awaitInstance(context)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            onFailed()
            return@LaunchedEffect
        }
        try {
            val bound = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, *useCases)
            camera = bound
            onTorchAvailable(bound.cameraInfo.hasFlashUnit())
            awaitCancellation()
        } catch (_: IllegalArgumentException) {
            // No back camera on this device.
            onFailed()
        } finally {
            provider.unbind(*useCases)
        }
    }
    LaunchedEffect(camera, torchOn) {
        camera?.cameraControl?.enableTorch(torchOn)
    }

    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                viewSize = size
                val frame = aimingFrame(size.width.toFloat())
                analyzer.window = ScanWindow(size.width, size.height, frame.width.roundToInt(), frame.height.roundToInt())
            },
    ) {
        surfaceRequest?.let { CameraXViewfinder(surfaceRequest = it, modifier = Modifier.fillMaxSize()) }
        if (viewSize != IntSize.Zero) AimingOverlay()
        Text(
            text = stringResource(R.string.food_scanner_hint),
            color = Color.White,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(horizontal = 32.dp, vertical = 48.dp),
        )
    }
}

/** Darkens the preview around the aiming frame, and draws the frame with a line to aim across the bars. */
@Composable
private fun AimingOverlay() {
    val line = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
        val frame = aimingFrame(size.width)
        val topLeft = Offset((size.width - frame.width) / 2, (size.height - frame.height) / 2)
        val corner = CornerRadius(16.dp.toPx())
        drawRect(Color.Black.copy(alpha = 0.55f))
        drawRoundRect(Color.Transparent, topLeft, frame, corner, blendMode = BlendMode.Clear)
        drawRoundRect(Color.White, topLeft, frame, corner, style = Stroke(width = 3.dp.toPx()))
        val middle = topLeft.y + frame.height / 2
        drawLine(
            color = line,
            start = Offset(topLeft.x + 12.dp.toPx(), middle),
            end = Offset(topLeft.x + frame.width - 12.dp.toPx(), middle),
            strokeWidth = 2.dp.toPx(),
        )
    }
}

/** The aiming frame, centred in a view [viewWidth] wide: the shape of a food barcode, most of the width. */
private fun aimingFrame(viewWidth: Float): Size {
    val width = viewWidth * 0.8f
    return Size(width, width * 0.55f)
}
