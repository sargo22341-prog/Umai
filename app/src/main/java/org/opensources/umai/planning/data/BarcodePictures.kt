package org.opensources.umai.planning.data

import android.content.Context
import android.graphics.ImageDecoder
import androidx.core.net.toUri
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opensources.umai.core.image.CameraCapture
import org.opensources.umai.planning.domain.Barcodes
import kotlin.math.max
import kotlin.math.roundToInt

/** The photo of a barcode, only read, never kept. */
fun interface BarcodePictures {
    /** The barcode on the picture at [sourceUri], `null` when none can be read. */
    suspend fun read(sourceUri: String): String?
}

/**
 * Opens the picture, applying its EXIF orientation, scaled down to a size the
 * bars stay sharp at, and reads it with [BarcodeDecoder]. A photo taken with
 * the camera for this is deleted as soon as it is read.
 */
class DeviceBarcodePictures(context: Context) : BarcodePictures {

    private val appContext = context.applicationContext

    override suspend fun read(sourceUri: String): String? = withContext(Dispatchers.Default) {
        try {
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
        } finally {
            CameraCapture.clear(appContext)
        }
    }

    private companion object {
        /** A barcode filling a third of a photo keeps some 700 pixels across its bars. */
        const val MAX_SIDE = 2048
    }
}

/** Finds the barcode of a food product in the pixels of a picture. */
object BarcodeDecoder {

    private val hints = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.UPC_A),
        DecodeHintType.TRY_HARDER to true,
    )

    /**
     * The barcode in [pixels], ARGB rows of [width]; `null` when none is read.
     * A barcode photographed upright, bars lying down, is read too.
     */
    fun decode(pixels: IntArray, width: Int, height: Int): String? =
        decodeOnce(pixels, width, height) ?: decodeOnce(rotated(pixels, width, height), height, width)

    private fun decodeOnce(pixels: IntArray, width: Int, height: Int): String? {
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(width, height, pixels)))
        val result = try {
            MultiFormatReader().decode(bitmap, hints)
        } catch (_: NotFoundException) {
            return null
        }
        // Open Food Facts files a UPC-A under its EAN-13, the same digits after a 0.
        val text = if (result.barcodeFormat == BarcodeFormat.UPC_A) "0${result.text}" else result.text
        return Barcodes.normalize(text)
    }

    /** [pixels] turned a quarter clockwise. */
    private fun rotated(pixels: IntArray, width: Int, height: Int): IntArray {
        val turned = IntArray(pixels.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                turned[x * height + (height - 1 - y)] = pixels[y * width + x]
            }
        }
        return turned
    }
}
