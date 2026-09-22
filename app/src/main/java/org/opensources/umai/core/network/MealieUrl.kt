package org.opensources.umai.core.network

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Normalisation of the server address typed by the user.
 *
 * Mealie is almost always self-hosted, so the address can be a bare custom
 * domain (`mealie.ndd.custom`), an address with a port (`192.168.1.10:9000`),
 * or an instance served under a sub-path (`https://home.lan/mealie`). All of
 * those are accepted; only the shape of the URL is validated here, reachability
 * is decided by the first request.
 */
object MealieUrl {

    sealed interface Result {
        data class Valid(val url: HttpUrl, val isCleartext: Boolean) : Result
        data object Empty : Result
        data object Malformed : Result
        data object UnsupportedScheme : Result
    }

    fun parse(raw: String): Result {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return Result.Empty

        val lower = trimmed.lowercase()
        val schemeSeparator = lower.indexOf("://")
        val withScheme = when {
            lower.startsWith("http://") || lower.startsWith("https://") -> trimmed
            // A non-empty scheme that is neither http nor https: say so, rather
            // than pretending the address is simply unreadable.
            schemeSeparator > 0 && lower.take(schemeSeparator).all { it.isLetterOrDigit() } ->
                return Result.UnsupportedScheme
            schemeSeparator >= 0 -> return Result.Malformed
            else -> "https://$trimmed"
        }

        val parsed = withScheme.toHttpUrlOrNull() ?: return Result.Malformed
        if (parsed.host.isBlank() || !parsed.host.contains(Regex("[A-Za-z0-9]"))) return Result.Malformed

        // Always keep a trailing slash: Retrofit resolves relative paths against it.
        val normalized = parsed.newBuilder()
            .query(null)
            .fragment(null)
            .build()
            .let { url ->
                val path = url.encodedPath.trimEnd('/')
                url.newBuilder().encodedPath(if (path.isEmpty()) "/" else "$path/").build()
            }

        return Result.Valid(normalized, isCleartext = normalized.scheme == "http")
    }

    /** Canonical string form stored in preferences, e.g. `https://mealie.ndd.custom/`. */
    fun canonical(url: HttpUrl): String = url.toString()
}
