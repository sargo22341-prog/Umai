package org.opensources.umai.youtube.data

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

/**
 * Makes NewPipeExtractor's requests with the app's own HTTP client, the one
 * for websites other than Mealie: it never carries Mealie credentials.
 */
internal class OkHttpDownloader(private val client: OkHttpClient) : Downloader() {

    override fun execute(request: Request): Response {
        val data = request.dataToSend()
        // OkHttp wants a body on every POST, even an empty one.
        val body = data?.toRequestBody() ?: if (request.httpMethod() == "POST") ByteArray(0).toRequestBody() else null
        val builder = okhttp3.Request.Builder()
            .method(request.httpMethod(), body)
            .url(request.url())
            .header(USER_AGENT, BROWSER_USER_AGENT)
        request.headers().forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { builder.addHeader(name, it) }
        }
        client.newCall(builder.build()).execute().use { response ->
            // YouTube answers "too many requests" with a page asking to solve a captcha.
            if (response.code == TOO_MANY_REQUESTS) throw ReCaptchaException("reCaptcha challenge requested", request.url())
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                response.body.string(),
                response.request.url.toString(),
            )
        }
    }

    private companion object {
        const val USER_AGENT = "User-Agent"
        const val TOO_MANY_REQUESTS = 429

        /** The one NewPipe itself sends: the extractor is tested against the pages YouTube serves it. */
        const val BROWSER_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
    }
}
