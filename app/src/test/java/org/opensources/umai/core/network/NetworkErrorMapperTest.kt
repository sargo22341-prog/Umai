package org.opensources.umai.core.network

import kotlinx.serialization.SerializationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

class NetworkErrorMapperTest {

    @Test
    fun `dns failures are unreachable`() {
        assertEquals(NetworkError.Unreachable, NetworkErrorMapper.map(UnknownHostException("mealie.lan")))
    }

    @Test
    fun `refused connections are unreachable`() {
        assertEquals(NetworkError.Unreachable, NetworkErrorMapper.map(ConnectException("refused")))
    }

    @Test
    fun `generic io failures are unreachable`() {
        assertEquals(NetworkError.Unreachable, NetworkErrorMapper.map(IOException("broken pipe")))
    }

    @Test
    fun `socket timeouts are reported as a timeout`() {
        assertEquals(NetworkError.Timeout, NetworkErrorMapper.map(SocketTimeoutException()))
    }

    @Test
    fun `tls failures keep their own category`() {
        assertTrue(NetworkErrorMapper.map(SSLHandshakeException("bad cert")) is NetworkError.Tls)
        assertTrue(NetworkErrorMapper.map(SSLPeerUnverifiedException("no peer")) is NetworkError.Tls)
    }

    @Test
    fun `401 and 403 both mean the credentials were refused`() {
        assertEquals(NetworkError.Unauthorized, NetworkErrorMapper.map(httpException(401)))
        assertEquals(NetworkError.Unauthorized, NetworkErrorMapper.map(httpException(403)))
    }

    @Test
    fun `404 is reported as not found`() {
        assertEquals(NetworkError.NotFound, NetworkErrorMapper.map(httpException(404)))
    }

    @Test
    fun `5xx keeps the status code`() {
        assertEquals(NetworkError.Server(502), NetworkErrorMapper.map(httpException(502)))
    }

    @Test
    fun `other statuses expose the detail sent by Mealie`() {
        val error = NetworkErrorMapper.map(
            httpException(422, """{"detail":"invalid query string"}"""),
        )
        assertEquals(NetworkError.Http(422, "invalid query string"), error)
    }

    @Test
    fun `a structured detail object is unwrapped`() {
        val error = NetworkErrorMapper.map(
            httpException(400, """{"detail":{"message":"Recipe already exists"}}"""),
        )
        assertEquals(NetworkError.Http(400, "Recipe already exists"), error)
    }

    @Test
    fun `unparseable bodies are reported as invalid responses`() {
        assertEquals(
            NetworkError.InvalidResponse,
            NetworkErrorMapper.map(SerializationException("unexpected token")),
        )
    }

    @Test
    fun `retry is offered only where it can help`() {
        assertTrue(NetworkError.Unreachable.isRetryable)
        assertTrue(NetworkError.Timeout.isRetryable)
        assertTrue(NetworkError.Server(500).isRetryable)
        assertTrue(NetworkError.Http(429, null).isRetryable)
        assertFalse(NetworkError.Unauthorized.isRetryable)
        assertFalse(NetworkError.NotFound.isRetryable)
        assertFalse(NetworkError.Tls(null).isRetryable)
    }

    private fun httpException(code: Int, body: String = ""): HttpException {
        val response = Response.Builder()
            .code(code)
            .message("error")
            .protocol(Protocol.HTTP_1_1)
            .request(Request.Builder().url("https://mealie.lan/api/recipes").build())
            .build()
        return HttpException(
            retrofit2.Response.error<Any>(
                body.toResponseBody("application/json".toMediaType()),
                response,
            ),
        )
    }
}
