package org.opensources.umai.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** A picture ready to be uploaded to Mealie. */
class EncodedImage(val bytes: ByteArray, val mediaType: String, val extension: String)

/**
 * Turns the picture the user picked, and the region they kept in the crop
 * editor, into an image Mealie can store.
 *
 * Repositories depend on this rather than on a `Context`, so they stay
 * testable on the JVM and know nothing about content resolvers.
 */
interface ImageCropper {
    /** `null` when the picture cannot be read or decoded. */
    suspend fun crop(sourceUri: String, region: CropRegion, maxSide: Int): EncodedImage?
}

/**
 * Decodes with [ImageDecoder], which applies the EXIF orientation like the
 * crop editor's preview does, so the region lands where the user put it.
 * The decode is scaled down first, so a 50-megapixel photo never has to fit
 * in memory at full size.
 */
class DeviceImageCropper(context: Context) : ImageCropper {

    private val resolver = context.applicationContext.contentResolver

    override suspend fun crop(sourceUri: String, region: CropRegion, maxSide: Int): EncodedImage? =
        withContext(Dispatchers.IO) {
            val uri = runCatching { sourceUri.toUri() }.getOrNull() ?: return@withContext null
            val decoded = runCatching {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val (width, height) = scaledSize(info.size.width, info.size.height, region, maxSide)
                    decoder.setTargetSize(width, height)
                }
            }.getOrNull() ?: return@withContext null

            val cropped = decoded.cropTo(region)
            val output = ByteArrayOutputStream()
            cropped.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            if (cropped !== decoded) cropped.recycle()
            decoded.recycle()
            EncodedImage(output.toByteArray(), mediaType = "image/jpeg", extension = "jpg")
        }

    /**
     * The size to decode the whole picture at, so that the cropped part comes
     * out no larger than [maxSide] on its longer side.
     */
    private fun scaledSize(width: Int, height: Int, region: CropRegion, maxSide: Int): Pair<Int, Int> {
        val croppedLongest = max(width * region.width, height * region.height)
        val factor = max(1f, croppedLongest / maxSide)
        return max(1, (width / factor).roundToInt()) to max(1, (height / factor).roundToInt())
    }

    private fun Bitmap.cropTo(region: CropRegion): Bitmap {
        val x = (region.left * width).roundToInt().coerceIn(0, width - 1)
        val y = (region.top * height).roundToInt().coerceIn(0, height - 1)
        val w = (region.width * width).roundToInt().coerceIn(1, width - x)
        val h = (region.height * height).roundToInt().coerceIn(1, height - y)
        return Bitmap.createBitmap(this, x, y, w, h)
    }

    private companion object {
        const val JPEG_QUALITY = 88
    }
}
