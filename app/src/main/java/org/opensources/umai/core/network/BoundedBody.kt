package org.opensources.umai.core.network

import okhttp3.ResponseBody
import java.io.IOException

/** A response longer than the reader accepts: it was not read to its end. */
class ResponseTooLargeException(maxBytes: Long) : IOException("Response longer than $maxBytes bytes")

/**
 * The body, as long as it holds at most [maxBytes]: never more than that is
 * kept in memory, whatever length the server announces, or none at all.
 *
 * @throws ResponseTooLargeException when the body is longer.
 */
fun ResponseBody.bytesUpTo(maxBytes: Long): ByteArray {
    val source = source()
    // Buffers one byte past the limit at most, and answers whether it got there.
    if (source.request(maxBytes + 1)) throw ResponseTooLargeException(maxBytes)
    return source.buffer.readByteArray()
}

/** The body as text, in its declared charset or UTF-8; see [bytesUpTo]. */
fun ResponseBody.stringUpTo(maxBytes: Long): String =
    String(bytesUpTo(maxBytes), contentType()?.charset() ?: Charsets.UTF_8)
