package org.opensources.umai.core.network

import mockwebserver3.MockResponse
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * The client of an instance also loads pictures and videos from other sites:
 * only the instance may see the token, or sign the user out with a 401.
 */
class InstanceCredentialsTest {

    private lateinit var fake: FakeMealieServer
    private var signedOut = 0

    @Before
    fun setUp() {
        fake = FakeMealieServer()
    }

    @After
    fun tearDown() = fake.shutdown()

    private fun client(instance: HttpUrl) = MealieClientFactory.okHttpClient(
        instance = instance,
        tokenProvider = { "secret-token" },
        unauthorizedListener = { signedOut++ },
    )

    private fun get(instance: HttpUrl, code: Int = 200) {
        fake.server.enqueue(MockResponse.Builder().code(code).build())
        client(instance).newCall(Request.Builder().url(fake.baseUrl).build()).execute().close()
    }

    @Test
    fun `the instance receives the token`() {
        get(instance = fake.baseUrl)

        assertEquals("Bearer secret-token", fake.takeRequest().headers["Authorization"])
    }

    @Test
    fun `another website never sees it`() {
        get(instance = "https://mealie.example/".toHttpUrl())

        assertNull(fake.takeRequest().headers["Authorization"])
    }

    @Test
    fun `the same host on another scheme or port never sees it`() {
        // An HTTPS instance linking to itself in plain HTTP, or behind another port.
        get(instance = fake.baseUrl.newBuilder().scheme("https").build())
        assertNull(fake.takeRequest().headers["Authorization"])

        get(instance = fake.baseUrl.newBuilder().port(fake.baseUrl.port + 1).build())
        assertNull(fake.takeRequest().headers["Authorization"])
    }

    @Test
    fun `without an instance no request carries the token`() {
        fake.server.enqueue(MockResponse.Builder().build())
        MealieClientFactory.okHttpClient(instance = null, tokenProvider = { "secret-token" })
            .newCall(Request.Builder().url(fake.baseUrl).build()).execute().close()

        assertNull(fake.takeRequest().headers["Authorization"])
    }

    @Test
    fun `a 401 from another website does not sign the user out`() {
        get(instance = "https://mealie.example/".toHttpUrl(), code = 401)
        assertEquals(0, signedOut)

        get(instance = fake.baseUrl, code = 401)
        assertEquals(1, signedOut)
    }
}
