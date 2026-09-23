package org.opensources.umai.core.network

import mockwebserver3.MockResponse
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

    private fun client(instanceHost: String) = MealieClientFactory.okHttpClient(
        tokenProvider = { "secret-token" },
        unauthorizedListener = { signedOut++ },
        instanceHost = instanceHost,
    )

    private fun get(instanceHost: String, code: Int = 200) {
        fake.server.enqueue(MockResponse.Builder().code(code).build())
        client(instanceHost).newCall(Request.Builder().url(fake.baseUrl).build()).execute().close()
    }

    @Test
    fun `the instance receives the token`() {
        get(instanceHost = fake.baseUrl.host)

        assertEquals("Bearer secret-token", fake.takeRequest().headers["Authorization"])
    }

    @Test
    fun `another website never sees it`() {
        get(instanceHost = "mealie.example")

        assertNull(fake.takeRequest().headers["Authorization"])
    }

    @Test
    fun `a 401 from another website does not sign the user out`() {
        get(instanceHost = "mealie.example", code = 401)
        assertEquals(0, signedOut)

        get(instanceHost = fake.baseUrl.host, code = 401)
        assertEquals(1, signedOut)
    }
}
