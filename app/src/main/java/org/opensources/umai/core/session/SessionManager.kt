package org.opensources.umai.core.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.opensources.umai.core.network.MealieClientFactory
import org.opensources.umai.core.network.TokenProvider
import org.opensources.umai.core.network.api.MealieApi

/**
 * Single source of truth for "which Mealie instance are we talking to, and are
 * we still authenticated?".
 *
 * The HTTP stack is rebuilt only when the base URL changes; the token is read
 * through a provider on every request, so a silent refresh does not invalidate
 * connection pools or the image cache.
 */
class SessionManager(
    private val store: SessionStore,
    private val scope: CoroutineScope,
    private val acceptLanguage: () -> String,
) : SessionHolder {

    private val current = MutableStateFlow<ServerSession?>(null)
    private val tokenProvider = TokenProvider { current.value?.token }

    @Volatile
    private var clients: Clients? = null

    val state: StateFlow<SessionState> = store.stored
        .onEach { stored -> current.value = stored?.toSession() }
        .map { stored ->
            when {
                stored == null -> SessionState.NotConfigured
                stored.token.isNullOrBlank() || stored.tokenRejected ->
                    SessionState.Expired(stored.baseUrl, stored.username)
                else -> SessionState.Active(stored.toSession()!!)
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, SessionState.Loading)

    /** The API bound to the configured instance, or `null` when none is set up. */
    override fun api(): MealieApi? = clientsFor(current.value?.baseUrl)?.api

    /** Shared OkHttp client, reused by Coil so images carry the same auth header. */
    fun imageClient(): OkHttpClient? = clientsFor(current.value?.baseUrl)?.http

    override fun activeSession(): ServerSession? = current.value

    fun baseUrl(): String? = current.value?.baseUrl

    override suspend fun activate(session: ServerSession) {
        current.value = session
        store.save(session)
    }

    override suspend fun updateToken(token: String) {
        current.value = current.value?.copy(token = token)
        store.updateToken(token)
    }

    /**
     * Re-reads the identity carried by the session after the user edited their
     * Mealie profile, so the avatar shown in the navigation bar follows.
     */
    suspend fun updateIdentity(displayName: String?, isAdmin: Boolean, avatarCacheKey: String?) {
        val updated = current.value?.copy(
            userDisplayName = displayName,
            isAdmin = isAdmin,
            avatarCacheKey = avatarCacheKey,
        ) ?: return
        current.value = updated
        store.save(updated)
    }

    override suspend fun signOut() {
        current.value = null
        clients = null
        store.clear()
    }

    /** Keeps the instance URL so the user only has to re-enter credentials. */
    suspend fun markExpired() {
        current.value = null
        store.markTokenRejected()
    }

    private fun onUnauthorized() {
        scope.launch { markExpired() }
    }

    private fun clientsFor(baseUrl: String?): Clients? {
        if (baseUrl.isNullOrBlank()) return null
        clients?.let { if (it.baseUrl == baseUrl) return it }
        return synchronized(this) {
            clients?.takeIf { it.baseUrl == baseUrl } ?: build(baseUrl).also { clients = it }
        }
    }

    private fun build(baseUrl: String): Clients {
        val http = MealieClientFactory.okHttpClient(
            tokenProvider = tokenProvider,
            unauthorizedListener = { onUnauthorized() },
            acceptLanguage = acceptLanguage,
        )
        return Clients(baseUrl, http, MealieClientFactory.api(baseUrl, http))
    }

    private fun SessionStore.StoredSession.toSession(): ServerSession? {
        val value = token ?: return null
        return ServerSession(
            baseUrl = baseUrl,
            token = value,
            authMode = authMode,
            username = username,
            userId = userId,
            userDisplayName = userDisplayName,
            serverVersion = serverVersion,
            isAdmin = isAdmin,
            avatarCacheKey = avatarCacheKey,
        )
    }

    private class Clients(val baseUrl: String, val http: OkHttpClient, val api: MealieApi)
}
