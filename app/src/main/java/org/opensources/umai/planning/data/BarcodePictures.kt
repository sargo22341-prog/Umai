package org.opensources.umai.planning.data

import android.content.Context
import android.graphics.ImageDecoder
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/** A picture of a barcode, picked in the gallery, only read. */
fun interface BarcodePictures {
    /** The barcode on the picture at [sourceUri], `null` when none can be read. */
    suspend fun read(sourceUri: String): String?
}

/**
 * Opens the picture, applying its EXIF orientation, scaled down to a size the
 * bars stay sharp at, and reads it with [BarcodeDecoder].
 */
class DeviceBarcodePictures(context: Context) : BarcodePictures {

    private val appContext = context.applicationContext

    override suspend fun read(sourceUri: String): String? = withContext(Dispatchers.Default) {
        val uri = runCatching { sourceUri.toUri() }.getOrNull() ?: return@withContext null
        val bitmap = runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(appContext.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val factor = max(1f, max(info.size.width, info.size.height) / MAX_SIDE.toFloat())
                decoder.setTargetSize(
                    max(1, (info.size.width / factor).roundToInt()),
                    max(1, (info.size.height / factor).roundToInt()),
                )
            }
        }.getOrNull() ?: return@withContext null
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val width = bitmap.width
        val height = bitmap.height
        bitmap.recycle()
        BarcodeDecoder.decode(pixels, width, height)
    }

    private companion object {
        /** A barcode filling a third of a photo keeps some 700 pixels across its bars. */
        const val MAX_SIDE = 2048
    }
}
