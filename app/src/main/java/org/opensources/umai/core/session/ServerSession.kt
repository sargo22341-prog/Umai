package org.opensources.umai.core.session

/** How the user proved their identity to the Mealie instance. */
enum class AuthMode {
    /** Username + password exchanged for a token on `/api/auth/token`. */
    PASSWORD,

    /** A long-lived API token created from the Mealie web UI. */
    API_TOKEN,
}

/**
 * Everything Umai needs to talk to one Mealie instance. The token is only ever
 * held in memory here; on disk it is encrypted by [SecretVault].
 */
data class ServerSession(
    val baseUrl: String,
    val token: String,
    val authMode: AuthMode,
    val username: String?,
    val userId: String?,
    val userDisplayName: String?,
    val serverVersion: String?,
    /** Mealie lets an administrator delete anyone's comment. */
    val isAdmin: Boolean = false,
    /** Cache key of the profile picture, refreshed on every upload. */
    val avatarCacheKey: String? = null,
) {
    val isCleartext: Boolean get() = baseUrl.startsWith("http://", ignoreCase = true)

    /** Never include [token] in logs or crash reports. */
    override fun toString(): String =
        "ServerSession(baseUrl=$baseUrl, authMode=$authMode, username=$username, token=***)"
}

sealed interface SessionState {
    /** Preferences have not been read yet. */
    data object Loading : SessionState

    /** No instance has been set up, the app must show the setup screen. */
    data object NotConfigured : SessionState

    data class Active(val session: ServerSession) : SessionState

    /** A server is configured but the token was rejected; the user must sign in again. */
    data class Expired(val baseUrl: String, val username: String?) : SessionState
}
