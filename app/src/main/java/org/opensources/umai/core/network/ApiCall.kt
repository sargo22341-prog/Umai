package org.opensources.umai.core.network

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

/** Either the decoded payload or a classified [NetworkError]. */
sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class Failure(val error: NetworkError) : ApiResult<Nothing>
}

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(value))
    is ApiResult.Failure -> this
}

fun <T> ApiResult<T>.valueOrNull(): T? = (this as? ApiResult.Success)?.value

/**
 * Runs a suspending Retrofit call and turns every failure mode into a
 * [NetworkError]. Cancellation is rethrown so structured concurrency keeps
 * working (screens cancel their in-flight requests when they leave).
 */
suspend inline fun <T> apiCall(crossinline block: suspend () -> T): ApiResult<T> = try {
    ApiResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    ApiResult.Failure(NetworkErrorMapper.map(e))
}

object NetworkErrorMapper {

    private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

    fun map(throwable: Throwable): NetworkError = when (throwable) {
        is HttpException -> fromHttp(throwable)
        is SSLPeerUnverifiedException -> NetworkError.Tls(throwable.message)
        is SSLHandshakeException -> NetworkError.Tls(throwable.message)
        is SSLException -> NetworkError.Tls(throwable.message)
        is SocketTimeoutException -> NetworkError.Timeout
        is UnknownHostException -> NetworkError.Unreachable
        is ConnectException -> NetworkError.Unreachable
        is EOFException -> NetworkError.InvalidResponse
        is SerializationException -> NetworkError.InvalidResponse
        is IOException -> NetworkError.Unreachable
        else -> NetworkError.Unknown(throwable.message)
    }

    private fun fromHttp(e: HttpException): NetworkError = when (val code = e.code()) {
        401, 403 -> NetworkError.Unauthorized
        404 -> NetworkError.NotFound
        in 500..599 -> NetworkError.Server(code)
        else -> NetworkError.Http(code, detailOf(e))
    }

    /** Mealie reports errors as `{"detail": "..."}` or `{"detail": {"message": "..."}}`. */
    private fun detailOf(e: HttpException): String? = runCatching {
        val body = e.response()?.errorBody()?.string().orEmpty()
        if (body.isBlank()) return null
        val root = lenientJson.parseToJsonElement(body) as? JsonObject ?: return null
        when (val detail = root["detail"]) {
            is JsonObject -> detail["message"]?.jsonPrimitive?.content
            null -> null
            else -> detail.jsonPrimitive.content
        }
    }.getOrNull()
}
