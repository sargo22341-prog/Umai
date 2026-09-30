package org.opensources.umai.core.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BoundedBodyTest {

    @Test
    fun `a body within the limit is read whole`() {
        assertArrayEquals(ByteArray(10) { 7 }, ByteArray(10) { 7 }.toResponseBody().bytesUpTo(10))
    }

    @Test
    fun `a longer body is refused, whatever length it announces`() {
        // No announced length, as a chunked answer: only the limit tells.
        val unannounced = Buffer().write(ByteArray(11)).asResponseBody(contentType = null, contentLength = -1)

        val _ = assertThrows(ResponseTooLargeException::class.java) { val _ = unannounced.bytesUpTo(10) }
    }

    @Test
    fun `text is read in its declared charset`() {
        val latin = "crème".toByteArray(Charsets.ISO_8859_1).toResponseBody("text/plain; charset=ISO-8859-1".toMediaType())

        assertEquals("crème", latin.stringUpTo(100))
    }

    @Test
    fun `too long an answer is an answer off the contract`() {
        assertEquals(NetworkError.InvalidResponse, NetworkErrorMapper.map(ResponseTooLargeException(10)))
    }
}
