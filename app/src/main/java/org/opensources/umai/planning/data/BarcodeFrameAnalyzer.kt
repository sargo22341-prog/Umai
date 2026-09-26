package org.opensources.umai.planning.data

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A size in pixels of a camera frame. */
data class FrameCrop(val width: Int, val height: Int)

/**
 * The frame the user aims with, centred over the camera preview, in pixels of
 * that preview. The preview fills its view and is cropped to it, as a
 * viewfinder is.
 */
data class ScanWindow(val viewWidth: Int, val viewHeight: Int, val frameWidth: Int, val frameHeight: Int) {

    /**
     * The part of an upright camera frame of [imageWidth] x [imageHeight] that
     * lies under the aiming frame, a little wider so that a barcode just
     * touching its edges is read too.
     */
    fun cropIn(imageWidth: Int, imageHeight: Int): FrameCrop {
        if (viewWidth <= 0 || viewHeight <= 0) return FrameCrop(imageWidth, imageHeight)
        val scale = max(viewWidth / imageWidth.toFloat(), viewHeight / imageHeight.toFloat())
        return FrameCrop(
            width = min(imageWidth, (frameWidth / scale * MARGIN).roundToInt()).coerceAtLeast(1),
            height = min(imageHeight, (frameHeight / scale * MARGIN).roundToInt()).coerceAtLeast(1),
        )
    }

    private companion object {
        const val MARGIN = 1.2f
    }
}

/**
 * Reads each camera frame, only where the aiming frame is, until a barcode is
 * found: [onFound] is then called once, on the analysis thread. A smaller part
 * of the frame keeps each read quick and away from the text and pictures around
 * the barcode.
 */
class BarcodeFrameAnalyzer(private val onFound: (String) -> Unit) : ImageAnalysis.Analyzer {

    /** Where the user aims; the whole frame is read until it is known. */
    @Volatile
    var window: ScanWindow? = null

    private val found = AtomicBoolean(false)

    override fun analyze(image: ImageProxy) {
        image.use {
            if (found.get()) return
            val code = read(it) ?: return
            if (found.compareAndSet(false, true)) onFound(code)
        }
    }

    private fun read(image: ImageProxy): String? {
        // The sensor lies sideways: a quarter turn swaps the sides of the upright frame.
        val quarter = image.imageInfo.rotationDegrees % 180 != 0
        val uprightWidth = if (quarter) image.height else image.width
        val uprightHeight = if (quarter) image.width else image.height
        val crop = window?.cropIn(uprightWidth, uprightHeight) ?: FrameCrop(uprightWidth, uprightHeight)
        // Centred, the crop only needs its sides swapped back to the sensor's.
        val width = if (quarter) crop.height else crop.width
        val height = if (quarter) crop.width else crop.height
        val left = (image.width - width) / 2
        val top = (image.height - height) / 2

        // The first plane of a YUV_420_888 frame is its luminance, one byte per pixel.
        val plane = image.planes[0]
        val buffer = plane.buffer
        val luminance = ByteArray(width * height)
        for (row in 0 until height) {
            buffer.position((top + row) * plane.rowStride + left)
            buffer.get(luminance, row * width, width)
        }
        return BarcodeDecoder.decodeLuminance(luminance, width, height)
    }
}
