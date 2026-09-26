package org.opensources.umai.planning

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opensources.umai.planning.data.DeviceBarcodePictures

/**
 * Reads a real barcode picture the way the app does, with the phone's image
 * decoder. Skipped without the picture, which is pushed with adb:
 *
 * ```
 * adb push "CLASSIC' Jambon Beurre – BON APP' – 125 g.png" /sdcard/Android/data/org.opensources.umai.debug/files/labels/barcode.png
 * ```
 */
@RunWith(AndroidJUnit4::class)
class BarcodePicturesDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theBarcodeOfAProductPageIsRead() = runBlocking {
        val picture = context.getExternalFilesDir("labels")?.resolve("barcode.png")
        assumeTrue("barcode.png is not on the phone", picture?.isFile == true)

        val barcode = DeviceBarcodePictures(context).read(Uri.fromFile(picture).toString())

        assertEquals("3560070565313", barcode)
    }
}
