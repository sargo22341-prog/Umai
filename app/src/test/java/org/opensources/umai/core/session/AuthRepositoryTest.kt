package org.opensources.umai.core.session

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.FakeMealieServer
import org.opensources.umai.core.network.MealieClientFactory
import org.opensources.umai.core.network.NetworkError

class AuthRepositoryTest {

    private lateinit var fake: FakeMealieServer
    private lateinit var repository: AuthRepository
    private lateinit var holder: FakeSessionHolder

    @Before
    fun setUp() {
        fake = FakeMealieServer()
        holder = FakeSessionHolder()
        repository = AuthRepository(
            session = holder,
            clientBuilder = { token ->
                val client = MealieClientFactory.okHttpClient({ token })
                client to { baseUrl: String -> MealieClientFactory.api(baseUrl, client) }
            },
        )
    }

    @After
    fun tearDown() = fake.shutdown()

    private val baseUrl: String get() = fake.baseUrl.toString()

    @Test
    fun `probing a Mealie instance returns its version`() = runTest {
        fake.enqueueJson(APP_INFO)
        val result = repository.probe(baseUrl)
        assertEquals("v3.27.0", (result as ApiResult.Success).value)
        assertEquals("/api/app/about", fake.takeRequest().url.encodedPath)
    }

    @Test
    fun `a host that is not Mealie is reported as such`() = runTest {
        fake.enqueueJson("""{"production":true,"version":"","demoStatus":false}""")
        val result = repository.probe(baseUrl)
        assertEquals(NetworkError.NotMealie, (result as ApiResult.Failure).error)
    }

    @Test
    fun `an unreachable host is reported before any credential is sent`() = runTest {
        fake.shutdown()
        val result = repository.probe(baseUrl)
        assertTrue(result is ApiResult.Failure)
    }

    @Test
    fun `a successful password sign-in builds a session`() = runTest {
        fake.enqueueJson(APP_INFO)
        fake.enqueueJson("""{"access_token":"secret-token","token_type":"bearer"}""")
        fake.enqueueJson(USER)

        val result = repository.connectWithPassword(baseUrl, "hiroo", "hunter2")

        assertTrue(result is AuthRepository.ConnectResult.Success)
        val session = (result as AuthRepository.ConnectResult.Success).session
        assertEquals("secret-token", session.token)
        assertEquals(AuthMode.PASSWORD, session.authMode)
        assertEquals("hiroo", session.username)
        assertEquals("223b00e4-06ec-4544-89e4-e0598560ad47", session.userId)
        assertEquals("v3.27.0", session.serverVersion)
    }

    @Test
    fun `the password is posted as a form to the documented endpoint`() = runTest {
        fake.enqueueJson(APP_INFO)
        fake.enqueueJson("""{"access_token":"secret-token"}""")
        fake.enqueueJson(USER)

        repository.connectWithPassword(baseUrl, "hiroo", "hunter2")

        fake.takeRequest()
        val login = fake.takeRequest()
        assertEquals("POST", login.method)
        assertEquals("/api/auth/token", login.url.encodedPath)
        val body = login.body?.utf8().orEmpty()
        assertTrue(body.contains("username=hiroo"))
        assertTrue(body.contains("remember_me=true"))
    }

    @Test
    fun `wrong credentials are reported as unauthorized`() = runTest {
        fake.enqueueJson(APP_INFO)
        fake.enqueueError(401)

        val result = repository.connectWithPassword(baseUrl, "hiroo", "wrong")

        assertEquals(
            NetworkError.Unauthorized,
            (result as AuthRepository.ConnectResult.Failure).error,
        )
    }

    @Test
    fun `an instance with password login disabled says so`() = runTest {
        fake.enqueueJson(APP_INFO.replace(""""allowPasswordLogin":true""", """"allowPasswordLogin":false"""))

        val result = repository.connectWithPassword(baseUrl, "hiroo", "hunter2")

        assertEquals(AuthRepository.ConnectResult.PasswordLoginDisabled, result)
    }

    @Test
    fun `an API token sign-in resolves the user it belongs to`() = runTest {
        fake.enqueueJson(APP_INFO)
        fake.enqueueJson(USER)

        val result = repository.connectWithApiToken(baseUrl, "long-lived-token")

        val session = (result as AuthRepository.ConnectResult.Success).session
        assertEquals(AuthMode.API_TOKEN, session.authMode)
        assertEquals("long-lived-token", session.token)
        assertEquals("hiroo", session.username)
    }

    @Test
    fun `a rejected API token is reported as unauthorized`() = runTest {
        fake.enqueueJson(APP_INFO)
        fake.enqueueError(401)

        val result = repository.connectWithApiToken(baseUrl, "revoked")

        assertEquals(
            NetworkError.Unauthorized,
            (result as AuthRepository.ConnectResult.Failure).error,
        )
    }

    @Test
    fun `the session never prints its token`() {
        val session = ServerSession(
            baseUrl = "https://mealie.lan/",
            token = "super-secret",
            authMode = AuthMode.API_TOKEN,
            username = "hiroo",
            userId = "1",
            userDisplayName = "hiroo",
            serverVersion = "v3.27.0",
        )
        val text = session.toString()
        assertFalse(text.contains("super-secret"))
        assertTrue(text.contains("token=***"))
    }

    @Test
    fun `adopting a session hands it to the session holder`() = runTest {
        fake.enqueueJson(APP_INFO)
        fake.enqueueJson(USER)
        val result = repository.connectWithApiToken(baseUrl, "long-lived-token")

        repository.adopt((result as AuthRepository.ConnectResult.Success).session)

        assertEquals(1, holder.activated.size)
        assertEquals("long-lived-token", holder.activeSession()?.token)
    }

    @Test
    fun `signing out forgets the session even when the server call fails`() = runTest {
        repository.signOut()
        assertTrue(holder.signedOut)
    }

    @Test
    fun `url normalization is shared with the setup screen`() {
        assertEquals("https://mealie.ndd.custom/", AuthRepository.normalize("mealie.ndd.custom"))
        assertEquals(null, AuthRepository.normalize("   "))
    }

    private companion object {
        const val APP_INFO = """
            {"production":true,"version":"v3.27.0","demoStatus":false,
             "allowSignup":false,"allowPasswordLogin":true,"enableOidc":false,
             "oidcRedirect":false,"oidcProviderName":"OAuth","tokenTime":48}
        """

        const val USER = """
            {"id":"223b00e4-06ec-4544-89e4-e0598560ad47","username":"hiroo","fullName":"hiroo",
             "email":"hiroo@here.me","admin":true,"group":"Home","household":"Family",
             "groupId":"g","groupSlug":"home","householdId":"h","householdSlug":"family"}
        """
    }
}
