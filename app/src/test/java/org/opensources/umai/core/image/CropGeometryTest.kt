package org.opensources.umai.core.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CropGeometryTest {

    private val tolerance = 0.0001f

    /** A landscape photo under a square frame, as for an avatar. */
    private val landscape = CropGeometry(imageWidth = 2000f, imageHeight = 1000f, frameWidth = 500f, frameHeight = 500f)

    @Test
    fun `at first the picture covers the frame on its shorter side and is centred`() {
        assertEquals(1000f, landscape.displayedWidth, tolerance)
        assertEquals(500f, landscape.displayedHeight, tolerance)

        val region = landscape.region()
        assertEquals(0.25f, region.left, tolerance)
        assertEquals(0f, region.top, tolerance)
        assertEquals(0.5f, region.width, tolerance)
        assertEquals(1f, region.height, tolerance)
    }

    @Test
    fun `dragging moves the kept region the other way`() {
        // Dragging the picture right shows more of its left side.
        val region = landscape.transformed(panX = 250f, panY = 0f, zoomChange = 1f).region()

        assertEquals(0f, region.left, tolerance)
    }

    @Test
    fun `the picture can never leave an empty band in the frame`() {
        val dragged = landscape.transformed(panX = 5000f, panY = -5000f, zoomChange = 1f)

        assertEquals(250f, dragged.offsetX, tolerance)
        assertEquals(0f, dragged.offsetY, tolerance)
        val region = dragged.region()
        assertTrue(region.left >= 0f && region.left + region.width <= 1f + tolerance)
    }

    @Test
    fun `zooming in keeps a smaller part of the picture`() {
        val region = landscape.transformed(panX = 0f, panY = 0f, zoomChange = 2f).region()

        assertEquals(0.25f, region.width, tolerance)
        assertEquals(0.5f, region.height, tolerance)
        assertEquals(0.375f, region.left, tolerance)
        assertEquals(0.25f, region.top, tolerance)
    }

    @Test
    fun `zoom stays between its bounds`() {
        assertEquals(CropGeometry.MAX_ZOOM, landscape.transformed(0f, 0f, 100f).zoom, tolerance)
        assertEquals(CropGeometry.MIN_ZOOM, landscape.transformed(0f, 0f, 0.01f).zoom, tolerance)
    }

    @Test
    fun `a portrait photo under a landscape frame is cropped top and bottom`() {
        val portrait = CropGeometry(imageWidth = 900f, imageHeight = 1600f, frameWidth = 800f, frameHeight = 600f)

        val region = portrait.region()

        assertEquals(1f, region.width, tolerance)
        assertEquals(0f, region.left, tolerance)
        assertEquals(1f - 2 * region.top, region.height, tolerance)
    }
}
