package org.opensources.umai.core.network

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.HttpUrl
import org.opensources.umai.core.network.api.MealieApi

/**
 * A Mealie instance backed by MockWebServer, so repositories can be tested
 * against real HTTP without touching a server.
 */
class FakeMealieServer {

    val server = MockWebServer().apply { start() }

    val baseUrl: HttpUrl get() = server.url("/")

    fun api(token: String? = "test-token"): MealieApi {
        val client = MealieClientFactory.okHttpClient(
            tokenProvider = { token },
            acceptLanguage = { "fr-FR" },
        )
        return MealieClientFactory.api(baseUrl.toString(), client)
    }

    fun enqueueJson(body: String, code: Int = 200) {
        server.enqueue(
            MockResponse.Builder()
                .code(code)
                .setHeader("Content-Type", "application/json")
                .body(body)
                .build(),
        )
    }

    fun enqueueError(code: Int, body: String = """{"detail":"nope"}""") = enqueueJson(body, code)

    fun takeRequest(): RecordedRequest = server.takeRequest()

    fun shutdown() = server.close()
}

/** Query parameters of a recorded request, for readable assertions. */
fun RecordedRequest.query(name: String): String? = url.queryParameter(name)

fun RecordedRequest.queryValues(name: String): List<String> = url.queryParameterValues(name).filterNotNull()
