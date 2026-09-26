package org.opensources.umai.planning.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.opensources.umai.TestPictures

/** Reads a real barcode picture the way the app does, with the phone's image decoder. */
@RunWith(AndroidJUnit4::class)
class BarcodePicturesDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theBarcodeOfAProductPageIsRead() = runBlocking {
        val barcode = DeviceBarcodePictures(context).read(TestPictures.uriOf("barcode_jambon_beurre.png"))

        assertEquals("3560070565313", barcode)
    }
}
