package org.opensources.umai.core.session

import okhttp3.OkHttpClient
import org.opensources.umai.core.network.ApiResult
import org.opensources.umai.core.network.MealieClientFactory
import org.opensources.umai.core.network.MealieUrl
import org.opensources.umai.core.network.NetworkError
import org.opensources.umai.core.network.TokenProvider
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
    private val clientBuilder: (String?) -> Pair<OkHttpClient, (String) -> MealieApi> = ::defaultClient,
) {

    sealed interface ConnectResult {
        data class Success(val session: ServerSession) : ConnectResult
        data class Failure(val error: NetworkError) : ConnectResult

        /** The host answered but the password grant is disabled server-side. */
        data object PasswordLoginDisabled : ConnectResult
    }

    /** Checks that the address really is a Mealie instance and returns its version. */
    suspend fun probe(baseUrl: String): ApiResult<String> {
        val (_, apiFor) = clientBuilder(null)
        return apiCall { apiFor(baseUrl).appInfo() }.let { result ->
            when (result) {
                is ApiResult.Success ->
                    if (result.value.version.isBlank()) ApiResult.Failure(NetworkError.NotMealie)
                    else ApiResult.Success(result.value.version)
                is ApiResult.Failure -> result
            }
        }
    }

    suspend fun connectWithPassword(
        baseUrl: String,
        username: String,
        password: String,
    ): ConnectResult {
        val (_, apiFor) = clientBuilder(null)
        val anonymous = apiFor(baseUrl)

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
        val (_, apiFor) = clientBuilder(null)
        val info = apiCall { apiFor(baseUrl).appInfo() }
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

    suspend fun signOut() {
        // Best effort: the server invalidates password sessions, API tokens stay valid.
        session.api()?.let { api -> apiCall { api.logout() } }
        session.signOut()
    }

    /** Exchanges a still-valid token for a fresh one; used when the app resumes. */
    suspend fun refreshIfPossible(): Boolean {
        val current = session.activeSession() ?: return false
        if (current.authMode != AuthMode.PASSWORD) return false
        val api = session.api() ?: return false
        return when (val result = apiCall { api.refreshToken() }) {
            is ApiResult.Success -> {
                session.updateToken(result.value.accessToken)
                true
            }
            is ApiResult.Failure -> false
        }
    }

    private suspend fun finish(
        baseUrl: String,
        token: String,
        authMode: AuthMode,
        username: String?,
        serverVersion: String,
    ): ConnectResult {
        val (_, apiFor) = clientBuilder(token)
        return when (val user = apiCall { apiFor(baseUrl).currentUser() }) {
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
                ),
            )
        }
    }

    companion object {
        /** Normalizes user input and returns the canonical base URL, or `null` if invalid. */
        fun normalize(rawUrl: String): String? =
            (MealieUrl.parse(rawUrl) as? MealieUrl.Result.Valid)?.let { MealieUrl.canonical(it.url) }

        private fun defaultClient(token: String?): Pair<OkHttpClient, (String) -> MealieApi> {
            val client = MealieClientFactory.okHttpClient(TokenProvider { token })
            return client to { baseUrl -> MealieClientFactory.api(baseUrl, client) }
        }
    }
}
