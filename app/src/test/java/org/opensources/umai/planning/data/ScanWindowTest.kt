package org.opensources.umai.planning.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ScanWindowTest {

    /** A Pixel 6 Pro screen, portrait, with the frame 80 % of its width. */
    private val window = ScanWindow(viewWidth = 1440, viewHeight = 3120, frameWidth = 1152, frameHeight = 634)

    @Test
    fun `the part of the camera frame under the aiming frame is read, a little wider`() {
        // A 16:9 frame, upright, is cropped at its sides to fill the taller screen: 1920 / 3120 of a pixel per pixel.
        assertEquals(FrameCrop(width = 851, height = 468), window.cropIn(imageWidth = 1080, imageHeight = 1920))
    }

    @Test
    fun `the crop never leaves the camera frame`() {
        val wide = ScanWindow(viewWidth = 1000, viewHeight = 1000, frameWidth = 1000, frameHeight = 1000)

        // The square frame covers the whole height, 480 pixels, and 1.2 times that across.
        assertEquals(FrameCrop(width = 576, height = 480), wide.cropIn(imageWidth = 640, imageHeight = 480))
    }

    @Test
    fun `without a view yet, the whole camera frame is read`() {
        val unknown = ScanWindow(viewWidth = 0, viewHeight = 0, frameWidth = 0, frameHeight = 0)

        assertEquals(FrameCrop(width = 1080, height = 1920), unknown.cropIn(imageWidth = 1080, imageHeight = 1920))
    }
}
