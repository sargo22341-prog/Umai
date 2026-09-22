package org.opensources.umai.core.network

/**
 * User-facing classification of everything that can go wrong when talking to a
 * Mealie instance. Kept free of Android types so it can be unit-tested and
 * mapped to localized strings in the UI layer.
 */
sealed interface NetworkError {
    /** No route to the server, DNS failure, airplane mode, wrong host. */
    data object Unreachable : NetworkError

    /** The request took too long. */
    data object Timeout : NetworkError

    /** TLS handshake or certificate validation failure. */
    data class Tls(val detail: String?) : NetworkError

    /** 401/403 - token expired, revoked, or insufficient permissions. */
    data object Unauthorized : NetworkError

    /** 404 on a resource that should exist. */
    data object NotFound : NetworkError

    /** 5xx. */
    data class Server(val code: Int) : NetworkError

    /** Any other HTTP status, with the server's `detail` message when present. */
    data class Http(val code: Int, val detail: String?) : NetworkError

    /** The response body did not match the OpenAPI contract. */
    data object InvalidResponse : NetworkError

    /** The host answered but does not look like a Mealie instance. */
    data object NotMealie : NetworkError

    data class Unknown(val detail: String?) : NetworkError
}

/** True when retrying the very same request can plausibly succeed. */
val NetworkError.isRetryable: Boolean
    get() = when (this) {
        is NetworkError.Unreachable, is NetworkError.Timeout, is NetworkError.Server -> true
        is NetworkError.Http -> code == 429 || code == 408
        else -> false
    }
