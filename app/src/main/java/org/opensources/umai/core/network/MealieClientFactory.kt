package org.opensources.umai.core.network

import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import org.opensources.umai.BuildConfig
import org.opensources.umai.core.network.api.MealieApi
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Supplies the bearer token for outgoing calls; `null` means anonymous. */
fun interface TokenProvider {
    fun token(): String?
}

/** Called when the server rejects the token, so the session can be invalidated once. */
fun interface UnauthorizedListener {
    fun onUnauthorized()
}

/**
 * Builds the OkHttp/Retrofit stack for one Mealie instance.
 *
 * TLS is left entirely to the platform: the app never installs a permissive
 * trust manager or hostname verifier. Private certificate authorities are
 * supported through the user CA store (see `res/xml/network_security_config.xml`).
 */
object MealieClientFactory {

    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        isLenient = true
    }

    private val jsonMediaType = "application/json".toMediaType()

    /**
     * What every client of the app shares: the timeouts, and one connection
     * pool and dispatcher, so a client built for a sign-in attempt or for
     * another instance leaves no connections or threads of its own behind.
     */
    private val sharedClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Only requests to [instance] — same scheme, host and port — receive the
     * token, and only their 401 answers invalidate the session; without an
     * instance, no request does. The same client also loads pictures and media
     * from other sites (a recipe source, a CDN), which must never see the
     * Mealie credentials nor sign the user out, and a link to the instance in
     * plain HTTP must not carry the token of an HTTPS instance in clear.
     */
    fun okHttpClient(
        instance: HttpUrl?,
        tokenProvider: TokenProvider,
        unauthorizedListener: UnauthorizedListener? = null,
        acceptLanguage: () -> String = { defaultAcceptLanguage() },
    ): OkHttpClient = sharedClient.newBuilder()
        .addInterceptor(HeaderInterceptor(tokenProvider, acceptLanguage, instance))
        .apply {
            unauthorizedListener?.let { addInterceptor(UnauthorizedInterceptor(it, instance)) }
            // Request logging exists only in debug builds, and the Authorization
            // header is redacted so a token can never reach logcat.
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                        redactHeader("Authorization")
                        redactHeader("Cookie")
                        redactHeader("Set-Cookie")
                    },
                )
            }
        }
        .build()

    fun retrofit(baseUrl: String, client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory(jsonMediaType))
        .build()

    fun api(baseUrl: String, client: OkHttpClient): MealieApi =
        retrofit(baseUrl, client).create(MealieApi::class.java)

    private fun defaultAcceptLanguage(): String = Locale.getDefault().toLanguageTag()
}

private class HeaderInterceptor(
    private val tokenProvider: TokenProvider,
    private val acceptLanguage: () -> String,
    private val instance: HttpUrl?,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val builder: Request.Builder = request.newBuilder()
            .header("Accept", "application/json")
            .header("Accept-Language", acceptLanguage())
        if (request.isForInstance(instance)) {
            tokenProvider.token()?.takeIf { it.isNotBlank() }?.let {
                builder.header("Authorization", "Bearer $it")
            }
        }
        return chain.proceed(builder.build())
    }
}

private class UnauthorizedInterceptor(
    private val listener: UnauthorizedListener,
    private val instance: HttpUrl?,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.code == 401 && chain.request().isForInstance(instance)) listener.onUnauthorized()
        return response
    }
}

private fun Request.isForInstance(instance: HttpUrl?): Boolean =
    instance != null && url.scheme == instance.scheme && url.host == instance.host && url.port == instance.port
