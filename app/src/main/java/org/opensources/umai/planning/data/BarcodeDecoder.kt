package org.opensources.umai.planning.data

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import org.opensources.umai.planning.domain.Barcodes

/** Finds the barcode of a food product in the pixels of a picture or of a camera frame. */
object BarcodeDecoder {

    private val hints = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.UPC_A),
        DecodeHintType.TRY_HARDER to true,
    )

    /** The barcode in [pixels], ARGB rows of [width]; `null` when none is read. */
    fun decode(pixels: IntArray, width: Int, height: Int): String? =
        decodeLuminance(RGBLuminanceSource(width, height, pixels).matrix, width, height)

    /**
     * The barcode in [luminance], one byte per pixel in rows of [width]; `null`
     * when none is read. Bars standing or lying down are both read.
     */
    fun decodeLuminance(luminance: ByteArray, width: Int, height: Int): String? =
        decodeOnce(luminance, width, height) ?: decodeOnce(rotated(luminance, width, height), height, width)

    private fun decodeOnce(luminance: ByteArray, width: Int, height: Int): String? {
        val source = PlanarYUVLuminanceSource(luminance, width, height, 0, 0, width, height, false)
        val result = try {
            MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source)), hints)
        } catch (_: NotFoundException) {
            return null
        }
        // Open Food Facts files a UPC-A under its EAN-13, the same digits after a 0.
        val text = if (result.barcodeFormat == BarcodeFormat.UPC_A) "0${result.text}" else result.text
        return Barcodes.normalize(text)
    }

    /** [luminance] turned a quarter clockwise. */
    private fun rotated(luminance: ByteArray, width: Int, height: Int): ByteArray {
        val turned = ByteArray(luminance.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                turned[x * height + (height - 1 - y)] = luminance[y * width + x]
            }
        }
        return turned
    }
}
