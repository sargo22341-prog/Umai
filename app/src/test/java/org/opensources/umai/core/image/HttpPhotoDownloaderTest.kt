package org.opensources.umai.core.image

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HttpPhotoDownloaderTest {

    private val server = MockWebServer().apply { start() }
    private val downloader = HttpPhotoDownloader(OkHttpClient())

    @After
    fun tearDown() = server.close()

    private fun download() = runBlocking { downloader.download(server.url("/photo").toString()) }

    @Test
    fun `a picture is downloaded with its type`() {
        val png = byteArrayOf(1, 2, 3)
        server.enqueue(MockResponse.Builder().setHeader("Content-Type", "image/png").body(Buffer().write(png)).build())

        val image = download()

        assertArrayEquals(png, image?.bytes)
        assertEquals("image/png", image?.mediaType)
        assertEquals("png", image?.extension)
    }

    @Test
    fun `what is not a picture, or not there, gives none`() {
        server.enqueue(MockResponse.Builder().setHeader("Content-Type", "text/html").body("<html/>").build())
        assertNull(download())

        server.enqueue(MockResponse.Builder().code(404).setHeader("Content-Type", "image/png").build())
        assertNull(download())
    }

    @Test
    fun `a picture sent without its length is refused past the limit, not loaded whole`() {
        val huge = Buffer().write(ByteArray(16 * 1024 * 1024))
        server.enqueue(MockResponse.Builder().setHeader("Content-Type", "image/jpeg").chunkedBody(huge, maxChunkSize = 1024 * 1024).build())

        assertNull(download())
    }

    @Test
    fun `an address that is not http gives none`() {
        assertNull(runBlocking { downloader.download("file:///etc/passwd") })
    }
}
