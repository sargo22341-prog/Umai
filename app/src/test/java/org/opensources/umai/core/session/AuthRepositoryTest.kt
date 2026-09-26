package org.opensources.umai.core.session

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
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
        repository = repositoryFor(holder)
    }

    private fun repositoryFor(holder: SessionHolder) = AuthRepository(
        session = holder,
        apiFor = { baseUrl, token -> MealieClientFactory.api(baseUrl, MealieClientFactory.okHttpClient({ token })) },
        clock = { now },
    )

    private var now = 0L

    @After
    fun tearDown() = fake.shutdown()

    private val baseUrl: String get() = fake.baseUrl.toString()

    @Test
    fun `a host that is not Mealie is reported as such`() = runTest {
        fake.enqueueJson("""{"production":true,"version":"","demoStatus":false}""")

        val result = repository.connectWithApiToken(baseUrl, "long-lived-token")

        assertEquals(NetworkError.NotMealie, (result as AuthRepository.ConnectResult.Failure).error)
        assertEquals(1, fake.server.requestCount)
    }

    @Test
    fun `an unreachable host is reported before any credential is sent`() = runTest {
        fake.shutdown()
        val result = repository.connectWithPassword(baseUrl, "hiroo", "hunter2")
        assertEquals(NetworkError.Unreachable, (result as AuthRepository.ConnectResult.Failure).error)
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
    fun `a password session gets a fresh token, then waits before asking again`() = runTest {
        val holder = signedIn(AuthMode.PASSWORD)
        val repository = repositoryFor(holder)
        fake.enqueueJson("""{"access_token":"fresh-token"}""")

        assertTrue(repository.refreshIfDue())
        assertEquals("fresh-token", holder.activeSession()?.token)
        val refresh = fake.takeRequest()
        assertEquals("POST", refresh.method)
        assertEquals("/api/auth/refresh", refresh.url.encodedPath)

        now += 60 * 60 * 1000L
        assertFalse(repository.refreshIfDue())
        assertEquals(1, fake.server.requestCount)

        now += 12 * 60 * 60 * 1000L
        fake.enqueueJson("""{"access_token":"fresher-token"}""")
        assertTrue(repository.refreshIfDue())
        assertEquals("fresher-token", holder.activeSession()?.token)
    }

    @Test
    fun `an API token is never refreshed`() = runTest {
        val repository = repositoryFor(signedIn(AuthMode.API_TOKEN))

        assertFalse(repository.refreshIfDue())
        assertEquals(0, fake.server.requestCount)
    }

    @Test
    fun `a failed refresh keeps the token and is tried again next time`() = runTest {
        val holder = signedIn(AuthMode.PASSWORD)
        val repository = repositoryFor(holder)
        fake.enqueueError(500)
        fake.enqueueJson("""{"access_token":"fresh-token"}""")

        assertFalse(repository.refreshIfDue())
        assertEquals("old-token", holder.activeSession()?.token)
        assertTrue(repository.refreshIfDue())
        assertEquals("fresh-token", holder.activeSession()?.token)
    }

    @Test
    fun `a refreshed token never lands on a session that changed meanwhile`() = runTest {
        val holder = FakeSessionHolder(session(AuthMode.PASSWORD).copy(token = "another-token"))

        assertFalse(holder.replaceToken(previous = "old-token", token = "fresh-token"))
        assertEquals("another-token", holder.activeSession()?.token)
    }

    private fun session(mode: AuthMode) = ServerSession(
        baseUrl = baseUrl,
        token = "old-token",
        authMode = mode,
        username = "hiroo",
        userId = "1",
        userDisplayName = "hiroo",
        serverVersion = "v3.27.0",
    )

    private fun signedIn(mode: AuthMode) = FakeSessionHolder(
        session = session(mode),
        api = fake.api("old-token"),
    )

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
