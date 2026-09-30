package org.opensources.umai.core.session

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * The session as stored on the phone, token sealed by the keystore. Each test
 * has a file and a key of its own: the session of the installed app is never
 * touched.
 */
@RunWith(AndroidJUnit4::class)
class SessionStoreTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val file = File(context.cacheDir, "test-session-${UUID.randomUUID()}.preferences_pb")
    private val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
    private val vault = SecretVault(keyAlias = "umai.test.session")
    private val store = SessionStore(dataStore, vault)

    private val session = ServerSession(
        baseUrl = "https://mealie.example/",
        token = "secret-token",
        authMode = AuthMode.PASSWORD,
        username = "marie",
        userId = "u1",
        userDisplayName = "Marie",
        serverVersion = "v3.0.0",
        isAdmin = false,
        avatarCacheKey = null,
    )

    @After
    fun tearDown() {
        scope.cancel()
        vault.clear()
        val _ = file.delete()
    }

    @Test
    fun aSavedSessionIsReadBackWithItsTokenSealedOnDisk() = runBlocking {
        store.save(session)

        val stored = store.stored.first()
        assertEquals("secret-token", stored?.token)
        assertEquals("marie", stored?.username)
        assertFalse(stored?.tokenRejected ?: true)
        assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains("secret-token"))
    }

    @Test
    fun aRejectedTokenIsForgottenButTheInstanceKept() = runBlocking {
        store.save(session)
        store.markTokenRejected()

        val stored = store.stored.first()
        assertNull(stored?.token)
        assertTrue(stored?.tokenRejected ?: false)
        assertEquals("https://mealie.example/", stored?.baseUrl)
        // A refresh arriving after the rejection does not bring the token back.
        store.updateToken("https://mealie.example/", "late-token")
        assertNull(store.stored.first()?.token)
    }

    @Test
    fun aRefreshedTokenOnlyLandsOnTheSessionItWasIssuedFor() = runBlocking {
        store.save(session)

        store.updateToken("https://other.example/", "other-token")
        assertEquals("secret-token", store.stored.first()?.token)

        store.updateToken("https://mealie.example/", "fresh-token")
        assertEquals("fresh-token", store.stored.first()?.token)
    }

    @Test
    fun signingOutForgetsTheSessionButNotWhereTheLocalDataCameFrom() = runBlocking {
        assertFalse(store.claimLocalData("a|u1"))
        store.save(session)
        store.clear()

        assertNull(store.stored.first())
        assertFalse(store.claimLocalData("a|u1"))
        assertTrue(store.claimLocalData("b|u1"))
    }

    @Test
    fun theManagerFollowsTheStoredSession() = runBlocking {
        val manager = SessionManager(store, scope, acceptLanguage = { "fr" })
        withTimeout(TIMEOUT_MS) { manager.state.first { it == SessionState.NotConfigured } }

        manager.activate(session)
        withTimeout(TIMEOUT_MS) { manager.state.first { it is SessionState.Active } }
        assertEquals("https://mealie.example/|u1", manager.instanceKey())
        val api = manager.api()
        assertSame(api, manager.api())

        manager.activate(session.copy(baseUrl = "https://other.example/"))
        // Another instance gets a client of its own.
        assertNotSame(api, manager.api())

        manager.markExpired()
        withTimeout(TIMEOUT_MS) { manager.state.first { it is SessionState.Expired } }
        assertNull(manager.api())

        manager.signOut()
        withTimeout(TIMEOUT_MS) { manager.state.first { it == SessionState.NotConfigured } }
        assertNull(manager.instanceKey())
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
