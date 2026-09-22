package org.opensources.umai.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.opensources.umai.R
import org.opensources.umai.core.network.NetworkError

/**
 * Turns a [NetworkError] into a sentence the user can act on. A raw stack trace
 * or an HTTP status alone is never shown.
 */
@Composable
fun NetworkError.title(): String = when (this) {
    NetworkError.Unreachable -> stringResource(R.string.error_unreachable_title)
    NetworkError.Timeout -> stringResource(R.string.error_timeout_title)
    is NetworkError.Tls -> stringResource(R.string.error_tls_title)
    NetworkError.Unauthorized -> stringResource(R.string.error_unauthorized_title)
    NetworkError.NotFound -> stringResource(R.string.error_not_found_title)
    is NetworkError.Server -> stringResource(R.string.error_server_title)
    is NetworkError.Http -> stringResource(R.string.error_http_title)
    NetworkError.InvalidResponse -> stringResource(R.string.error_invalid_response_title)
    NetworkError.NotMealie -> stringResource(R.string.error_not_mealie_title)
    is NetworkError.Unknown -> stringResource(R.string.error_unknown_title)
}

@Composable
fun NetworkError.message(): String = when (this) {
    NetworkError.Unreachable -> stringResource(R.string.error_unreachable_message)
    NetworkError.Timeout -> stringResource(R.string.error_timeout_message)
    is NetworkError.Tls -> stringResource(R.string.error_tls_message)
    NetworkError.Unauthorized -> stringResource(R.string.error_unauthorized_message)
    NetworkError.NotFound -> stringResource(R.string.error_not_found_message)
    is NetworkError.Server -> stringResource(R.string.error_server_message, code)
    // The server's own `detail` is shown when it exists: it is usually the most
    // precise explanation available, and Mealie localizes it itself.
    is NetworkError.Http -> detail ?: stringResource(R.string.error_http_message, code)
    NetworkError.InvalidResponse -> stringResource(R.string.error_invalid_response_message)
    NetworkError.NotMealie -> stringResource(R.string.error_not_mealie_message)
    is NetworkError.Unknown -> stringResource(R.string.error_unknown_message)
}
