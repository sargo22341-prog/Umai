package org.opensources.umai.planning.data

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Executors

/**
 * Reads a real barcode held in front of the back camera, the way the scanner
 * does. Skipped unless the barcode expected is given, and the camera allowed:
 *
 * ```
 * gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.barcode=3250390103745
 * ```
 *
 * The barcode must lie in the middle of the camera's view, where the scanner's
 * frame would be on a screen of the size of this phone's.
 */
@RunWith(AndroidJUnit4::class)
class BarcodeCameraDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val expected: String? = InstrumentationRegistry.getArguments().getString("barcode")

    private class ResumedOwner : LifecycleOwner {
        override val lifecycle = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
    }

    @Test
    fun theBarcodeInFrontOfTheCameraIsRead() = runBlocking {
        assumeTrue("No barcode expected: pass the barcode argument", expected != null)
        assumeTrue(
            "The camera is not allowed",
            context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
        val read = CompletableDeferred<String>()
        val analyzer = BarcodeFrameAnalyzer { read.complete(it) }
        val screen = context.resources.displayMetrics
        val frameWidth = (screen.widthPixels * 0.8f).toInt()
        analyzer.window = ScanWindow(screen.widthPixels, screen.heightPixels, frameWidth, (frameWidth * 0.55f).toInt())
        val executor = Executors.newSingleThreadExecutor()
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(
                        ResolutionStrategy(android.util.Size(1920, 1080), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                    )
                    .build(),
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysis.setAnalyzer(executor, analyzer)

        val provider = ProcessCameraProvider.awaitInstance(context)
        withContext(Dispatchers.Main) {
            provider.bindToLifecycle(ResumedOwner(), CameraSelector.DEFAULT_BACK_CAMERA, analysis)
        }
        try {
            assertEquals(expected, withTimeout(TIMEOUT_MS) { read.await() })
        } finally {
            withContext(Dispatchers.Main) { provider.unbindAll() }
            executor.shutdown()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 15_000L
    }
}
