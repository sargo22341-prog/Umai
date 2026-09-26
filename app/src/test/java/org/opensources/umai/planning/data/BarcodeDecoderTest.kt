package org.opensources.umai.planning.data

import com.google.zxing.BarcodeFormat
import com.google.zxing.oned.EAN13Writer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

class BarcodeDecoderTest {

    /** The ARGB pixels of an EAN-13 drawn black on white, with a quiet zone around it. */
    private fun drawn(code: String, width: Int = 400, height: Int = 120): IntArray {
        val matrix = EAN13Writer().encode(code, BarcodeFormat.EAN_13, width, height)
        return IntArray(width * height) { index ->
            if (matrix[index % width, index / width]) BLACK else WHITE
        }
    }

    @Test
    fun `an EAN-13 is read`() {
        assertEquals("3560070565313", BarcodeDecoder.decode(drawn("3560070565313"), 400, 120))
    }

    @Test
    fun `a barcode photographed upright is read too`() {
        val width = 400
        val height = 120
        val pixels = drawn("5449000000996", width, height)
        // Turned a quarter anticlockwise: the bars lie down.
        val upright = IntArray(pixels.size) { index ->
            val x = index % height
            val y = index / height
            pixels[x * width + (width - 1 - y)]
        }

        assertEquals("5449000000996", BarcodeDecoder.decode(upright, height, width))
    }

    @Test
    fun `the barcode of a camera frame is read on its luminance, bars standing or lying`() {
        val width = 400
        val height = 120
        val pixels = drawn("3250390103745", width, height)
        val luminance = ByteArray(pixels.size) { index -> (pixels[index] and 0xFF).toByte() }
        val lying = ByteArray(luminance.size) { index ->
            val x = index % height
            val y = index / height
            luminance[x * width + (width - 1 - y)]
        }

        assertEquals("3250390103745", BarcodeDecoder.decodeLuminance(luminance, width, height))
        assertEquals("3250390103745", BarcodeDecoder.decodeLuminance(lying, height, width))
    }

    @Test
    fun `a picture without a barcode gives none`() {
        assertNull(BarcodeDecoder.decode(IntArray(300 * 200) { WHITE }, 300, 200))
    }

    /** The screenshot of a product page given as test data, at the root of the repository. */
    @Test
    fun `the barcode of the test picture is read`() {
        val file = File("..").listFiles().orEmpty().firstOrNull { it.name.startsWith("CLASSIC") && it.extension == "png" }
        assumeTrue("The test picture is not at the root of the repository", file != null)
        val image = ImageIO.read(file)
        val pixels = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)

        assertEquals("3560070565313", BarcodeDecoder.decode(pixels, image.width, image.height))
    }

    private companion object {
        const val BLACK = 0xFF000000.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
    }
}
