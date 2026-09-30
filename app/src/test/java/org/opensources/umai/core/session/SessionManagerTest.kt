package org.opensources.umai.core.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

/**
 * The session as read from its store. What needs the Android keystore — a
 * token sealed and unsealed — is tested on the device (androidTest).
 */
class SessionManagerTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val file by lazy { PreferenceDataStoreFactory.create(scope = scope) { folder.root.resolve("session.preferences_pb") } }

    /** A store whose file cannot be read the first [failures] times. */
    private inner class Unreadable(var failures: Int) : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow {
            if (failures > 0) {
                failures--
                throw IOException("The disk refused the read")
            }
            emitAll(file.data)
        }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = file.updateData(transform)
    }

    private fun manager(store: DataStore<Preferences>) = SessionManager(SessionStore(store), scope, acceptLanguage = { "fr" })

    /** Waits for [expected]; a test may only need to know it came. */
    @IgnorableReturnValue
    private suspend fun SessionManager.awaitState(expected: SessionState): SessionState =
        withTimeout(TIMEOUT_MS) { state.first { it == expected } }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `nothing stored is nothing set up`() = runBlocking {
        val manager = manager(file)

        manager.awaitState(SessionState.NotConfigured)
        assertNull(manager.api())
        assertNull(manager.instanceKey())
    }

    @Test
    fun `a store that cannot be read is not taken for nothing set up, and is read again on demand`() = runBlocking<Unit> {
        val manager = manager(Unreadable(failures = 1))

        manager.awaitState(SessionState.Unreadable)
        assertNull(manager.api())

        manager.retryRead()
        manager.awaitState(SessionState.NotConfigured)
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
