package org.opensources.umai.core.network

import kotlinx.serialization.json.Json
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
     * [instanceHost], when set, is the only host that receives the token and
     * whose 401 answers invalidate the session. The same client also loads
     * pictures and media from other sites (a recipe source, a CDN), which must
     * never see the Mealie credentials nor sign the user out.
     */
    fun okHttpClient(
        tokenProvider: TokenProvider,
        unauthorizedListener: UnauthorizedListener? = null,
        acceptLanguage: () -> String = { defaultAcceptLanguage() },
        instanceHost: String? = null,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor(HeaderInterceptor(tokenProvider, acceptLanguage, instanceHost))
        .apply {
            unauthorizedListener?.let { addInterceptor(UnauthorizedInterceptor(it, instanceHost)) }
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
    private val instanceHost: String?,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val builder: Request.Builder = request.newBuilder()
            .header("Accept", "application/json")
            .header("Accept-Language", acceptLanguage())
        if (request.isForInstance(instanceHost)) {
            tokenProvider.token()?.takeIf { it.isNotBlank() }?.let {
                builder.header("Authorization", "Bearer $it")
            }
        }
        return chain.proceed(builder.build())
    }
}

private class UnauthorizedInterceptor(
    private val listener: UnauthorizedListener,
    private val instanceHost: String?,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.code == 401 && chain.request().isForInstance(instanceHost)) listener.onUnauthorized()
        return response
    }
}

private fun Request.isForInstance(instanceHost: String?): Boolean =
    instanceHost == null || url.host.equals(instanceHost, ignoreCase = true)
