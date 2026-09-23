package org.opensources.umai.core.image

/**
 * The part of a picture kept by the crop editor, in fractions of the picture's
 * width and height, so it holds whatever resolution the picture is decoded at.
 */
data class CropRegion(val left: Float, val top: Float, val width: Float, val height: Float) {
    companion object {
        val Full = CropRegion(0f, 0f, 1f, 1f)
    }
}

/**
 * Where the picture sits under the crop frame.
 *
 * At [zoom] 1 the picture exactly covers the frame on its shorter side; the
 * offsets move the picture's centre away from the frame's centre, in pixels.
 * Every operation keeps the frame fully covered, so the result never contains
 * an empty band.
 */
data class CropGeometry(
    val imageWidth: Float,
    val imageHeight: Float,
    val frameWidth: Float,
    val frameHeight: Float,
    val zoom: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
) {
    private val coverScale: Float
        get() = maxOf(frameWidth / imageWidth, frameHeight / imageHeight)

    /** Size of the picture on screen at the current zoom. */
    val displayedWidth: Float get() = imageWidth * coverScale * zoom
    val displayedHeight: Float get() = imageHeight * coverScale * zoom

    /** Applies one step of a pinch-and-drag gesture. */
    fun transformed(panX: Float, panY: Float, zoomChange: Float): CropGeometry =
        copy(
            zoom = (zoom * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM),
            offsetX = offsetX + panX,
            offsetY = offsetY + panY,
        ).clamped()

    fun clamped(): CropGeometry {
        val maxX = ((displayedWidth - frameWidth) / 2f).coerceAtLeast(0f)
        val maxY = ((displayedHeight - frameHeight) / 2f).coerceAtLeast(0f)
        return copy(offsetX = offsetX.coerceIn(-maxX, maxX), offsetY = offsetY.coerceIn(-maxY, maxY))
    }

    /** The part of the picture inside the frame. */
    fun region(): CropRegion {
        val width = frameWidth / displayedWidth
        val height = frameHeight / displayedHeight
        val left = ((displayedWidth - frameWidth) / 2f - offsetX) / displayedWidth
        val top = ((displayedHeight - frameHeight) / 2f - offsetY) / displayedHeight
        return CropRegion(
            left = left.coerceIn(0f, 1f - width),
            top = top.coerceIn(0f, 1f - height),
            width = width.coerceAtMost(1f),
            height = height.coerceAtMost(1f),
        )
    }

    val isValid: Boolean
        get() = imageWidth > 0f && imageHeight > 0f && frameWidth > 0f && frameHeight > 0f

    companion object {
        const val MIN_ZOOM = 1f
        const val MAX_ZOOM = 5f
    }
}
