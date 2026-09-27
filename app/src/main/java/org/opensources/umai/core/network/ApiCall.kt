package org.opensources.umai.core.network

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.opensources.umai.core.network.api.MealieApi
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

inline fun <T, R> ApiResult<T>.flatMap(transform: (T) -> ApiResult<R>): ApiResult<R> = when (this) {
    is ApiResult.Success -> transform(value)
    is ApiResult.Failure -> this
}

fun <T> ApiResult<T>.valueOrNull(): T? = (this as? ApiResult.Success)?.value

/** The failure, or `null` on success: for a call whose value says nothing, such as an upload. */
fun ApiResult<*>.failureOrNull(): ApiResult.Failure? = this as? ApiResult.Failure

/**
 * The value, or what [onFailure] makes of the failure: `valueOr { return it }`
 * hands it straight back to the caller.
 */
inline fun <T> ApiResult<T>.valueOr(onFailure: (ApiResult.Failure) -> Nothing): T = when (this) {
    is ApiResult.Success -> value
    is ApiResult.Failure -> onFailure(this)
}

/** A value Mealie should have sent and did not: the answer is off the OpenAPI contract. */
fun <T : Any> ApiResult<T?>.orInvalid(): ApiResult<T> =
    flatMap { value -> value?.let { ApiResult.Success(it) } ?: ApiResult.Failure(NetworkError.InvalidResponse) }

/**
 * Runs [block] on the API of the configured instance, through [apiCall]; with
 * no instance set up, the call fails as [NetworkError.Unauthorized].
 */
suspend inline fun <T> (() -> MealieApi?).call(crossinline block: suspend MealieApi.() -> T): ApiResult<T> {
    val api = this() ?: return ApiResult.Failure(NetworkError.Unauthorized)
    return apiCall { api.block() }
}

/**
 * Runs a suspending Retrofit call and turns every failure mode into a
 * [NetworkError]. Cancellation is rethrown so structured concurrency keeps
 * working (screens cancel their in-flight requests when they leave).
 */
suspend inline fun <T> apiCall(crossinline block: suspend () -> T): ApiResult<T> = try {
    ApiResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    // Every exception a call can end with becomes a NetworkError; an Error
    // (out of memory, a broken build) is not the network's and goes on.
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
    private fun detailOf(e: HttpException): String? {
        val body = try {
            e.response()?.errorBody()?.string().orEmpty()
        } catch (_: IOException) {
            // The status alone still tells what went wrong.
            return null
        }
        if (body.isBlank()) return null
        val root = try {
            lenientJson.parseToJsonElement(body) as? JsonObject
        } catch (_: SerializationException) {
            null
        } ?: return null
        return when (val detail = root["detail"]) {
            is JsonObject -> (detail["message"] as? JsonPrimitive)?.contentOrNull
            is JsonPrimitive -> detail.contentOrNull
            // A list of validation errors, which only a developer could read.
            is JsonArray, null -> null
        }
    }
}
