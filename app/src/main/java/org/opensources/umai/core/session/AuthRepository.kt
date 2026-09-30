package org.opensources.umai.core.session

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.MealieClientFactory
import org.opensources.umai.core.network.MealieUrl
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.network.apiCall
import org.opensources.umai.core.network.api.MealieApi

/**
 * Connects to a candidate instance and produces a [ServerSession].
 *
 * Runs on a throwaway HTTP stack so a failed attempt never disturbs the client
 * of the instance that is currently configured, and a 401 on wrong credentials
 * never invalidates the running session.
 */
class AuthRepository(
    private val session: SessionHolder,
    /** The API of the instance at `baseUrl`, sending `token` when there is one. */
    private val apiFor: (baseUrl: String, token: String?) -> MealieApi = ::defaultApi,
    /** Monotonic milliseconds, which space out the token refreshes. */
    private val clock: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
) {

    private val refreshing = Mutex()
    private var lastRefresh: Long? = null

    sealed interface ConnectResult {
        data class Success(val session: ServerSession) : ConnectResult
        data class Failure(val error: NetworkError) : ConnectResult

        /** The host answered but the password grant is disabled server-side. */
        data object PasswordLoginDisabled : ConnectResult
    }

    suspend fun connectWithPassword(
        baseUrl: String,
        username: String,
        password: String,
    ): ConnectResult {
        val anonymous = apiFor(baseUrl, null)

        val info = apiCall { anonymous.appInfo() }
        if (info is ApiResult.Failure) return ConnectResult.Failure(info.error)
        val appInfo = (info as ApiResult.Success).value
        if (appInfo.version.isBlank()) return ConnectResult.Failure(NetworkError.NotMealie)
        if (!appInfo.allowPasswordLogin) return ConnectResult.PasswordLoginDisabled

        return when (val token = apiCall { anonymous.login(username, password, rememberMe = true) }) {
            is ApiResult.Failure -> ConnectResult.Failure(token.error)
            is ApiResult.Success -> finish(
                baseUrl = baseUrl,
                token = token.value.accessToken,
                authMode = AuthMode.PASSWORD,
                username = username,
                serverVersion = appInfo.version,
            )
        }
    }

    suspend fun connectWithApiToken(baseUrl: String, apiToken: String): ConnectResult {
        val info = apiCall { apiFor(baseUrl, null).appInfo() }
        if (info is ApiResult.Failure) return ConnectResult.Failure(info.error)
        val appInfo = (info as ApiResult.Success).value
        if (appInfo.version.isBlank()) return ConnectResult.Failure(NetworkError.NotMealie)

        return finish(
            baseUrl = baseUrl,
            token = apiToken,
            authMode = AuthMode.API_TOKEN,
            username = null,
            serverVersion = appInfo.version,
        )
    }

    /** Persists the session and makes it the active one. */
    suspend fun adopt(newSession: ServerSession) = session.activate(newSession)

    /**
     * Forgets the session on the device. Mealie has nothing to call: its
     * logout only clears a browser cookie, and a token stays valid until it
     * expires (an API token until it is deleted from the user's profile).
     */
    suspend fun signOut() = session.signOut()

    /**
     * Exchanges the token of a password session for a fresh one, at most once
     * every [REFRESH_INTERVAL_MS]. Asked each time the app comes to the
     * foreground, it keeps a session in use from ever expiring; Mealie carries
     * the remember-me choice over to the new token. An API token has no expiry
     * the app could push back. Answers whether the token was replaced.
     */
    suspend fun refreshIfDue(): Boolean = refreshing.withLock {
        val now = clock()
        if (lastRefresh?.let { now - it < REFRESH_INTERVAL_MS } == true) return false
        val current = session.activeSession() ?: return false
        if (current.authMode != AuthMode.PASSWORD) return false
        val api = session.api() ?: return false
        when (val result = apiCall { api.refreshToken() }) {
            is ApiResult.Failure -> false
            is ApiResult.Success -> {
                lastRefresh = now
                session.replaceToken(previous = current.token, token = result.value.accessToken)
            }
        }
    }

    private suspend fun finish(
        baseUrl: String,
        token: String,
        authMode: AuthMode,
        username: String?,
        serverVersion: String,
    ): ConnectResult {
        return when (val user = apiCall { apiFor(baseUrl, token).currentUser() }) {
            is ApiResult.Failure -> ConnectResult.Failure(user.error)
            is ApiResult.Success -> ConnectResult.Success(
                ServerSession(
                    baseUrl = baseUrl,
                    token = token,
                    authMode = authMode,
                    username = username ?: user.value.username,
                    userId = user.value.id.takeIf { it.isNotBlank() },
                    userDisplayName = user.value.fullName ?: user.value.username,
                    serverVersion = serverVersion,
                    isAdmin = user.value.admin,
                    avatarCacheKey = user.value.cacheKey.takeIf { it.isNotBlank() },
                ),
            )
        }
    }

    companion object {
        /** Normalizes user input and returns the canonical base URL, or `null` if invalid. */
        fun normalize(rawUrl: String): String? =
            (MealieUrl.parse(rawUrl) as? MealieUrl.Result.Valid)?.let { MealieUrl.canonical(it.url) }

        /** Well within the shortest token Mealie hands out (`TOKEN_TIME`, 48 hours by default). */
        private const val REFRESH_INTERVAL_MS = 12 * 60 * 60 * 1000L
        private const val NANOS_PER_MILLI = 1_000_000L

        private fun defaultApi(baseUrl: String, token: String?): MealieApi {
            val client = MealieClientFactory.okHttpClient(instance = baseUrl.toHttpUrlOrNull(), tokenProvider = { token })
            return MealieClientFactory.api(baseUrl, client)
        }
    }
}
