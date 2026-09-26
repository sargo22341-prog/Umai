package org.opensources.umai.planning.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BarcodesTest {

    @Test
    fun `the barcodes of food are kept, spaces left out`() {
        assertEquals("3560070565313", Barcodes.normalize("3560070565313"))
        assertEquals("5449000000996", Barcodes.normalize(" 5 449000 000996 "))
        assertEquals("96385074", Barcodes.normalize("9638-5074"))
    }

    @Test
    fun `a mistyped digit or a wrong length is no barcode`() {
        assertNull(Barcodes.normalize("3560070565314"))
        assertNull(Barcodes.normalize("356007056531"))
        assertNull(Barcodes.normalize("35600705653a3"))
        assertNull(Barcodes.normalize(""))
    }
}
