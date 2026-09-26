package org.opensources.umai.planning.data

import android.content.Context
import org.opensources.umai.core.image.CameraCapture
import org.opensources.umai.core.image.CropRegion
import org.opensources.umai.core.image.ImageCropper

/** The photo of a nutrition label, only read, never kept. */
fun interface LabelPictures {
    /** The picture at [sourceUri] as a JPEG the model can look at, `null` when it cannot be read. */
    suspend fun read(sourceUri: String): ByteArray?
}

/**
 * Reads the whole picture, scaled down: the model looks at pictures well
 * under this size, and a label keeps readable at it. A photo taken with the
 * camera for this is deleted as soon as it is read.
 */
class DeviceLabelPictures(
    context: Context,
    private val cropper: ImageCropper,
) : LabelPictures {

    private val appContext = context.applicationContext

    override suspend fun read(sourceUri: String): ByteArray? =
        try {
            cropper.crop(sourceUri, CropRegion.Full, MAX_SIDE)?.bytes
        } finally {
            CameraCapture.clear(appContext)
        }

    private companion object {
        const val MAX_SIDE = 1024
    }
}
