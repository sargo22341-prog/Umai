package org.opensources.umai.core.session

import org.opensources.umai.core.network.api.MealieApi

/** In-memory [SessionHolder] for tests: no DataStore, no keystore. */
class FakeSessionHolder(
    private var session: ServerSession? = null,
    private val api: MealieApi? = null,
) : SessionHolder {

    var signedOut: Boolean = false
        private set

    val activated: MutableList<ServerSession> = mutableListOf()

    override fun activeSession(): ServerSession? = session

    override fun api(): MealieApi? = api

    override suspend fun activate(session: ServerSession) {
        this.session = session
        activated += session
    }

    override suspend fun updateToken(token: String) {
        session = session?.copy(token = token)
    }

    override suspend fun signOut() {
        session = null
        signedOut = true
    }
}
